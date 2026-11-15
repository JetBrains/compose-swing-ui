// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.LocalInspectionTables
import androidx.compose.testutils.ComposeTestCase
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.core.MotionDurationScale
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.setContent
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.util.Collections
import java.util.EventListener
import java.util.IdentityHashMap
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.RepaintManager
import kotlin.coroutines.CoroutineContext

/**
 * Drives one group of subjects on the production Swing frame clock, so a measured toggle runs the same recomposition,
 * layout and paint path a real window does.
 *
 * Construct, use, and close it on the EDT. A Swing timer would otherwise deliver frames at wall-clock times and change
 * the event counts between runs, so timer ticks are held for the control's lifetime and posted when it settles or
 * closes. Effects receive a zero [MotionDurationScale] so animations finish deterministically. Frame-driven work must
 * still settle before a measurement completes.
 *
 * @param instances the subjects composed in order under one vertical panel.
 * @property coroutineContext the calling coroutine whose cancellation stops pending work. A control that outlives the
 * coroutine that created it is assigned the coroutine of each later invocation that drives it.
 * @param caseName the subject identified when pending work is cancelled.
 */
class SwingExecutionControl(
    private val instances: List<ComposeTestCase>,
    var coroutineContext: CoroutineContext,
    private val caseName: String = "Swing benchmark",
) : AutoCloseable {
    /** The Swing host that owns the component tree, layout and offscreen painting. */
    val host: Host = Host()
    private lateinit var driver: SwingRecomposer
    private lateinit var recomposer: Recomposer
    private lateinit var compositionMetrics: CompositionMetrics
    private var content: DisposableHandle? = null
    private var contentVisible by mutableStateOf(false)
    private val compositionData = mutableSetOf<CompositionData>()

    /** The first failure the recomposer reported, which the recomposer state alone does not carry. */
    private var compositionFailure: Throwable? = null
    private var closed = false
    private val teardownSteps =
        listOf<() -> Unit>(
            { content?.dispose() },
            {
                content = null
                compositionData.clear()
            },
            { if (this::compositionMetrics.isInitialized) compositionMetrics.close() },
            { if (this::driver.isInitialized) driver.dispose() },
            { if (this::recomposer.isInitialized) doFrame() },
            { BenchmarkInstrumentation.eventQueue.holdingTicks = false },
            { host.close() },
        )

    init {
        runCatching {
            BenchmarkInstrumentation.eventQueue.holdingTicks = true
            host.panel.addNotify()
            driver = SwingRecomposer.create(host.panel, effectContext = NoMotion + EffectStarts())
            recomposer = driver.compositionContext as Recomposer
            recomposer.effectCoroutineContext[Job]?.invokeOnCompletion { failure ->
                if (compositionFailure == null) compositionFailure = failure
            }
            compositionMetrics = CompositionMetrics(recomposer) { BenchmarkInstrumentation.metrics }
            doFrame()
        }.onFailure { failure ->
            runCatching { close() }.onFailure { if (it !== failure) failure.addSuppressed(it) }
        }.getOrThrow()
    }

    /** Composes the subjects into an empty host. */
    fun compose() {
        contentVisible = true
        if (content == null) {
            content =
                runCatching {
                    host.panel.setContent(parent = driver.compositionContext) {
                        compositionData += currentComposer.compositionData
                        CompositionLocalProvider(LocalInspectionTables provides compositionData) {
                            if (contentVisible) {
                                Panel(PanelLayout.Box(axis = BoxLayout.Y_AXIS)) {
                                    instances.forEach { it.Content() }
                                }
                            }
                        }
                    }
                }.onFailure { compositionFailure = it }.getOrThrow()
        }
    }

    /** Removes the subjects on the next frame while keeping their composition alive. */
    fun disposeContent() {
        contentVisible = false
    }

    /**
     * Drains composition, frame-clock and posted-event work without requesting paint.
     *
     * @param onFrame runs on each frame probe while work remains, so layout that frame work waits for can happen
     * between frames. It must not call back into this control's drain.
     */
    fun recompose(onFrame: () -> Unit = {}) {
        drain(onFrame)
    }

    /**
     * Drives composition, layout and dirty-region painting, including the work those steps post.
     *
     * @throws IllegalStateException if the recomposer shuts down before this control is disposed,
     * with the reported content failure as its cause.
     */
    fun doFrame() {
        drain {
            host.layout()
            host.drawDirty()
        }
    }

    /**
     * Runs every phase until no recomposition, frame awaiter, posted event, invalid container or dirty region remains.
     *
     * @throws IllegalStateException if the recomposer shuts down before this control is disposed,
     * with the reported content failure as its cause.
     */
    fun settle() {
        do {
            doFrame()
            host.layout()
            host.drawDirty()
        } while (hasPendingChanges() || host.hasPendingLayout || host.hasPendingDraw)
    }

    /**
     * Pumps the Swing event queue until [hasPendingChanges] is false, running [onProbe] on every probe.
     *
     * @param onProbe runs after the cancellation check of each probe; a failure it throws ends the
     * loop and is rethrown to the caller.
     * @throws IllegalStateException if the recomposer shuts down before this control is disposed.
     */
    @OptIn(InternalCoroutinesApi::class)
    private fun drain(onProbe: () -> Unit) {
        val verify = {
            check(!cancelled) {
                "$caseName did not settle before the calling coroutine was cancelled"
            }
        }
        verify()
        if (hasPendingChanges()) {
            val queue = Toolkit.getDefaultToolkit().systemEventQueue
            val loop = queue.createSecondaryLoop()
            var probing = true
            var probeFailure: Throwable? = null

            fun probe() {
                if (!probing) return
                probeFailure =
                    runCatching {
                        verify()
                        onProbe()
                    }.exceptionOrNull()
                if (probeFailure != null || !hasPendingChanges()) {
                    BenchmarkInstrumentation.eventQueue.exitLoop(loop)
                } else {
                    if (!BenchmarkInstrumentation.eventQueue.eventPending()) {
                        val frameClock = recomposer.effectCoroutineContext[MonotonicFrameClock] as BroadcastFrameClock
                        BenchmarkInstrumentation.eventQueue.releaseTicks {
                            if (frameClock.hasAwaiters) BenchmarkInstrumentation.metrics?.add("eventsPosted")
                        }
                    }
                    BenchmarkInstrumentation.eventQueue.probe(::probe)
                }
            }
            BenchmarkInstrumentation.eventQueue.probe(::probe)
            // A subject that reposts an event at normal priority starves the probe, which runs at paint priority, so
            // only cancellation itself can end the loop.
            // The loop's exit, from the probe or from cancellation, may precede its entry, which then returns false
            // having nothing left to wait for.
            val cancellation =
                coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) {
                    BenchmarkInstrumentation.eventQueue.exitLoop(loop)
                }
            try {
                loop.enter()
            } finally {
                probing = false
                cancellation?.dispose()
            }
            probeFailure?.let { throw it }
        }
        if (recomposerStopped) {
            throw IllegalStateException("Recomposer is ${recomposer.currentState.value}", compositionFailure)
        }
    }

    /** Whether a control that is still open has lost its recomposer. */
    private val recomposerStopped: Boolean
        get() =
            !closed &&
                recomposer.currentState.value.let {
                    it == Recomposer.State.ShuttingDown ||
                        it == Recomposer.State.ShutDown
                }

    /** Whether the calling coroutine was cancelled while the control is open; a closing control must still drain. */
    private val cancelled: Boolean
        get() = !closed && coroutineContext[Job]?.isCancelled == true

    /** Whether recomposition, frame-clock or posted-event work remains. */
    fun hasPendingChanges(): Boolean {
        val frameClock = recomposer.effectCoroutineContext[MonotonicFrameClock] as BroadcastFrameClock
        return if (closed) {
            BenchmarkInstrumentation.eventQueue.eventPending()
        } else {
            !recomposerStopped &&
                (
                    recomposer.hasPendingWork ||
                        frameClock.hasAwaiters ||
                        BenchmarkInstrumentation.eventQueue.eventPending() ||
                        BenchmarkInstrumentation.eventQueue.tickHeld
                )
        }
    }

    /** Measures a change and settles all the work it causes. */
    internal fun measure(toggle: () -> Unit): Map<String, Long> {
        val before = compositionSize(compositionData)
        val previousComponents = componentsOf(host.panel)
        val previousListeners = listenersOf(host.panel)
        val metrics = ToggleMetrics()
        host.drawDirty()
        BenchmarkInstrumentation.metrics = metrics
        try {
            toggle()
            settle()
            metrics.add("componentsAdded", componentsOf(host.panel).count { it !in previousComponents }.toLong())
            metrics.add("listenersCreated", listenersOf(host.panel).count { it !in previousListeners }.toLong())
            compositionSize(compositionData).forEach { (name, value) ->
                metrics.add(name, value - before.getValue(name))
            }
            return metrics.snapshot()
        } finally {
            BenchmarkInstrumentation.metrics = null
        }
    }

    /** Disposes the composition and driver, drains teardown work and closes the host. */
    override fun close() {
        if (closed) return
        closed = true
        var failure: Throwable? = null
        teardownSteps.forEach { step ->
            runCatching(step).onFailure { next ->
                val first = failure
                if (first == null) {
                    failure = next
                } else if (first !== next) {
                    first.addSuppressed(next)
                }
            }
        }
        failure?.let { throw it }
    }

    /**
     * The Swing component tree of one [SwingExecutionControl], with the offscreen image its paints land in.
     *
     * Use it on the EDT. Closing restores the repaint manager that was current when it was created.
     */
    class Host internal constructor() : AutoCloseable {
        // Any fixed size works; [image] and the swing twins take their size from the host.
        internal val panel = JPanel(BorderLayout()).apply { setSize(800, 600) }

        /** The offscreen image that [drawDirty] and [drawToBitmap] paint into; transparent until the first paint. */
        val image: BufferedImage = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB)
        private val graphics: Graphics2D = image.createGraphics()
        private val previousRepaintManager = RepaintManager.currentManager(panel)
        private val repaintManager = CountingRepaintManager(panel) { BenchmarkInstrumentation.metrics }
        private var rawSwingPanel: JPanel? = null

        init {
            RepaintManager.setCurrentManager(repaintManager)
        }

        /** Whether a container in the host needs [layout]. */
        val hasPendingLayout: Boolean
            get() = invalidContainers(panel) > 0

        /** Whether the repaint manager holds a dirty region that [drawDirty] has not painted. */
        val hasPendingDraw: Boolean
            get() = repaintManager.dirtyRectangle() != null

        /**
         * Adds the raw Swing twin of every subject to the host under one vertical panel. Calling it again after
         * [disposeRawSwing] puts the same components back.
         *
         * Returns false and adds nothing when a subject has no raw Swing twin that a container can hold.
         */
        fun mountRawSwing(subjects: List<FixtureSubject>): Boolean {
            val twins =
                rawSwingPanel
                    ?: JPanel()
                        .apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
                        .takeIf { created -> subjects.all { it.mountRawSwing(created) } }
            twins?.let(panel::add)
            rawSwingPanel = twins
            return twins != null
        }

        /** Removes the raw Swing twins from the host while keeping them alive for [mountRawSwing]. */
        fun disposeRawSwing() {
            rawSwingPanel?.let(panel::remove)
        }

        /** Validates the host after recording the invalid containers, without painting. */
        fun layout() {
            BenchmarkInstrumentation.metrics?.add("layouts", invalidContainers(panel))
            panel.validate()
        }

        /** Paints the coalesced dirty region into [image], or only clears the dirty state when nothing is dirty. */
        fun drawDirty() {
            val rectangle = repaintManager.dirtyRectangle()
            if (rectangle == null) {
                repaintManager.clear()
            } else {
                paintRegion(rectangle)
            }
        }

        /** Paints the complete host into [image], even when no region is dirty. */
        fun drawToBitmap() {
            paintRegion(panel.bounds)
        }

        /** Hashes the host's component tree after the most recent step with [componentDigest]. */
        fun digest(): Long = componentDigest(panel)

        /**
         * Paints [region] into [image] after recording its metrics. The dirty state is cleared before painting, so
         * dirt that painting raises is kept for the next [drawDirty].
         *
         * @param region the clip to paint, in host coordinates.
         */
        private fun paintRegion(region: Rectangle) {
            BenchmarkInstrumentation.metrics?.add("dirtyArea", region.width.toLong() * region.height)
            BenchmarkInstrumentation.metrics?.add(
                "componentsInDirtyRegion",
                repaintManager.componentsInDirtyRegion(region),
            )
            repaintManager.clear()
            graphics.clip = region
            panel.paint(graphics)
        }

        /** Detaches the host, restores the previous repaint manager and releases the graphics. */
        override fun close() {
            try {
                panel.removeNotify()
            } finally {
                try {
                    RepaintManager.setCurrentManager(previousRepaintManager)
                } finally {
                    graphics.dispose()
                }
            }
        }
    }
}

/** The components of [host] and its descendants, compared by identity to count the ones a toggle creates. */
private fun componentsOf(host: Component): Set<Component> {
    val components = Collections.newSetFromMap(IdentityHashMap<Component, Boolean>())
    componentTree(host).forEach(components::add)
    return components
}

/** The listeners registered on [host] and its descendants, compared by identity to count the ones a toggle creates. */
private fun listenersOf(host: Component): Set<EventListener> {
    val listeners = Collections.newSetFromMap(IdentityHashMap<EventListener, Boolean>())
    componentTree(host).forEach { listeners.addAll(componentListeners(it)) }
    return listeners
}

/** Makes every animation finish in one frame, so repeated runs count the same work. */
private object NoMotion : MotionDurationScale {
    override val scaleFactor: Float = 0f
}

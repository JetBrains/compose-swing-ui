package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.core.SwingFrameClock.Companion.displayRefreshRate
import org.jetbrains.compose.swing.util.DeferredAction
import java.awt.Component
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * What a composition is driven by: a single [Recomposer], the frame clock it recomposes on, and the
 * [CoroutineScope] they run on. Every content composition nested into one of these recomposes on one
 * recomposer and one frame clock.
 *
 * Frame-driven work is cadenced to the display the component this was created for is on, and follows
 * that component across displays: a component reports every [java.awt.GraphicsConfiguration] change,
 * including the ones an ancestor propagates down to it.
 *
 * Content is mounted on it by passing its [compositionContext] as the parent of a content composition.
 *
 * It holds a live coroutine scope and a Swing timer, so it has to be [dispose]d. A caller handed one by
 * [create] owns it and decides when it ends; the one a window is given is this library's own and ends
 * itself once nothing composes under it any more.
 *
 * A failure ends it too. The runtime records the first throw and recomposes nothing after it - from a
 * recomposition pass, from content's first composition, or from an effect the content launched - so this
 * is [dispose]d as soon as the runtime reports it stopped. A pass or an effect that throws is reported
 * through the thread's uncaught-exception handler once; a first composition's throw reaches whoever
 * composed it. A pass or an effect reports the stop as it happens.
 * A first composition records the failure without deriving anything, so its stop is reported only on
 * the next change reaching the runtime - a state write, or the settlement an input event queues - and
 * content set in between composes once and is torn down with the rest.
 *
 * Marked [InternalSwingUiApi]; it may change without notice in any release.
 */
@InternalSwingUiApi
public class SwingRecomposer private constructor(
    internal val recomposer: Recomposer,
    internal val clock: SwingFrameClock,
    private val scope: CoroutineScope,
    private val refreshRateWatch: DisposableHandle,
    private val disposeOnceUnused: Boolean,
    private val onDisposed: () -> Unit,
) {
    private var disposed = false

    /** What has been disposed drives nothing and is never handed out again. */
    internal val isDisposed: Boolean
        get() = disposed

    /**
     * Disposes this once the last content composition registered with it is gone, on the turn after the
     * one that took it away. Deferred rather than immediate because a container moving between windows
     * withdraws its registration and takes one up again a turn later: what a content composition is on
     * its way back to is still in use.
     *
     * Installed only where this library both creates the recomposer and keeps it - a window's own. A
     * caller who was handed one owns it and ends it themselves.
     */
    private val disposeIfStillUnused = DeferredAction { if (contentCompositions.isEmpty()) dispose() }

    /**
     * Ends a recomposer this library keeps on the next turn unless something registers with it first;
     * see [disposeIfStillUnused]. A no-op on one a caller owns.
     */
    internal fun disposeIfUnused() {
        if (disposeOnceUnused) disposeIfStillUnused.schedule()
    }

    /** The content compositions registered as composing under this; see [registerContentComposition]. */
    private val contentCompositions = LinkedHashSet<DisposableHandle>()

    /** The context to pass as the parent of a content composition this recomposer drives. */
    public val compositionContext: CompositionContext
        get() = recomposer

    init {
        // Launched first, and on the same FIFO dispatcher the watch below runs on: the runner registers
        // itself with the recomposer before it first suspends, so the watch's first turn already reads
        // the state that registration left behind.
        scope.launch(clock) {
            // Every type is caught because what a pass throws is the content's to choose. A cancellation
            // is the scope ending, which is a disposal rather than a failure: an effect that throws is
            // reported by the handler the scope carries, not from here.
            @Suppress("TooGenericExceptionCaught")
            try {
                recomposer.runRecomposeAndApplyChanges()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                reportUncaught(failure)
            }
        }
        // The runtime answers Idle or PendingWork for as long as it recomposes, and falls to Inactive once
        // it has recorded a failure, so a fall out of those two states is the recomposer stopping - for an
        // effect that throws as well as for a pass that does.
        // Cancelling in dispose() falls out of them too, and finds disposed set. Where a first composition
        // in the event this was created in has already failed, the first turn reads that Inactive and
        // disposes before the start-up frame the runner queued, so nothing recomposes under it.
        scope.launch {
            recomposer.currentState.first { !it.isRecomposing }
            dispose()
        }
    }

    /**
     * Registers [content] to be disposed with this recomposer, which is how a window's teardown reaches
     * content whose container hangs off no window at all - content no walk over a component tree can
     * find.
     *
     * Content registers itself when it composes under this recomposer or adopts its window, and
     * [deregisterContentComposition]s when it is disposed, composes under another recomposer, or leaves
     * the window mid-move for its queued rejoin to place again. Two kinds of content stand outside the
     * set: content composed under a caller-named context that is no window's own, while its container
     * hangs off no window and stands inside no other content, registers with no recomposer until its
     * container adopts a window; and content on a recomposer of its caller's own registers with none at
     * all. Either is disposed by its own
     * handle, and by the teardown of a window whose tree its container stands in when that window closes.
     *
     * A disposed recomposer keeps no set to be disposed with, so it disposes [content] on the spot rather
     * than hold it where no teardown would ever reach it again.
     */
    internal fun registerContentComposition(content: DisposableHandle) {
        if (disposed) {
            content.dispose()
            return
        }
        contentCompositions += content
    }

    /**
     * Withdraws a registration made by [registerContentComposition], ending a recomposer this library
     * keeps once its last one is gone; see [disposeIfStillUnused].
     */
    internal fun deregisterContentComposition(content: DisposableHandle) {
        // Withdrawing what is not registered ends nothing: the walk in dispose() clears the set before
        // disposing what stood in it, and each of those disposals arrives here.
        if (!contentCompositions.remove(content)) return
        if (disposeOnceUnused && contentCompositions.isEmpty()) disposeIfStillUnused.schedule()
    }

    /**
     * Disposes the content compositions registered as composing under this recomposer, then cancels the
     * recomposer, disposes the clock - which is what withdraws it from [EventDispatchHook] - and cancels
     * the scope they run on. Idempotent.
     *
     * Must be called on the Event Dispatch Thread.
     */
    public fun dispose() {
        checkEventDispatchThread()
        if (disposed) return
        disposed = true
        refreshRateWatch.dispose()
        // Disposed before the recomposer is cancelled, so every content composition tears down on a live
        // one. The set is cleared first: each disposal deregisters itself, and clearing keeps that a
        // no-op while the copy is walked.
        val registered = contentCompositions.toList()
        contentCompositions.clear()
        registered.forEach(DisposableHandle::dispose)
        recomposer.cancel()
        clock.dispose()
        scope.cancel()
        onDisposed()
    }

    /** Creates what a component's composition is driven by. */
    @InternalSwingUiApi
    public companion object {
        /**
         * The bound property a [Component] fires when its [java.awt.GraphicsConfiguration] changes, i.e.
         * when it moves to a screen device with a potentially different display refresh rate.
         */
        private const val GRAPHICS_CONFIGURATION_PROPERTY: String = "graphicsConfiguration"

        /**
         * Starts a recomposer whose frame-driven work is paced by the display [component] is on, running
         * at a default cadence while that component is outside any container. This is what serves
         * content built to be read rather than shown, which reaches a composition with no window
         * anywhere in the picture.
         *
         * Creating it publishes nothing on [component], so a `setContent` resolving its parent from the
         * Swing tree reaches this recomposer only through content a caller has already mounted under it,
         * and two content compositions a window accounts for stay on one recomposer and one frame clock.
         *
         * The caller owns what is returned and decides when it ends: [dispose] it once the content it
         * drives is torn down. Content that belongs to a window joins that window's own instead.
         *
         * Must be called on the Event Dispatch Thread.
         *
         * @param component the component whose display sets the frame cadence. A listener is installed
         *   on it to retime the clock as it moves between displays, and [dispose] removes that listener.
         * @param effectContext extra elements for the context the content this recomposer drives runs
         *   its effects over - a [MotionDurationScale] stating how fast motion plays here, say. Overrides
         *   what [SwingUiSettings] states for the whole process; the
         *   dispatcher, job and failure handler this recomposer runs on are not overridable.
         * @return the started recomposer.
         */
        public fun create(
            component: Component,
            effectContext: CoroutineContext = EmptyCoroutineContext,
        ): SwingRecomposer = start(component, effectContext = effectContext, disposeOnceUnused = false, onDisposed = {})

        /**
         * Starts the recomposer a window owns: this library creates and keeps it, so it ends itself once
         * the last content composition registered with it is gone, running [onDisposed] when it does.
         *
         * Must be called on the Event Dispatch Thread.
         */
        internal fun forWindow(
            window: Component,
            onDisposed: () -> Unit,
        ): SwingRecomposer =
            start(window, effectContext = EmptyCoroutineContext, disposeOnceUnused = true, onDisposed = onDisposed)

        private fun start(
            component: Component,
            effectContext: CoroutineContext,
            disposeOnceUnused: Boolean,
            onDisposed: () -> Unit,
        ): SwingRecomposer {
            checkEventDispatchThread()
            GlobalSnapshotManager.ensureStarted()
            val dispatcher = SwingUiDispatcher()
            // A supervisor, so that the runtime's effect job failing does not carry the failure up and
            // cancel the watch that exists to notice the recomposer stopping: the runtime parents that
            // job on this one, and an effect a composition launches is its child. The handler is where
            // such an effect's failure is reported from, as a pass's own is reported by the runner.
            // Widest to narrowest: what the process states is overridden by what this caller states, and
            // both lose to the dispatcher, job and handler this recomposer cannot run without.
            val scope =
                CoroutineScope(
                    SwingUiSettings.motionDurationScale + effectContext + dispatcher + SupervisorJob() +
                        CoroutineExceptionHandler { _, failure -> reportUncaught(failure) },
                )
            val recomposer = Recomposer(scope.coroutineContext)
            val clock = dispatcher.frameClock
            clock.pace(recomposer)
            clock.setFramesPerSecond(component.displayRefreshRate())
            // Retime the clock when the component moves to a display with a different refresh rate. Fires
            // on the EDT; SwingFrameClock.setFramesPerSecond early-returns when the cadence is unchanged.
            val refreshRateWatch =
                onPropertyChanged(component, GRAPHICS_CONFIGURATION_PROPERTY) {
                    clock.setFramesPerSecond(component.displayRefreshRate())
                }
            EventDispatchHook.subscribe(component.toolkit, clock)
            return SwingRecomposer(recomposer, clock, scope, refreshRateWatch, disposeOnceUnused, onDisposed)
        }
    }
}

/**
 * Whether a recomposer in this state is running and recomposing what invalidates. The states are named
 * rather than compared by order, so a state added between them cannot change what this answers.
 */
private val Recomposer.State.isRecomposing: Boolean
    get() = this == Recomposer.State.Idle || this == Recomposer.State.PendingWork

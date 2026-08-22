package org.jetbrains.compose.swing.passcost.harness

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

/**
 * The composition runtime the arms are mounted on: a frame clock this module sends frames on, and a
 * recomposer running on the event dispatch thread.
 *
 * Standing this up directly is what separates the runtime's cost from a test harness's. Nothing here
 * waits for a widget to lay out or paint, so a pass costs what the composition costs and nothing else.
 *
 * Lifted from SwingMark's `harness/Frames.kt`, which stands the same runtime up for the same reason;
 * the frame protocol around it ([sendFrame], [hasFrameAwaiter]) is this module's own.
 */
internal object Frames {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(EventDispatchThread + Job() + clock)
    private val recomposer = Recomposer(scope.coroutineContext)

    /** The context an arm's content is mounted under, so this runtime drives it and no window's does. */
    val compositionContext: CompositionContext get() = recomposer

    /** Whether the recomposer is waiting for a frame, so a frame sent now reaches it. */
    val hasFrameAwaiter: Boolean get() = clock.hasAwaiters

    private var started = false

    /** Starts the recomposer, once. */
    fun start() {
        if (started) return
        started = true
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    /** Delivers a frame. A frame nobody waits for is dropped. Call on the event dispatch thread. */
    fun sendFrame() {
        clock.sendFrame(System.nanoTime())
    }

    /**
     * Raises unless the recomposer is still recomposing.
     *
     * A composition that threw while applying takes the recomposer down with it, and every pass after
     * that changes nothing while still being counted. A run that ends this way reports the frame
     * protocol's own cost under every arm's name, so it is stopped here instead.
     */
    fun checkRunning() {
        val state = recomposer.currentState.value
        check(state > Recomposer.State.ShuttingDown) { "the recomposer has stopped: it is $state" }
    }
}

/**
 * Runs coroutines on the event dispatch thread, where a composition recomposes and writes to the widgets.
 * Written here rather than taken from `kotlinx-coroutines-swing`, so this module's only dependency is the
 * library.
 */
private object EventDispatchThread : CoroutineDispatcher() {
    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        SwingUtilities.invokeLater(block)
    }
}

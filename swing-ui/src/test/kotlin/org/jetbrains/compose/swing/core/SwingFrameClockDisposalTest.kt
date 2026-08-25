package org.jetbrains.compose.swing.core

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.runSwingTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for [SwingFrameClock.dispose]: disposal is final. Frames awaited afterwards
 * schedule nothing, and a frame dispatch already on the event queue at disposal neither broadcasts a
 * frame nor restarts the timer.
 *
 * These drive the clock directly, on a recomposer that is never started: the race they pin sits
 * between the clock's own event-queue dispatch and its disposal, and needs each step of that
 * interleaving placed by hand.
 */
class SwingFrameClockDisposalTest {
    @Test
    fun aDispatchQueuedBeforeDisposalDoesNotRestartTheTimer() = runSwingTest {
        val recomposer = Recomposer(Dispatchers.Swing)
        val clock = SwingFrameClock(recomposer)
        val compositionClock = checkNotNull(recomposer.effectCoroutineContext[MonotonicFrameClock])
        // Parked for the whole test: this is the frame-driven work the timer paces, and what a
        // dispatch running after disposal reads when it decides to restart the timer.
        val frameDrivenWork = launch(start = CoroutineStart.UNDISPATCHED) { compositionClock.withFrameNanos { } }
        var firstFrame: Job? = null
        var lateFrame: Job? = null
        try {
            firstFrame = launch(start = CoroutineStart.UNDISPATCHED) { clock.withFrameNanos { } }
            awaitUntil("the first dispatch runs and the timer paces the parked work") {
                clock.isPacingFrameDrivenWork
            }

            // Awaiting a frame queues a dispatch behind the current event; disposing in the same
            // event guarantees the dispatch runs on a disposed clock.
            lateFrame = launch(start = CoroutineStart.UNDISPATCHED) { clock.withFrameNanos { } }
            clock.dispose()

            drainEventQueue()
            assertFalse(
                clock.isPacingFrameDrivenWork,
                "a dispatch queued before disposal restarted a disposed clock's timer",
            )
            assertTrue(
                lateFrame.isActive,
                "a dispatch queued before disposal broadcast a frame from a disposed clock",
            )
        } finally {
            lateFrame?.cancel()
            firstFrame?.cancel()
            frameDrivenWork.cancel()
            recomposer.cancel()
        }
    }

    @Test
    fun aFrameAwaitedAfterDisposalIsNeverServed() = runSwingTest {
        val recomposer = Recomposer(Dispatchers.Swing)
        val clock = SwingFrameClock(recomposer)
        val compositionClock = checkNotNull(recomposer.effectCoroutineContext[MonotonicFrameClock])
        val frameDrivenWork = launch(start = CoroutineStart.UNDISPATCHED) { compositionClock.withFrameNanos { } }
        clock.dispose()
        val lateFrame = launch(start = CoroutineStart.UNDISPATCHED) { clock.withFrameNanos { } }
        try {
            drainEventQueue()
            assertTrue(
                lateFrame.isActive,
                "a frame awaited after disposal was served by a disposed clock",
            )
            assertFalse(
                clock.isPacingFrameDrivenWork,
                "a frame awaited after disposal started a disposed clock's timer",
            )
        } finally {
            lateFrame.cancel()
            frameDrivenWork.cancel()
            recomposer.cancel()
        }
    }

    /**
     * Yields the event dispatch thread enough cycles that anything queued before the first yield -
     * in these tests, the clock's frame dispatch - has run by the time this returns.
     */
    private suspend fun drainEventQueue() {
        repeat(DRAIN_CYCLES) { yield() }
    }

    /**
     * Suspends on the EDT until [condition] holds, yielding the EDT back between checks. A condition
     * that never becomes true fails the test at the deadline, naming [description], instead of hanging.
     */
    private suspend fun awaitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        try {
            withTimeout(SETTLE_TIMEOUT) {
                while (!condition()) yield()
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after $SETTLE_TIMEOUT waiting until $description", timedOut)
        }
    }

    private companion object {
        const val DRAIN_CYCLES: Int = 10
        val SETTLE_TIMEOUT = 10.seconds
    }
}

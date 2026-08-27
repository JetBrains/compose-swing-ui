package org.jetbrains.compose.swing.core

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for the frame [SwingFrameClock.settleInPlace] runs out of turn.
 *
 * A frame taken out of turn is what puts a declaration back onto a widget inside the event that moved it,
 * which is why it runs whatever else is queued rather than standing down for it. Frame-driven work does
 * not hold it back: the frame withholds the recomposer's broadcast instead, so it recomposes and applies
 * without carrying an animation forward by a step it has not earned. What does refuse it is a frame
 * already running, because a frame started inside one is the re-entrant apply this runtime cannot
 * survive.
 *
 * These drive the clock directly, on a recomposer that is never started: what they pin is which awaiter is
 * served in which event, and that needs each step of the interleaving placed by hand. The other direction
 * - a frame really does run out of turn when nothing holds it back - is asserted through the components
 * that ask for one, by `assertUnadoptedChangeIsNeverPainted`.
 */
class SwingFrameClockOutOfTurnFrameTest {
    @Test
    fun aFrameTakenOutOfTurnRunsWhileFrameDrivenWorkAwaitsOne() = runSwingTest {
        val recomposer = Recomposer(Dispatchers.Swing)
        val clock = SwingUiDispatcher().frameClock.apply { pace(recomposer) }
        val compositionClock = checkNotNull(recomposer.effectCoroutineContext[MonotonicFrameClock])
        // Frame-driven work parked for the whole test: what the frame withholds its broadcast from,
        // rather than standing down for.
        val frameDrivenWork = launch(start = CoroutineStart.UNDISPATCHED) { compositionClock.withFrameNanos { } }
        var served = false
        var frame: Job? = null
        try {
            frame = launch(start = CoroutineStart.UNDISPATCHED) { clock.withFrameNanos { served = true } }

            clock.settleInPlace()

            assertTrue(
                served,
                "a settlement must reach the recomposer inside the event that asked for it, whether or " +
                    "not frame-driven work is parked",
            )
        } finally {
            frame?.cancel()
            frameDrivenWork.cancel()
            clock.dispose()
            recomposer.cancel()
        }
    }

    @Test
    fun aFrameTakenOutOfTurnRecomposesWithoutAdvancingFrameDrivenWork() = runSwingTest {
        // A running recomposer, because withholding the broadcast is something only a recomposer serving
        // its own clock can be seen to do: on a recomposer that never runs, nothing is served either way.
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        var mounted: DisposableHandle? = null
        try {
            var ticks = 0
            var declared by mutableStateOf("first")
            var composedWith = ""
            mounted =
                composition.setContent(parent = recomposer.compositionContext) {
                    composedWith = declared
                    LaunchedEffect(Unit) {
                        while (true) {
                            withFrameNanos { ticks++ }
                        }
                    }
                }
            awaitUntil("the frame-driven work has taken a frame and parked for the next") { ticks >= 1 }
            val advancedBefore = ticks

            declared = "second"
            recomposer.clock.settleInPlace()

            assertEquals(
                "second",
                composedWith,
                "a settlement must recompose against the declaration standing when it runs",
            )
            // In this steady state the cadence is already holding the recomposer's clock paused - a
            // queued frame that carried frame-driven work pauses it behind itself - so this holds with or
            // without the settlement pausing on its own account. What the settlement's own pause covers is
            // the window just after a timer tick resumes the clock and before the frame it asks for runs;
            // that window is not reached here.
            assertEquals(
                advancedBefore,
                ticks,
                "a settlement must not carry frame-driven work forward by a step it has not earned",
            )
        } finally {
            mounted?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun aFrameTakenOutOfTurnFromInsideAFrameIsDeclined() = runSwingTest {
        val recomposer = Recomposer(Dispatchers.Swing)
        val clock = SwingUiDispatcher().frameClock.apply { pace(recomposer) }
        var outerFrameRan = false
        var nested = false
        var nestedFrame: Job? = null
        // Nothing is parked on the recomposer's clock, so what holds the re-entrant call back is the
        // frame that is running and nothing else.
        val outerFrame =
            launch(start = CoroutineStart.UNDISPATCHED) {
                clock.withFrameNanos {
                    nestedFrame =
                        launch(start = CoroutineStart.UNDISPATCHED) { clock.withFrameNanos { nested = true } }
                    clock.settleInPlace()
                    outerFrameRan = true
                }
            }
        try {
            awaitUntil("the frame the clock scheduled for itself has run") { outerFrameRan }

            assertFalse(nested, "a frame running must not start a frame inside itself")
        } finally {
            nestedFrame?.cancel()
            outerFrame.cancel()
            clock.dispose()
            recomposer.cancel()
        }
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
        val SETTLE_TIMEOUT = 10.seconds
    }
}

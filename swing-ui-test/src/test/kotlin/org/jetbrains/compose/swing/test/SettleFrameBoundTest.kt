package org.jetbrains.compose.swing.test

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import org.jetbrains.compose.swing.components.Label
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the frame bound the two settling gates - [ComposeSwingTest.setContent]'s initial settle and
 * [ComposeSwingTest.awaitIdle] - give up at.
 *
 * A composition that produces fresh work on every frame is never quiescent, so a gate that waited for
 * quiescence with no bound would keep sending frames until the surrounding test framework's timeout,
 * saying nothing about why. Each gate instead stops at a frame bound and fails naming itself, the
 * outstanding work, and the tree it was waiting on.
 *
 * Each case produces that work for more frames than the bound tolerates and then stops, so the
 * composition it leaves behind is one a gate would settle: the failure can only come from the bound, and
 * a gate that stopped failing at it ends these cases with a settled composition and no failure rather
 * than with a hang.
 */
class SettleFrameBoundTest {
    @Test
    fun awaitIdleFailsReadablyForACompositionThatNeverSettles() = runComposeSwingTest {
        var restless by mutableStateOf(false)
        var consumed by mutableIntStateOf(0)
        setContent {
            if (restless) {
                // Every frame the gate sends is consumed and answered with a write to a composition
                // input, so the write the gate publishes at the top of its next pass revives
                // recomposition and the composition is never quiescent when the gate checks.
                LaunchedEffect(Unit) {
                    repeat(FRAMES_BEYOND_THE_BOUND) {
                        withFrameNanos { }
                        consumed++
                    }
                }
            }
            Label(text = "restless for $consumed frames")
        }

        restless = true
        val failure = assertFailsWith<AssertionError> { awaitIdle() }

        val message = failure.message.orEmpty()
        assertTrue(message.contains("awaitIdle did not settle"), "the failure should name the gate: $message")
        assertTrue(
            message.contains("hasPendingWork="),
            "the failure should report the outstanding work it gave up on: $message",
        )
        assertTrue(message.contains("restless for"), "the failure should carry a dump of the tree: $message")
    }

    @Test
    fun setContentFailsReadablyForAnInitialCompositionThatNeverSettles() = runComposeSwingTest {
        var consumed by mutableIntStateOf(0)

        // The initial settle runs inside setContent, so a content whose first composition already
        // produces endless work fails there rather than at the first await the test writes.
        val failure =
            assertFailsWith<AssertionError> {
                setContent {
                    LaunchedEffect(Unit) {
                        repeat(FRAMES_BEYOND_THE_BOUND) {
                            withFrameNanos { }
                            consumed++
                        }
                    }
                    Label(text = "restless for $consumed frames")
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(message.contains("setContent did not settle"), "the failure should name the gate: $message")
        assertTrue(
            message.contains("hasPendingWork="),
            "the failure should report the outstanding work it gave up on: $message",
        )
        assertTrue(message.contains("restless for"), "the failure should carry a dump of the tree: $message")
    }

    private companion object {
        // More frames of work than either gate's bound tolerates, so the bound is what ends the wait.
        // The work stops shortly after, which is what turns an unbounded gate into a failing case here
        // instead of a test that hangs.
        const val FRAMES_BEYOND_THE_BOUND: Int = 20_000
    }
}

package org.jetbrains.compose.swing.test

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import org.jetbrains.compose.swing.components.Label
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins [ComposeSwingTest.waitUntil] - the harness's escape hatch for a condition that cannot be expressed
 * as a settled assertion - and the one-shot contract of [ComposeSwingTest.setContent].
 *
 * A wait must return as soon as its condition holds and must fail readably, with a tree dump, when it
 * does not hold by the deadline, so a downstream test never hangs until its framework's timeout.
 */
class WaitUntilContractTest {
    @Test
    fun waitUntilReturnsImmediatelyForAConditionAlreadyMet() = runComposeSwingTest {
        setContent { Label(text = "settled") }

        // The condition holds on the first evaluation, so the wait consumes no frame and no deadline.
        waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { true }
    }

    @Test
    fun waitUntilPollsUntilDeferredWorkMakesTheConditionTrue() = runComposeSwingTest {
        var arrived by mutableStateOf(false)
        setContent {
            Label(text = "host")
            if (arrived) Label(text = "arrived")
        }
        onNodeWithText("arrived").assertDoesNotExist()

        // The flip happens on a later event-dispatch turn, so the condition is false on the first
        // evaluation and the wait must keep polling - dispatching queued work between checks -
        // rather than give up after one pass.
        SwingUtilities.invokeLater { arrived = true }

        waitUntil { arrived }
        awaitIdle()
        onNodeWithText("arrived").assertExists()
    }

    @Test
    fun waitUntilDrivesTheFramesTheConditionIsGatedOn() = runComposeSwingTest {
        var start by mutableStateOf(false)
        var frameGated by mutableStateOf(false)
        setContent {
            if (start) {
                LaunchedEffect(Unit) {
                    repeat(AWAITED_FRAMES) { withFrameNanos { } }
                    frameGated = true
                }
            }
            Label(text = "host")
        }

        // The condition becomes true only after an effect has consumed several frames. Frames are
        // produced under test control, so the wait itself must drive them; a wait that only polled
        // would never see the condition met.
        start = true
        waitUntil { frameGated }
    }

    @Test
    fun anUnmetWaitFailsAtItsDeadlineWithATreeDump() = runComposeSwingTest {
        setContent { Label(text = "never-changes") }

        val failure =
            assertFailsWith<AssertionError> {
                waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { false }
            }
        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("Condition still not met"),
            "the failure should say the condition was never met: $message",
        )
        assertTrue(
            message.contains("${WAIT_TIMEOUT_MILLIS}ms"),
            "the failure should report the deadline it ran to: $message",
        )
        assertTrue(
            message.contains("never-changes"),
            "the failure should carry a dump of the tree it waited on: $message",
        )
    }

    @Test
    fun setContentMayOnlyBeCalledOnce() = runComposeSwingTest {
        setContent { Label(text = "first") }

        // A second mount would leave two compositions writing the same root, so it is rejected.
        assertFailsWith<IllegalStateException> { setContent { Label(text = "second") } }
        onNodeWithText("first").assertExists()
        onNodeWithText("second").assertDoesNotExist()
    }

    private companion object {
        // Short enough to keep the suite quick while still exercising the wall-clock deadline.
        const val WAIT_TIMEOUT_MILLIS: Long = 100

        // More frames than a single wait pass produces, so the wait must keep driving them.
        const val AWAITED_FRAMES: Int = 3
    }
}

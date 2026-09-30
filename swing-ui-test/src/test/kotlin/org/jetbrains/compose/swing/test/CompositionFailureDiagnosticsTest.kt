package org.jetbrains.compose.swing.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.performClick
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pins what a test sees once an uncontained throw has ended recomposition - a node's own update block
 * raising, which reaches the recomposer directly rather than through the caller-callback containment
 * [CallerFailureContainmentTest] pins.
 *
 * Such a throw ends the recomposer for good: nothing it applies afterward reflects fresh state. The gate
 * that ran the failing pass throws it, once, so a test that does not expect it fails with it; a later gate
 * that gives up names it in its report.
 */
class CompositionFailureDiagnosticsTest {
    @Test
    fun aThrowThatEndsRecompositionFailsTheGateThatRanThePassAndALaterReportNamesIt() = runComposeSwingTest {
        var fail by mutableStateOf(false)
        setContent { NodeFailingToApply(fail) }

        fail = true
        val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
        assertEquals(APPLY_FAILURE, thrown.message)
        awaitIdle()

        // The condition never becomes true, so the deadline - not an idle composition - is what ends
        // this wait; it fails regardless of whether the dead recomposer still reports pending work.
        val report = assertFailsWith<AssertionError> { waitUntil(timeout = 100.milliseconds) { false } }
        val message = report.message.orEmpty()
        assertTrue(
            message.contains("Recomposition ended earlier with"),
            "the report should name that recomposition ended: $message",
        )
        assertTrue(
            message.contains(APPLY_FAILURE),
            "the report should carry the throw that ended recomposition: $message",
        )
    }

    @Test
    fun aWaitThatRunsTheFailingPassThrowsTheFailureRatherThanATimeout() = runComposeSwingTest {
        var fail by mutableStateOf(false)
        setContent { NodeFailingToApply(fail) }

        fail = true
        val thrown = assertFailsWith<IllegalStateException> { waitUntil(timeout = 10.seconds) { false } }
        assertEquals(APPLY_FAILURE, thrown.message)
    }

    @Test
    fun theThrowFailsOneGateAndLeavesLaterGatesAndActionsWorking() = runComposeSwingTest {
        var fail by mutableStateOf(false)
        var clicks = 0
        setContent {
            NodeFailingToApply(fail)
            Button(text = "press", onClick = { clicks++ })
        }

        fail = true
        assertFailsWith<IllegalStateException> { awaitIdle() }

        awaitIdle()
        awaitEventsDelivered()
        // An action ends in a gate of its own, and what it drives is the widget, which outlives the
        // recomposer.
        onNodeWithText("press").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun aTestThatDoesNotExpectTheThrowFailsWithIt() {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    var fail by mutableStateOf(false)
                    setContent { NodeFailingToApply(fail) }

                    fail = true
                    awaitIdle()
                }
            }

        // runTest rethrows a copy of the failure with the original as its cause so the stack trace it
        // reports spans the coroutine boundary.
        assertEquals(APPLY_FAILURE, (failure.cause ?: failure).message)
    }

    @Test
    fun theThrowIsSuppressedOntoAFailureAlreadyPending() = runComposeSwingTest {
        var fail by mutableStateOf(false)
        setContent { NodeFailingToApply(fail) }

        fail = true
        // Delivers the write without a frame, so the very next frame runs the failing pass.
        awaitEventsDelivered()
        val pending = UnsupportedOperationException("raised outside the recomposer")
        SwingUtilities.invokeLater { throw pending }

        val thrown = assertFailsWith<UnsupportedOperationException> { awaitIdle() }
        assertSame(pending, thrown)
        assertEquals(APPLY_FAILURE, thrown.suppressed.single().message)
        awaitIdle()
    }
}

private const val APPLY_FAILURE = "the apply of this node fails"

/**
 * A node whose apply throws once [fail] is set. A declared value's own write is the library's, not a caller
 * callback, so the throw is never contained: it reaches the recomposer directly.
 */
@Composable
private fun NodeFailingToApply(fail: Boolean) {
    SwingNode(
        factory = { JPanel() },
        update = { set(fail) { if (it) error(APPLY_FAILURE) } },
    )
}

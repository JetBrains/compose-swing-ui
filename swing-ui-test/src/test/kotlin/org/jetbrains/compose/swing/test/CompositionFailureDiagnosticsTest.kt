package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pins the diagnostic every settling gate appends once recomposition itself has ended from an
 * uncontained throw - a node's own update block raising, which reaches the recomposer directly rather
 * than through the caller-callback containment [CallerFailureContainmentTest] pins.
 *
 * Such a throw ends the recomposer for good: nothing it applies afterward reflects fresh state, so a
 * gate that goes on to find nothing to settle names the failure that stopped recomposition rather than
 * reporting a bare stale tree.
 */
class CompositionFailureDiagnosticsTest {
    @Test
    fun anUncontainedApplyFailureEndsRecompositionAndTheNextGateNamesIt() = runComposeSwingTest {
        var fail by mutableStateOf(false)
        setContent {
            // A declared value's own write is the library's, not a caller callback, so a throw from it
            // is never contained: it reaches the recomposer directly.
            SwingNode(
                factory = { JPanel() },
                update = { set(fail) { if (it) error("the apply of this node fails") } },
            )
            Label(text = "steady")
        }

        fail = true
        // The condition never becomes true, so the deadline - not a settled composition - is what ends
        // this wait; it fails regardless of whether the dead recomposer still reports pending work.
        val failure =
            assertFailsWith<AssertionError> {
                waitUntil(timeout = WAIT_TIMEOUT) { false }
            }

        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("Recomposition ended earlier with"),
            "the failure should name that recomposition ended: $message",
        )
        assertTrue(
            message.contains("the apply of this node fails"),
            "the failure should carry the throw that ended recomposition: $message",
        )
    }

    private companion object {
        // Short enough to keep the suite quick while still exercising the wall-clock deadline.
        val WAIT_TIMEOUT = 100.milliseconds
    }
}

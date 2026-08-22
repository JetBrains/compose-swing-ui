package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.Scrollbar
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import javax.swing.JScrollBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for the `adjustmentListener` builder. The library wraps no scrollbar composable, so
 * the target is a scrollbar a custom component contributes through [SwingNode] - which is the case the
 * builder exists for. Each test asserts what an observer of the live component sees: the exact instance
 * is registered through the widget's own `getAdjustmentListeners()`, and it is notified of the value
 * changes the widget publishes.
 */
class AdjustmentListenerModifierTest {
    @Test
    fun theListenerInstanceIsRegisteredOnASwingScrollBarAndReportsItsValue() = runComposeSwingTest {
        val values = mutableListOf<Int>()
        val listener = AdjustmentListener { values += it.value }
        setContent {
            SwingNode(
                factory = { JScrollBar(JScrollBar.VERTICAL, 0, 10, 0, 100) },
                update = { applyModifier(SwingModifier.adjustmentListener(listener)) },
            )
        }
        val bar = onNodeOfType<JScrollBar>().fetch()
        assertTrue(
            bar.adjustmentListeners.any { it === listener },
            "the listener instance should be registered on the scrollbar",
        )

        bar.value = 30
        assertEquals(listOf(30), values, "the registered listener should be notified of the new value")
    }

    @Test
    fun aValueReachedWhileAnAdjustmentIsUnderwayIsReportedAsSuch() = runComposeSwingTest {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val listener = AdjustmentListener { events += it.value to it.valueIsAdjusting }
        setContent {
            SwingNode(
                factory = { JScrollBar(JScrollBar.VERTICAL, 0, 10, 0, 100) },
                update = { applyModifier(SwingModifier.adjustmentListener(listener)) },
            )
        }
        val bar = onNodeOfType<JScrollBar>().fetch()

        // A drag of the scrollbar's thumb passes through intermediate values while the adjustment is
        // underway, and settles on a final one once it ends.
        bar.valueIsAdjusting = true
        bar.value = 20
        bar.value = 40
        bar.valueIsAdjusting = false

        assertEquals(
            listOf(0 to true, 20 to true, 40 to true, 40 to false),
            events,
            "every value a drag passes through must reach the listener while the adjustment is underway, " +
                "and the value it settles on again once the adjustment ends",
        )
    }

    @Test
    fun theListenerInstanceIsRegisteredOnAnAwtScrollbar() = runComposeSwingTest {
        // The component under test is a heavyweight AWT one, which cannot be built at all
        // without a display, rather than merely shown on one.
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val values = mutableListOf<Int>()
        val listener = AdjustmentListener { values += it.value }
        setContent {
            SwingNode(
                factory = { Scrollbar(Scrollbar.HORIZONTAL) },
                update = { applyModifier(SwingModifier.adjustmentListener(listener)) },
            )
        }
        val bar = onNodeOfType<Scrollbar>().fetch()
        assertTrue(
            bar.adjustmentListeners.any { it === listener },
            "the listener instance should be registered on the AWT scrollbar",
        )

        // An AWT Scrollbar publishes an adjustment event only for a change made through its own
        // event path, which is what setValue plus a posted event models.
        bar.adjustmentListeners.forEach {
            it.adjustmentValueChanged(
                AdjustmentEvent(bar, AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED, AdjustmentEvent.TRACK, 7),
            )
        }
        assertEquals(listOf(7), values, "the registered listener should be notified of the new value")
    }

    @Test
    fun droppingTheModifierRemovesTheListener() = runComposeSwingTest {
        var observed by mutableStateOf(true)
        val listener = AdjustmentListener { }
        setContent {
            SwingNode(
                factory = { JScrollBar() },
                update = {
                    applyModifier(if (observed) SwingModifier.adjustmentListener(listener) else SwingModifier)
                },
            )
        }
        val bar = onNodeOfType<JScrollBar>().fetch()
        assertTrue(bar.adjustmentListeners.any { it === listener }, "the listener starts registered")

        observed = false
        awaitIdle()
        assertFalse(
            bar.adjustmentListeners.any { it === listener },
            "the listener must be removed once the modifier leaves the chain",
        )
    }

    @Test
    fun aComponentThatIsNotAScrollbarIsRejected() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label("X", modifier = SwingModifier.adjustmentListener(AdjustmentListener { }))
                }
                awaitIdle()
            }
        val message = error.message.orEmpty()
        assertTrue(
            "scrollbar" in message,
            "the wrong-target error must explain the required scrollbar target, but was: $message",
        )
    }
}

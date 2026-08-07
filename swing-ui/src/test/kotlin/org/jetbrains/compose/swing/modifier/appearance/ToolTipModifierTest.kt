package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.ToolTipManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for the `toolTip` modifiers - the constant tooltip a component shows wherever the
 * pointer rests on it, and the per-location one that answers for the place under the pointer.
 *
 * They assert what an observer of the live Swing component sees: the text the component reports for a
 * point, its registration with [ToolTipManager] - which is what decides whether a tooltip is shown at
 * all - and the round trip back to the tooltip the component carried before the declaration. A tooltip
 * is resolved from the pointer's travel, so the cases drive real [MouseEvent]s at the component and
 * read the tooltip back through [JComponent.getToolTipText], the method the manager itself asks.
 */
class ToolTipModifierTest {
    /** Moves the pointer to (x, y) the way the toolkit does, and reports the tooltip found there. */
    private fun JComponent.tooltipAt(
        x: Int,
        y: Int,
    ): String? {
        val moved = MouseEvent(this, MouseEvent.MOUSE_MOVED, 0L, 0, x, y, 0, false)
        dispatchEvent(moved)
        return getToolTipText(moved)
    }

    private fun JComponent.isUnderToolTipManager(): Boolean = ToolTipManager.sharedInstance() in mouseListeners

    @Test
    fun toolTipModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        setContent {
            Label("X", modifier = if (styled) SwingModifier.toolTip("hint") else SwingModifier)
        }
        val label = onNodeOfType<JLabel>()
        assertEquals("hint", label.fetch<JLabel>().toolTipText, "the tooltip should apply while present")

        styled = false
        awaitIdle()
        // The element left the chain, so the tooltip is cleared back to the prior (null) default.
        assertNull(label.fetch<JLabel>().toolTipText, "removing the modifier should clear the tooltip")
    }

    @Test
    fun aConstantToolTipNeedsNothingFromThePointer() = runComposeSwingTest {
        setContent { Label("X", modifier = SwingModifier.toolTip("hint")) }
        val label = onNodeOfType<JLabel>().fetch()
        assertTrue(
            label.mouseMotionListeners.none { it.javaClass.name.startsWith("org.jetbrains.compose.swing") },
            "a tooltip that is the same everywhere is read off the component, not followed",
        )
    }

    @Test
    fun thePerLocationFormAnswersForThePlaceUnderThePointer() = runComposeSwingTest {
        setContent {
            Label(
                "X",
                modifier = SwingModifier.toolTip { event -> if (event.x < 10) "left half" else "right half" },
            )
        }
        val label = onNodeOfType<JLabel>().fetch()

        assertEquals("left half", label.tooltipAt(2, 0), "the place under the pointer decides the tooltip")
        assertEquals("right half", label.tooltipAt(20, 0), "and the next place decides it again")
    }

    @Test
    fun aPlaceWithNoToolTipShowsNone() = runComposeSwingTest {
        setContent {
            Label("X", modifier = SwingModifier.toolTip { event -> "cell".takeIf { event.y < 5 } })
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals("cell", label.tooltipAt(0, 0), "the place that carries a tooltip reports it")

        assertNull(label.tooltipAt(0, 9), "and the place that carries none reports none")
        assertTrue(
            label.isUnderToolTipManager(),
            "a place with no tooltip must not take the component out of the tooltip manager",
        )
    }

    @Test
    fun aComponentWithOnlyThePerLocationFormIsUnderTheToolTipManager() = runComposeSwingTest {
        setContent { Label("X", modifier = SwingModifier.toolTip { "cell" }) }
        val label = onNodeOfType<JLabel>().fetch()

        assertNull(label.toolTipText, "the declaration writes no tooltip of its own until the pointer arrives")
        assertTrue(
            label.isUnderToolTipManager(),
            "yet the component is registered for tooltips, which is what lets one be shown at all",
        )
    }

    @Test
    fun theLambdaOfTheLatestCompositionAnswers() = runComposeSwingTest {
        var declared by mutableIntStateOf(1)
        setContent {
            val generation = declared
            Label("X", modifier = SwingModifier.toolTip { "generation $generation" })
        }
        val label = onNodeOfType<JLabel>().fetch()

        declared = 2
        awaitIdle()
        assertEquals(
            "generation 2",
            label.tooltipAt(0, 0),
            "the tooltip must come from the lambda the latest recomposition declared, not a captured earlier one",
        )
    }

    @Test
    fun theLastToolTipInTheChainOwnsTheComponentsToolTip() = runComposeSwingTest {
        setContent {
            Label("X", modifier = SwingModifier.toolTip("everywhere").toolTip { "here" })
        }
        val label = onNodeOfType<JLabel>().fetch()

        assertEquals(
            "here",
            label.tooltipAt(0, 0),
            "a component has one tooltip: the chain's last declaration owns it",
        )
    }

    @Test
    fun switchingToThePerLocationFormLeavesNoConstantToolTipBehind() = runComposeSwingTest {
        var perLocation by mutableStateOf(false)
        setContent {
            Label(
                "X",
                modifier =
                    if (perLocation) {
                        SwingModifier.toolTip { event -> "cell".takeIf { event.x < 10 } }
                    } else {
                        SwingModifier.toolTip("everywhere")
                    },
            )
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals("everywhere", label.toolTipText, "the constant declaration shows its tooltip anywhere")

        perLocation = true
        awaitIdle()
        assertNull(label.toolTipText, "the tooltip it left behind is not the new declaration's to show")
        assertEquals("cell", label.tooltipAt(0, 0), "which answers for the place under the pointer instead")
        assertNull(label.tooltipAt(20, 0), "including the places that carry none")
    }

    @Test
    fun droppingThePerLocationFormRestoresTheComponent() = runComposeSwingTest {
        var hinted by mutableStateOf(true)
        setContent {
            Label("X", modifier = if (hinted) SwingModifier.toolTip { "cell" } else SwingModifier)
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals("cell", label.tooltipAt(0, 0), "the tooltip is answered while the declaration is in the chain")

        hinted = false
        awaitIdle()
        assertNull(label.toolTipText, "the tooltip the component carried before the declaration comes back")
        assertNull(label.tooltipAt(0, 0), "and the pointer no longer publishes one")
        assertFalse(
            label.isUnderToolTipManager(),
            "a component the declaration put under the tooltip manager is taken back out with it",
        )
    }
}

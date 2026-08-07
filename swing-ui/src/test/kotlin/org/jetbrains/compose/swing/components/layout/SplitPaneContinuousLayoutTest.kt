package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSplitPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Whether a [SplitPane] lays its two sides out continuously as the divider is dragged is declared like
 * every other look-and-feel-defaulted aspect: a declared choice reaches the pane, a withdrawn one hands
 * the choice back to the look and feel, and one never declared is left alone.
 */
class SplitPaneContinuousLayoutTest {
    @Test
    fun theContinuousLayoutChoiceFollowsEveryDeclaredValue() = runComposeSwingTest {
        var continuous by mutableStateOf<Boolean?>(null)
        setContent {
            SplitPane(continuousLayout = continuous) {
                Label(text = "A", modifier = SwingModifier.first())
                Label(text = "B", modifier = SwingModifier.second())
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(
            JSplitPane().isContinuousLayout,
            pane.isContinuousLayout,
            "an undeclared choice should be the look-and-feel value",
        )

        continuous = true
        awaitIdle()
        assertTrue(pane.isContinuousLayout, "the declared choice should reach the pane")

        continuous = false
        awaitIdle()
        assertEquals(false, pane.isContinuousLayout, "a new declared choice should reach the pane")
    }

    @Test
    fun withdrawingTheContinuousLayoutChoiceGivesItBackToTheLookAndFeel() = runComposeSwingTest {
        var continuous by mutableStateOf<Boolean?>(true)
        setContent {
            SplitPane(continuousLayout = continuous) {
                Label(text = "A", modifier = SwingModifier.first())
                Label(text = "B", modifier = SwingModifier.second())
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertTrue(pane.isContinuousLayout, "the declared choice should reach the pane")

        continuous = null
        awaitIdle()

        // A pane that was never given a choice carries the one its look and feel leaves it with, so the
        // oracle is a pane built the plain Swing way.
        val handWritten = JSplitPane().isContinuousLayout
        assertNotEquals(
            true,
            handWritten,
            "the look and feel under test should leave a pane laying out on release, so that a " +
                "withdrawal that did nothing could not pass for one that gave the choice back",
        )
        assertEquals(
            handWritten,
            pane.isContinuousLayout,
            "withdrawing the choice should leave the pane as its look and feel leaves one",
        )
    }
}

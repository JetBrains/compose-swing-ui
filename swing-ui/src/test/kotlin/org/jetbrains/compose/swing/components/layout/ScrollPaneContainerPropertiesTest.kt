package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JScrollPane
import javax.swing.border.Border
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The two properties a [ScrollPane] carries beside its scrollbars: the border drawn around the viewport,
 * and whether the mouse wheel scrolls the pane. The border defaults to whatever the look and feel gave
 * the pane at construction, and a withdrawn declaration settles back at that answer.
 */
class ScrollPaneContainerPropertiesTest {
    @Test
    fun theViewportBorderFollowsEveryDeclaredValue() = runComposeSwingTest {
        val first: Border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
        val second: Border = BorderFactory.createLineBorder(Color.RED)
        var border by mutableStateOf<Border?>(first)
        setContent {
            ScrollPane(viewportBorder = border) {
                Label(text = "Body", modifier = SwingModifier.viewport())
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertSame(first, pane.viewportBorder, "the declared border should reach the pane")

        border = second
        awaitIdle()
        assertSame(second, pane.viewportBorder, "a new declared border should reach the pane")
    }

    @Test
    fun withdrawingTheViewportBorderGivesItBackToTheLookAndFeel() = runComposeSwingTest {
        var border by mutableStateOf<Border?>(BorderFactory.createEmptyBorder(2, 2, 2, 2))
        setContent {
            ScrollPane(viewportBorder = border) {
                Label(text = "Body", modifier = SwingModifier.viewport())
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertSame(border, pane.viewportBorder, "the declared border should reach the pane")

        border = null
        awaitIdle()
        assertEquals(
            JScrollPane().viewportBorder,
            pane.viewportBorder,
            "withdrawing the border should leave the pane as its look and feel leaves one",
        )
    }

    @Test
    fun anUndeclaredViewportBorderIsTheOneTheLookAndFeelGave() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Label(text = "Body", modifier = SwingModifier.viewport())
            }
        }

        assertEquals(
            JScrollPane().viewportBorder,
            onNodeOfType<JScrollPane>().fetch().viewportBorder,
            "an undeclared border should be the look-and-feel value",
        )
    }

    @Test
    fun wheelScrollingFollowsEveryDeclaredValue() = runComposeSwingTest {
        var wheel by mutableStateOf(true)
        setContent {
            ScrollPane(wheelScrollingEnabled = wheel) {
                Label(text = "Body", modifier = SwingModifier.viewport())
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertTrue(pane.isWheelScrollingEnabled, "a pane scrolls on the wheel by default")

        wheel = false
        awaitIdle()
        assertFalse(pane.isWheelScrollingEnabled, "the declared choice should reach the pane")

        wheel = true
        awaitIdle()
        assertTrue(pane.isWheelScrollingEnabled, "a new declared choice should reach the pane")
    }
}

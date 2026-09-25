package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JScrollPane
import javax.swing.border.Border
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The properties a [ScrollPane] carries beside its scrollbar policies: the border drawn around the
 * viewport, whether the mouse wheel scrolls the pane, and what its own viewport and scroll bars are
 * declared with. Each is whatever the look and feel gave the pane until it is declared, and a withdrawn
 * declaration hands that value back.
 */
class ScrollPaneContainerPropertiesTest {
    @Test
    fun theViewportBorderFollowsEveryDeclaredValue() = runComposeSwingTest {
        val first: Border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
        val second: Border = BorderFactory.createLineBorder(Color.RED)
        var border by mutableStateOf<Border?>(first)
        setContent {
            ScrollPane(viewportBorder = border) {
                Viewport { Label(text = "Body") }
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
                Viewport { Label(text = "Body") }
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
                Viewport { Label(text = "Body") }
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
                Viewport { Label(text = "Body") }
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

    @Test
    fun viewportAndScrollbarsFollowTheirGeneralModifiers() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Viewport(modifier = SwingModifier.background(Color.RED)) { Label(text = "Body") }
                VerticalScrollbar(modifier = SwingModifier.background(Color.BLUE))
                HorizontalScrollbar(modifier = SwingModifier.background(Color.BLUE))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals(Color.RED, pane.viewport.background, "the general modifier should reach the viewport")
        assertEquals(Color.BLUE, pane.verticalScrollBar.background, "the modifier should reach the vertical scrollbar")
        assertEquals(
            Color.BLUE,
            pane.horizontalScrollBar.background,
            "the modifier should reach the horizontal scrollbar",
        )
    }

    @Test
    fun partsWhoseDeclarationsLeaveGetTheirEarlierValuesBack() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            ScrollPane {
                if (declared) {
                    Viewport(modifier = SwingModifier.background(Color.RED)) {}
                    VerticalScrollbar(modifier = SwingModifier.background(Color.BLUE))
                    HorizontalScrollbar(modifier = SwingModifier.background(Color.BLUE))
                }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val viewport = pane.viewport
        val verticalBar = pane.verticalScrollBar
        val horizontalBar = pane.horizontalScrollBar
        val raw = JScrollPane()

        declared = false
        awaitIdle()

        assertSame(viewport, pane.viewport, "the viewport stays the pane's own")
        assertSame(verticalBar, pane.verticalScrollBar, "the vertical bar stays the pane's own")
        assertSame(horizontalBar, pane.horizontalScrollBar, "the horizontal bar stays the pane's own")
        assertEquals(raw.viewport.background, viewport.background, "the viewport gets the look and feel's back")
        assertEquals(raw.verticalScrollBar.background, verticalBar.background, "and so does the vertical bar")
        assertEquals(raw.horizontalScrollBar.background, horizontalBar.background, "and the horizontal bar")
    }

    @Test
    fun aToggledScrollbarDeclarationStylesTheBarOnlyWhileDeclared() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        setContent {
            ScrollPane(verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS) {
                Viewport { Label(text = "Body") }
                if (declared) VerticalScrollbar(modifier = SwingModifier.background(Color.RED))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val bar = pane.verticalScrollBar
        val earlier = bar.background

        declared = true
        awaitIdle()
        assertEquals(Color.RED, bar.background, "the declared background reaches the pane's own bar")
        assertSame(pane, bar.parent, "the bar stays in the pane while declared")

        declared = false
        awaitIdle()
        assertEquals(earlier, bar.background, "the bar gets its earlier background back")
        assertSame(bar, pane.verticalScrollBar, "the bar stays the pane's own")
        assertSame(pane, bar.parent, "and stays where the pane holds it")
        assertTrue(bar.isVisible, "shown as the pane's policy says")
    }

    @Test
    fun aBarThePaneInstallsInPlaceOfAnotherCarriesTheDeclarations() = runComposeSwingTest {
        var unitIncrement by mutableStateOf<Int?>(10)
        setContent {
            ScrollPane {
                Viewport(unitIncrement = unitIncrement) { Label(text = "Body") }
                VerticalScrollbar(modifier = SwingModifier.background(Color.BLUE))
                HorizontalScrollbar(modifier = SwingModifier.background(Color.BLUE))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val initialBar = pane.verticalScrollBar
        val earlier = JScrollPane().verticalScrollBar.background
        assertEquals(Color.BLUE, initialBar.background)
        assertEquals(10, initialBar.getUnitIncrement(1), "the declared increment reaches the initial bar")

        // A withdrawn increment makes the pane install fresh bars that ask the content again.
        unitIncrement = null
        awaitIdle()

        val replacement = pane.verticalScrollBar
        assertNotSame(initialBar, replacement, "withdrawing the increment installs a fresh bar")
        assertEquals(Color.BLUE, replacement.background, "the declared modifier reaches the fresh bar")
        assertEquals(Color.BLUE, pane.horizontalScrollBar.background, "and the fresh horizontal bar")
        assertEquals(earlier, initialBar.background, "the replaced bar gets its earlier background back")
    }
}

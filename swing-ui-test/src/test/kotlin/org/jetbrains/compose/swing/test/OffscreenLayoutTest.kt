package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OffscreenLayoutTest {
    @Test
    fun theComponentTakesTheSizeItIsLaidOutAt() = runComposeSwingTest {
        val panel = JPanel()

        panel.layoutOffscreen(Dimension(WIDTH, HEIGHT))

        assertEquals(Dimension(WIDTH, HEIGHT), panel.size)
    }

    @Test
    fun everyDescendantIsGivenRealBounds() = runComposeSwingTest {
        val label = JLabel("scrolled")
        val panel = JPanel(BorderLayout()).apply { add(JScrollPane(label), BorderLayout.CENTER) }

        panel.layoutOffscreen(Dimension(WIDTH, HEIGHT))

        val scroller = panel.getComponent(0)
        assertEquals(WIDTH, scroller.width, "the scroll pane fills the panel's center")
        assertEquals(HEIGHT, scroller.height, "the scroll pane fills the panel's center")
        assertTrue(label.width > 0 && label.height > 0, "the scrolled label is laid out too: $label")
    }

    /**
     * A menu keeps its items in a popup no container holds, so nothing else would ever size it: a walk
     * that stopped at the component array would leave every item on empty bounds.
     */
    @Test
    fun aMenusItemsAreLaidOutInThePopupThatHoldsThem() = runComposeSwingTest {
        val item = JMenuItem("Save")
        val bar = JMenuBar().apply { add(JMenu("File").apply { add(item) }) }

        bar.layoutOffscreen(Dimension(WIDTH, HEIGHT))

        assertTrue(item.width > 0 && item.height > 0, "the menu item was left on ${item.bounds}")
    }

    /** A layout keeping what it measured hears a child's change through the invalidation it sends up. */
    @Test
    fun aLayoutKeepingWhatItMeasuredSeesAChangeTheCompositionApplied() = runComposeSwingTest {
        var text by mutableStateOf("short")
        val label = JLabel()
        setContent {
            SwingNode(factory = { JPanel(KeepingLayout()) }) {
                SwingNode(factory = { label }, update = { set(text) { this.text = it } })
            }
        }
        val shortWidth = label.width

        text = "a considerably longer text"
        awaitIdle()

        assertEquals(
            label.preferredSize.width,
            label.width,
            "the label should widen to fit \"a considerably longer text\"",
        )
        assertTrue(label.width > shortWidth, "the label should widen past the width it had for \"short\": $shortWidth")
    }

    @Test
    fun aWidgetHoldingNoChildrenIsSized() = runComposeSwingTest {
        val canvas = Canvas()

        canvas.layoutOffscreen(Dimension(WIDTH, HEIGHT))

        assertEquals(Dimension(WIDTH, HEIGHT), canvas.size)
    }

    /** Places its first child at the size it measured, measuring again only once it is invalidated. */
    private class KeepingLayout : BorderLayout() {
        private var measured: Dimension? = null

        override fun layoutContainer(target: Container) {
            val child = target.getComponent(0)
            child.size = measured ?: child.preferredSize.also { measured = it }
        }

        override fun invalidateLayout(target: Container) {
            measured = null
        }
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 240
    }
}

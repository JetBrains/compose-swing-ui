package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Canvas
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.interaction.performMouseWheel
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.Scrollable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Every container this library builds, and the [Canvas] beside them, answers a scroll pane for itself as
 * the Swing widgets that live in one do: it scrolls by a line of its own font rather than by the single
 * pixel a pane falls back to for a view that answers nothing, and it is laid out exactly as that pane
 * would lay out such a view.
 *
 * The answers are read through the pane the component is composed in, so these tests pin what the arrow
 * buttons, the wheel and the layout do, not which methods the component carries.
 */
class ScrollableDefaultsTest {
    @Test
    fun aRowScrollsByALineOfItsOwnFont() = assertScrollsByALineOfItsOwnFont { Row(it) {} }

    @Test
    fun aColumnScrollsByALineOfItsOwnFont() = assertScrollsByALineOfItsOwnFont { Column(it) {} }

    @Test
    fun aBoxPanelScrollsByALineOfItsOwnFont() = assertScrollsByALineOfItsOwnFont { BoxPanel(it) {} }

    @Test
    fun aFlowPanelScrollsByALineOfItsOwnFont() = assertScrollsByALineOfItsOwnFont { FlowPanel(it) {} }

    @Test
    fun aGridPanelScrollsByALineOfItsOwnFont() = assertScrollsByALineOfItsOwnFont { GridPanel(it) {} }

    @Test
    fun aGridBagPanelScrollsByALineOfItsOwnFont() = assertScrollsByALineOfItsOwnFont { GridBagPanel(it) {} }

    @Test
    fun aBorderPanelScrollsByALineOfItsOwnFont() = assertScrollsByALineOfItsOwnFont { BorderPanel(it) {} }

    @Test
    fun aCardPanelScrollsByALineOfItsOwnFont() =
        assertScrollsByALineOfItsOwnFont { CardPanel(selectedCard = "only", modifier = it) {} }

    @Test
    fun aCanvasScrollsByALineOfItsOwnFont() =
        assertScrollsByALineOfItsOwnFont { Canvas(modifier = it, onDraw = { _, _, _ -> }) }

    @Test
    fun aColumnScrollsByAViewportPagePerBlock() = assertScrollsByAViewportPage { Column(it) {} }

    @Test
    fun aCanvasScrollsByAViewportPagePerBlock() =
        assertScrollsByAViewportPage { Canvas(modifier = it, onDraw = { _, _, _ -> }) }

    @Test
    fun aCanvasAsksTheViewportForTheSizeItPrefers() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Canvas(
                    modifier = SwingModifier.viewport().preferredSize(CONTENT_LONG, CONTENT_LONG),
                    onDraw = { _, _, _ -> },
                )
            }
        }

        // A pane sizes itself around what a Scrollable view asks the viewport for.
        val pane = onNodeOfType<JScrollPane>().fetch()
        assertTrue(
            pane.preferredSize.width >= CONTENT_LONG && pane.preferredSize.height >= CONTENT_LONG,
            "the pane must make room for the size the surface asks the viewport for: ${pane.preferredSize}",
        )
    }

    @Test
    fun aWheelNotchOverAContainerScrollsWholeLines() = runComposeSwingTest {
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                Column(SwingModifier.viewport().preferredSize(CONTENT_NARROW, CONTENT_LONG)) {}
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val line = lineOf(pane.viewport.view)
        onNodeOfType<JScrollPane>().performMouseWheel(rotation = 1)

        val scrolled = pane.viewport.viewPosition.y
        assertTrue(
            scrolled >= line,
            "one wheel notch scrolls at least a line, not the single pixel a pane falls back to: $scrolled",
        )
        assertEquals(0, scrolled % line, "and it scrolls whole lines of the content's font: $scrolled")
    }

    @Test
    fun aContainerNarrowerThanTheViewportIsLaidOutAsARawPaneWouldLayItOut() =
        assertLaidOutAsARawPaneWould(Dimension(CONTENT_NARROW, CONTENT_LONG))

    @Test
    fun aContainerWiderThanTheViewportIsLaidOutAsARawPaneWouldLayItOut() =
        assertLaidOutAsARawPaneWould(Dimension(CONTENT_LONG, CONTENT_NARROW))

    @Test
    fun aContainerOutsideAViewportTracksNothing() = runComposeSwingTest {
        setContent {
            Column {
                Row(SwingModifier.preferredSize(CONTENT_NARROW, CONTENT_NARROW)) {
                    Label(text = "child")
                }
            }
        }

        val row = assertIs<Scrollable>(onNodeWithText("child").fetch().parent, "a container answers for itself")
        assertFalse(row.scrollableTracksViewportWidth, "a container with no viewport over it takes no width from one")
        assertFalse(row.scrollableTracksViewportHeight, "and no height either")
    }

    /**
     * Composes [content] as the whole of a pane's viewport, at a size that overflows it, and asserts the
     * pane moves by a line of that content's font wherever it moves by one unit.
     */
    private fun assertScrollsByALineOfItsOwnFont(content: @Composable (SwingModifier) -> Unit) = runComposeSwingTest {
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                content(SwingModifier.viewport().preferredSize(CONTENT_LONG, CONTENT_LONG))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val line = lineOf(pane.viewport.view)
        assertTrue(line > 1, "precondition: a line is more than the single pixel a pane falls back to")
        assertEquals(
            line,
            pane.verticalScrollBar.getUnitIncrement(1),
            "an arrow button, a wheel unit and a keyboard line scroll the pane down by a line",
        )
        assertEquals(
            line,
            pane.horizontalScrollBar.getUnitIncrement(1),
            "and across by one too",
        )
    }

    /**
     * Composes [content] as the whole of a pane's viewport, at a size that overflows it, and asserts the
     * pane moves by the viewport's own extent wherever it moves by one block.
     */
    private fun assertScrollsByAViewportPage(content: @Composable (SwingModifier) -> Unit) = runComposeSwingTest {
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                content(SwingModifier.viewport().preferredSize(CONTENT_LONG, CONTENT_LONG))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals(
            pane.viewport.viewRect.height,
            pane.verticalScrollBar.getBlockIncrement(1),
            "a page down moves the pane by the height the viewport shows",
        )
        assertEquals(
            pane.viewport.viewRect.width,
            pane.horizontalScrollBar.getBlockIncrement(1),
            "and a page across by the width it shows",
        )
    }

    /**
     * Composes a container of [contentSize] in a pane, and asserts it is laid out at the size a raw
     * `JScrollPane` of the same size lays out a view that answers nothing at all.
     */
    private fun assertLaidOutAsARawPaneWould(contentSize: Dimension) = runComposeSwingTest {
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                Column(SwingModifier.viewport().preferredSize(contentSize.width, contentSize.height)) {}
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val raw = JScrollPane(JPanel().also { it.preferredSize = contentSize })
        raw.size = pane.size
        raw.doLayout()
        raw.viewport.doLayout()

        assertEquals(
            raw.viewport.view.size,
            pane.viewport.view.size,
            "the container takes the viewport's extent where the viewport is the larger, and its own " +
                "size where it is not, exactly as a raw pane lays out a view answering nothing",
        )
    }

    /** A line of [view]'s own font, which is what the library's components scroll by. */
    private fun lineOf(view: java.awt.Component): Int =
        assertIs<JComponent>(view, "the content is the viewport's view as it stands").let {
            it.getFontMetrics(it.font).height
        }
}

private const val PANE_WIDTH: Int = 200
private const val PANE_HEIGHT: Int = 100

/** A content side that fits inside the viewport, so the pane has room to lay the content out in. */
private const val CONTENT_NARROW: Int = 50

/** A content side that overflows the viewport, so the pane has something to scroll. */
private const val CONTENT_LONG: Int = 400

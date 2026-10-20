package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A [Panel] in a scroll pane is the `JPanel` Swing builds, under every [PanelLayout]: the pane scrolls it and
 * lays it out exactly as a raw `JScrollPane` does a `JPanel` view.
 *
 * The answers are read through the pane the panel is composed in, so these tests pin what the arrow buttons,
 * the page keys and the layout do.
 */
class PanelScrollTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("panels")
    fun aPanelScrollsAsAJPanel(content: @Composable (SwingStyle<ContainerStyleScope>) -> Unit) =
        assertScrollsAsAJPanel(content)

    @Test
    fun aPanelNarrowerThanTheViewportIsLaidOutAsARawPaneWouldLayItOut() =
        assertLaidOutAsARawPaneWould(Dimension(50, 400))

    @Test
    fun aPanelWiderThanTheViewportIsLaidOutAsARawPaneWouldLayItOut() = assertLaidOutAsARawPaneWould(Dimension(400, 50))

    /**
     * Composes [content] as the whole of a pane's viewport, at a size that overflows it, and asserts it is the
     * pane's view as a stock `JPanel`, scrolled by a raw pane's unit and by the viewport's extent per block.
     */
    private fun assertScrollsAsAJPanel(content: @Composable (SwingStyle<ContainerStyleScope>) -> Unit) =
        runComposeSwingTest {
            setContent {
                ScrollPane(style = { preferredSize(200, 100) }) {
                    Viewport { content(SwingStyle { preferredSize(400, 400) }) }
                }
            }

            val pane = onNodeOfType<JScrollPane>().fetch()
            val raw = JScrollPane(JPanel())
            assertEquals<Class<*>>(
                JPanel::class.java,
                pane.viewport.view.javaClass,
                "the panel is the pane's view, as Swing builds it",
            )
            assertEquals(
                raw.verticalScrollBar.getUnitIncrement(1),
                pane.verticalScrollBar.getUnitIncrement(1),
                "an arrow button, a wheel unit and a keyboard line scroll the pane down as a raw pane's",
            )
            assertEquals(
                raw.horizontalScrollBar.getUnitIncrement(1),
                pane.horizontalScrollBar.getUnitIncrement(1),
                "and across as a raw pane's too",
            )
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
     * Composes a panel of [contentSize] in a pane, and asserts it is laid out at the size a raw `JScrollPane` of
     * the same size lays out a `JPanel` view.
     */
    private fun assertLaidOutAsARawPaneWould(contentSize: Dimension) = runComposeSwingTest {
        setContent {
            ScrollPane(style = { preferredSize(200, 100) }) {
                Viewport {
                    Panel(
                        PanelLayout.Box(),
                        style = { preferredSize(contentSize.width, contentSize.height) },
                    ) {}
                }
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
            "the panel takes the viewport's extent where the viewport is the larger, and its own size where it is not",
        )
    }

    companion object {
        @JvmStatic
        fun panels(): List<Named<@Composable (SwingStyle<ContainerStyleScope>) -> Unit>> = listOf(
            Named.of("horizontal box", { Panel(PanelLayout.Box(axis = BoxLayout.X_AXIS), style = it) {} }),
            Named.of("vertical box", { Panel(PanelLayout.Box(), style = it) {} }),
            Named.of("flow", { Panel(PanelLayout.Flow(), style = it) {} }),
            Named.of("grid", { Panel(PanelLayout.Grid(), style = it) {} }),
            Named.of("grid bag", { Panel(PanelLayout.GridBag, style = it) {} }),
            Named.of("border", { Panel(PanelLayout.Border(), style = it) {} }),
            Named.of("card", { Panel(PanelLayout.Card(selectedCard = "only"), style = it) {} }),
        )
    }
}

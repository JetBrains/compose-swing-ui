package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A row of the table the cases below put in a pane. */
private data class Person(
    val name: String,
    val age: Int,
)

/**
 * How a scroll pane scrolls and lays out content that is wrapped in an animated container: as it scrolls
 * and lays out that content without one. A pane asks only the view it holds, so the container has to
 * answer on the content's behalf.
 */
class AnimatedScrollableTest {
    private val people = List(ROWS) { Person("Person $it", it) }

    @Test
    fun `a table inside an animated container scrolls by its own rows`() =
        runComposeSwingTest {
            setContent {
                ScrollPane(modifier = SwingModifier.preferredSize(PANE_SIDE, PANE_SIDE)) {
                    Viewport {
                        AnimatedVisibility(visible = true) {
                            Table(rows = people, columns = { column("Name") { it.name } })
                        }
                    }
                }
            }

            val pane = onNodeOfType<JScrollPane>().fetch()
            val table = onNodeOfType<JTable>().fetch()
            assertSame(table.parent, pane.viewport.view, "the animated container is the pane's view")
            val row = table.getScrollableUnitIncrement(pane.viewport.viewRect, SwingConstants.VERTICAL, 1)
            assertTrue(row > 1, "precondition: a row is more than the single pixel a pane falls back to")
            assertEquals(
                row,
                pane.verticalScrollBar.getUnitIncrement(1),
                "an arrow button moves the pane by a row of the table the container holds",
            )
        }

    @Test
    fun `a table that takes the viewport's width is laid out at it without a scroll bar`() =
        runComposeSwingTest {
            setContent {
                ScrollPane(modifier = SwingModifier.preferredSize(PANE_SIDE, PANE_SIDE)) {
                    Viewport {
                        AnimatedVisibility(visible = true) {
                            Table(
                                rows = people,
                                columns = {
                                    column("Name") { it.name }
                                    column("Age") { it.age }
                                    column("Also name") { it.name }
                                    column("Also age") { it.age }
                                },
                            )
                        }
                    }
                }
            }

            val pane = onNodeOfType<JScrollPane>().fetch()
            assertFalse(pane.horizontalScrollBar.isVisible, "content taking the viewport's width needs no scroll bar")
            assertEquals(
                pane.viewport.width,
                pane.viewport.view.width,
                "the container is laid out at the viewport's width",
            )
        }

    @Test
    fun `a container holding two contents at once answers a pane as a panel of its own`() =
        runComposeSwingTest {
            var state by mutableStateOf(0)
            setContent {
                ScrollPane(modifier = SwingModifier.preferredSize(PANE_SIDE, PANE_SIDE)) {
                    Viewport {
                        AnimatedContent(targetState = state) { shown ->
                            Table(rows = people, columns = { column("Name $shown") { it.name } })
                        }
                    }
                }
            }
            val pane = onNodeOfType<JScrollPane>().fetch()
            val view = pane.viewport.view as Container

            mainClock.autoAdvance = false
            state = 1
            repeat(FRAMES_INTO_THE_TRANSITION) { driveOneFrame() }

            // The default enter's scale is mid-travel, so a hit-test overlay stands alongside both contents.
            assertEquals(3, view.componentCount, "precondition: both contents stand in the container")
            assertEquals(
                view.getFontMetrics(view.font).height,
                pane.verticalScrollBar.getUnitIncrement(1),
                "a container with two contents has no single content to ask, and answers a line of its own font",
            )
            assertEquals(
                pane.viewport.viewRect.height,
                pane.verticalScrollBar.getBlockIncrement(1),
                "a container with two contents answers a full viewport page",
            )
        }

    @Test
    fun `a container narrower than the viewport is stretched to it`() =
        // A content side well inside the pane, so the pane has room to stretch the container.
        assertLaidOutAsARawPaneWould(Dimension(50, 50))

    @Test
    fun `a container larger than the viewport keeps its own size`() =
        // A content side past the pane, so the container keeps its own size and the pane scrolls.
        assertLaidOutAsARawPaneWould(Dimension(400, 400))

    /**
     * That a pane lays the animated container out where it lays out any view answering nothing:
     * at the viewport's extent where the viewport is the larger, at the container's own size where it is
     * not. Answering a pane at all costs a view that stretch unless it answers for it too.
     */
    private fun assertLaidOutAsARawPaneWould(contentSize: Dimension) =
        runComposeSwingTest {
            setContent {
                ScrollPane(modifier = SwingModifier.preferredSize(PANE_SIDE, PANE_SIDE)) {
                    Viewport {
                        AnimatedVisibility(visible = true) {
                            Column(SwingModifier.preferredSize(contentSize.width, contentSize.height)) {}
                        }
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
                "the animated container is laid out exactly as a raw pane lays out a view answering nothing",
            )
        }
}

/** Rows enough to overflow the pane, so it has something to scroll. */
private const val ROWS = 40

/** The side of the pane, narrower than the four columns the second case declares. */
private const val PANE_SIDE = 150

/** Frames enough to mount the arriving content, so the container is holding both of them. */
private const val FRAMES_INTO_THE_TRANSITION = 3

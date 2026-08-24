package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a composable cell hands the widget: the component the cell composes, with nothing of the
 * library's between it and the row.
 *
 * The widget bounds that component at the row it paints and measures rows by its preferred size; its
 * layout decides the spacing and alignment inside it. A cell composing several components has no single
 * component to hand over, which is why it is refused.
 */
class ComposableCellLayoutTest {
    @Test
    fun theWidgetIsHandedTheComponentTheCellComposes() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("alpha", "beta")) { item ->
                Label(item)
            }
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        val cell = list.stampCell(index = 0)
        assertTrue(cell is JLabel, "a cell composing a label must be rendered by that label itself")
        assertEquals("alpha", (cell as JLabel).text, "the label must carry the row's own text")
    }

    @Test
    fun aRowIsMeasuredByTheCellsOwnComponent() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("alpha")) { item ->
                FlowPanel { Label(item) }
            }
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        val cell = list.stampCell(index = 0)
        assertEquals(
            cell.preferredSize.height,
            list.getCellBounds(0, 0).height,
            "the row must be exactly as tall as the component the cell composes",
        )
    }

    @Test
    fun aComboBoxIsMeasuredByTheCellsOwnComponent() = runComposeSwingTest {
        setContent {
            ComboBox(items = listOf("Kotlin", "Java"), selectedItem = "Kotlin", onSelectionChange = {}) { item ->
                FlowPanel { Label(item) }
            }
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        val cell = combo.renderer.stampCell(combo.getItemAt(0), index = -1)
        assertTrue(
            combo.preferredSize.height >= cell.preferredSize.height,
            "the combo box must hold the component the cell composes",
        )
    }

    @Test
    fun theSameComponentIsRestampedForEveryRow() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("alpha", "beta")) { item ->
                FlowPanel { Label(item) }
            }
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        val first = list.stampCell(index = 0)
        val second = list.stampCell(index = 1)
        assertSame(first, second, "every row must be rendered by the same reused component")
        assertEquals("beta", second.firstLabelText(), "the reused cell must carry row 1")
    }

    @Test
    fun cellsOfDifferentWidgetsRenderIndependently() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("alpha")) { item -> FlowPanel { Label(item) } }
            ListBox(items = listOf("beta")) { item -> FlowPanel { Label(item) } }
        }

        // Every cell island is rooted at the same empty container, so two widgets rendering composable
        // cells at once would collide there if a cell's component ever joined that root.
        val lists = onAllNodesOfType<JList<*>>().fetchAll()
        val first = lists[0].stampCell(index = 0)
        val second = lists[1].stampCell(index = 0)
        assertEquals("alpha", first.firstLabelText(), "the first widget renders its own row")
        assertEquals("beta", second.firstLabelText(), "the second widget renders its own row")
    }

    @Test
    fun aCellComposingSeveralComponentsIsRefused() {
        // The failure message names the container that would arrange the components instead.
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent {
                        ListBox(items = listOf("alpha")) { item ->
                            Label("*")
                            Label(item)
                        }
                    }
                }
            }
        assertContains(
            failure.message.orEmpty(),
            "single component",
            message = "the failure must say what a cell renders",
        )
    }

    @Test
    fun aCellComposingNothingRendersAnEmptyComponent() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("alpha")) { }
        }

        // A cell body that composes no component leaves the widget with nothing to paint the row
        // with, which is a cell that takes up no room rather than a stamp that fails.
        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        val cell = list.stampCell(index = 0)
        assertEquals(0, (cell as Container).componentCount, "an empty cell must hold nothing")
        assertEquals(Dimension(0, 0), cell.preferredSize, "an empty cell must ask for no room")
    }
}

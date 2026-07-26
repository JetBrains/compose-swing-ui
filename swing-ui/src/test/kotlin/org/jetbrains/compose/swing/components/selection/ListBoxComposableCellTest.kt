package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ListBox]'s composable `itemContent`. They prove the rubber-stamp mechanism end
 * to end: stamping a row through the installed [javax.swing.ListCellRenderer] realizes the composable
 * cell into a real Swing subtree, that the same reused renderer restamps as items/selection change, and
 * that omitting `itemContent` leaves the JList's default `toString` renderer in place.
 *
 * The cell subtree lives inside the renderer's reused host rather than the composition root, so these
 * drive the renderer directly (as `JList` does when it paints a row) and inspect the returned host.
 */
class ListBoxComposableCellTest {
    @Test
    fun itemContentRealizesAComposableCellPerRow() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("alpha", "beta", "gamma")) { item ->
                FlowPanel { Label(item) }
            }
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        // Stamp row 1 exactly as the JList's CellRendererPane would when painting it.
        val cell = stampRow(list, index = 1)

        assertEquals(
            "beta",
            cell.firstLabelText(),
            "the composable cell for row 1 should have realized a JLabel carrying that row's text",
        )
    }

    @Test
    fun theSameRendererRestampsAsRowsChange() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("one", "two", "three")) { item ->
                Label(item)
            }
        }

        val list = onNodeOfType<JList<String>>().fetch<JList<String>>()
        assertEquals("one", stampRow(list, index = 0).firstLabelText(), "row 0 should render its item")
        // A single reused composition/host restamps for each row the widget asks to paint.
        assertEquals("three", stampRow(list, index = 2).firstLabelText(), "the reused cell should restamp row 2")
        assertEquals("two", stampRow(list, index = 1).firstLabelText(), "and restamp again for row 1")
    }

    @Test
    fun recomposedRowContentUpdatesTheCell() = runComposeSwingTest {
        var items by mutableStateOf(listOf("draft"))
        setContent {
            ListBox(items = items) { item ->
                Label(item)
            }
        }

        val list = onNodeOfType<JList<String>>().fetch<JList<String>>()
        assertEquals("draft", stampRow(list, index = 0).firstLabelText(), "the initial item should render")

        // Changing the backing items recomposes the list and the cell restamps the new value for that row.
        items = listOf("final")
        awaitIdle()
        assertEquals(
            "final",
            stampRow(list, index = 0).firstLabelText(),
            "changing the item should restamp the cell with the new content",
        )
    }

    @Test
    fun cellScopeReflectsSelectionForTheStampedRow() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("x", "y")) { item ->
                Label(if (isSelected) "$item*" else item)
            }
        }

        val list = onNodeOfType<JList<String>>().fetch<JList<String>>()
        assertEquals(
            "y",
            stampRow(list, index = 1, isSelected = false).firstLabelText(),
            "an unselected stamp should render the plain item",
        )
        assertEquals(
            "y*",
            stampRow(list, index = 1, isSelected = true).firstLabelText(),
            "a selected stamp should observe isSelected through the ListItemScope",
        )
    }

    @Test
    fun composableCellsWorkInsideAScrollPane() = runComposeSwingTest {
        // A composable cell island joins the enclosing composition, so it must not inherit the slot
        // attachment of the ScrollPane viewport that hosts the ListBox — otherwise the cell's own nodes
        // would try to install into the renderer's plain host as if it were a JScrollPane. Selecting a
        // row synchronously stamps a cell during the enclosing composition's apply pass, which is exactly
        // when such leakage surfaces.
        setContent {
            ScrollPane {
                content {
                    ListBox(items = listOf("first", "second"), selectedIndices = listOf(0)) { item ->
                        FlowPanel { Label(item) }
                    }
                }
            }
        }

        val list = onNodeOfType<JList<String>>().fetch<JList<String>>()
        assertEquals(
            "second",
            stampRow(list, index = 1).firstLabelText(),
            "a composable cell inside a ScrollPane should realize its row content, not leak the viewport slot",
        )
    }

    @Test
    fun aStampAfterTheRendererLeavesTheCompositionIsSafe() = runComposeSwingTest {
        var showList by mutableStateOf(true)
        setContent {
            if (showList) {
                ListBox(items = listOf("alpha", "beta")) { item ->
                    Label(item)
                }
            }
        }

        val list = onNodeOfType<JList<String>>().fetch<JList<String>>()
        val cellBefore = stampRow(list, index = 0)

        // The JList outlives its composition: nothing resets the renderer the widget captured, and
        // Swing keeps dispatching queued focus/layout events against the widget while its window is
        // torn down, re-invoking that renderer after the cell island is disposed.
        showList = false
        awaitIdle()

        val cellAfter = stampRow(list, index = 1)
        assertSame(
            cellBefore,
            cellAfter,
            "a stamp on a disposed cell island must be a no-op returning the reused host",
        )
    }

    @Test
    fun omittingItemContentKeepsTheDefaultRenderer() = runComposeSwingTest {
        setContent { ListBox(items = listOf("a", "b")) }

        val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
        // The default JList renderer is a DefaultListCellRenderer (a JLabel), NOT our composing host.
        assertNull(
            list.cellRenderer as? ComposingListCellRenderer<*>,
            "omitting itemContent must leave the JList default renderer, not install a composing renderer",
        )
        val cell = stampRow(list, index = 0)
        assertTrue(cell is JLabel, "the default renderer stamps a JLabel")
        assertEquals("a", (cell as JLabel).text, "the default renderer renders the item's toString")
    }
}

/** Stamps [index] through [list]'s installed cell renderer exactly as its `CellRendererPane` would. */
private fun <T> stampRow(
    list: JList<T>,
    index: Int,
    isSelected: Boolean = false,
    cellHasFocus: Boolean = false,
): Component = list.cellRenderer.getListCellRendererComponent(
    list,
    list.model.getElementAt(index),
    index,
    isSelected,
    cellHasFocus,
)

/** The text of the first [JLabel] anywhere in this component subtree, or `null` if there is none. */
private fun Component.firstLabelText(): String? = firstLabel()?.text

private fun Component.firstLabel(): JLabel? = when (this) {
    is JLabel -> this
    is Container -> components.firstNotNullOfOrNull { it.firstLabel() }
    else -> null
}

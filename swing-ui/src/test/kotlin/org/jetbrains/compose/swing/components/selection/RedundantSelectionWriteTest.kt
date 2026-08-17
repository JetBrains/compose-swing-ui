package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.TableModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Re-applying a selection the widget already holds would rewrite it interval by interval. That rewrite
 * moves the lead the user's drag left behind - the row a following shift-click extends from. [ListBox]
 * and [Table] leave a matching selection alone, so a recomposition that changes nothing leaves the
 * selection model exactly as the user left it.
 *
 * A user's drag reaches a widget as a selection write from the row it started on to the row it ended on.
 * These tests write through the widget's own selection model to stand in for a drag, then change a
 * property that has nothing to do with selection.
 *
 * A selection names rows, not an order. A declaration counts as the one already applied no matter what
 * order it iterates in, even on a sorted table, which holds the same rows in an order of its own.
 */
class RedundantSelectionWriteTest {
    @Test
    fun aRecompositionThatChangesNothingKeepsTheListLead() = runComposeSwingTest {
        var count by mutableStateOf(8)
        setContent {
            ListBox(
                items = listOf("red", "green", "blue"),
                selectedIndices = linkedSetOf(2, 0, 1),
                visibleRowCount = count,
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        // A drag upwards from the last row: it ends - and so leaves the lead - on the first one.
        list.selectionModel.setSelectionInterval(2, 0)
        assertEquals(0, list.selectionModel.leadSelectionIndex, "the drag left the lead on the row it ended on")

        count = 6
        awaitIdle()

        assertEquals(listOf(0, 1, 2), list.selectedIndices.toList(), "the selection is the one already applied")
        assertEquals(0, list.selectionModel.leadSelectionIndex, "the lead the user left is where it was")
    }

    @Test
    fun aRecompositionThatChangesNothingKeepsTheTableLead() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                modifier = SwingModifier.name(label),
                selectedRowIndices = linkedSetOf(2, 0, 1),
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        // A drag upwards from the last row: it ends - and so leaves the lead - on the first one.
        table.selectionModel.setSelectionInterval(2, 0)
        assertEquals(0, table.selectionModel.leadSelectionIndex, "the drag left the lead on the row it ended on")

        label = "second"
        awaitIdle()

        assertEquals(listOf(0, 1, 2), table.selectedRows.toList(), "the selection is the one already applied")
        assertEquals(0, table.selectionModel.leadSelectionIndex, "the lead the user left is where it was")
    }

    @Test
    fun aRecompositionThatChangesNothingKeepsTheLeadOfASortedTable() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                modifier = SwingModifier.name(label),
                selectedRowIndices = linkedSetOf(2, 0, 1),
                sortable = true,
                sortKeys = listOf(SortKey(0, SortOrder.DESCENDING)),
            ) {
                column("Name") { it.name }
            }
        }

        // The declared rows are the model's; the descending sort draws them bottom to top, the reverse of
        // declared order.
        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Grace", "Alan", "Ada"), (0..2).map { table.getValueAt(it, 0) }, "sorted top to bottom")

        // A drag downwards from the first row: it ends - and so leaves the lead - on the last one.
        table.selectionModel.setSelectionInterval(0, 2)
        assertEquals(2, table.selectionModel.leadSelectionIndex, "the drag left the lead on the row it ended on")

        label = "second"
        awaitIdle()

        assertEquals(listOf(0, 1, 2), table.selectedRows.toList(), "the selection is the one already applied")
        assertEquals(2, table.selectionModel.leadSelectionIndex, "the lead the user left is where it was")
    }

    @Test
    fun aRecompositionThatChangesNothingKeepsTheLeadOfAFilteredTable() = runComposeSwingTest {
        var label by mutableStateOf("first")
        // Held across the whole test so it is the same instance every pass - a fresh one every pass would
        // read as a moved rowFilter and defeat what this test is checking.
        val filter = RowFilter.regexFilter<TableModel, Int>(".", 0)
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                modifier = SwingModifier.name(label),
                selectedRowIndices = linkedSetOf(2, 0, 1),
                sortable = true,
                rowFilter = filter,
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        // A drag downwards from the first row: it ends - and so leaves the lead - on the last one.
        table.selectionModel.setSelectionInterval(0, 2)
        assertEquals(2, table.selectionModel.leadSelectionIndex, "the drag left the lead on the row it ended on")

        label = "second"
        awaitIdle()

        assertEquals(listOf(0, 1, 2), table.selectedRows.toList(), "the selection is the one already applied")
        assertEquals(2, table.selectionModel.leadSelectionIndex, "the lead the user left is where it was")
    }
}

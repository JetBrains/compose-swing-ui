package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.TableModel
import kotlin.test.Test

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
        onNodeOfType<JList<*>>().assertProperty(
            0,
            "the drag left the lead on the row it ended on",
        ) { selectionModel.leadSelectionIndex }

        count = 6
        awaitIdle()

        onNodeOfType<JList<*>>().assertProperty(
            listOf(0, 1, 2),
            "the selection is the one already applied",
        ) { selectedIndices.toList() }
        onNodeOfType<JList<*>>().assertProperty(
            0,
            "the lead the user left is where it was",
        ) { selectionModel.leadSelectionIndex }
    }

    @Test
    fun aRecompositionThatChangesNothingKeepsTheTableLead() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                columns = { column("Name") { it.name } },
                modifier = SwingModifier.name(label),
                selectedRowIndices = linkedSetOf(2, 0, 1),
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        // A drag upwards from the last row: it ends - and so leaves the lead - on the first one.
        table.selectionModel.setSelectionInterval(2, 0)
        onNodeOfType<JTable>().assertProperty(
            0,
            "the drag left the lead on the row it ended on",
        ) { selectionModel.leadSelectionIndex }

        label = "second"
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(0, 1, 2),
            "the selection is the one already applied",
        ) { selectedRows.toList() }
        onNodeOfType<JTable>().assertProperty(
            0,
            "the lead the user left is where it was",
        ) { selectionModel.leadSelectionIndex }
    }

    @Test
    fun aRecompositionThatChangesNothingKeepsTheLeadOfASortedTable() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                columns = { column("Name") { it.name } },
                modifier = SwingModifier.name(label),
                state =
                    rememberTableState(
                        initialSelectedRowIndices = linkedSetOf(2, 0, 1),
                        initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING)),
                    ),
                sortable = true,
            )
        }

        // The declared rows are the model's; the descending sort draws them bottom to top, the reverse of
        // declared order.
        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTable>().assertProperty(
            listOf("Grace", "Alan", "Ada"),
            "sorted top to bottom",
        ) { (0..2).map { getValueAt(it, 0) } }

        // A drag downwards from the first row: it ends - and so leaves the lead - on the last one.
        table.selectionModel.setSelectionInterval(0, 2)
        onNodeOfType<JTable>().assertProperty(
            2,
            "the drag left the lead on the row it ended on",
        ) { selectionModel.leadSelectionIndex }

        label = "second"
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(0, 1, 2),
            "the selection is the one already applied",
        ) { selectedRows.toList() }
        onNodeOfType<JTable>().assertProperty(
            2,
            "the lead the user left is where it was",
        ) { selectionModel.leadSelectionIndex }
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
                columns = { column("Name") { it.name } },
                modifier = SwingModifier.name(label),
                selectedRowIndices = linkedSetOf(2, 0, 1),
                sortable = true,
                rowFilter = filter,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        // A drag downwards from the first row: it ends - and so leaves the lead - on the last one.
        table.selectionModel.setSelectionInterval(0, 2)
        onNodeOfType<JTable>().assertProperty(
            2,
            "the drag left the lead on the row it ended on",
        ) { selectionModel.leadSelectionIndex }

        label = "second"
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(0, 1, 2),
            "the selection is the one already applied",
        ) { selectedRows.toList() }
        onNodeOfType<JTable>().assertProperty(
            2,
            "the lead the user left is where it was",
        ) { selectionModel.leadSelectionIndex }
    }
}

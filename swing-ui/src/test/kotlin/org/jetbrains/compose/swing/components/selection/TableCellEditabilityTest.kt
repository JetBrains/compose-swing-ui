package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which cells of a column can be edited in place: the whole column, as `isEditable` says, or the rows a
 * per-row `isCellEditable` admits - which answers for every row of the column while it is declared.
 *
 * The rows a predicate is asked about are the ones the table was declared with, whatever order they are
 * drawn in, and an edit committed on a cell it admits reaches `onCellEdit` like any other.
 */
class TableCellEditabilityTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    @Test
    fun aPerRowPredicateDecidesWhichCellsCanBeEdited() = runComposeSwingTest {
        setContent {
            Table(rows = people) {
                column("Name") { it.name }
                column("Age", isCellEditable = { row, _ -> row.age > 40 }) { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertFalse(table.isCellEditable(0, 1), "the predicate should keep the younger row's cell read-only")
        assertTrue(table.isCellEditable(1, 1), "and make the older row's cell editable")
        assertFalse(table.isCellEditable(1, 0), "a column that declares neither stays read-only")
    }

    @Test
    fun aPerRowPredicateAnswersForAColumnDeclaredEditable() = runComposeSwingTest {
        setContent {
            Table(rows = people) {
                column("Age", isEditable = true, isCellEditable = { _, rowIndex -> rowIndex == 0 }) { it.age }
            }
        }

        // A per-row answer is what the column is asked for while it declares one, so it decides the rows a
        // column-wide `isEditable` would otherwise have settled for all of.
        val table = onNodeOfType<JTable>().fetch()
        assertTrue(table.isCellEditable(0, 0), "the row the predicate admits should be editable")
        assertFalse(table.isCellEditable(1, 0), "the row it refuses should not be, despite isEditable")
    }

    @Test
    fun aPredicateIsAskedAboutTheRowTheTableWasDeclaredWith() = runComposeSwingTest {
        val asked = mutableListOf<Pair<String, Int>>()
        setContent {
            Table(
                rows = people,
                sortable = true,
                sortKeys = listOf(SortKey(0, SortOrder.DESCENDING)),
            ) {
                column(
                    header = "Name",
                    isCellEditable = { row, rowIndex ->
                        asked += row.name to rowIndex
                        true
                    },
                ) { it.name }
            }
        }

        // Sorted by name descending, "Alan" is drawn first; the predicate is asked about the row that cell
        // holds, named by its index into the declared rows.
        val table = onNodeOfType<JTable>().fetch()
        asked.clear()
        assertTrue(table.isCellEditable(0, 0), "the predicate admits the cell")
        assertEquals(listOf("Alan" to 1), asked.toList(), "the predicate should be asked about the declared row")
    }

    @Test
    fun aChangedPredicateDecidesTheNextAnswer() = runComposeSwingTest {
        var threshold by mutableStateOf(40)
        setContent {
            Table(rows = people) {
                column("Age", isCellEditable = { row, _ -> row.age > threshold }) { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertFalse(table.isCellEditable(0, 0), "the younger row starts read-only")

        threshold = 30
        awaitIdle()
        assertTrue(table.isCellEditable(0, 0), "a changed predicate should decide the next answer")
    }

    @Test
    fun anEditOnAPerRowEditableCellCommits() = runComposeSwingTest {
        val edits = mutableListOf<Triple<String, Int, Any?>>()
        setContent {
            Table(rows = people) {
                column(
                    header = "Age",
                    isCellEditable = { row, _ -> row.age > 40 },
                    onCellEdit = { row, rowIndex, newValue -> edits += Triple(row.name, rowIndex, newValue) },
                ) { it.age }
            }
        }

        // Committing an edit routes through JTable.setValueAt -> model.setValueAt, the same path the cell
        // editor takes on commit.
        val table = onNodeOfType<JTable>().fetch()
        table.setValueAt(42, 1, 0)

        assertEquals(Triple("Alan", 1, 42), edits.single(), "the edited row, its index, and the new value")
    }
}

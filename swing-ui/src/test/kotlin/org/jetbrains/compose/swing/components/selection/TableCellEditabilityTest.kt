package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Which cells of a column can be edited in place: every cell of a column that sets `onCellEdit`, narrowed
 * to the rows a per-row `isCellEditable` admits - which answers for every row of the column while it is
 * declared. A column without `onCellEdit` edits nothing.
 *
 * The rows a predicate is asked about are the ones the table was declared with, whatever order they are
 * drawn in, and an edit committed on a cell it admits reaches `onCellEdit` like any other.
 */
class TableCellEditabilityTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    @Test
    fun onlyAColumnWithOnCellEditStartsAnEditorAndItsEditArrivesInTheColumnsClass() = runComposeSwingTest {
        val edits = mutableListOf<Any?>()
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name") { it.name }
                    column("Age", onCellEdit = { _, _, age -> edits += age }) { it.age }
                },
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertFalse(table.editCellAt(0, 0), "a column without onCellEdit should not start an editor")
        assertTrue(table.editCellAt(0, 1), "a column with onCellEdit should start one")
        (table.editorComponent as JTextField).text = "37"
        table.cellEditor.stopCellEditing()

        val age = edits.single()
        assertIs<Int>(age, "the edit should arrive as the column's class")
        assertEquals(37, age)
    }

    @Test
    fun aPerRowPredicateNarrowsEditingToTheRowsItAdmits() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name") { it.name }
                    column("Age", isCellEditable = { row, _ -> row.age > 40 }, onCellEdit = { _, _, _ -> }) { it.age }
                },
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertFalse(table.isCellEditable(0, 1), "the predicate should keep the younger row's cell read-only")
        assertTrue(table.isCellEditable(1, 1), "and make the older row's cell editable")
        assertFalse(table.isCellEditable(1, 0), "a column without onCellEdit stays read-only")
    }

    @Test
    fun aPerRowPredicateWithoutOnCellEditIsRefused() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    Table(
                        rows = people,
                        columns = { column("Age", isCellEditable = { _, _ -> true }) { it.age } },
                    )
                }
                awaitIdle()
            }

        assertContains(
            failure.message.orEmpty(),
            "isCellEditable is set on column \"Age\" but onCellEdit is not",
            message = "the refusal must name the column and the missing callback",
        )
        assertContains(failure.message.orEmpty(), "Set onCellEdit", message = "and say what to do")
    }

    @Test
    fun togglingOnCellEditTurnsEditingOffAndOn() = runComposeSwingTest {
        var editable by mutableStateOf(false)
        val onCellEdit: (Person, Int, Int?) -> Unit = { _, _, _ -> }
        setContent {
            Table(
                rows = people,
                columns = { column("Age", onCellEdit = if (editable) onCellEdit else null) { it.age } },
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertFalse(table.editCellAt(0, 0), "the column starts read-only")

        editable = true
        awaitIdle()
        assertTrue(table.editCellAt(0, 0), "setting onCellEdit should make the column editable")
        table.cellEditor.cancelCellEditing()

        editable = false
        awaitIdle()
        assertFalse(table.editCellAt(0, 0), "taking onCellEdit away should make it read-only again")
    }

    @Test
    fun aPredicateIsAskedAboutTheRowTheTableWasDeclaredWith() = runComposeSwingTest {
        val asked = mutableListOf<Pair<String, Int>>()
        setContent {
            Table(
                rows = people,
                columns = {
                    column(
                        header = "Name",
                        isCellEditable = { row, rowIndex ->
                            asked += row.name to rowIndex
                            true
                        },
                        onCellEdit = { _, _, _ -> },
                    ) { it.name }
                },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING))),
                sortable = true,
            )
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
            Table(
                rows = people,
                columns = {
                    column("Age", isCellEditable = { row, _ -> row.age > threshold }, onCellEdit = { _, _, _ -> }) {
                        it.age
                    }
                },
            )
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
            Table(
                rows = people,
                columns = {
                    column(
                        header = "Age",
                        isCellEditable = { row, _ -> row.age > 40 },
                        onCellEdit = { row, rowIndex, newValue -> edits += Triple(row.name, rowIndex, newValue) },
                    ) { it.age }
                },
            )
        }

        // Committing an edit routes through JTable.setValueAt -> model.setValueAt, the same path the cell
        // editor takes on commit.
        val table = onNodeOfType<JTable>().fetch()
        table.setValueAt(42, 1, 0)

        assertEquals(Triple("Alan", 1, 42), edits.single(), "the edited row, its index, and the new value")
    }
}

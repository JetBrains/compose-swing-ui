package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What becomes of an edit the user has open when the rows, the ordering they are drawn in, or the order of
 * the columns changes under it: an edit whose cell the latest declarations no longer describe is ended,
 * rather than left to commit into the cell that took its place, and one whose row an insert or a delete
 * elsewhere only moved follows that row.
 */
class TableEditInFlightTest {
    @Test
    fun aRowThatGoesAwayUnderAnEditorEndsTheEdit() = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41)))
        val edited = mutableListOf<Pair<Person, Any?>>()
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { row, _, value -> edited += row to value }) {
                        it.name
                    }
                },
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(1, 0), "the second row's cell should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        rows = listOf(Person("Ada", 36))
        awaitIdle()

        assertFalse(table.isEditing, "the edit should end with the row it was opened on")
        assertEquals(emptyList(), edited, "and commit nothing, since the row it named is gone")
    }

    @Test
    fun aRowTakingAnothersPlaceUnderAnEditorEndsTheEdit() = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41)))
        setContent {
            Table(rows = rows, columns = { column("Name", onCellEdit = { _, _, _ -> }) { it.name } })
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(1, 0), "the second row's cell should open for editing")

        // The row being edited is rewritten in place.
        rows = listOf(Person("Ada", 36), Person("Grace", 45))
        awaitIdle()

        assertFalse(table.isEditing, "the edit should end with the row it was opened on")
    }

    @Test
    fun aChangeElsewhereLeavesAnEditStanding() = runComposeSwingTest {
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41)))
        setContent {
            Table(rows = rows, columns = { column("Name", onCellEdit = { _, _, _ -> }) { it.name } })
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(1, 0), "the second row's cell should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        // A row the editor does not stand on, so what the user is typing is none of its business.
        rows = listOf(Person("Grace", 45), Person("Alan", 41))
        awaitIdle()

        assertTrue(table.isEditing, "an edit on a row that did not change should stand")
        assertEquals("Turing", (table.editorComponent as JTextField).text, "with what was typed into it")
    }

    @Test
    fun aRowArrivingAboveAnEditorInASortedTableCarriesTheEditAlong() = runComposeSwingTest {
        // The table re-points the editing row itself across an insert, so the edit is none of that
        // insert's business.
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41)))
        val edited = mutableListOf<Pair<Person, Any?>>()
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { row, _, value -> edited += row to value }) {
                        it.name
                    }
                },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING))),
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        // Sorted by name descending, the model's second row is drawn on top.
        assertTrue(table.editCellAt(0, 0), "the top row on screen should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        rows = listOf(Person("Grace", 45), Person("Ada", 36), Person("Alan", 41))
        awaitIdle()

        assertTrue(table.isEditing, "an edit on a row that only moved should stand")
        assertEquals(1, table.editingRow, "on the row it was opened on, where the insert left it")
        assertEquals("Turing", (table.editorComponent as JTextField).text, "with what was typed into it")

        table.cellEditor.stopCellEditing()

        assertEquals(Person("Alan", 41) to "Turing", edited.single(), "committing onto that same row")
    }

    @Test
    fun aRowArrivingAboveAnEditorInAnUnsortedTableCarriesTheEditAlong() = runComposeSwingTest {
        // A table without a sorter leaves the editing row at the index the editor was opened on, which the
        // insert has left naming the row above, so the model re-points it.
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41)))
        val edited = mutableListOf<Pair<Person, Any?>>()
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { row, _, value -> edited += row to value }) {
                        it.name
                    }
                },
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(1, 0), "the second row's cell should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        rows = listOf(Person("Grace", 45), Person("Ada", 36), Person("Alan", 41))
        awaitIdle()

        assertTrue(table.isEditing, "an edit on a row that only moved should stand")
        assertEquals(2, table.editingRow, "on the row it was opened on, where the insert left it")
        assertEquals("Turing", (table.editorComponent as JTextField).text, "with what was typed into it")

        table.cellEditor.stopCellEditing()

        assertEquals(Person("Alan", 41) to "Turing", edited.single(), "committing onto that same row")
    }

    @Test
    fun aRowLeavingAboveAnEditorInASortedTableCarriesTheEditAlong() = runComposeSwingTest {
        // A delete re-points a standing editor the same way an insert does, in the other direction.
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Grace", 45), Person("Alan", 41)))
        setContent {
            Table(
                rows = rows,
                columns = { column("Name", onCellEdit = { _, _, _ -> }) { it.name } },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING))),
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        // Sorted by name descending the rows are drawn Grace, Alan, Ada, so the model's last row is the
        // second one on screen, and the row taken away below is the one above it.
        assertTrue(table.editCellAt(1, 0), "the second row on screen should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        rows = listOf(Person("Ada", 36), Person("Alan", 41))
        awaitIdle()

        assertTrue(table.isEditing, "an edit on a row that only moved should stand")
        assertEquals(0, table.editingRow, "on the row it was opened on, where the delete left it")
        assertEquals("Turing", (table.editorComponent as JTextField).text, "with what was typed into it")
    }

    @Test
    fun aMixedChangeInASortedTableEndsAnEditEvenBeforeTheRowsDiffer() = runComposeSwingTest {
        // A change that both adds and removes rows goes out as the wholesale change, and a sorted
        // `JTable` cancels the editor over one whatever the wrapper did with it: `sortedTableChanged`
        // looks for no model row behind the editor of an all-rows event and restores it onto none. This
        // pins what the column's own documentation promises, not the wrapper's guard: the table ends the
        // edit on its own.
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 45)))
        setContent {
            Table(
                rows = rows,
                columns = { column("Name", onCellEdit = { _, _, _ -> }) { it.name } },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING))),
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        // Sorted by name descending the rows are drawn Grace, Alan, Ada, so the model's first row - the
        // one the two lists still share - is the last one on screen.
        assertTrue(table.editCellAt(2, 0), "the last row on screen should open for editing")

        rows = listOf(Person("Ada", 36), Person("Zoe", 30), Person("Bob", 52), Person("Cy", 28))
        awaitIdle()

        assertFalse(table.isEditing, "a sorted table should cancel the edit over the wholesale change")
    }

    @Test
    fun aRowGoingOutOfRangeUnderAnEditorEndsTheEditEvenWhereItWasNull() = runComposeSwingTest {
        // A row list holds whatever the caller declares, `null` among it.
        var rows by mutableStateOf(listOf<String?>("Ada", null))
        setContent {
            Table(rows = rows, columns = { column("Name", onCellEdit = { _, _, _ -> }) { it } })
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(1, 0), "the null row's cell should open for editing")

        rows = listOf("Ada")
        awaitIdle()

        assertFalse(table.isEditing, "the edit should end with the row that went out of range")
    }

    @Test
    fun aMixedChangeUnderAnEditorEndsTheEdit() = runComposeSwingTest {
        // A change that both adds and removes rows names no run to shift an index by, so a row at or
        // past the first row the two lists differ at is gone. Unsorted, nothing else ends the edit: a
        // wholesale change clears the selection and repaints, and never touches the editor.
        var rows by
            mutableStateOf(
                listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 45), Person("Dot", 50)),
            )
        val edited = mutableListOf<Pair<Person, Any?>>()
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { row, _, value -> edited += row to value }) {
                        it.name
                    }
                },
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        // The last row is past the run the two lists differ over: shifting it by what the change added
        // and removed would leave it standing.
        assertTrue(table.editCellAt(3, 0), "the last row's cell should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        rows =
            listOf(
                Person("Ada", 36),
                Person("Zoe", 30),
                Person("Bob", 52),
                Person("Cy", 28),
                Person("Dot", 50),
            )
        awaitIdle()

        assertFalse(table.isEditing, "the edit should end where the change both adds and removes rows")
        assertEquals(emptyList(), edited, "and commit nothing into the row that took its place")
    }

    @Test
    fun aNullRowStandingUnderAnEditorKeepsTheEdit() = runComposeSwingTest {
        // What ends an edit is the model holding nothing at the row, which is not the same question as
        // the row being null: a null row the change leaves standing carries its edit and commits it.
        var rows by mutableStateOf(listOf<String?>("Ada", null))
        val edited = mutableListOf<Pair<String?, Any?>>()
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { row, _, value -> edited += row to value }) {
                        it
                    }
                },
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(1, 0), "the null row's cell should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        // Only the row ahead of it is rewritten.
        rows = listOf("Grace", null)
        awaitIdle()

        assertTrue(table.isEditing, "the edit should stand on a null row the change left where it was")

        table.cellEditor.stopCellEditing()

        assertEquals(listOf<Pair<String?, Any?>>(null to "Turing"), edited, "and commit against that row")
    }

    @Test
    fun aSortedTableFindsTheEditedRowInTheModel() = runComposeSwingTest {
        // The editor names a row of the view; which row of the declared list that is is what decides
        // whether the edit still stands, so the two have to be told apart.
        var rows by mutableStateOf(listOf(Person("Ada", 36), Person("Alan", 41)))
        setContent {
            Table(
                rows = rows,
                columns = { column("Name", onCellEdit = { _, _, _ -> }) { it.name } },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING))),
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        // Sorted by name descending, the model's second row is drawn on top.
        assertTrue(table.editCellAt(0, 0), "the top row on screen should open for editing")

        rows = listOf(Person("Ada", 36), Person("Grace", 45))
        awaitIdle()

        assertFalse(table.isEditing, "the edit should end with the model row it was opened on")
    }

    @Test
    fun takingTheSorterOffUnderAnEditorEndsTheEdit() = runComposeSwingTest {
        // The editor names a view row, and the sorter is what decides which row of the model that is.
        var sortable by mutableStateOf(true)
        val edited = mutableListOf<Pair<Person, Any?>>()
        val rows = listOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { row, _, value -> edited += row to value }) {
                        it.name
                    }
                },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING))),
                sortable = sortable,
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        // Sorted by name descending, the model's second row is drawn on top.
        assertTrue(table.editCellAt(0, 0), "the top row on screen should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        sortable = false
        awaitIdle()

        assertFalse(table.isEditing, "the edit should end with the ordering it was opened under")
        assertEquals(emptyList(), edited, "and commit nothing, rather than onto the row that took its place")
    }

    @Test
    fun turningOnCellEditOffUnderAnEditorEndsTheEdit() = runComposeSwingTest {
        // onCellEdit turning off is a structure change: it rebuilds the columns, the editor's among them.
        var editable by mutableStateOf(true)
        val onCellEdit: (Person, Int, Any?) -> Unit = { _, _, _ -> }
        val rows = listOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(
                rows = rows,
                columns = { column("Name", onCellEdit = if (editable) onCellEdit else null) { it.name } },
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(1, 0), "the second row's cell should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        editable = false
        awaitIdle()

        assertFalse(table.isEditing, "the edit should end with the column that stopped taking edits")
    }

    @Test
    fun aColumnWidthAssignedUnderAnEditorCommitsTheEditRatherThanDiscardingIt() = runComposeSwingTest {
        // Applying a layout writes a width onto every column it names, and only a column it actually
        // moves re-points the editor. A width is answered by the table itself, which commits the edit
        // over the margin change it publishes; ending the edit here would discard what was typed.
        val state = TableState(initialColumnLayout = TableColumnLayout(listOf(0, 1), listOf(100, 100)))
        val edited = mutableListOf<Pair<String, Any?>>()
        val rows = listOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { _, _, value -> edited += "Name" to value }) {
                        it.name
                    }
                    column("Age") { it.age }
                },
                state = state,
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(0, 0), "the name cell should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        state.columnLayout = TableColumnLayout(listOf(0, 1), listOf(150, 100))
        awaitIdle()

        assertEquals(150, table.columnModel.getColumn(0).preferredWidth, "the assigned width should reach the column")
        assertEquals(
            listOf<Pair<String, Any?>>("Name" to "Turing"),
            edited,
            "and the edit it moves no column under should commit into the cell it was opened on",
        )
    }

    @Test
    fun reorderingColumnsUnderAnEditorEndsTheEdit() = runComposeSwingTest {
        // The editor names a view column, and a reorder re-points that column at another of the model's.
        val state = TableState(initialColumnLayout = TableColumnLayout(listOf(0, 1), listOf(100, 100)))
        val edited = mutableListOf<Pair<String, Any?>>()
        val rows = listOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { _, _, value -> edited += "Name" to value }) {
                        it.name
                    }
                    // The column the move draws in the editor's place, listening so that a commit into it is
                    // seen.
                    column("Age", onCellEdit = { _, _, value -> edited += "Age" to value }) { it.age }
                },
                state = state,
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(0, 0), "the name cell should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        state.columnLayout = TableColumnLayout(listOf(1, 0), listOf(100, 100))
        awaitIdle()

        assertFalse(table.isEditing, "the edit should end with the column order it was opened under")
        assertEquals(emptyList(), edited, "and commit nothing, rather than into the column that took its place")
        assertEquals("Ada", table.getValueAt(0, 1), "leaving the name the row was declared with standing")
    }

    @Test
    fun takingTheSorterOffUnderAnEditorOnAnUnmovedRowCarriesTheEditAlong() = runComposeSwingTest {
        // Sorted the way the model already holds them, so the rows are drawn where they would be without
        // a sorter and the editor names the same row either way.
        var sortable by mutableStateOf(true)
        val edited = mutableListOf<Pair<Person, Any?>>()
        val rows = listOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { row, _, value -> edited += row to value }) {
                        it.name
                    }
                },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING))),
                sortable = sortable,
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(0, 0), "the top row on screen should open for editing")
        (table.editorComponent as JTextField).text = "Turing"

        sortable = false
        awaitIdle()

        assertTrue(table.isEditing, "an edit on a row the sorter drew where it stands should stand")
        assertEquals(0, table.editingRow, "on the row it was opened on")
        assertEquals("Turing", (table.editorComponent as JTextField).text, "with what was typed into it")

        table.cellEditor.stopCellEditing()

        assertEquals(Person("Ada", 36) to "Turing", edited.single(), "committing onto that same row")
    }

    @Test
    fun turningSortingOnUnderAnEditorCarriesTheEditAlong() = runComposeSwingTest {
        // A sorter takes the table unsorted and is told to sort afterwards, and that sort is published, so
        // the table re-points the editor across it itself.
        var sortable by mutableStateOf(false)
        val edited = mutableListOf<Pair<Person, Any?>>()
        val rows = listOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { row, _, value -> edited += row to value }) {
                        it.name
                    }
                },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING))),
                sortable = sortable,
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(0, 0), "the top row should open for editing while the rows are unsorted")
        (table.editorComponent as JTextField).text = "Turing"

        sortable = true
        awaitIdle()

        assertTrue(table.isEditing, "an edit on a row that only moved should stand")
        // Sorted by name descending, the row the editor was opened on is drawn second.
        assertEquals(1, table.editingRow, "on the row it was opened on, where the sort left it")
        assertEquals("Turing", (table.editorComponent as JTextField).text, "with what was typed into it")

        table.cellEditor.stopCellEditing()

        assertEquals(Person("Ada", 36) to "Turing", edited.single(), "committing onto that same row")
    }

    @Test
    fun aFilterArrivingWithTheSorterUnderAnEditorEndsTheEdit() = runComposeSwingTest {
        // A sorter carries the declared filter before the table takes it, so the rows it hides are gone
        // with no event for the table to re-point the editor by.
        var sortable by mutableStateOf(false)
        val edited = mutableListOf<Pair<Person, Any?>>()
        val rows = listOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(
                rows = rows,
                columns = {
                    column("Name", onCellEdit = { row, _, value -> edited += row to value }) {
                        it.name
                    }
                },
                sortable = sortable,
                rowFilter = RowFilter.regexFilter("Alan", 0),
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        assertTrue(table.editCellAt(0, 0), "the first row should open for editing while nothing is filtered")
        (table.editorComponent as JTextField).text = "Turing"

        sortable = true
        awaitIdle()

        assertFalse(table.isEditing, "the edit should end with the row the filter takes away")
        assertEquals(emptyList(), edited, "and commit nothing, rather than onto the row that took its place")
    }
}

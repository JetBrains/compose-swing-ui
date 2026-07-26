package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.ListSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [Table], driven through the real composition pipeline and asserting against
 * the live `JTable` and its `TableModel`.
 *
 * The central guarantees: rows/columns render into the model; user selection fires
 * `onSelectionChange` with the selected row indices; committing an edit on an editable cell fires
 * `onCellEdit`; state-driven row changes rebuild the model; a pass that changed no data leaves the
 * table's columns alone; and a controlled selection update does not echo back as a spurious callback.
 *
 * Headless caveat: no native peer realizes, so a cell edit is committed through the same
 * model-write path the cell editor uses on commit (`JTable.setValueAt`), and a user selection is
 * driven through the table's selection model, exactly where a real mouse gesture would land.
 */
class TableBehaviorTest {
    @Test
    fun rowsAndColumnsRenderIntoTheModel() = runComposeSwingTest {
        setContent {
            Table(rows = listOf(Person("Ada", 36), Person("Alan", 41))) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val model = table.model
        assertEquals(2, model.rowCount, "row count")
        assertEquals(2, model.columnCount, "column count")
        assertEquals("Name", model.getColumnName(0), "column 0 header")
        assertEquals("Age", model.getColumnName(1), "column 1 header")
        assertEquals("Ada", model.getValueAt(0, 0), "cell (0,0) value")
        assertEquals(36, model.getValueAt(0, 1), "cell (0,1) value")
        assertEquals("Alan", model.getValueAt(1, 0), "cell (1,0) value")
        assertEquals(41, model.getValueAt(1, 1), "cell (1,1) value")
    }

    @Test
    fun selectingRowsFiresOnSelectionChange() = runComposeSwingTest {
        val received = mutableListOf<List<Int>>()
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                onSelectionChange = { received += it },
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        // Drive the selection through the table's selection model, where a real mouse gesture
        // would land; the wrapper's listener observes it and fires onSelectionChange.
        table.setRowSelectionInterval(0, 0)
        table.addRowSelectionInterval(2, 2)
        awaitIdle()

        assertEquals(listOf(0, 2), received.last(), "selected row indices reported to callback")
    }

    @Test
    fun editingAnEditableCellFiresOnCellEdit() = runComposeSwingTest {
        val edits = mutableListOf<Triple<String, Int, Any?>>()
        setContent {
            Table(rows = listOf(Person("Ada", 36), Person("Alan", 41))) {
                column("Name") { it.name }
                column(
                    header = "Age",
                    isEditable = true,
                    onCellEdit = { row, rowIndex, newValue -> edits += Triple(row.name, rowIndex, newValue) },
                ) { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertFalse(table.isCellEditable(0, 0), "Name column must be read-only")
        assertTrue(table.isCellEditable(0, 1), "Age column must be editable")
        // Committing an edit routes through JTable.setValueAt -> model.setValueAt, the same
        // path the cell editor takes on commit.
        table.setValueAt(37, 0, 1)

        assertEquals(1, edits.size, "exactly one edit committed")
        assertEquals(Triple("Ada", 0, 37), edits.single(), "edited row, index, and new value")
    }

    @Test
    fun stateDrivenRowsUpdateTheTable() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36))
        setContent {
            Table(rows = rows.toList()) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(1, table.model.rowCount, "the model should start with one row")

        rows.add(Person("Alan", 41))
        awaitIdle()

        assertEquals(2, table.model.rowCount, "row added to the model")
        assertEquals("Alan", table.model.getValueAt(1, 0), "the added row's cell should render")

        rows.clear()
        rows.add(Person("Grace", 50))
        awaitIdle()

        assertEquals(1, table.model.rowCount, "model reflects replaced rows")
        assertEquals("Grace", table.model.getValueAt(0, 0), "the replaced row's cell should render")
    }

    @Test
    fun controlledSelectionUpdateConvergesWithoutALoop() = runComposeSwingTest {
        // The controller mirrors the callback back into the controlled state, exactly as a real
        // caller would. The selection guard must make this converge: applying an external update
        // settles on that selection without oscillating between values frame after frame.
        var selection by mutableStateOf(listOf(0))
        val received = mutableListOf<List<Int>>()
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(0), table.selectedRows.toList(), "initial selection applied")

        // A purely external selection update applies to the table and settles. Because the guard
        // skips re-applying an unchanged selection, any echoed callback carries the SAME new
        // indices the controller already holds, so it does not bounce back to the old value.
        selection = listOf(2)
        awaitIdle()

        assertEquals(listOf(2), table.selectedRows.toList(), "external selection applied")
        assertEquals(listOf(2), selection, "controlled state settled on the new selection")
        assertTrue(
            received.all { it == listOf(2) },
            "selection oscillated instead of converging: $received",
        )

        // A second idle pass produces no further churn: the guard sees the selection unchanged
        // and re-applies nothing, so no new callback fires.
        val callbacksAfterSettle = received.size
        awaitIdle()
        assertEquals(callbacksAfterSettle, received.size, "selection kept firing callbacks after settling")
    }

    @Test
    fun aPassThatChangedNoDataLeavesTheColumnsAlone() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Table(rows = listOf(Person("Ada", 36)), modifier = SwingModifier.name(label)) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        // A structure change rebuilds the table's columns, taking any width along with them, so the
        // column object the table still carries is what tells a narrow refresh from a broad one.
        val column = table.columnModel.getColumn(0)
        column.preferredWidth = 123

        label = "second"
        awaitIdle()

        assertSame(column, table.columnModel.getColumn(0), "the column survives a pass that changed no data")
        assertEquals(123, table.columnModel.getColumn(0).preferredWidth, "and so does the width set on it")
    }
}

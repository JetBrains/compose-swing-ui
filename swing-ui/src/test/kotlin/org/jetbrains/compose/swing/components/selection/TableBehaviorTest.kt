package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedMoveIsPutBack
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JCheckBox
import javax.swing.JTable
import javax.swing.JTextField
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
 * The root is never attached to a window, so no native peer realizes. Where the editor itself is
 * under test, it is driven directly, since opening one on screen would take the focus.
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
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                onSelectionChange = { received += it },
                selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
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

        assertEquals(setOf(0, 2), received.last(), "selected row indices reported to callback")
    }

    /**
     * The table is settled back onto the declared selection by the time the move's own recomposition
     * finishes, not once some later, unrelated recomposition happens to run. A declaration is the
     * composition's state and is asserted again for every move made against it, so a user who makes the
     * same move twice is answered twice: what the table was left holding after the first is not a reason
     * to leave the second alone.
     */
    @Test
    fun aSelectionTheCallerDoesNotAdoptDoesNotStandWhenItIsMadeAgain() = runComposeSwingTest {
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41)),
                selectedRowIndices = setOf(0),
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        repeat(2) {
            table.setRowSelectionInterval(1, 1)
            awaitIdle()
        }

        assertEquals(
            listOf(0),
            table.selectedRows.toList(),
            "an unadopted selection change does not stand, however often it is made",
        )
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
    fun eachColumnAnswersWithTheClassOfTheValuesItHolds() = runComposeSwingTest {
        setContent {
            Table(rows = listOf(Person("Ada", 36))) {
                column("Name") { it.name }
                column("Age") { it.age }
                column("Senior") { it.age > 40 }
            }
        }

        val model = onNodeOfType<JTable>().fetch().model
        assertEquals(String::class.java, model.getColumnClass(0), "a column of names holds strings")
        assertEquals(Int::class.javaObjectType, model.getColumnClass(1), "a column of ages holds integers")
        assertEquals(Boolean::class.javaObjectType, model.getColumnClass(2), "a column of flags holds booleans")
    }

    @Test
    fun aBooleanColumnRendersAsACheckBox() = runComposeSwingTest {
        setContent {
            Table(rows = listOf(Person("Ada", 36))) {
                column("Name") { it.name }
                column("Senior") { it.age > 40 }
            }
        }

        // A table picks a cell's renderer by the column's class, so the class the column declares is
        // what decides whether a flag is drawn as a checkbox or written out as text. The wrapper
        // installs no renderer of its own.
        val table = onNodeOfType<JTable>().fetch()
        assertTrue(table.getCellRenderer(0, 1) is JCheckBox, "a boolean column should draw a checkbox")
        assertFalse(table.getCellRenderer(0, 0) is JCheckBox, "a string column should not")
    }

    @Test
    fun anIntColumnCommitsItsEditAsAnInt() = runComposeSwingTest {
        val edits = mutableListOf<Any?>()
        setContent {
            Table(rows = listOf(Person("Ada", 36))) {
                column("Age", isEditable = true, onCellEdit = { _, _, newValue -> edits += newValue }) { it.age }
            }
        }

        // The column's class picks the editor as well as the renderer. Driving the editor, not the
        // model, puts under test how it turns typed text into a value of the
        // column's class; committing it is the model write JTable makes once editing stops.
        val table = onNodeOfType<JTable>().fetch()
        val editor = table.getCellEditor(0, 0)
        val typedInto = editor.getTableCellEditorComponent(table, 36, false, 0, 0) as JTextField
        typedInto.text = "37"
        editor.stopCellEditing()
        table.setValueAt(editor.cellEditorValue, 0, 0)

        assertEquals(listOf<Any?>(37), edits, "an int column should commit the edited value as an int")
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
    fun aSelectionTheUserMadeStandsAcrossARowChange() = runComposeSwingTest {
        // The selection the caller declared nothing about is the user's, and a pass that gives the table
        // new rows keeps it: a change naming only the added rows leaves it where it is, and one spanning
        // the table empties it and has it put back. The row it names is the one it named before, so the
        // lead and the anchor stand as well.
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(rows = rows) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.setRowSelectionInterval(0, 0)
        awaitIdle()

        rows.add(Person("Grace", 45))
        awaitIdle()

        assertEquals(3, table.model.rowCount, "the pass this asserts about has to have run")
        assertEquals(listOf(0), table.selectedRows.toList(), "the row the user selected should stay selected")
        assertEquals(0, table.selectionModel.leadSelectionIndex, "the selected row should stay the lead")
        assertEquals(0, table.selectionModel.anchorSelectionIndex, "the selected row should stay the anchor")
    }

    @Test
    fun aSelectionTheUserMadeStandsWhenARowIsInsertedAboveIt() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 45))
        setContent {
            Table(rows = rows) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.setRowSelectionInterval(2, 2)
        awaitIdle()

        rows.add(0, Person("Edsger", 51))
        awaitIdle()

        assertEquals(4, table.model.rowCount, "the pass this asserts about has to have run")
        assertEquals(
            listOf(3),
            table.selectedRows.toList(),
            "the row the user selected should shift down and stay selected",
        )
    }

    @Test
    fun aSelectionTheUserMadeStandsWhenARowAboveItIsRemoved() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 45))
        var lossReported = false
        setContent {
            Table(
                rows = rows,
                onSelectionChange = { if (it.isEmpty()) lossReported = true },
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.setRowSelectionInterval(2, 2)
        awaitIdle()
        lossReported = false

        rows.removeAt(0)
        awaitIdle()

        assertEquals(2, table.model.rowCount, "the pass this asserts about has to have run")
        assertEquals(
            listOf(1),
            table.selectedRows.toList(),
            "the row the user selected should shift up and stay selected",
        )
        assertFalse(lossReported, "a row the table kept must not be reported as lost")
    }

    /**
     * A declared run of rows leaves the table where the user's own drag over them would: the anchor at the
     * start of the run and the lead at its end. A shift-click extends from the anchor, so where it sits is
     * what the user gets next.
     */
    @Test
    fun aDeclaredRunOfRowsLeavesTheAnchorAtItsStart() = runComposeSwingTest {
        val rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 45), Person("Edsger", 51))
        setContent {
            Table(rows = rows, selectedRowIndices = setOf(1, 2, 3)) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(1, 2, 3), table.selectedRows.toList(), "the declared rows should be selected")
        assertEquals(3, table.selectionModel.leadSelectionIndex, "the end of the run should be the lead")
        assertEquals(1, table.selectionModel.anchorSelectionIndex, "the start of the run should be the anchor")
    }

    @Test
    fun controlledSelectionUpdateConvergesWithoutALoop() = runComposeSwingTest {
        // The controller mirrors the callback back into the controlled state, exactly as a real
        // caller would. The selection guard must make this converge: applying an external update
        // settles on that selection without oscillating between values frame after frame.
        var selection by mutableStateOf(setOf(0))
        val received = mutableListOf<Set<Int>>()
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
        selection = setOf(2)
        awaitIdle()

        assertEquals(listOf(2), table.selectedRows.toList(), "external selection applied")
        assertEquals(setOf(2), selection, "controlled state settled on the new selection")
        assertTrue(
            received.all { it == setOf(2) },
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

    @Test
    fun aSelectionTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedMoveIsPutBack(
            type = JTable::class.java,
            declared = emptyList<Int>(),
            content = {
                Table(
                    rows = listOf("Ada", "Alan"),
                    selectedRowIndices = emptySet(),
                    onSelectionChange = {},
                ) {
                    column("Name") { it }
                }
            },
            move = { it.setRowSelectionInterval(1, 1) },
            read = { it.selectedRows.toList() },
        )
    }
}

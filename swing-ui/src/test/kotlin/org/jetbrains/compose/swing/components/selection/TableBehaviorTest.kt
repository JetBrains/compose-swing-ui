package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsNeverPainted
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    fun anUndeclaredTableIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { Table(model = DefaultTableModel()) }
        onNodeOfType<JTable>().assertTreeMatches(JTable(DefaultTableModel()))
    }

    @Test
    fun rowsAndColumnsRenderIntoTheModel() = runComposeSwingTest {
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41)),
                columns = {
                    column("Name") { it.name }
                    column("Age") { it.age }
                },
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(2, "row count") { model.rowCount }
        table.assertProperty(2, "column count") { model.columnCount }
        table.assertProperty("Name", "column 0 header") { model.getColumnName(0) }
        table.assertProperty("Age", "column 1 header") { model.getColumnName(1) }
        table.assertProperty("Ada", "cell (0,0) value") { model.getValueAt(0, 0) }
        table.assertProperty(36, "cell (0,1) value") { model.getValueAt(0, 1) }
        table.assertProperty("Alan", "cell (1,0) value") { model.getValueAt(1, 0) }
        table.assertProperty(41, "cell (1,1) value") { model.getValueAt(1, 1) }
    }

    @Test
    fun aColumnsNewValueReachesTheModelWhileItsRowsAndHeaderStay() = runComposeSwingTest {
        var unit by mutableStateOf("years")
        setContent {
            // Read here, so each pass declares a column whose extractor holds a plain value.
            val declaredUnit = unit
            Table(rows = listOf(Person("Ada", 36)), columns = { column("Age") { "${it.age} $declaredUnit" } })
        }

        val model = onNodeOfType<JTable>().fetch().model
        assertEquals("36 years", model.getValueAt(0, 0), "the declared extractor fills the cell")

        unit = "yrs"
        awaitIdle()

        assertEquals("36 yrs", model.getValueAt(0, 0), "a new extractor under the same header fills the model")
    }

    @Test
    fun aColumnsNewValueRepaintsTheCellsItFills() = runComposeSwingTest {
        var unit by mutableStateOf("years")
        setContent {
            val declaredUnit = unit
            Table(rows = listOf(Person("Ada", 36)), columns = { column("Age") { "${it.age} $declaredUnit" } })
        }
        val table = onNodeOfType<JTable>().fetch()
        withRecordedRepaints { recorded ->
            unit = "yrs"
            awaitIdle()

            // A table paints each cell from the model when it is repainted, so a cell the table is not asked
            // to repaint keeps showing the value it last painted.
            val firstRow = table.getCellRect(0, 0, true).union(table.getCellRect(0, table.columnCount - 1, true))
            val tableRepaints = recorded.dirtyRegionsOf(table)
            assertTrue(tableRepaints.any { it.contains(firstRow) }, "the row the new value fills: $tableRepaints")
        }
    }

    @Test
    fun aPassThatKeepsTheColumnsLeavesTheTablesColumnsAlone() = runComposeSwingTest {
        var rowHeight by mutableStateOf(20)
        setContent {
            Table(rows = listOf(Person("Ada", 36)), columns = { column("Age") { it.age } }, rowHeight = rowHeight)
        }
        val table = onNodeOfType<JTable>()
        table
            .fetch()
            .columnModel
            .getColumn(0)
            .minWidth = 50

        rowHeight = 30
        awaitIdle()

        table.assertProperty(30, "the pass reached the table") { this.rowHeight }
        table.assertProperty(
            50,
            "unchanged columns are not declared again",
        ) { columnModel.getColumn(0).minWidth }
    }

    @Test
    fun selectingRowsFiresOnSelectionChange() = runComposeSwingTest {
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                columns = { column("Name") { it.name } },
                onSelectionChange = { received += it },
                selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
            )
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
     * The table is settled back onto the declared selection by the time the change's own recomposition
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
                columns = { column("Name") { it.name } },
                selectedRowIndices = setOf(0),
            )
        }

        val table = onNodeOfType<JTable>()
        repeat(2) { attempt ->
            table.fetch().setRowSelectionInterval(1, 1)
            awaitIdle()
            table.assertProperty(
                listOf(0),
                "attempt ${attempt + 1}: an unadopted selection change does not stand, however often it is made",
            ) { selectedRows.toList() }
        }
    }

    @Test
    fun editingAnEditableCellFiresOnCellEdit() = runComposeSwingTest {
        val edits = mutableListOf<Triple<String, Int, Any?>>()
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41)),
                columns = {
                    column("Name") { it.name }
                    column(
                        header = "Age",
                        onCellEdit = { row, rowIndex, newValue -> edits += Triple(row.name, rowIndex, newValue) },
                    ) { it.age }
                },
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(false, "Name column must be read-only") { isCellEditable(0, 0) }
        table.assertProperty(true, "Age column must be editable") { isCellEditable(0, 1) }
        // Committing an edit routes through JTable.setValueAt -> model.setValueAt, the same
        // path the cell editor takes on commit.
        table.fetch().setValueAt(37, 0, 1)

        assertEquals(1, edits.size, "exactly one edit committed")
        assertEquals(Triple("Ada", 0, 37), edits.single(), "edited row, index, and new value")
    }

    @Test
    fun eachColumnAnswersWithTheClassOfTheValuesItHolds() = runComposeSwingTest {
        setContent {
            Table(
                rows = listOf(Person("Ada", 36)),
                columns = {
                    column("Name") { it.name }
                    column("Age") { it.age }
                    column("Senior") { it.age > 40 }
                },
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(String::class.java, "a column of names holds strings") { model.getColumnClass(0) }
        table.assertProperty(Int::class.javaObjectType, "a column of ages holds integers") { model.getColumnClass(1) }
        table.assertProperty(
            Boolean::class.javaObjectType,
            "a column of flags holds booleans",
        ) { model.getColumnClass(2) }
    }

    @Test
    fun aBooleanColumnRendersAsACheckBox() = runComposeSwingTest {
        setContent {
            Table(
                rows = listOf(Person("Ada", 36)),
                columns = {
                    column("Name") { it.name }
                    column("Senior") { it.age > 40 }
                },
            )
        }

        // A table picks a cell's renderer by the column's class, so the class the column declares is
        // what decides whether a flag is drawn as a checkbox or written out as text. The wrapper
        // installs no renderer of its own.
        val table = onNodeOfType<JTable>().fetch()
        assertSame(
            table.getDefaultRenderer(Boolean::class.javaObjectType),
            table.getCellRenderer(0, 1),
            "a boolean column should draw through the renderer the table keeps for Boolean",
        )
        assertSame(
            table.getDefaultRenderer(String::class.java),
            table.getCellRenderer(0, 0),
            "a string column should draw through the renderer the table keeps for String",
        )
    }

    @Test
    fun anIntColumnCommitsItsEditAsAnInt() = runComposeSwingTest {
        val edits = mutableListOf<Any?>()
        setContent {
            Table(
                rows = listOf(Person("Ada", 36)),
                columns = {
                    column("Age", onCellEdit = { _, _, newValue -> edits += newValue }) { it.age }
                },
            )
        }

        // The column's class picks the editor as well as the renderer. Driving the editor, not the
        // model, puts under test how it turns typed text into a value of the
        // column's class; committing it is the model write JTable makes once editing stops.
        val table = onNodeOfType<JTable>().fetch()
        val editor = table.getCellEditor(0, 0)
        val typedInto = assertIs<JTextField>(editor.getTableCellEditorComponent(table, 36, false, 0, 0))
        typedInto.text = "37"
        editor.stopCellEditing()
        table.setValueAt(editor.cellEditorValue, 0, 0)

        assertEquals(listOf<Any?>(37), edits, "an int column should commit the edited value as an int")
    }

    @Test
    fun stateDrivenRowsUpdateTheTable() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36))
        setContent {
            Table(rows = rows.toList(), columns = { column("Name") { it.name } })
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(1, "the model should start with one row") { model.rowCount }

        rows.add(Person("Alan", 41))
        awaitIdle()

        table.assertProperty(2, "row added to the model") { model.rowCount }
        table.assertProperty("Alan", "the added row's cell should render") { model.getValueAt(1, 0) }

        rows.clear()
        rows.add(Person("Grace", 50))
        awaitIdle()

        table.assertProperty(1, "model reflects replaced rows") { model.rowCount }
        table.assertProperty("Grace", "the replaced row's cell should render") { model.getValueAt(0, 0) }
    }

    @Test
    fun aSelectionTheUserMadeStandsAcrossARowChange() = runComposeSwingTest {
        // The selection the caller declared nothing about is the user's, and a pass that gives the table
        // new rows keeps it: a change naming only the added rows leaves it where it is, and one spanning
        // the table empties it and has it put back. The row it names is the one it named before, so the
        // lead and the anchor stand as well.
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } })
        }

        val table = onNodeOfType<JTable>()
        table.fetch().setRowSelectionInterval(0, 0)
        awaitIdle()

        rows.add(Person("Grace", 45))
        awaitIdle()

        table.assertProperty(3, "the pass this asserts about has to have run") { model.rowCount }
        table.assertProperty(
            listOf(0),
            "the row the user selected should stay selected",
        ) { selectedRows.toList() }
        table.assertProperty(
            0,
            "the selected row should stay the lead",
        ) { selectionModel.leadSelectionIndex }
        table.assertProperty(
            0,
            "the selected row should stay the anchor",
        ) { selectionModel.anchorSelectionIndex }
    }

    @Test
    fun aSelectionTheUserMadeStandsWhenARowIsInsertedAboveIt() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 45))
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } })
        }

        val table = onNodeOfType<JTable>()
        table.fetch().setRowSelectionInterval(2, 2)
        awaitIdle()

        rows.add(0, Person("Edsger", 51))
        awaitIdle()

        table.assertProperty(4, "the pass this asserts about has to have run") { model.rowCount }
        table.assertProperty(
            listOf(3),
            "the row the user selected should shift down and stay selected",
        ) { selectedRows.toList() }
    }

    @Test
    fun aSelectionTheUserMadeStandsWhenARowAboveItIsRemoved() = runComposeSwingTest {
        val rows = mutableStateListOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 45))
        var lossReported = false
        setContent {
            Table(
                rows = rows,
                columns = { column("Name") { it.name } },
                onSelectionChange = { if (it.isEmpty()) lossReported = true },
            )
        }

        val table = onNodeOfType<JTable>()
        table.fetch().setRowSelectionInterval(2, 2)
        awaitIdle()
        lossReported = false

        rows.removeAt(0)
        awaitIdle()

        table.assertProperty(2, "the pass this asserts about has to have run") { model.rowCount }
        table.assertProperty(
            listOf(1),
            "the row the user selected should shift up and stay selected",
        ) { selectedRows.toList() }
        assertFalse(lossReported, "a row the table kept must not be reported as lost")
    }

    @Test
    fun draggingAcrossRowsDoesNotSettleUntilAdjustingEnds() = runComposeSwingTest {
        val rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 45))
        var selection by mutableStateOf(setOf(0))
        var changes = 0
        setContent {
            Table(
                rows = rows,
                columns = { column("Name") { it.name } },
                selectedRowIndices = selection,
                onSelectionChange = {
                    selection = it
                    changes++
                },
            )
        }
        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.valueIsAdjusting = true
        table.selectionModel.setSelectionInterval(1, 1)
        table.selectionModel.addSelectionInterval(2, 2)
        awaitIdle()

        assertEquals(0, changes, "a selection still under the user's hand is not the one to report")
        table.selectionModel.valueIsAdjusting = false
        awaitIdle()
        assertEquals(1, changes, "the selection the drag settles on is reported once")
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
            Table(rows = rows, columns = { column("Name") { it.name } }, selectedRowIndices = setOf(1, 2, 3))
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(1, 2, 3), "the declared rows should be selected") { selectedRows.toList() }
        table.assertProperty(3, "the end of the run should be the lead") { selectionModel.leadSelectionIndex }
        table.assertProperty(1, "the start of the run should be the anchor") { selectionModel.anchorSelectionIndex }
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
                columns = { column("Name") { it.name } },
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(0), "initial selection applied") { selectedRows.toList() }

        // A purely external selection update applies to the table and settles. Because the guard
        // skips re-applying an unchanged selection, any echoed callback carries the SAME new
        // indices the controller already holds, so it does not bounce back to the old value.
        selection = setOf(2)
        awaitIdle()

        table.assertProperty(listOf(2), "external selection applied") { selectedRows.toList() }
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
            Table(
                rows = listOf(Person("Ada", 36)),
                columns = { column("Name") { it.name } },
                modifier = SwingModifier.name(label),
            )
        }

        val table = onNodeOfType<JTable>()
        // A structure change rebuilds the table's columns, taking any width along with them, so the
        // column object the table still carries is what tells a narrow refresh from a broad one.
        val before = table.fetch()
        val column = before.columnModel.getColumn(0)
        column.preferredWidth = 123

        label = "second"
        awaitIdle()
        assertSame(before, table.fetch(), "the component is updated in place, not rebuilt")

        assertSame(column, table.fetch().columnModel.getColumn(0), "the column survives a pass that changed no data")
        table.assertProperty(
            123,
            "and so does the width set on it",
        ) { columnModel.getColumn(0).preferredWidth }
    }

    @Test
    fun aSelectionTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        assertUnadoptedChangeIsNeverPainted(
            type = JTable::class.java,
            declared = emptyList<Int>(),
            content = { report ->
                Table(
                    rows = listOf("Ada", "Alan"),
                    columns = { column("Name") { it } },
                    selectedRowIndices = emptySet(),
                    onSelectionChange = { report() },
                )
            },
            change = { it.setRowSelectionInterval(1, 1) },
            read = { it.selectedRows.toList() },
        )
    }
}

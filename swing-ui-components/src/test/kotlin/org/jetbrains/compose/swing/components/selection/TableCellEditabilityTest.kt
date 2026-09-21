// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
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
        assertIs<JTextField>(table.editorComponent).text = "37"
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(
            false,
            "the predicate should keep the younger row's cell read-only",
        ) { isCellEditable(0, 1) }
        table.assertProperty(true, "and make the older row's cell editable") { isCellEditable(1, 1) }
        table.assertProperty(false, "a column without onCellEdit stays read-only") { isCellEditable(1, 0) }
    }

    @Test
    fun aPerRowPredicateWithoutOnCellEditIsRefused() = runComposeSwingTest {
        assertFailsWith<IllegalArgumentException>("the refusal names the missing callback") {
            setContent {
                Table(
                    rows = people,
                    columns = { column("Age", isCellEditable = { _, _ -> true }) { it.age } },
                )
            }
            awaitIdle()
        }
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
        asked.clear()
        onNodeOfType<JTable>().assertProperty(true, "the predicate admits the cell") { isCellEditable(0, 0) }
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

        val table = onNodeOfType<JTable>()
        val tableBefore = table.fetch()
        table.assertProperty(false, "the younger row starts read-only") { isCellEditable(0, 0) }

        threshold = 30
        awaitIdle()
        assertSame(tableBefore, table.fetch(), "the component is updated in place, not rebuilt")
        table.assertProperty(true, "a changed predicate should decide the next answer") { isCellEditable(0, 0) }
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

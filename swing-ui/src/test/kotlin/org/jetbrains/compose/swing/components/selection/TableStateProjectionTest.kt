package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.table.TableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * What a [TableState] reports of the table it drives, as opposed to what it declares to it.
 *
 * A declaration is a request. A table has no row for an index its rows do not reach, a selection mode that
 * holds one row keeps one of the rows a wider declaration named, and a filtered-out row has no place on
 * screen to be selected at. These queries read the table, which is why they can disagree with the state
 * that drove it.
 */
class TableStateProjectionTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

    /** Hides the row at [rowIndex] of the model, whatever it holds. */
    private fun hidingRow(rowIndex: Int): RowFilter<TableModel, Int> = object : RowFilter<TableModel, Int>() {
        override fun include(entry: Entry<out TableModel, out Int>): Boolean = entry.identifier != rowIndex
    }

    @Test
    fun theRowCountFollowsWhatTheTableShows() = runComposeSwingTest {
        var filter by mutableStateOf<RowFilter<TableModel, Int>?>(null)
        lateinit var state: TableState
        setContent {
            state = rememberTableState()
            Table(rows = people, state = state, sortable = true, rowFilter = filter) {
                column("Name") { it.name }
            }
        }

        assertEquals(people.size, state.rowCount, "every declared row is a row the table shows")

        filter = hidingRow(1)
        awaitIdle()

        assertEquals(people.size - 1, state.rowCount, "a filtered row is one the table no longer shows")
    }

    @Test
    fun anUnboundStateAnswersForNoTable() {
        val state = TableState(initialSelectedRowIndices = setOf(0, 1))

        assertEquals(0, state.rowCount, "a state with no table has no rows to report")
        assertEquals(emptySet(), state.shownSelectedRowIndices, "nor a selection")
    }

    @Test
    fun aDeclaredRowTheTableDoesNotHoldIsNotReportedSelected() = runComposeSwingTest {
        lateinit var state: TableState
        setContent {
            state = rememberTableState()
            Table(rows = people, state = state) {
                column("Name") { it.name }
            }
        }

        state.selectedRowIndices = setOf(0, people.size)
        awaitIdle()

        assertEquals(setOf(0, people.size), state.selectedRowIndices, "the declaration is what the caller wrote")
        assertEquals(
            setOf(0),
            state.shownSelectedRowIndices,
            "the table selected the one row it has, and reports only that",
        )
    }

    @Test
    fun aSelectionModeNarrowerThanTheDeclarationReportsWhatTheTableKept() = runComposeSwingTest {
        lateinit var state: TableState
        setContent {
            state = rememberTableState()
            Table(rows = people, state = state, selectionMode = ListSelectionModel.SINGLE_SELECTION) {
                column("Name") { it.name }
            }
        }

        state.selectedRowIndices = setOf(0, 1, 2)
        awaitIdle()

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(1, table.selectedRowCount, "a table holding one row at a time kept one of the three")
        assertEquals(
            table.selectedRows.toSet(),
            state.shownSelectedRowIndices,
            "and that is the row the projection reports",
        )
        assertNotEquals(
            state.selectedRowIndices,
            state.shownSelectedRowIndices,
            "which is not the three rows the state goes on declaring",
        )
    }

    @Test
    fun aFilteredRowIsNotReportedSelected() = runComposeSwingTest {
        lateinit var state: TableState
        setContent {
            state = rememberTableState()
            Table(rows = people, state = state, sortable = true, rowFilter = hidingRow(1)) {
                column("Name") { it.name }
            }
        }

        state.selectedRowIndices = setOf(0, 1)
        awaitIdle()

        assertEquals(setOf(0, 1), state.selectedRowIndices, "the declaration names both rows")
        assertEquals(
            setOf(0),
            state.shownSelectedRowIndices,
            "and the hidden row has no place on screen to be selected at",
        )
    }

    @Test
    fun theSelectionTheUserReachesIsReported() = runComposeSwingTest {
        lateinit var state: TableState
        setContent {
            state = rememberTableState()
            Table(rows = people, state = state) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(2, 2)
        awaitIdle()

        assertEquals(setOf(2), state.shownSelectedRowIndices, "what the user selected is what the table holds")
    }
}

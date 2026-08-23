package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A [TableState] owns the selection of the table it drives: what the state holds is what the table shows
 * selected, what the user reaches is written back into the state, and the table is settled onto the state
 * on every pass.
 *
 * A user's click or drag reaches a table as a write to its own selection model, which is what these tests
 * make to stand in for the user.
 */
class TableStateTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

    private fun tableModel(): DefaultTableModel =
        DefaultTableModel(people.map { arrayOf<Any?>(it.name) }.toTypedArray(), arrayOf<Any?>("Name"))

    @Test
    fun theRowsAStateStartsOnAreTheOnesTheTableShows() = runComposeSwingTest {
        lateinit var state: TableState
        setContent {
            state = rememberTableState(initialSelectedRowIndices = setOf(1))
            Table(rows = people, state = state) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(1), table.selectedRows.toList(), "the rows the state starts on reach the table")
        assertEquals(setOf(1), state.selectedRowIndices, "and are what the state goes on holding")
    }

    @Test
    fun assigningTheStateSelectsTheRowsItNames() = runComposeSwingTest {
        val state = TableState()
        setContent {
            Table(rows = people, state = state) {
                column("Name") { it.name }
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(emptyList(), table.selectedRows.toList(), "a state naming no row selects none")

        state.selectedRowIndices = setOf(0, 2)
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(0, 2),
            table.selectedRows.toList(),
            "the one pass that carries the state's new rows should already have selected them",
        )
    }

    @Test
    fun theRowsTheUserSelectsAreWrittenBackIntoTheState() = runComposeSwingTest {
        val state = TableState()
        setContent {
            Table(rows = people, state = state) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.selectionModel.setSelectionInterval(1, 2)
        awaitIdle()

        assertEquals(setOf(1, 2), state.selectedRowIndices, "the state holds what the user selected")
        assertEquals(listOf(1, 2), table.selectedRows.toList(), "and the table is left standing on it")
    }

    @Test
    fun aRowTheRowsStopReachingIsSelectedAgainOnceTheyReachItOnceMore() = runComposeSwingTest {
        var shown by mutableStateOf(people)
        val state = TableState(initialSelectedRowIndices = setOf(2))
        setContent {
            Table(rows = shown, state = state) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        shown = people.take(1)
        awaitIdle()

        assertEquals(emptyList(), table.selectedRows.toList(), "the row the rows no longer reach is not selected")
        assertEquals(setOf(2), state.selectedRowIndices, "and the state goes on naming it")

        shown = people
        awaitIdle()

        assertEquals(listOf(2), table.selectedRows.toList(), "rows that reach it again show it selected")
    }

    @Test
    fun aStateDrivesAModelDrivenTableToo() = runComposeSwingTest {
        val state = TableState(initialSelectedRowIndices = setOf(2))
        setContent {
            Table(model = tableModel(), state = state)
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(2), table.selectedRows.toList(), "the rows the state names reach the model's table")

        table.selectionModel.setSelectionInterval(0, 0)
        awaitIdle()

        assertEquals(setOf(0), state.selectedRowIndices, "and the user's own row is written back")
    }

    @Test
    fun revealingARowScrollsTheTableToIt() = runComposeSwingTest {
        val rows = (0 until ROW_COUNT).map { Person("person $it", it) }
        val state = TableState()
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(160, 80)) {
                Table(rows = rows, state = state, modifier = SwingModifier.viewport()) {
                    column("Name") { it.name }
                }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val viewport = onNodeOfType<JScrollPane>().fetch().viewport
        val cell = table.getCellRect(DISTANT_ROW, 0, true)
        assertFalse(viewport.viewRect.contains(cell), "the row starts out of view")

        assertTrue(state.revealRow(DISTANT_ROW), "the bound table reveals the row")
        assertTrue(viewport.viewRect.contains(cell), "which scrolls the pane to it")
    }

    @Test
    fun aStateRevealsOnlyARowTheTableShows() = runComposeSwingTest {
        val state = TableState()
        setContent {
            Table(rows = people, state = state) {
                column("Name") { it.name }
            }
        }

        assertTrue(state.revealRow(people.lastIndex), "the last row of the bound table is there to reveal")
        assertFalse(state.revealRow(people.size), "a row the rows do not reach is not")
        assertFalse(state.revealRow(-1), "and neither is a row before the first")
    }

    @Test
    fun aFilteredRowIsNotRevealed() = runComposeSwingTest {
        val hidingTheLast =
            object : RowFilter<TableModel, Int>() {
                override fun include(entry: Entry<out TableModel, out Int>): Boolean =
                    entry.identifier != people.lastIndex
            }
        val state = TableState()
        setContent {
            Table(rows = people, state = state, sortable = true, rowFilter = hidingTheLast) {
                column("Name") { it.name }
            }
        }

        assertTrue(state.revealRow(0), "a row the filter admits is there to reveal")
        assertFalse(state.revealRow(people.lastIndex), "a row the filter hides has nowhere to be shown")
    }

    @Test
    fun anUnboundStateRevealsNothing() {
        assertFalse(TableState().revealRow(0), "a state driving no table reveals no row")
    }

    @Test
    fun aStateGivesUpATableThatLeavesTheComposition() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val state = TableState()
        setContent {
            if (shown) {
                Table(rows = people, state = state) {
                    column("Name") { it.name }
                }
            }
        }
        assertTrue(state.revealRow(0), "the bound table reveals a row")

        shown = false
        awaitIdle()

        assertFalse(state.revealRow(0), "a table that left the composition is driven no longer")
        assertEquals(0, state.rowCount, "and answers for no rows")
    }

    @Test
    fun aSecondTableTakesTheStateAndLeavesTheFirstUnbound() = runComposeSwingTest {
        var second by mutableStateOf(false)
        val state = TableState()
        setContent {
            Table(rows = people, state = state) {
                column("Name") { it.name }
            }
            if (second) {
                Table(rows = people.take(1), state = state) {
                    column("Name") { it.name }
                }
            }
        }
        assertEquals(people.size, state.rowCount, "the first table is the one driven")

        second = true
        awaitIdle()

        assertEquals(1, state.rowCount, "the second table has taken the state over")
    }

    @Test
    fun aContiguousSelectionIsWrittenAsOneInterval() = runComposeSwingTest {
        val rows = (0 until ROW_COUNT).map { Person("person $it", it) }
        val state = TableState()
        setContent {
            Table(rows = rows, state = state) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        var eventCount = 0
        table.selectionModel.addListSelectionListener { eventCount++ }

        val contiguousRun = 10..49
        state.selectedRowIndices = contiguousRun.toSet()
        awaitIdle()

        assertEquals(contiguousRun.toList(), table.selectedRows.toList(), "every row of the run is selected")
        assertEquals(
            contiguousRun.last,
            table.selectionModel.leadSelectionIndex,
            "the lead stays the highest selected row",
        )
        assertEquals(
            2,
            eventCount,
            "a run of ${contiguousRun.count()} adjacent rows is written as the one interval that spans " +
                "them, which the model publishes once as it adjusts and once settled",
        )
    }

    private companion object {
        const val ROW_COUNT = 200

        /** The row far enough down that no pane sized here can be showing it to begin with. */
        const val DISTANT_ROW = 150
    }
}

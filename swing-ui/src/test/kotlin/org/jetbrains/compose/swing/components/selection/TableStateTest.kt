// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.event.InputEvent
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A [TableState] owns the selection, the sort order and the column layout of the table it drives: what the
 * state holds is what the table shows, and what the user reaches is written back into the state. The
 * selection is settled onto the table on every pass; the order and the layout are applied when assigned.
 *
 * A row click reaches the table as a mouse gesture on the table the scroll pane lays out. A header click, a
 * column drag and a column resize are mouse gestures on the header the scroll pane shows.
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
            Table(rows = people, columns = { column("Name") { it.name } }, state = state)
        }

        onNodeOfType<JTable>().assertProperty(
            listOf(1),
            "the rows the state starts on reach the table",
        ) { selectedRows.toList() }
        assertEquals(setOf(1), state.selectedRowIndices, "and are what the state goes on holding")
    }

    @Test
    fun assigningTheStateSelectsTheRowsItNames() = runComposeSwingTest {
        val state = TableState()
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, state = state)
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val table = onNodeOfType<JTable>()
        val held = table.fetch()
        table.assertProperty(emptyList(), "a state naming no row selects none") { selectedRows.toList() }

        state.selectedRowIndices = setOf(0, 2)
        mainClock.advanceTimeByFrame()

        assertSame(held, table.fetch())
        table.assertProperty(
            listOf(0, 2),
            "the one pass that carries the state's new rows should already have selected them",
        ) { selectedRows.toList() }
    }

    @Test
    fun theRowsTheUserSelectsAreWrittenBackIntoTheState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = TableState()
        setContent {
            HeaderPane { Table(rows = people, columns = { column("Name") { it.name } }, state = state) }
        }

        val table = onNodeOfType<JTable>()
        table.performClick(table.fetch().rowCenter(1))
        table.performClick(table.fetch().rowCenter(2), modifiers = InputEvent.SHIFT_DOWN_MASK)

        assertEquals(setOf(1, 2), state.selectedRowIndices, "the state holds what the user selected")
        table.assertProperty(
            listOf(1, 2),
            "and the table is left standing on it",
        ) { selectedRows.toList() }
    }

    @Test
    fun aRowTheRowsStopReachingIsSelectedAgainOnceTheyReachItOnceMore() = runComposeSwingTest {
        var shown by mutableStateOf(people)
        val state = TableState(initialSelectedRowIndices = setOf(2))
        setContent {
            Table(rows = shown, columns = { column("Name") { it.name } }, state = state)
        }

        val table = onNodeOfType<JTable>()
        shown = people.take(1)
        awaitIdle()

        table.assertProperty(emptyList(), "the row the rows no longer reach is not selected") { selectedRows.toList() }
        assertEquals(setOf(2), state.selectedRowIndices, "and the state goes on naming it")

        shown = people
        awaitIdle()

        table.assertProperty(listOf(2), "rows that reach it again show it selected") { selectedRows.toList() }
    }

    @Test
    fun aStateDrivesAModelDrivenTableToo() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = TableState(initialSelectedRowIndices = setOf(2))
        setContent {
            HeaderPane { Table(model = tableModel(), state = state) }
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(
            listOf(2),
            "the rows the state names reach the model's table",
        ) { selectedRows.toList() }

        table.performClick(table.fetch().rowCenter(0))

        assertEquals(setOf(0), state.selectedRowIndices, "and the user's own row is written back")
    }

    @Test
    fun revealingARowScrollsTheTableToIt() = runComposeSwingTest {
        val rows = (0 until ROW_COUNT).map { Person("person $it", it) }
        val state = TableState()
        setContent {
            ScrollPane(style = { preferredSize(160, 80) }) {
                Viewport {
                    Table(rows = rows, columns = { column("Name") { it.name } }, state = state)
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
            Table(rows = people, columns = { column("Name") { it.name } }, state = state)
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
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                state = state,
                sortable = true,
                rowFilter = hidingTheLast,
            )
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
                Table(rows = people, columns = { column("Name") { it.name } }, state = state)
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
            Table(rows = people, columns = { column("Name") { it.name } }, state = state)
            if (second) {
                Table(rows = people.take(1), columns = { column("Name") { it.name } }, state = state)
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
            Table(rows = rows, columns = { column("Name") { it.name } }, state = state)
        }

        val table = onNodeOfType<JTable>()
        var eventCount = 0
        table.fetch().selectionModel.addListSelectionListener { eventCount++ }

        val contiguousRun = 10..49
        state.selectedRowIndices = contiguousRun.toSet()
        awaitIdle()

        table.assertProperty(
            contiguousRun.toList(),
            "every row of the run is selected",
        ) { selectedRows.toList() }
        table.assertProperty(
            contiguousRun.last,
            "the lead stays the highest selected row",
        ) { selectionModel.leadSelectionIndex }
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

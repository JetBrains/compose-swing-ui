package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performMouseDrag
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.event.InputEvent
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.DefaultTableModel
import javax.swing.table.JTableHeader
import javax.swing.table.TableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(1), table.selectedRows.toList(), "the rows the state starts on reach the table")
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
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = TableState()
        setContent {
            HeaderPane { Table(rows = people, columns = { column("Name") { it.name } }, state = state) }
        }

        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTable>().performClick(table.rowCenter(1))
        onNodeOfType<JTable>().performClick(table.rowCenter(2), modifiers = InputEvent.SHIFT_DOWN_MASK)

        assertEquals(setOf(1, 2), state.selectedRowIndices, "the state holds what the user selected")
        assertEquals(listOf(1, 2), table.selectedRows.toList(), "and the table is left standing on it")
    }

    @Test
    fun aRowTheRowsStopReachingIsSelectedAgainOnceTheyReachItOnceMore() = runComposeSwingTest {
        var shown by mutableStateOf(people)
        val state = TableState(initialSelectedRowIndices = setOf(2))
        setContent {
            Table(rows = shown, columns = { column("Name") { it.name } }, state = state)
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
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = TableState(initialSelectedRowIndices = setOf(2))
        setContent {
            HeaderPane { Table(model = tableModel(), state = state) }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(2), table.selectedRows.toList(), "the rows the state names reach the model's table")

        onNodeOfType<JTable>().performClick(table.rowCenter(0))

        assertEquals(setOf(0), state.selectedRowIndices, "and the user's own row is written back")
    }

    @Test
    fun revealingARowScrollsTheTableToIt() = runComposeSwingTest {
        val rows = (0 until ROW_COUNT).map { Person("person $it", it) }
        val state = TableState()
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(160, 80)) {
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

    @Test
    fun theValuesARememberedStateStartsOnApplyOnTheFirstPass() = runComposeSwingTest {
        val layout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        lateinit var state: TableState
        setContent {
            state =
                rememberTableState(
                    initialSelectedRowIndices = setOf(1),
                    initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING)),
                    initialColumnLayout = layout,
                )
            Table(
                rows = people,
                columns = {
                    column("Name") { it.name }
                    column("Age") { it.age }
                },
                state = state,
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(SortKey(0, SortOrder.DESCENDING)), table.rowSorter.sortKeys.toList(), "the initial order")
        assertEquals(listOf(1, 0), table.columnModel.modelIndices(), "the initial column order")
        assertEquals(120, table.columnModel.getColumn(0).preferredWidth, "the initial width of the first column")
        assertEquals(setOf(1), state.shownSelectedRowIndices, "the initial selection")
    }

    @Test
    fun assigningTheStatesSortKeysSortsTheRows() = runComposeSwingTest {
        val state = TableState()
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, state = state, sortable = true)
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Ada", "Alan", "Grace"), table.shownNames(), "an empty order leaves the rows unsorted")

        state.sortKeys = listOf(SortKey(0, SortOrder.DESCENDING))
        awaitIdle()

        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "the order the state names sorts the rows")
    }

    @Test
    fun aHeaderClickWritesTheNewOrderIntoTheState() = runComposeSwingTest {
        val state = TableState()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                    sortable = true,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTableHeader>().performClick(table.headerCenterOf(1))

        val clicked = listOf(SortKey(1, SortOrder.ASCENDING))
        assertEquals(clicked, state.sortKeys, "the state holds the order the click left the rows in")
        assertEquals(clicked, table.rowSorter.sortKeys.toList(), "and the table is left standing on it")
    }

    @Test
    fun sortKeysApplyOnlyWhileTheTableIsSortable() = runComposeSwingTest {
        val keys = listOf(SortKey(0, SortOrder.DESCENDING))
        val state = TableState(initialSortKeys = keys)
        var sortable by mutableStateOf(false)
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, state = state, sortable = sortable)
        }

        val table = onNodeOfType<JTable>().fetch()
        assertNull(table.rowSorter, "a table that is not sortable installs no row sorter for the state's keys")
        assertEquals(listOf("Ada", "Alan", "Grace"), table.shownNames(), "and shows the rows as they come")

        sortable = true
        awaitIdle()

        assertEquals(keys, table.rowSorter.sortKeys.toList(), "a table made sortable stands on the state's keys")
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "and shows the rows in that order")
    }

    @Test
    fun assigningTheStatesColumnLayoutReordersAndResizesTheColumns() = runComposeSwingTest {
        val state = TableState()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(
            listOf(0, 1),
            table.columnModel.modelIndices(),
            "a state without a layout leaves the columns as declared",
        )

        awaitIdle()
        assertNull(state.columnLayout, "and writes none of its own while the user moves no column")

        state.columnLayout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        awaitIdle()

        assertEquals(listOf(1, 0), table.columnModel.modelIndices(), "the layout the state names reorders the columns")
        assertEquals(120, table.columnModel.getColumn(0).preferredWidth, "and gives the first its width")
        assertEquals(40, table.columnModel.getColumn(1).preferredWidth, "and the second its width")
    }

    @Test
    fun aStateWithoutAColumnLayoutLeavesTheColumnsTheUserSetAlone() = runComposeSwingTest {
        val state = TableState()
        var rows by mutableStateOf(people)
        setContent {
            HeaderPane {
                Table(
                    rows = rows,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                    autoResizeMode = JTable.AUTO_RESIZE_OFF,
                )
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        dragColumn(from = 0, to = 1)
        val firstWidth = table.columnModel.getColumn(0).width + 40
        val secondWidth = table.columnModel.getColumn(1).width - 20
        dragColumnDivider(position = 0, dx = 40)
        dragColumnDivider(position = 1, dx = -20)
        state.columnLayout = null
        awaitIdle()

        rows = people.drop(1)
        awaitIdle()

        assertEquals(listOf(1, 0), table.columnModel.modelIndices(), "the order the user set should stay")
        assertEquals(firstWidth, table.columnModel.getColumn(0).preferredWidth, "and the first width")
        assertEquals(secondWidth, table.columnModel.getColumn(1).preferredWidth, "and the second width")
        assertNull(state.columnLayout, "and the state should stay without a layout of its own")
    }

    @Test
    fun clearingTheStatesColumnLayoutLeavesTheColumnsAsTheTableLaidThemOut() = runComposeSwingTest {
        val state = TableState()
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name") { it.name }
                    column("Age") { it.age }
                },
                state = state,
            )
        }
        val table = onNodeOfType<JTable>().fetch()
        state.columnLayout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        awaitIdle()

        state.columnLayout = null
        awaitIdle()

        assertEquals(
            listOf(1, 0),
            table.columnModel.modelIndices(),
            "a state declaring no layout leaves the order alone",
        )
        assertEquals(120, table.columnModel.getColumn(0).preferredWidth, "and the first width")
        assertEquals(40, table.columnModel.getColumn(1).preferredWidth, "and the second width")
    }

    @Test
    fun aColumnDragWritesTheNewLayoutIntoTheState() = runComposeSwingTest {
        val state = TableState()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTableHeader>().performMouseDrag(table.headerCenterOf(1), table.headerCenterOf(0))

        assertEquals(listOf(1, 0), state.columnLayout?.modelIndices, "the state holds the order the drag left")
        assertEquals(listOf(1, 0), table.columnModel.modelIndices(), "and the table is left standing on it")
    }

    @Test
    fun aColumnResizeWritesTheNewLayoutIntoTheState() = runComposeSwingTest {
        val state = TableState()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                    autoResizeMode = JTable.AUTO_RESIZE_OFF,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val resized = table.columnModel.getColumn(0).width + 40
        onNodeOfType<JTableHeader>().performMouseDrag(table.dividerAfter(0), table.dividerAfter(0).movedBy(40))

        assertEquals(resized, state.columnLayout?.preferredWidths?.get(0), "the state holds the width the resize left")
        assertEquals(resized, table.columnModel.getColumn(0).preferredWidth, "and the table is left standing on it")
    }

    @Test
    fun aStateSortsAndLaysOutAModelDrivenTableToo() = runComposeSwingTest {
        val model =
            DefaultTableModel(
                people.map { arrayOf<Any?>(it.name, it.age) }.toTypedArray(),
                arrayOf<Any?>("Name", "Age"),
            )
        val state =
            TableState(
                initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING)),
                initialColumnLayout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(60, 90)),
            )
        setContent {
            HeaderPane { Table(model = model, state = state, sortable = true) }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(
            listOf(1, 0),
            table.columnModel.modelIndices(),
            "the layout the state names reaches the model's table",
        )
        assertEquals(
            listOf("Grace", "Alan", "Ada"),
            (0 until table.rowCount).map { table.getValueAt(it, 1) },
            "the order the state names sorts the model's rows",
        )

        onNodeOfType<JTableHeader>().performClick(table.headerCenterOf(0))

        assertEquals(
            listOf(SortKey(1, SortOrder.ASCENDING), SortKey(0, SortOrder.DESCENDING)),
            state.sortKeys,
            "a header click is written back, the clicked column first and the earlier order after it",
        )
    }

    /**
     * A caller that puts the order back in the same frame as the header click that changed it assigns the
     * value the table was last given, which a check against that value alone would take for no change.
     */
    @Test
    fun anOrderAssignedBackInTheFrameOfAHeaderClickIsApplied() = runComposeSwingTest {
        val descending = listOf(SortKey(0, SortOrder.DESCENDING))
        val state = TableState(initialSortKeys = descending)
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name") { it.name }
                    column("Age") { it.age }
                },
                state = state,
                sortable = true,
            )
        }
        val table = onNodeOfType<JTable>().fetch()

        // What a header click does, with the caller's answer in the same event: no pass runs in between.
        table.rowSorter.toggleSortOrder(1)
        assertEquals(listOf(SortKey(1, SortOrder.ASCENDING)), state.sortKeys.take(1), "the click is written back")
        state.sortKeys = descending
        awaitIdle()

        assertEquals(descending, table.rowSorter.sortKeys.toList(), "the order assigned back should be applied")
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "and sort the rows")
    }

    /**
     * A caller that puts the layout back in the same frame as the user's reorder assigns the value the table
     * was last given, which a check against that value alone would take for no change.
     */
    @Test
    fun aLayoutAssignedBackInTheFrameOfAColumnDragIsApplied() = runComposeSwingTest {
        val layout = TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(80, 90))
        val state = TableState(initialColumnLayout = layout)
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name") { it.name }
                    column("Age") { it.age }
                },
                state = state,
                autoResizeMode = JTable.AUTO_RESIZE_OFF,
            )
        }
        val table = onNodeOfType<JTable>().fetch()

        // What a header drag does, with the caller's answer in the same event: no pass runs in between.
        table.columnModel.moveColumn(1, 0)
        assertEquals(listOf(1, 0), state.columnLayout?.modelIndices, "the drag is written back")
        state.columnLayout = layout
        awaitIdle()

        assertEquals(listOf(0, 1), table.columnModel.modelIndices(), "the layout assigned back should be applied")
        assertEquals(listOf(80, 90), table.columnModel.preferredWidths(), "with its widths")
    }

    /**
     * The layout a caller assigns back stands between two drags to the same layout, so the second drag is a
     * change of its own and is written back like the first.
     */
    @Test
    fun aDragRepeatedAfterTheCallerAssignedTheLayoutBackIsWrittenBackAgain() = runComposeSwingTest {
        val layout = TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(80, 90))
        val state = TableState(initialColumnLayout = layout)
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                    autoResizeMode = JTable.AUTO_RESIZE_OFF,
                )
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTableHeader>().performMouseDrag(table.headerCenterOf(1), table.headerCenterOf(0))
        val dragged = state.columnLayout
        assertEquals(listOf(1, 0), dragged?.modelIndices, "the first drag is written back")

        state.columnLayout = layout
        awaitIdle()
        assertEquals(listOf(0, 1), table.columnModel.modelIndices(), "the layout assigned back is applied")

        onNodeOfType<JTableHeader>().performMouseDrag(table.headerCenterOf(1), table.headerCenterOf(0))

        assertEquals(dragged, state.columnLayout, "the same drag made again should be written back again")
        assertEquals(listOf(1, 0), table.columnModel.modelIndices(), "and the table should stand on it")
    }

    @Test
    fun theOrderAndTheLayoutOutliveTheTableComponent() = runComposeSwingTest {
        val state = TableState()
        var generation by mutableStateOf(0)
        setContent {
            HeaderPane {
                key(generation) {
                    Table(
                        rows = people,
                        columns = {
                            column("Name") { it.name }
                            column("Age") { it.age }
                        },
                        state = state,
                        sortable = true,
                        autoResizeMode = JTable.AUTO_RESIZE_OFF,
                    )
                }
            }
        }
        val first = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTableHeader>().performClick(first.headerCenterOf(1))
        onNodeOfType<JTableHeader>().performMouseDrag(first.headerCenterOf(1), first.headerCenterOf(0))
        val order = state.sortKeys
        val layout = state.columnLayout
        assertEquals(listOf(1, 0), layout?.modelIndices, "the drag is written back")

        generation++
        awaitIdle()

        val second = onNodeOfType<JTable>().fetch()
        assertTrue(second !== first, "a new key builds a new table")
        assertEquals(order, second.rowSorter.sortKeys.toList(), "the new table starts on the state's order")
        assertEquals(layout?.modelIndices, second.columnModel.modelIndices(), "and on its column order")
        assertEquals(layout?.preferredWidths, second.columnModel.preferredWidths(), "and on its widths")
        assertEquals(order, state.sortKeys, "and the state is left holding the order")
        assertEquals(layout, state.columnLayout, "and the layout")
    }

    private companion object {
        const val ROW_COUNT = 200

        /** The row far enough down that no pane sized here can be showing it to begin with. */
        const val DISTANT_ROW = 150
    }
}

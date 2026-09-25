package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.deliverEvent
import org.jetbrains.compose.swing.mouseEvent
import org.jetbrains.compose.swing.paintsOf
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableColumn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The sort order and the column layout a [TableState] holds, across what changes the table's columns: a
 * column the table drops and brings back, a key or a layout naming a column it lacks, a column model the
 * caller swaps in, and a structure change the caller makes to their own model. Also that the user's own
 * header click or column drag is painted as they made it, with no pass after it showing another arrangement.
 */
class TableStateArrangementTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

    @Test
    fun aColumnTheTableDropsKeepsItsPlaceInTheState() = runComposeSwingTest {
        var withAge by mutableStateOf(true)
        val layout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        val state = TableState(initialColumnLayout = layout)
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        if (withAge) column("Age") { it.age }
                    },
                    state = state,
                )
            }
        }
        val table = onNodeOfType<JTable>().fetch()

        withAge = false
        awaitIdle()

        assertEquals(listOf(0), table.columnModel.modelIndices(), "only the surviving column should be left")
        assertEquals(listOf(40), table.columnModel.preferredWidths(), "at the width the state gives it")
        assertEquals(layout, state.columnLayout, "the state should go on naming the column the table dropped")

        withAge = true
        awaitIdle()

        assertEquals(listOf(1, 0), table.columnModel.modelIndices(), "the column should come back in its place")
        assertEquals(listOf(120, 40), table.columnModel.preferredWidths(), "at its width")
    }

    @Test
    fun aSortedColumnTheTableDropsKeepsItsPlaceInTheStatesOrder() = runComposeSwingTest {
        var withAge by mutableStateOf(true)
        val byAge = listOf(SortKey(1, SortOrder.DESCENDING))
        val state = TableState(initialSortKeys = byAge)
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name") { it.name }
                    if (withAge) column("Age") { it.age }
                },
                state = state,
                sortable = true,
            )
        }
        val table = onNodeOfType<JTable>().fetch()

        withAge = false
        awaitIdle()

        assertEquals(emptyList(), table.rowSorter.sortKeys.toList(), "the table has no column left to sort by")
        assertEquals(byAge, state.sortKeys, "the state should go on naming the column the table dropped")

        withAge = true
        awaitIdle()

        assertEquals(byAge, table.rowSorter.sortKeys.toList(), "the column should sort the rows again")
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "in the state's order")
    }

    @Test
    fun aSortKeyForAColumnTheTableLacksStaysInTheState() = runComposeSwingTest {
        val state = TableState()
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, state = state, sortable = true)
        }
        val table = onNodeOfType<JTable>().fetch()

        val keys = listOf(SortKey(5, SortOrder.ASCENDING), SortKey(0, SortOrder.DESCENDING))
        state.sortKeys = keys
        awaitIdle()

        assertEquals(
            listOf(SortKey(0, SortOrder.DESCENDING)),
            table.rowSorter.sortKeys.toList(),
            "the table sorts by the columns it has",
        )
        assertEquals(keys, state.sortKeys, "and the state should go on naming the one it lacks")
    }

    @Test
    fun aLayoutNamingAColumnTheTableLacksStaysInTheState() = runComposeSwingTest {
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

        val layout = TableColumnLayout(modelIndices = listOf(2, 1, 0), preferredWidths = listOf(60, 80, 90))
        state.columnLayout = layout
        awaitIdle()

        assertEquals(listOf(1, 0), table.columnModel.modelIndices(), "the table lays out the columns it has")
        assertEquals(listOf(80, 90), table.columnModel.preferredWidths(), "at the widths the layout names")
        assertEquals(layout, state.columnLayout, "and the state should go on naming the one it lacks")
    }

    /**
     * A column dragged to another place is written into the state from inside the event that made it, so no
     * pass after it applies the order the state held before, and the user only ever sees the order they
     * dragged to.
     */
    @Test
    fun aColumnDragWrittenIntoTheStateIsPaintedAsTheUserMadeIt() = runSwingTest {
        val state =
            TableState(
                initialColumnLayout = TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(75, 75)),
            )
        var before: List<Int>? = null
        var after: List<Int>? = null
        val shown =
            paintsOf(
                JTable::class.java,
                content = {
                    ScrollPane {
                        Viewport {
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
                },
                read = { table -> table.columnModel.modelIndices() },
                before = { table -> before = table.columnModel.modelIndices() },
                made = { table ->
                    val header = table.tableHeader
                    val from = table.headerCenterOf(0)
                    val to = table.headerCenterOf(1)
                    header.deliverEvent(header.mouseEvent(MouseEvent.MOUSE_PRESSED, from, InputEvent.BUTTON1_DOWN_MASK))
                    yield()
                    header.deliverEvent(
                        header.mouseEvent(
                            MouseEvent.MOUSE_DRAGGED,
                            to,
                            InputEvent.BUTTON1_DOWN_MASK,
                            MouseEvent.NOBUTTON,
                        ),
                    )
                    yield()
                    header.deliverEvent(header.mouseEvent(MouseEvent.MOUSE_RELEASED, to, 0))
                    after = table.columnModel.modelIndices()
                    assertNotEquals(before, after, "the drag must reach the JTable")
                },
            )

        assertTrue(shown.isNotEmpty(), "the drag must provoke a paint of the JTable")
        assertEquals(
            emptyList(),
            shown.filter { it != after },
            "every paint the drag asked for must show the order the user left the columns in; the paints " +
                "showed $shown",
        )
        assertEquals(after, state.columnLayout?.modelIndices, "the state holds the order the drag left")
    }

    /**
     * A caller can replace the table's column model whole. The columns that arrive carry a layout of their
     * own, and no later change of rows, columns or model would otherwise put the state's layout onto them.
     */
    @Test
    fun aStatesLayoutReachesTheColumnsAColumnModelSwapBringsInOnAModelTable() = runComposeSwingTest {
        val layout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(80, 90))
        val state = TableState(initialColumnLayout = layout)
        val model = modelWithColumns("Name", "Age")
        setContent { HeaderPane { Table(model = model, state = state) } }
        val table = onNodeOfType<JTable>().fetch()

        table.columnModel = twoFreshColumns()
        awaitIdle()

        assertEquals(listOf(1, 0), table.columnModel.modelIndices(), "the state's order reaches the new columns")
        assertEquals(listOf(80, 90), table.columnModel.preferredWidths(), "and so do its widths")
        assertEquals(layout, state.columnLayout, "a column model the caller installs is not written back")
    }

    @Test
    fun aStatesLayoutReachesTheColumnsAColumnModelSwapBringsInOnARowsTable() = runComposeSwingTest {
        val layout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(80, 90))
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
                )
            }
        }
        val table = onNodeOfType<JTable>().fetch()

        table.columnModel = twoFreshColumns()
        awaitIdle()

        assertEquals(listOf(1, 0), table.columnModel.modelIndices(), "the state's order reaches the new columns")
        assertEquals(listOf(80, 90), table.columnModel.preferredWidths(), "and so do its widths")
        assertEquals(layout, state.columnLayout, "a column model the caller installs is not written back")
    }

    /**
     * A structure change the caller makes to their own model in place starts the sorter and the columns
     * over, as it does on a bare `JTable`, and the state follows the table there.
     */
    @Test
    fun aStructureChangeTheCallerMakesToTheirModelResetsTheArrangementAndTheStateFollows() = runComposeSwingTest {
        val state =
            TableState(
                initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING)),
                initialColumnLayout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(80, 90)),
            )
        val model = modelWithColumns("Name", "Age")
        setContent { HeaderPane { Table(model = model, state = state, sortable = true) } }
        val table = onNodeOfType<JTable>().fetch()

        model.addColumn("City")
        awaitIdle()

        assertEquals(emptyList(), table.rowSorter.sortKeys.toList(), "the table's rows are unsorted again")
        assertEquals(emptyList(), state.sortKeys, "and the state holds that")
        assertEquals(listOf(0, 1, 2), table.columnModel.modelIndices(), "the columns are in the model's order")
        assertEquals(listOf(0, 1, 2), state.columnLayout?.modelIndices, "and the state holds that order")
        assertEquals(
            table.columnModel.preferredWidths(),
            state.columnLayout?.preferredWidths,
            "and the widths the new columns are at",
        )
    }

    private fun twoFreshColumns(): DefaultTableColumnModel = DefaultTableColumnModel().apply {
        addColumn(TableColumn(0))
        addColumn(TableColumn(1))
    }
}

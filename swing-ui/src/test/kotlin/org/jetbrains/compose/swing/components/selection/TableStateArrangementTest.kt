// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.deliverEvent
import org.jetbrains.compose.swing.mouseEvent
import org.jetbrains.compose.swing.paintsOf
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
        val table = onNodeOfType<JTable>()

        withAge = false
        awaitIdle()

        table.assertProperty(listOf(0), "only the surviving column should be left") { columnModel.modelIndices() }
        table.assertProperty(listOf(40), "at the width the state gives it") { columnModel.preferredWidths() }
        assertEquals(layout, state.columnLayout, "the state should go on naming the column the table dropped")

        withAge = true
        awaitIdle()

        table.assertProperty(listOf(1, 0), "the column should come back in its place") { columnModel.modelIndices() }
        table.assertProperty(listOf(120, 40), "at its width") { columnModel.preferredWidths() }
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
        val table = onNodeOfType<JTable>()

        withAge = false
        awaitIdle()

        table.assertProperty(emptyList<SortKey>(), "the table has no column left to sort by") {
            rowSorter.sortKeys.toList()
        }
        assertEquals(byAge, state.sortKeys, "the state should go on naming the column the table dropped")

        withAge = true
        awaitIdle()

        table.assertProperty(byAge, "the column should sort the rows again") { rowSorter.sortKeys.toList() }
        table.assertProperty(listOf("Grace", "Alan", "Ada"), "in the state's order") { shownNames() }
    }

    @Test
    fun aSortKeyForAColumnTheTableLacksStaysInTheState() = runComposeSwingTest {
        val state = TableState()
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, state = state, sortable = true)
        }

        val keys = listOf(SortKey(5, SortOrder.ASCENDING), SortKey(0, SortOrder.DESCENDING))
        state.sortKeys = keys
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            listOf(SortKey(0, SortOrder.DESCENDING)),
            "the table sorts by the columns it has",
        ) { rowSorter.sortKeys.toList() }
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
        val table = onNodeOfType<JTable>()

        val layout = TableColumnLayout(modelIndices = listOf(2, 1, 0), preferredWidths = listOf(60, 80, 90))
        state.columnLayout = layout
        awaitIdle()

        table.assertProperty(listOf(1, 0), "the table lays out the columns it has") { columnModel.modelIndices() }
        table.assertProperty(listOf(80, 90), "at the widths the layout names") { columnModel.preferredWidths() }
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
    @ParameterizedTest(name = "{0}")
    @MethodSource("tableKinds")
    fun aStatesLayoutReachesTheColumnsAColumnModelSwapBringsIn(table: @Composable (TableState) -> Unit) =
        runComposeSwingTest {
            val layout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(80, 90))
            val state = TableState(initialColumnLayout = layout)
            setContent { HeaderPane { table(state) } }

            onNodeOfType<JTable>().fetch().columnModel = twoFreshColumns()
            awaitIdle()

            onNodeOfType<JTable>().assertProperty(
                listOf(1, 0),
                "the state's order reaches the new columns",
            ) { columnModel.modelIndices() }
            onNodeOfType<JTable>().assertProperty(
                listOf(80, 90),
                "and so do its widths",
            ) { columnModel.preferredWidths() }
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

        onNodeOfType<JTable>().assertProperty(
            emptyList(),
            "the table's rows are unsorted again",
        ) { rowSorter.sortKeys.toList() }
        assertEquals(emptyList(), state.sortKeys, "and the state holds that")
        onNodeOfType<JTable>().assertProperty(
            listOf(0, 1, 2),
            "the columns are in the model's order",
        ) { columnModel.modelIndices() }
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

    companion object {
        private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

        @JvmStatic
        fun tableKinds(): List<Named<@Composable (TableState) -> Unit>> = listOf(
            Named.of("caller's model") { state ->
                Table(model = remember { modelWithColumns("Name", "Age") }, state = state)
            },
            Named.of("declared rows") { state ->
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                )
            },
        )
    }
}

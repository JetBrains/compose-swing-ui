package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performMouseDrag
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.DefaultTableModel
import javax.swing.table.JTableHeader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

/**
 * The sort order and the column layout a [TableState] holds: applied to the table when assigned, and written
 * back when the user clicks a header, drags a column or resizes one on the header the scroll pane shows.
 */
class TableStateOrderAndLayoutTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

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

        val table = onNodeOfType<JTable>()
        table.assertProperty(
            listOf(SortKey(0, SortOrder.DESCENDING)),
            "the initial order",
        ) { rowSorter.sortKeys.toList() }
        table.assertProperty(listOf(1, 0), "the initial column order") { columnModel.modelIndices() }
        table.assertProperty(120, "the initial width of the first column") { columnModel.getColumn(0).preferredWidth }
        assertEquals(setOf(1), state.shownSelectedRowIndices, "the initial selection")
    }

    @Test
    fun assigningTheStatesSortKeysSortsTheRows() = runComposeSwingTest {
        val state = TableState()
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, state = state, sortable = true)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf("Ada", "Alan", "Grace"), "an empty order leaves the rows unsorted") { shownNames() }

        state.sortKeys = listOf(SortKey(0, SortOrder.DESCENDING))
        awaitIdle()

        table.assertProperty(
            listOf("Grace", "Alan", "Ada"),
            "the order the state names sorts the rows",
        ) { shownNames() }
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

        val table = onNodeOfType<JTable>()
        onNodeOfType<JTableHeader>().performClick(table.fetch().headerCenterOf(1))

        val clicked = listOf(SortKey(1, SortOrder.ASCENDING))
        assertEquals(clicked, state.sortKeys, "the state holds the order the click left the rows in")
        table.assertProperty(
            clicked,
            "and the table is left standing on it",
        ) { rowSorter.sortKeys.toList() }
    }

    @Test
    fun sortKeysApplyOnlyWhileTheTableIsSortable() = runComposeSwingTest {
        val keys = listOf(SortKey(0, SortOrder.DESCENDING))
        val state = TableState(initialSortKeys = keys)
        var sortable by mutableStateOf(false)
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, state = state, sortable = sortable)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(
            null,
            "a table that is not sortable installs no row sorter for the state's keys",
        ) { rowSorter }
        table.assertProperty(listOf("Ada", "Alan", "Grace"), "and shows the rows as they come") { shownNames() }

        sortable = true
        awaitIdle()

        table.assertProperty(keys, "a table made sortable stands on the state's keys") { rowSorter.sortKeys.toList() }
        table.assertProperty(listOf("Grace", "Alan", "Ada"), "and shows the rows in that order") { shownNames() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(
            listOf(0, 1),
            "a state without a layout leaves the columns as declared",
        ) { columnModel.modelIndices() }

        awaitIdle()
        assertNull(state.columnLayout, "and writes none of its own while the user moves no column")

        state.columnLayout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        awaitIdle()

        table.assertProperty(
            listOf(1, 0),
            "the layout the state names reorders the columns",
        ) { columnModel.modelIndices() }
        table.assertProperty(120, "and gives the first its width") { columnModel.getColumn(0).preferredWidth }
        table.assertProperty(40, "and the second its width") { columnModel.getColumn(1).preferredWidth }
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
        val table = onNodeOfType<JTable>()
        dragColumn(from = 0, to = 1)
        val firstWidth =
            table
                .fetch()
                .columnModel
                .getColumn(0)
                .width + 40
        val secondWidth =
            table
                .fetch()
                .columnModel
                .getColumn(1)
                .width - 20
        dragColumnDivider(position = 0, dx = 40)
        dragColumnDivider(position = 1, dx = -20)
        state.columnLayout = null
        awaitIdle()

        rows = people.drop(1)
        awaitIdle()

        table.assertProperty(
            listOf(1, 0),
            "the order the user set should stay",
        ) { columnModel.modelIndices() }
        table.assertProperty(
            firstWidth,
            "and the first width",
        ) { columnModel.getColumn(0).preferredWidth }
        table.assertProperty(
            secondWidth,
            "and the second width",
        ) { columnModel.getColumn(1).preferredWidth }
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
        val table = onNodeOfType<JTable>()
        state.columnLayout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        awaitIdle()

        state.columnLayout = null
        awaitIdle()

        table.assertProperty(
            listOf(1, 0),
            "a state declaring no layout leaves the order alone",
        ) { columnModel.modelIndices() }
        table.assertProperty(120, "and the first width") { columnModel.getColumn(0).preferredWidth }
        table.assertProperty(40, "and the second width") { columnModel.getColumn(1).preferredWidth }
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

        val table = onNodeOfType<JTable>()
        onNodeOfType<JTableHeader>().performMouseDrag(table.fetch().headerCenterOf(1), table.fetch().headerCenterOf(0))

        assertEquals(listOf(1, 0), state.columnLayout?.modelIndices, "the state holds the order the drag left")
        table.assertProperty(
            listOf(1, 0),
            "and the table is left standing on it",
        ) { columnModel.modelIndices() }
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

        val table = onNodeOfType<JTable>()
        val resized =
            table
                .fetch()
                .columnModel
                .getColumn(0)
                .width + 40
        onNodeOfType<JTableHeader>().performMouseDrag(
            table.fetch().dividerAfter(0),
            table.fetch().dividerAfter(0).movedBy(40),
        )

        assertEquals(resized, state.columnLayout?.preferredWidths?.get(0), "the state holds the width the resize left")
        table.assertProperty(
            resized,
            "and the table is left standing on it",
        ) { columnModel.getColumn(0).preferredWidth }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(
            listOf(1, 0),
            "the layout the state names reaches the model's table",
        ) { columnModel.modelIndices() }
        table.assertProperty(
            listOf("Grace", "Alan", "Ada"),
            "the order the state names sorts the model's rows",
        ) { (0 until rowCount).map { getValueAt(it, 1) } }

        onNodeOfType<JTableHeader>().performClick(table.fetch().headerCenterOf(0))

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
        val table = onNodeOfType<JTable>()

        // What a header click does, with the caller's answer in the same event: no pass runs in between.
        table.fetch().rowSorter.toggleSortOrder(1)
        assertEquals(listOf(SortKey(1, SortOrder.ASCENDING)), state.sortKeys.take(1), "the click is written back")
        state.sortKeys = descending
        awaitIdle()

        table.assertProperty(
            descending,
            "the order assigned back should be applied",
        ) { rowSorter.sortKeys.toList() }
        table.assertProperty(listOf("Grace", "Alan", "Ada"), "and sort the rows") { shownNames() }
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
        val table = onNodeOfType<JTable>()

        // What a header drag does, with the caller's answer in the same event: no pass runs in between.
        table.fetch().columnModel.moveColumn(1, 0)
        assertEquals(listOf(1, 0), state.columnLayout?.modelIndices, "the drag is written back")
        state.columnLayout = layout
        awaitIdle()

        table.assertProperty(
            listOf(0, 1),
            "the layout assigned back should be applied",
        ) { columnModel.modelIndices() }
        table.assertProperty(listOf(80, 90), "with its widths") { columnModel.preferredWidths() }
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
        val table = onNodeOfType<JTable>()
        onNodeOfType<JTableHeader>().performMouseDrag(table.fetch().headerCenterOf(1), table.fetch().headerCenterOf(0))
        val dragged = state.columnLayout
        assertEquals(listOf(1, 0), dragged?.modelIndices, "the first drag is written back")

        state.columnLayout = layout
        awaitIdle()
        table.assertProperty(
            listOf(0, 1),
            "the layout assigned back is applied",
        ) { columnModel.modelIndices() }

        onNodeOfType<JTableHeader>().performMouseDrag(table.fetch().headerCenterOf(1), table.fetch().headerCenterOf(0))

        assertEquals(dragged, state.columnLayout, "the same drag made again should be written back again")
        table.assertProperty(
            listOf(1, 0),
            "and the table should stand on it",
        ) { columnModel.modelIndices() }
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

        val second = onNodeOfType<JTable>()
        assertNotSame(first, second.fetch(), "a new key builds a new table")
        second.assertProperty(order, "the new table starts on the state's order") { rowSorter.sortKeys.toList() }
        second.assertProperty(layout?.modelIndices, "and on its column order") { columnModel.modelIndices() }
        second.assertProperty(layout?.preferredWidths, "and on its widths") { columnModel.preferredWidths() }
        assertEquals(order, state.sortKeys, "and the state is left holding the order")
        assertEquals(layout, state.columnLayout, "and the layout")
    }
}

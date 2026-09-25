package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.event.InputEvent
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.event.RowSorterEvent
import javax.swing.table.DefaultTableModel
import javax.swing.table.JTableHeader
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The row space of [Table] splits in two once sorting or filtering is on.
 *
 * A row the caller declares as selected, and a row the table reports back, is a row of the model: an index
 * into `rows` or into the caller's own `TableModel`. A row on screen is a position that a sort order and a
 * row filter both move, and that a filter can take away entirely. The wrapper converts between the two at
 * the table. A table that neither sorts nor filters shows the model row by row, so the two spaces hold the
 * same numbers.
 *
 * A sort order belongs to the user and is never imposed; a [TableState] sets one, and keeps it across a
 * rebuild of the rows or a swap of the model.
 *
 * A header click is a mouse gesture on the header the scroll pane shows, and a row click one on the table the
 * pane lays out.
 */
class TableRowSortingTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

    private fun tableModel(vararg names: String): DefaultTableModel =
        DefaultTableModel(names.map { arrayOf<Any?>(it) }.toTypedArray(), arrayOf<Any?>("Name"))

    private val byLengthDescending =
        Comparator<Any?> { first, second -> (second as String).length - (first as String).length }

    private val byNameAscending =
        Comparator<Any?> { first, second -> (first as String).compareTo(second as String) }

    @Test
    fun aTableDoesNotSortUntilItIsAskedTo() = runComposeSwingTest {
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } })
        }

        assertNull(onNodeOfType<JTable>().fetch().rowSorter, "a table sorts nothing until sortable turns it on")
    }

    @Test
    fun withoutASorterAModelRowIsTheRowOnScreen() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val received = mutableListOf<Set<Int>>()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = { column("Name") { it.name } },
                    selectedRowIndices = null,
                    onSelectionChange = { received += it },
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTable>().performClick(table.rowCenter(2))
        awaitIdle()

        assertEquals(setOf(2), received.last(), "the row the user picked is reported by its model index")
        assertEquals(listOf(2), table.selectedRows.toList(), "which is the row the table has selected")
    }

    @Test
    fun aDeclaredModelRowSelectsTheScreenRowItSortsTo() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name") { it.name }
                    column("Age") { it.age }
                },
                state =
                    rememberTableState(
                        initialSelectedRowIndices = setOf(0),
                        initialSortKeys = listOf(SortKey(1, SortOrder.DESCENDING)),
                    ),
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "the state's order should reach the rows")
        assertEquals(listOf(2), table.selectedRows.toList(), "the declared model row is selected where it is drawn")
        assertEquals("Ada", table.getValueAt(2, 0), "and that screen row is the one the declared index names")
    }

    @Test
    fun aClickOnASortedRowReportsTheModelRow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = TableState(initialSortKeys = listOf(SortKey(1, SortOrder.DESCENDING)))
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
        onNodeOfType<JTable>().performClick(table.rowCenter(0))
        awaitIdle()

        assertEquals(setOf(2), state.selectedRowIndices, "the top row on screen is the last row of the model")
    }

    @Test
    fun aFilterThatHidesASelectedRowDropsItFromTheReport() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var filter by mutableStateOf<RowFilter<in TableModel, in Int>?>(null)
        val received = mutableListOf<Set<Int>>()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    onSelectionChange = { received += it },
                    sortable = true,
                    rowFilter = filter,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTable>().performClick(table.rowCenter(0))
        onNodeOfType<JTable>().performClick(table.rowCenter(2), modifiers = InputEvent.SHIFT_DOWN_MASK)
        awaitIdle()
        assertEquals(setOf(0, 1, 2), received.last(), "every row is selected to start with")

        filter = RowFilter.regexFilter("Ada", 0)
        awaitIdle()

        assertEquals(listOf("Ada"), table.shownNames(), "only the rows the filter admits are shown")
        assertEquals(setOf(0), received.last(), "the rows it hides leave the selection and are reported gone")
    }

    @Test
    fun aDeclaredSelectionSurvivesAFilterChangeAndIsNeverReportedBack() = runComposeSwingTest {
        var filter by mutableStateOf<RowFilter<in TableModel, in Int>?>(null)
        var selection by mutableStateOf(setOf(0, 1))
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
                sortable = true,
                rowFilter = filter,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(setOf(0, 1), table.selectedModelRows(), "the declared rows should be selected to start with")

        // A filter takes hidden rows out of the table's selection - the wrapper's own write. The rows it drops
        // are re-selected once the filter admits them again, and reach no callback.
        filter = RowFilter.regexFilter("Ada", 0)
        awaitIdle()
        assertEquals(setOf(0), table.selectedModelRows(), "the row the filter hides has no screen row to hold")

        filter = null
        awaitIdle()
        assertEquals(setOf(0, 1), table.selectedModelRows(), "the declared selection should be back in full")
        assertEquals(emptyList(), received, "a selection the filter moved is the wrapper's doing, not the user's")
    }

    @Test
    fun aDeclaredSelectionChangeAloneStillNarrowsAgainstAnUnchangedFilter() = runComposeSwingTest {
        // Held across the whole test so it is the same instance every pass; the filter itself never moves.
        val filter = RowFilter.regexFilter<TableModel, Int>("A", 0)
        var selection by mutableStateOf(setOf(0))
        setContent {
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                selectedRowIndices = selection,
                sortable = true,
                rowFilter = filter,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(setOf(0), table.selectedModelRows(), "the first declared selection should be applied")

        // Grace never matches the filter, whether or not the caller declares her selected.
        selection = setOf(0, 1, 2)
        awaitIdle()

        assertEquals(setOf(0, 1), table.selectedModelRows(), "the row the filter hides should be left out")
    }

    @Test
    fun aFilterIsWrittenOnlyWhereTheCallerDeclaresAnotherOne() = runComposeSwingTest {
        var rows by mutableStateOf(people)
        val filter = RowFilter.regexFilter<TableModel, Int>("A", 0)
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } }, sortable = true, rowFilter = filter)
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Ada", "Alan"), table.shownNames(), "the declared filter should reach the rows")

        rows = people + Person("Alonzo", 33)
        awaitIdle()

        val installed: Any? = (table.rowSorter as TableRowSorter<*>).rowFilter
        assertSame(filter, installed, "the same filter should still be in place")
        assertEquals(listOf("Ada", "Alan", "Alonzo"), table.shownNames(), "and should admit the new row")
    }

    @Test
    fun aFilterDeclaredInTheSamePassAsASelectionChangeStillReachesTheSorter() = runComposeSwingTest {
        var filter by mutableStateOf<RowFilter<in TableModel, in Int>?>(null)
        var selection by mutableStateOf(setOf(0))
        setContent {
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                selectedRowIndices = selection,
                sortable = true,
                rowFilter = filter,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(people.map { it.name }, table.shownNames(), "every row is shown before a filter is declared")

        filter = RowFilter.regexFilter("Ada", 0)
        selection = setOf(1)
        awaitIdle()

        val installed: Any? = (table.rowSorter as TableRowSorter<*>).rowFilter
        assertSame(filter, installed, "a filter declared alongside a selection change should still reach the sorter")
        assertEquals(listOf("Ada"), table.shownNames(), "and be applied to the rows the table shows")
    }

    @Test
    fun aFilterDeclaredWhileSortingIsOffReachesTheSorterTurningItOnBuilds() = runComposeSwingTest {
        var sortable by mutableStateOf(false)
        val filter = RowFilter.regexFilter<TableModel, Int>("A", 0)
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, sortable = sortable, rowFilter = filter)
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(people.map { it.name }, table.shownNames(), "a table with no sorter filters nothing")

        sortable = true
        awaitIdle()

        val installed: Any? = (table.rowSorter as TableRowSorter<*>).rowFilter
        assertSame(filter, installed, "the sorter sorting turns on should carry the declared filter")
        assertEquals(listOf("Ada", "Alan"), table.shownNames(), "and hide the rows it rejects")
    }

    @Test
    fun aDeclaredFilterOutlivesTheSorterAModelSwapRebuilds() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        val filter = RowFilter.regexFilter<TableModel, Int>("A", 0)
        setContent { Table(model = model, sortable = true, rowFilter = filter) }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Ada", "Alan"), table.shownNames(), "the declared filter should reach the rows")

        // A sorter is welded to its model, so the swap takes this one off and builds another.
        model = tableModel("Ada", "Bob")
        awaitIdle()

        val installed: Any? = (table.rowSorter as TableRowSorter<*>).rowFilter
        assertSame(filter, installed, "the sorter built for the new model should carry the declared filter")
        assertEquals(listOf("Ada"), table.shownNames(), "and hide the rows it rejects")
    }

    @Test
    fun sortingByAColumnHeaderReachesOnSortChange() = runComposeSwingTest {
        val received = mutableListOf<List<SortKey>>()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    sortable = true,
                    onSortChange = { received += it },
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTableHeader>().performClick(table.headerCenterOf(1))

        assertEquals(
            listOf(SortKey(1, SortOrder.ASCENDING)),
            received.last(),
            "the order the user sorted the rows into should be reported",
        )
    }

    @Test
    fun aStatesSortOrderOutlivesTheSorterAModelSwapRebuilds() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        val declared = listOf(SortKey(0, SortOrder.DESCENDING))
        val state = TableState(initialSortKeys = declared)
        setContent { Table(model = model, state = state, sortable = true) }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "the state's order should reach the rows")

        // The sorter the swap builds starts out unsorted, so the state's order has to be put back onto it.
        model = tableModel("Bob", "Zoe", "Ada")
        awaitIdle()

        assertEquals(
            declared,
            table.rowSorter.sortKeys.toList(),
            "the sorter built for the new model should carry the state's order",
        )
        assertEquals(listOf("Zoe", "Bob", "Ada"), table.shownNames(), "and should order the new rows by it")
        assertEquals(declared, state.sortKeys, "an order the wrapper put back is its own doing, not the user's")
    }

    @Test
    fun anUndeclaredSortOrderIsNeverImposed() = runComposeSwingTest {
        var rows by mutableStateOf(people)
        setContent {
            HeaderPane {
                Table(
                    rows = rows,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    sortable = true,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTableHeader>().performClick(table.headerCenterOf(1))

        rows = people + Person("Nikola", 25)
        awaitIdle()

        assertEquals(
            listOf(SortKey(1, SortOrder.ASCENDING)),
            table.rowSorter.sortKeys.toList(),
            "the user's own order should survive a refresh of the rows",
        )
        assertEquals(listOf("Nikola", "Ada", "Alan", "Grace"), table.shownNames(), "and should order the new row too")
    }

    @Test
    fun aColumnDeclaredUnsortableIsNotSortedBy() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                columns = {
                    column("Name", isSortable = false) { it.name }
                    column("Age") { it.age }
                },
                sortable = true,
            )
        }

        val sorter = onNodeOfType<JTable>().fetch().rowSorter as TableRowSorter<*>
        assertFalse(sorter.isSortable(0), "a column declared unsortable should not sort")
        assertTrue(sorter.isSortable(1), "while its neighbor still does")
    }

    @Test
    fun aColumnsComparatorOrdersItsRows() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                columns = {
                    column(header = "Name", comparator = byLengthDescending) { it.name }
                    column("Age") { it.age }
                },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING))),
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(
            listOf("Grace", "Alan", "Ada"),
            table.shownNames(),
            "the column's own comparator should order it, not the ordering its class would get",
        )
    }

    @Test
    fun aColumnsNewComparatorReordersTheRowsItAlreadySorts() = runComposeSwingTest {
        val byLength = Comparator<Any?> { first, second -> (second as String).length - (first as String).length }
        // By last letter: Ada, Grace, Alan - neither the order the old comparator leaves them in nor the
        // one the rows fall into on their own, so an ignored declaration cannot pass for an applied one.
        val byLastLetter = Comparator<Any?> { first, second -> (first as String).last() - (second as String).last() }
        var comparator by mutableStateOf(byLength)
        setContent {
            Table(
                rows = people,
                columns = { column(header = "Name", comparator = comparator) { it.name } },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING))),
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "the declared comparator orders the rows")

        comparator = byLastLetter
        awaitIdle()

        assertEquals(listOf("Ada", "Grace", "Alan"), table.shownNames(), "and a new one reorders them by itself")
    }

    @Test
    fun aColumnsComparatorSurvivesARebuildOfTheColumns() = runComposeSwingTest {
        var header by mutableStateOf("Name")
        setContent {
            Table(
                rows = people,
                columns = { column(header = header, comparator = byLengthDescending) { it.name } },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING))),
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "the declared comparator orders the rows")

        header = "Person"
        awaitIdle()

        assertEquals(
            listOf("Grace", "Alan", "Ada"),
            table.shownNames(),
            "and still orders them once a new header has rebuilt the columns",
        )
    }

    @Test
    fun aColumnsComparatorTakenAwayReordersTheRowsItSorts() = runComposeSwingTest {
        val byLength = Comparator<Any?> { first, second -> (second as String).length - (first as String).length }
        var comparator by mutableStateOf<Comparator<Any?>?>(byLength)
        setContent {
            Table(
                rows = people,
                columns = { column(header = "Name", comparator = comparator) { it.name } },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING))),
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "the declared comparator orders the rows")

        comparator = null
        awaitIdle()

        assertEquals(
            listOf("Ada", "Alan", "Grace"),
            table.shownNames(),
            "and taking it away puts them back in the order the column's own class gets",
        )
    }

    @Test
    fun aPassThatRedeclaresBothTheComparatorAndTheOrderSortsTheRowsOnce() = runComposeSwingTest {
        val byLength = Comparator<Any?> { first, second -> (second as String).length - (first as String).length }
        // By last letter: Ada, Grace, Alan ascending, so neither order can be reached by the other
        // comparator or by the rows' own ordering.
        val byLastLetter = Comparator<Any?> { first, second -> (first as String).last() - (second as String).last() }
        var comparator by mutableStateOf(byLength)
        val state = TableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING)))
        setContent {
            Table(
                rows = people,
                columns = { column(header = "Name", comparator = comparator) { it.name } },
                state = state,
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        val sorter = table.rowSorter
        var sorts = 0
        sorter.addRowSorterListener { event ->
            if (event.type == RowSorterEvent.Type.SORTED) sorts++
        }

        comparator = byLastLetter
        state.sortKeys = listOf(SortKey(0, SortOrder.DESCENDING))
        awaitIdle()

        assertSame(sorter, table.rowSorter, "the sorter the count was taken on should still be the table's")
        assertEquals(listOf("Alan", "Grace", "Ada"), table.shownNames(), "the new comparator orders them, reversed")
        assertEquals(1, sorts, "the order the keys ask for is already the new comparator's, so it is sorted once")
    }

    @Test
    fun aColumnWithoutAComparatorIsNotSortedAgainOnEveryPass() = runComposeSwingTest {
        var rowHeight by mutableStateOf(20)
        setContent {
            Table(
                rows = people,
                columns = { column("Name") { it.name } },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING))),
                sortable = true,
                rowHeight = rowHeight,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        val sorter = table.rowSorter
        var sorts = 0
        sorter.addRowSorterListener { event ->
            if (event.type == RowSorterEvent.Type.SORTED) sorts++
        }

        rowHeight = 24
        awaitIdle()

        assertEquals(24, table.rowHeight, "the pass the count is taken over should have reached the table")
        assertSame(sorter, table.rowSorter, "on the sorter it was taken on, which the table still holds")
        assertEquals(0, sorts, "a pass that redeclares nothing about the ordering should not sort the rows again")
    }

    @Test
    fun aColumnWhoseDeclaredComparatorIsHeldIsNotSortedAgainOnEveryPass() = runComposeSwingTest {
        // Held across passes: a comparator compared by identity has to be, for the pass that redeclares
        // it to leave the ordering alone.
        var rowHeight by mutableStateOf(20)
        setContent {
            Table(
                rows = people,
                columns = { column("Name", comparator = byNameAscending) { it.name } },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING))),
                sortable = true,
                rowHeight = rowHeight,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        var sorts = 0
        table.rowSorter.addRowSorterListener { event ->
            if (event.type == RowSorterEvent.Type.SORTED) sorts++
        }

        rowHeight = 24
        awaitIdle()

        assertEquals(0, sorts, "a pass that redeclares nothing about the ordering should not sort the rows again")
    }

    @Test
    fun aShiftExtensionAfterAnUnrelatedPassRunsFromTheRowTheSelectionStartedOn() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val rows = List(10) { "row $it" }
        var rowHeight by mutableStateOf(20)
        setContent {
            HeaderPane {
                Table(
                    rows = rows,
                    columns = { column("Name", comparator = byNameAscending) { it } },
                    state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING))),
                    sortable = true,
                    rowHeight = rowHeight,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTable>().performClick(table.rowCenter(2))
        onNodeOfType<JTable>().performClick(table.rowCenter(5), modifiers = InputEvent.SHIFT_DOWN_MASK)
        awaitIdle()
        assertEquals((2..5).toList(), table.selectedRows.toList(), "the shift-click covers the rows it spans")

        rowHeight = 24
        awaitIdle()

        onNodeOfType<JTable>().performClick(table.rowCenter(8), modifiers = InputEvent.SHIFT_DOWN_MASK)

        assertEquals(
            (2..8).toList(),
            table.selectedRows.toList(),
            "the next shift-click should extend the selection from the row it was anchored on",
        )
    }

    @Test
    fun theAnchorCarriedAcrossASortIsNotReportedAsTheUsersSelection() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val rows = List(10) { "row $it" }
        // Two instances ordering alike: handing over the second is an ordering the caller redeclared, which
        // is what has the rows sorted again, while the order they land in is the one they were already in.
        val byName = Comparator<Any?> { first, second -> (first as String).compareTo(second as String) }
        val byNameAgain = Comparator<Any?> { first, second -> (first as String).compareTo(second as String) }
        var comparator by mutableStateOf(byName)
        val received = mutableListOf<Set<Int>>()
        setContent {
            HeaderPane {
                Table(
                    rows = rows,
                    columns = { column("Name", comparator = comparator) { it } },
                    selectedRowIndices = null,
                    onSelectionChange = { received += it },
                    sortable = true,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTableHeader>().performClick(table.headerCenterOf(0))
        onNodeOfType<JTable>().performClick(table.rowCenter(2))
        onNodeOfType<JTable>().performClick(table.rowCenter(5), modifiers = InputEvent.SHIFT_DOWN_MASK)
        awaitIdle()
        val reported = received.size

        comparator = byNameAgain
        awaitIdle()

        assertEquals(2, table.selectionModel.anchorSelectionIndex, "the anchor is put back across the re-sort")
        assertEquals(reported, received.size, "putting it back is the wrapper's own write, not a selection change")
    }

    @Test
    fun turningSortingOnKeepsTheRowsTheUserHadSelected() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var sortable by mutableStateOf(false)
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, sortable = sortable)
        }

        val table = onNodeOfType<JTable>().fetch()
        onNodeOfType<JTable>().performClick(table.rowCenter(1))
        awaitIdle()

        sortable = true
        awaitIdle()

        assertEquals(listOf(1), table.selectedRows.toList(), "installing a sorter should not empty the selection")
    }

    @Test
    fun aSorterFollowsTheModelTheTableIsGiven() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        setContent {
            Table(
                model = model,
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING))),
                sortable = true,
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "the state's order should reach the rows")

        model = tableModel("Nikola", "Marie")
        awaitIdle()

        assertSame(model, (table.rowSorter as TableRowSorter<*>).model, "the sorter should follow the new model")
        assertEquals(listOf("Nikola", "Marie"), table.shownNames(), "the state's order should survive the swap")
    }
}

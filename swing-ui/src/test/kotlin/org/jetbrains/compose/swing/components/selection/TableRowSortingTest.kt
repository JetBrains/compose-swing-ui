// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
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
import kotlin.test.assertIs
import kotlin.test.assertSame

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

    private val byLengthDescending =
        Comparator<Any?> { first, second -> (second as String).length - (first as String).length }

    private val byNameAscending =
        Comparator<Any?> { first, second -> (first as String).compareTo(second as String) }

    @Test
    fun aTableDoesNotSortUntilItIsAskedTo() = runComposeSwingTest {
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } })
        }

        onNodeOfType<JTable>().assertProperty(null, "a table sorts nothing until sortable turns it on") { rowSorter }
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

        val table = onNodeOfType<JTable>()
        table.performClick(table.fetch().rowCenter(2))
        awaitIdle()

        assertEquals(setOf(2), received.last(), "the row the user picked is reported by its model index")
        table.assertProperty(listOf(2), "which is the row the table has selected") { selectedRows.toList() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf("Grace", "Alan", "Ada"), "the state's order should reach the rows") { shownNames() }
        table.assertProperty(
            listOf(2),
            "the declared model row is selected where it is drawn",
        ) { selectedRows.toList() }
        table.assertProperty("Ada", "and that screen row is the one the declared index names") { getValueAt(2, 0) }
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

        val table = onNodeOfType<JTable>()
        table.performClick(table.fetch().rowCenter(0))
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

        val table = onNodeOfType<JTable>()
        table.performClick(table.fetch().rowCenter(0))
        table.performClick(table.fetch().rowCenter(2), modifiers = InputEvent.SHIFT_DOWN_MASK)
        awaitIdle()
        assertEquals(setOf(0, 1, 2), received.last(), "every row is selected to start with")

        filter = RowFilter.regexFilter("Ada", 0)
        awaitIdle()

        table.assertProperty(listOf("Ada"), "only the rows the filter admits are shown") { shownNames() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(setOf(0, 1), "the declared rows should be selected to start with") { selectedModelRows() }

        // A filter takes hidden rows out of the table's selection - the wrapper's own write. The rows it drops
        // are re-selected once the filter admits them again, and reach no callback.
        filter = RowFilter.regexFilter("Ada", 0)
        awaitIdle()
        table.assertProperty(setOf(0), "the row the filter hides has no screen row to hold") { selectedModelRows() }

        filter = null
        awaitIdle()
        table.assertProperty(setOf(0, 1), "the declared selection should be back in full") { selectedModelRows() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(setOf(0), "the first declared selection should be applied") { selectedModelRows() }

        // Grace never matches the filter, whether or not the caller declares her selected.
        selection = setOf(0, 1, 2)
        awaitIdle()

        table.assertProperty(setOf(0, 1), "the row the filter hides should be left out") { selectedModelRows() }
    }

    @Test
    fun aFilterIsWrittenOnlyWhereTheCallerDeclaresAnotherOne() = runComposeSwingTest {
        var rows by mutableStateOf(people)
        val filter = RowFilter.regexFilter<TableModel, Int>("A", 0)
        setContent {
            Table(rows = rows, columns = { column("Name") { it.name } }, sortable = true, rowFilter = filter)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf("Ada", "Alan"), "the declared filter should reach the rows") { shownNames() }

        rows = people + Person("Alonzo", 33)
        awaitIdle()

        val installed: Any? =
            assertIs<TableRowSorter<*>>(table.fetch().rowSorter, "expected a table row sorter").rowFilter
        assertSame(filter, installed, "the same filter should still be in place")
        table.assertProperty(listOf("Ada", "Alan", "Alonzo"), "and should admit the new row") { shownNames() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(people.map { it.name }, "every row is shown before a filter is declared") { shownNames() }

        filter = RowFilter.regexFilter("Ada", 0)
        selection = setOf(1)
        awaitIdle()

        val installed: Any? =
            assertIs<TableRowSorter<*>>(table.fetch().rowSorter, "expected a table row sorter").rowFilter
        assertSame(filter, installed, "a filter declared alongside a selection change should still reach the sorter")
        table.assertProperty(listOf("Ada"), "and be applied to the rows the table shows") { shownNames() }
    }

    @Test
    fun aFilterDeclaredWhileSortingIsOffReachesTheSorterTurningItOnBuilds() = runComposeSwingTest {
        var sortable by mutableStateOf(false)
        val filter = RowFilter.regexFilter<TableModel, Int>("A", 0)
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, sortable = sortable, rowFilter = filter)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(people.map { it.name }, "a table with no sorter filters nothing") { shownNames() }

        sortable = true
        awaitIdle()

        val installed: Any? =
            assertIs<TableRowSorter<*>>(table.fetch().rowSorter, "expected a table row sorter").rowFilter
        assertSame(filter, installed, "the sorter sorting turns on should carry the declared filter")
        table.assertProperty(listOf("Ada", "Alan"), "and hide the rows it rejects") { shownNames() }
    }

    @Test
    fun aDeclaredFilterOutlivesTheSorterAModelSwapRebuilds() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        val filter = RowFilter.regexFilter<TableModel, Int>("A", 0)
        setContent { Table(model = model, sortable = true, rowFilter = filter) }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf("Ada", "Alan"), "the declared filter should reach the rows") { shownNames() }

        // A sorter is welded to its model, so the swap takes this one off and builds another.
        model = tableModel("Ada", "Bob")
        awaitIdle()

        val installed: Any? =
            assertIs<TableRowSorter<*>>(table.fetch().rowSorter, "expected a table row sorter").rowFilter
        assertSame(filter, installed, "the sorter built for the new model should carry the declared filter")
        table.assertProperty(listOf("Ada"), "and hide the rows it rejects") { shownNames() }
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

    @TestFactory
    fun onlyHeaderGesturesReachOnSortChange(): List<DynamicTest> =
        listOf("declared rows" to false, "caller-owned model" to true).map { (name, modelDriven) ->
            dynamicTest(name) {
                assertOnlyHeaderGesturesReachOnSortChange(modelDriven, people)
            }
        }

    @Test
    fun aStatesSortOrderOutlivesTheSorterAModelSwapRebuilds() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        val declared = listOf(SortKey(0, SortOrder.DESCENDING))
        val state = TableState(initialSortKeys = declared)
        setContent { Table(model = model, state = state, sortable = true) }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf("Grace", "Alan", "Ada"), "the state's order should reach the rows") { shownNames() }

        // The sorter the swap builds starts out unsorted, so the state's order has to be put back onto it.
        model = tableModel("Bob", "Zoe", "Ada")
        awaitIdle()

        table.assertProperty(
            declared,
            "the sorter built for the new model should carry the state's order",
        ) { rowSorter.sortKeys.toList() }
        table.assertProperty(listOf("Zoe", "Bob", "Ada"), "and should order the new rows by it") { shownNames() }
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

        val table = onNodeOfType<JTable>()
        onNodeOfType<JTableHeader>().performClick(table.fetch().headerCenterOf(1))

        rows = people + Person("Nikola", 25)
        awaitIdle()

        table.assertProperty(
            listOf(SortKey(1, SortOrder.ASCENDING)),
            "the user's own order should survive a refresh of the rows",
        ) { rowSorter.sortKeys.toList() }
        table.assertProperty(
            listOf("Nikola", "Ada", "Alan", "Grace"),
            "and should order the new row too",
        ) { shownNames() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(false, "a column declared unsortable should not sort") {
            assertIs<TableRowSorter<*>>(rowSorter).isSortable(0)
        }
        table.assertProperty(true, "while its neighbor still does") {
            assertIs<TableRowSorter<*>>(rowSorter).isSortable(1)
        }
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

        onNodeOfType<JTable>().assertProperty(
            listOf("Grace", "Alan", "Ada"),
            "the column's own comparator should order it, not the ordering its class would get",
        ) { shownNames() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf("Grace", "Alan", "Ada"), "the declared comparator orders the rows") { shownNames() }

        comparator = byLastLetter
        awaitIdle()

        table.assertProperty(listOf("Ada", "Grace", "Alan"), "and a new one reorders them by itself") { shownNames() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf("Grace", "Alan", "Ada"), "the declared comparator orders the rows") { shownNames() }

        header = "Person"
        awaitIdle()

        table.assertProperty(
            listOf("Grace", "Alan", "Ada"),
            "and still orders them once a new header has rebuilt the columns",
        ) { shownNames() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf("Grace", "Alan", "Ada"), "the declared comparator orders the rows") { shownNames() }

        comparator = null
        awaitIdle()

        table.assertProperty(
            listOf("Ada", "Alan", "Grace"),
            "and taking it away puts them back in the order the column's own class gets",
        ) { shownNames() }
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

        val table = onNodeOfType<JTable>()
        val sorter = table.fetch().rowSorter
        var sorts = 0
        sorter.addRowSorterListener { event ->
            if (event.type == RowSorterEvent.Type.SORTED) sorts++
        }

        comparator = byLastLetter
        state.sortKeys = listOf(SortKey(0, SortOrder.DESCENDING))
        awaitIdle()

        assertSame(sorter, table.fetch().rowSorter, "the sorter the count was taken on should still be the table's")
        table.assertProperty(
            listOf("Alan", "Grace", "Ada"),
            "the new comparator orders them, reversed",
        ) { shownNames() }
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

        val table = onNodeOfType<JTable>()
        val sorter = table.fetch().rowSorter
        var sorts = 0
        sorter.addRowSorterListener { event ->
            if (event.type == RowSorterEvent.Type.SORTED) sorts++
        }

        rowHeight = 24
        awaitIdle()

        table.assertProperty(24, "the pass the count is taken over should have reached the table") { this.rowHeight }
        assertSame(sorter, table.fetch().rowSorter, "on the sorter it was taken on, which the table still holds")
        assertEquals(0, sorts, "a pass that redeclares nothing about the ordering should not sort the rows again")
    }

    @Test
    fun aColumnWhoseDeclaredComparatorIsHeldIsNotSortedAgainOnEveryPass() = runComposeSwingTest {
        // Held across passes: a comparator compared by identity has to be, for the pass that redeclares
        // it to leave the ordering alone.
        val rows = people.reversed()
        var rowHeight by mutableStateOf(20)
        setContent {
            Table(
                rows = rows,
                columns = { column("Name", comparator = byNameAscending) { it.name } },
                state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING))),
                sortable = true,
                rowHeight = rowHeight,
            )
        }

        val table = onNodeOfType<JTable>()
        val sorter = table.fetch().rowSorter
        table.assertProperty(listOf("Ada", "Alan", "Grace")) { shownNames() }
        table.assertProperty(listOf(SortKey(0, SortOrder.ASCENDING))) { rowSorter.sortKeys }
        var sorts = 0
        sorter.addRowSorterListener { event ->
            if (event.type == RowSorterEvent.Type.SORTED) sorts++
        }

        rowHeight = 24
        awaitIdle()

        table.assertProperty(24, "the pass the count is taken over should have reached the table") { this.rowHeight }
        assertSame(sorter, table.fetch().rowSorter, "on the sorter it was taken on, which the table still holds")
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

        val table = onNodeOfType<JTable>()
        table.performClick(table.fetch().rowCenter(2))
        table.performClick(table.fetch().rowCenter(5), modifiers = InputEvent.SHIFT_DOWN_MASK)
        awaitIdle()
        table.assertProperty((2..5).toList(), "the shift-click covers the rows it spans") { selectedRows.toList() }

        rowHeight = 24
        awaitIdle()

        table.performClick(table.fetch().rowCenter(8), modifiers = InputEvent.SHIFT_DOWN_MASK)

        table.assertProperty(
            (2..8).toList(),
            "the next shift-click should extend the selection from the row it was anchored on",
        ) { selectedRows.toList() }
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

        val table = onNodeOfType<JTable>()
        onNodeOfType<JTableHeader>().performClick(table.fetch().headerCenterOf(0))
        table.performClick(table.fetch().rowCenter(2))
        table.performClick(table.fetch().rowCenter(5), modifiers = InputEvent.SHIFT_DOWN_MASK)
        awaitIdle()
        val reported = received.size

        comparator = byNameAgain
        awaitIdle()

        table.assertProperty(2, "the anchor is put back across the re-sort") { selectionModel.anchorSelectionIndex }
        assertEquals(reported, received.size, "putting it back is the wrapper's own write, not a selection change")
    }

    @Test
    fun turningSortingOnKeepsTheRowsTheUserHadSelected() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var sortable by mutableStateOf(false)
        setContent {
            Table(rows = people, columns = { column("Name") { it.name } }, sortable = sortable)
        }

        val table = onNodeOfType<JTable>()
        table.performClick(table.fetch().rowCenter(1))
        awaitIdle()

        sortable = true
        awaitIdle()

        table.assertProperty(listOf(1), "installing a sorter should not empty the selection") { selectedRows.toList() }
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf("Grace", "Alan", "Ada"), "the state's order should reach the rows") { shownNames() }

        model = tableModel("Nikola", "Marie")
        awaitIdle()

        assertSame(
            model,
            assertIs<TableRowSorter<*>>(table.fetch().rowSorter, "expected a table row sorter").model,
            "the sorter should follow the new model",
        )
        table.assertProperty(listOf("Nikola", "Marie"), "the state's order should survive the swap") { shownNames() }
    }
}

private fun tableModel(vararg names: String): DefaultTableModel =
    DefaultTableModel(names.map { arrayOf<Any?>(it) }.toTypedArray(), arrayOf<Any?>("Name"))

private fun assertOnlyHeaderGesturesReachOnSortChange(
    modelDriven: Boolean,
    people: List<Person>,
) = runComposeSwingTest {
    val received = mutableListOf<List<SortKey>>()
    var rows by mutableStateOf(people)
    var header by mutableStateOf("Name")
    var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
    setContent {
        HeaderPane {
            if (modelDriven) {
                Table(model = model, sortable = true, onSortChange = { received += it })
            } else {
                Table(
                    rows = rows,
                    columns = { column(header) { it.name } },
                    sortable = true,
                    onSortChange = { received += it },
                )
            }
        }
    }

    val table = onNodeOfType<JTable>()
    val ascending = listOf(SortKey(0, SortOrder.ASCENDING))
    val descending = listOf(SortKey(0, SortOrder.DESCENDING))
    assertEquals(emptyList(), received, "installing the table reports no user gesture")

    onNodeOfType<JTableHeader>().performClick(table.fetch().headerCenterOf(0))
    assertEquals(listOf(ascending), received, "one header click reports its order exactly once")

    onNodeOfType<JTableHeader>().performClick(table.fetch().headerCenterOf(0))
    assertEquals(listOf(ascending, descending), received, "another click reports the new order exactly once")
    table.assertProperty(listOf("Grace", "Alan", "Ada"), "the user's order reaches the rows") { shownNames() }

    val before = table.fetch()
    if (modelDriven) {
        model = tableModel("Ada", "Alan", "Grace", "Bob")
    } else {
        rows = people + Person("Bob", 29)
        header = "Person"
    }
    awaitIdle()

    assertEquals(
        listOf(ascending, descending),
        received,
        "putting the user's order back after a content refresh reports no new gesture",
    )
    assertSame(before, table.fetch(), "the content refresh updates the same table")
    table.assertProperty(listOf("Grace", "Bob", "Alan", "Ada"), "the new rows keep the user's order") { shownNames() }

    onNodeOfType<JTableHeader>().performClick(table.fetch().headerCenterOf(0))
    assertEquals(
        listOf(ascending, descending, ascending),
        received,
        "header gestures are still reported after the refresh",
    )
    table.assertProperty(
        listOf("Ada", "Alan", "Bob", "Grace"),
        "the next gesture sorts the refreshed rows",
    ) { shownNames() }
}

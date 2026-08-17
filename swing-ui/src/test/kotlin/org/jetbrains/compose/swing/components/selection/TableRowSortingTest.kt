package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.DefaultTableModel
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
 * Sorting is the table's own state, the same as selection: a declared sort is re-asserted on every pass
 * and survives a rebuild of the rows or a swap of the model. An undeclared sort belongs to the user and is
 * never imposed.
 *
 * Headless caveat: no native peer realizes, so a header click is driven through `RowSorter.toggleSortOrder`,
 * the call a table header makes when clicked, and a user selection is driven through the table's own
 * selection model, where a real mouse gesture would land.
 */
class TableRowSortingTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50))

    private fun tableModel(vararg names: String): DefaultTableModel =
        DefaultTableModel(names.map { arrayOf<Any?>(it) }.toTypedArray(), arrayOf<Any?>("Name"))

    /** The names the table shows, top to bottom. */
    private fun JTable.shownNames(): List<Any?> = (0 until rowCount).map { getValueAt(it, 0) }

    @Test
    fun aTableDoesNotSortUntilItIsAskedTo() = runComposeSwingTest {
        setContent {
            Table(rows = people) {
                column("Name") { it.name }
            }
        }

        assertNull(onNodeOfType<JTable>().fetch().rowSorter, "a table sorts nothing until sortable turns it on")
    }

    @Test
    fun withoutASorterAModelRowIsTheRowOnScreen() = runComposeSwingTest {
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(rows = people, selectedRowIndices = null, onSelectionChange = { received += it }) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.setRowSelectionInterval(2, 2)
        awaitIdle()

        assertEquals(setOf(2), received.last(), "the row the user picked is reported by its model index")
        assertEquals(listOf(2), table.selectedRows.toList(), "which is the row the table has selected")
    }

    @Test
    fun aDeclaredModelRowSelectsTheScreenRowItSortsTo() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                selectedRowIndices = setOf(0),
                sortable = true,
                sortKeys = listOf(SortKey(1, SortOrder.DESCENDING)),
            ) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "the declared order should reach the rows")
        assertEquals(listOf(2), table.selectedRows.toList(), "the declared model row is selected where it is drawn")
        assertEquals("Ada", table.getValueAt(2, 0), "and that screen row is the one the declared index names")
    }

    @Test
    fun aClickOnASortedRowReportsTheModelRow() = runComposeSwingTest {
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                rows = people,
                onSelectionChange = { received += it },
                sortable = true,
                sortKeys = listOf(SortKey(1, SortOrder.DESCENDING)),
            ) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.setRowSelectionInterval(0, 0)
        awaitIdle()

        assertEquals(setOf(2), received.last(), "the top row on screen is the last row of the model")
    }

    @Test
    fun aFilterThatHidesASelectedRowDropsItFromTheReport() = runComposeSwingTest {
        var filter by mutableStateOf<RowFilter<in TableModel, in Int>?>(null)
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(rows = people, onSelectionChange = { received += it }, sortable = true, rowFilter = filter) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.setRowSelectionInterval(0, 2)
        awaitIdle()
        assertEquals(setOf(0, 1, 2), received.last(), "every row is selected to start with")

        filter = RowFilter.regexFilter<TableModel, Int>("Ada", 0)
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
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
                sortable = true,
                rowFilter = filter,
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(setOf(0, 1), table.selectedModelRows(), "the declared rows should be selected to start with")

        // A filter takes hidden rows out of the table's selection - the wrapper's own write. The rows it drops
        // are re-selected once the filter admits them again, and reach no callback.
        filter = RowFilter.regexFilter<TableModel, Int>("Ada", 0)
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
            Table(rows = people, selectedRowIndices = selection, sortable = true, rowFilter = filter) {
                column("Name") { it.name }
            }
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
            Table(rows = rows, sortable = true, rowFilter = filter) {
                column("Name") { it.name }
            }
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
            Table(rows = people, selectedRowIndices = selection, sortable = true, rowFilter = filter) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(people.map { it.name }, table.shownNames(), "every row is shown before a filter is declared")

        filter = RowFilter.regexFilter<TableModel, Int>("Ada", 0)
        selection = setOf(1)
        awaitIdle()

        val installed: Any? = (table.rowSorter as TableRowSorter<*>).rowFilter
        assertSame(filter, installed, "a filter declared alongside a selection change should still reach the sorter")
        assertEquals(listOf("Ada"), table.shownNames(), "and be applied to the rows the table shows")
    }

    @Test
    fun sortingByAColumnHeaderReachesOnSortChange() = runComposeSwingTest {
        val received = mutableListOf<List<SortKey>>()
        setContent {
            Table(rows = people, sortable = true, onSortChange = { received += it }) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.rowSorter.toggleSortOrder(1)
        awaitIdle()

        assertEquals(
            listOf(SortKey(1, SortOrder.ASCENDING)),
            received.last(),
            "the order the user sorted the rows into should be reported",
        )
    }

    @Test
    fun aSortOrderTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        val declared = listOf(SortKey(0, SortOrder.ASCENDING))
        setContent {
            Table(rows = people, sortable = true, sortKeys = declared) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(declared, table.rowSorter.sortKeys.toList(), "the declared order should reach the sorter")

        table.rowSorter.toggleSortOrder(1)
        awaitIdle()

        assertEquals(declared, table.rowSorter.sortKeys.toList(), "an order the caller does not adopt does not stand")
    }

    @Test
    fun anUndeclaredSortOrderIsNeverImposed() = runComposeSwingTest {
        var rows by mutableStateOf(people)
        setContent {
            Table(rows = rows, sortable = true) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.rowSorter.toggleSortOrder(1)
        awaitIdle()

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
            Table(rows = people, sortable = true) {
                column("Name", isSortable = false) { it.name }
                column("Age") { it.age }
            }
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
                sortable = true,
                sortKeys = listOf(SortKey(0, SortOrder.ASCENDING)),
            ) {
                column(
                    header = "Name",
                    comparator = Comparator { first, second -> (second as String).length - (first as String).length },
                ) { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(
            listOf("Grace", "Alan", "Ada"),
            table.shownNames(),
            "the column's own comparator should order it, not the ordering its class would get",
        )
    }

    @Test
    fun turningSortingOnKeepsTheRowsTheUserHadSelected() = runComposeSwingTest {
        var sortable by mutableStateOf(false)
        setContent {
            Table(rows = people, sortable = sortable) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.setRowSelectionInterval(1, 1)
        awaitIdle()

        sortable = true
        awaitIdle()

        assertEquals(listOf(1), table.selectedRows.toList(), "installing a sorter should not empty the selection")
    }

    @Test
    fun aSorterFollowsTheModelTheTableIsGiven() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        setContent {
            Table(model = model, sortable = true, sortKeys = listOf(SortKey(0, SortOrder.DESCENDING)))
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf("Grace", "Alan", "Ada"), table.shownNames(), "the declared order should reach the rows")

        model = tableModel("Nikola", "Marie")
        awaitIdle()

        assertSame(model, (table.rowSorter as TableRowSorter<*>).model, "the sorter should follow the new model")
        assertEquals(listOf("Nikola", "Marie"), table.shownNames(), "the declared order should survive the swap")
    }
}

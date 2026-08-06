package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for the model-driven `Table(model, ...)` overloads, driven through the real
 * composition pipeline and asserting against the live `JTable`.
 *
 * The central guarantees: a caller-supplied `TableModel` renders as-is; user selection fires
 * `onSelectionChange` with the selected row indices, which name rows of the model however the rows are
 * sorted on screen; a controlled `selectedRowIndices` re-applies after a model swap even though
 * `setModel` clears the selection; and a controlled selection update does not echo back as a spurious
 * callback.
 */
class TableModelBehaviorTest {
    private fun tableModel(vararg names: String): DefaultTableModel =
        DefaultTableModel(names.map { arrayOf<Any?>(it) }.toTypedArray(), arrayOf<Any?>("Name"))

    @Test
    fun modelRendersAsTheTableModel() = runComposeSwingTest {
        val model = tableModel("Ada", "Alan", "Grace")
        setContent { Table(model = model) }

        val table = onNodeOfType<JTable>().fetch()
        assertSame(model, table.model, "the caller-supplied model should back the table as-is")
        assertEquals(3, table.rowCount, "all rows should render")
        assertEquals("Ada", table.getValueAt(0, 0), "cell (0,0) should render the model value")
        assertEquals("Grace", table.getValueAt(2, 0), "cell (2,0) should render the model value")
    }

    @Test
    fun selectingRowsFiresOnSelectionChange() = runComposeSwingTest {
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                model = tableModel("Ada", "Alan", "Grace"),
                selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                onSelectionChange = { received += it },
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        table.setRowSelectionInterval(0, 0)
        table.addRowSelectionInterval(2, 2)
        awaitIdle()

        assertEquals(setOf(0, 2), received.last(), "selected row indices reported to callback")
    }

    @Test
    fun controlledSelectionReAppliesAfterModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        setContent {
            Table(
                model = model,
                selectedRowIndices = setOf(1),
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(1), table.selectedRows.toList(), "initial selection applied")

        // A model swap runs setModel, which clears selection; the controlled selection must
        // re-apply so the selection survives the swap.
        model = tableModel("Nikola", "Marie", "Rosalind")
        awaitIdle()

        assertSame(model, table.model, "the swapped-in model should back the table")
        assertEquals(listOf(1), table.selectedRows.toList(), "controlled selection survives the model swap")
    }

    @Test
    fun aSelectedRowIsNamedByItsPlaceInTheModel() = runComposeSwingTest {
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                model = tableModel("Ada", "Alan", "Grace"),
                onSelectionChange = { received += it },
                sortable = true,
                sortKeys = listOf(SortKey(0, SortOrder.DESCENDING)),
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals("Grace", table.getValueAt(0, 0), "the declared order should put the last row on top")

        table.setRowSelectionInterval(0, 0)
        awaitIdle()

        assertEquals(setOf(2), received.last(), "the row on top is reported by its place in the model")
    }

    @Test
    fun controlledSelectionUpdateConvergesWithoutALoop() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(0))
        val received = mutableListOf<Set<Int>>()
        setContent {
            val model = remember { tableModel("Ada", "Alan", "Grace") }
            Table(
                model = model,
                selectedRowIndices = selection,
                onSelectionChange = {
                    received += it
                    selection = it
                },
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        assertEquals(listOf(0), table.selectedRows.toList(), "initial selection applied")

        selection = setOf(2)
        awaitIdle()

        assertEquals(listOf(2), table.selectedRows.toList(), "external selection applied")
        assertEquals(setOf(2), selection, "controlled state settled on the new selection")
        assertTrue(received.all { it == setOf(2) }, "selection oscillated instead of converging: $received")

        val callbacksAfterSettle = received.size
        awaitIdle()
        assertEquals(callbacksAfterSettle, received.size, "selection kept firing callbacks after settling")
    }
}

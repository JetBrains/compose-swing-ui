// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
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

    /**
     * The twin of [TableBehaviorTest.aSelectionTheCallerDoesNotAdoptDoesNotStandWhenItIsMadeAgain] for a
     * model the caller owns.
     */
    @Test
    fun aSelectionTheCallerDoesNotAdoptDoesNotStandWhenItIsMadeAgain() = runComposeSwingTest {
        val model = tableModel("Ada", "Alan", "Grace")
        setContent { Table(model = model, selectedRowIndices = setOf(0)) }

        val table = onNodeOfType<JTable>()
        repeat(2) { attempt ->
            table.fetch().setRowSelectionInterval(1, 1)
            awaitIdle()
            table.assertProperty(
                listOf(0),
                "attempt ${attempt + 1}: an unadopted selection change does not stand, however often it is made",
            ) { selectedRows.toList() }
        }
    }

    @Test
    fun modelRendersAsTheTableModel() = runComposeSwingTest {
        val model = tableModel("Ada", "Alan", "Grace")
        setContent { Table(model = model) }

        val table = onNodeOfType<JTable>()
        assertSame(model, table.fetch().model, "the caller-supplied model should back the table as-is")
        table.assertProperty(3, "all rows should render") { rowCount }
        table.assertProperty("Ada", "cell (0,0) should render the model value") { getValueAt(0, 0) }
        table.assertProperty("Grace", "cell (2,0) should render the model value") { getValueAt(2, 0) }
    }

    @Test
    fun selectingRowsFiresOnSelectionChange() = runComposeSwingTest {
        val received = mutableListOf<Set<Int>>()
        setContent {
            Table(
                model = tableModel("Ada", "Alan", "Grace"),
                onSelectionChange = { received += it },
                selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
            )
        }

        val table = onNodeOfType<JTable>()
        table.fetch().setRowSelectionInterval(0, 0)
        table.fetch().addRowSelectionInterval(2, 2)
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(1), "initial selection applied") { selectedRows.toList() }

        // A model swap runs setModel, which clears selection; the controlled selection must
        // re-apply so the selection survives the swap.
        model = tableModel("Nikola", "Marie", "Rosalind")
        awaitIdle()

        assertSame(model, table.fetch().model, "the swapped-in model should back the table")
        table.assertProperty(
            listOf(1),
            "controlled selection survives the model swap",
        ) { selectedRows.toList() }
    }

    @Test
    fun aSelectedRowIsNamedByItsPlaceInTheModel() = runComposeSwingTest {
        val state = TableState(initialSortKeys = listOf(SortKey(0, SortOrder.DESCENDING)))
        setContent {
            Table(model = tableModel("Ada", "Alan", "Grace"), state = state, sortable = true)
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(
            "Grace",
            "the state's order should put the last row on top",
        ) { getValueAt(0, 0) }

        table.fetch().setRowSelectionInterval(0, 0)
        awaitIdle()

        assertEquals(setOf(2), state.selectedRowIndices, "the row on top is reported by its place in the model")
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

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(0), "initial selection applied") { selectedRows.toList() }

        selection = setOf(2)
        awaitIdle()

        table.assertProperty(listOf(2), "external selection applied") { selectedRows.toList() }
        assertEquals(setOf(2), selection, "controlled state settled on the new selection")
        assertTrue(received.all { it == setOf(2) }, "selection oscillated instead of converging: $received")

        val callbacksAfterSettle = received.size
        awaitIdle()
        assertEquals(callbacksAfterSettle, received.size, "selection kept firing callbacks after settling")
    }
}

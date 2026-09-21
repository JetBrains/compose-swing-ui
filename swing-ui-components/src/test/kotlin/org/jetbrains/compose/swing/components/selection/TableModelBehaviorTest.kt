// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

/**
 * Behavioral tests for the model-driven `Table(model, ...)` overloads, driven through the real
 * composition pipeline and asserting against the live `JTable`.
 *
 * The central guarantees: a caller-supplied `TableModel` renders as-is; user selection stands on the table
 * and, with a `TableState`, is written to it as row indices, which name rows of the model however the rows
 * are sorted on screen; the state's selection re-applies after a model swap even though `setModel` clears
 * the selection.
 */
class TableModelBehaviorTest {
    private fun tableModel(vararg names: String): DefaultTableModel =
        DefaultTableModel(names.map { arrayOf<Any?>(it) }.toTypedArray(), arrayOf<Any?>("Name"))

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
        setContent {
            Table(
                model = tableModel("Ada", "Alan", "Grace"),
                selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
            )
        }

        val table = onNodeOfType<JTable>()
        table.fetch().setRowSelectionInterval(0, 0)
        table.fetch().addRowSelectionInterval(2, 2)
        awaitIdle()

        table.assertProperty(listOf(0, 2), "selected rows stand on the table") { selectedRows.toList() }
    }

    @Test
    fun stateSelectionReAppliesAfterModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Ada", "Alan", "Grace"))
        val state = TableState(initialSelectedRowIndices = setOf(1))
        setContent {
            Table(
                model = model,
                state = state,
            )
        }

        val table = onNodeOfType<JTable>()
        table.assertProperty(listOf(1), "initial selection applied") { selectedRows.toList() }

        // A model swap runs setModel, which clears selection; the state's selection must
        // re-apply so the selection survives the swap.
        model = tableModel("Nikola", "Marie", "Rosalind")
        awaitIdle()

        assertSame(model, table.fetch().model, "the swapped-in model should back the table")
        table.assertProperty(
            listOf(1),
            "the state's selection survives the model swap",
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
}

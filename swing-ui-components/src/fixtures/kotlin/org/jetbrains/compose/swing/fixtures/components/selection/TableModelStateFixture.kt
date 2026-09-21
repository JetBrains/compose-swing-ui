// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.TableColumnLayout
import org.jetbrains.compose.swing.components.selection.rememberTableState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTable
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel

/**
 * The parameters [TableModelStateFixture] varies: the selection, sort order and column layout of the table state it
 * drives.
 */
public data class TableModelStateParameters(
    val selectedRowIndices: Set<Int>,
    val sortKeys: List<SortKey>,
    val columnLayout: TableColumnLayout?,
)

/**
 * A single-column [Table] over a fixed model, driven by a table state, against a raw [JTable] with the same data,
 * selection, sort order and column layout.
 */
public object TableModelStateFixture : ComposableFixture<TableModelStateParameters, JTable>(
    initial =
        TableModelStateParameters(
            selectedRowIndices = setOf(0),
            sortKeys = emptyList(),
            columnLayout = TableColumnLayout(listOf(0), listOf(100)),
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TableModelStateParameters> =
        listOf(
            initial.copy(selectedRowIndices = setOf(1)),
            initial.copy(sortKeys = listOf(SortKey(0, SortOrder.DESCENDING))),
            initial.copy(columnLayout = TableColumnLayout(listOf(0), listOf(150))),
        )

    @Composable
    override fun Content(parameters: TableModelStateParameters): Unit =
        Content(
            parameters.selectedRowIndices,
            parameters.sortKeys,
            parameters.columnLayout,
        )

    /** Composes a [Table] over a fixed model with the state given. */
    @Composable
    public fun Content(
        selectedRowIndices: Set<Int>,
        sortKeys: List<SortKey>,
        columnLayout: TableColumnLayout?,
    ) {
        val model = remember { createModel() }
        val state = rememberTableState(selectedRowIndices, sortKeys, columnLayout)
        SideEffect {
            state.selectedRowIndices = selectedRowIndices
            state.sortKeys = sortKeys
            state.columnLayout = columnLayout
        }
        Table(
            model = model,
            state = state,
            sortable = true,
        )
    }

    /** Builds a [JTable] over the fixed model before any of the fixture's values are applied. */
    override fun createRawSwing(): JTable = JTable(createModel())

    private fun createModel(): TableModel =
        DefaultTableModel(arrayOf(arrayOf("Alpha"), arrayOf("Beta"), arrayOf("Gamma")), arrayOf("Name"))

    /** Applies the values of [parameters] to [component]: the retained row sorter, the selection and the layout. */
    override fun updateRawSwing(
        component: JTable,
        parameters: TableModelStateParameters,
    ) {
        val sorter = component.keepSorter()
        if (sorter.sortKeys != parameters.sortKeys) sorter.sortKeys = parameters.sortKeys
        component.selectModelRows(parameters.selectedRowIndices)
        parameters.columnLayout?.let { component.applyColumnLayout(it) }
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.TableColumnLayout
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.components.selection.rememberTableState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTable
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder

/**
 * The parameters [TableStateFixture] varies: the sort order and column layout of the table state it drives.
 */
public data class TableStateParameters(
    val sortKeys: List<SortKey>,
    val columnLayout: TableColumnLayout?,
)

/**
 * A single-column [Table] over rows, driven by a table state, against a raw [JTable] with the same data,
 * selection, sort order and column layout.
 */
public object TableStateFixture : ComposableFixture<TableStateParameters, JTable>(
    initial =
        TableStateParameters(
            sortKeys = emptyList(),
            columnLayout = TableColumnLayout(listOf(0), listOf(100)),
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TableStateParameters> =
        listOf(
            initial.copy(sortKeys = listOf(SortKey(0, SortOrder.DESCENDING))),
            initial.copy(columnLayout = TableColumnLayout(listOf(0), listOf(150))),
        )

    @Composable
    override fun Content(parameters: TableStateParameters): Unit =
        Content(
            parameters.sortKeys,
            parameters.columnLayout,
        )

    /** Composes a [Table] over fixed rows and selection, with the sort order and column layout given. */
    @Composable
    public fun Content(
        sortKeys: List<SortKey>,
        columnLayout: TableColumnLayout?,
    ) {
        val state = rememberTableState(setOf(0), sortKeys, columnLayout)
        SideEffect {
            state.selectedRowIndices = setOf(0)
            state.sortKeys = sortKeys
            state.columnLayout = columnLayout
        }
        Table(
            rows = listOf("Alpha", "Beta", "Gamma"),
            columns = { column("Name") { it } },
            state = state,
            sortable = true,
        )
    }

    /** Builds a [JTable] over the same rows before the selection, sort order and column layout apply. */
    override fun createRawSwing(): JTable = JTable(RowsModel(listOf("Alpha", "Beta", "Gamma")))

    /**
     * Applies the sort order and column layout of [parameters] to [component]: the retained row sorter, the column
     * layout and the selection.
     */
    override fun updateRawSwing(
        component: JTable,
        parameters: TableStateParameters,
    ) {
        val sorter = component.keepSorter()
        if (sorter.sortKeys != parameters.sortKeys) sorter.sortKeys = parameters.sortKeys
        parameters.columnLayout?.let { component.applyColumnLayout(it) }
        component.selectModelRows(setOf(0))
    }
}

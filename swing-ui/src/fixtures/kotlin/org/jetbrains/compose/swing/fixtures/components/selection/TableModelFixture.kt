// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.TableColumnLayout
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

/**
 * The parameters [TableModelFixture] varies: the names in the single column of the model.
 */
public data class TableModelParameters(
    val names: List<String>,
)

/**
 * A single-column [Table] over a model remembered for names, against a raw [JTable] over a fresh model
 * of the same names.
 */
public object TableModelFixture : ComposableFixture<TableModelParameters, JTable>(
    initial = TableModelParameters(names = listOf("Alpha", "Beta", "Gamma")),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TableModelParameters> =
        listOf(
            initial.copy(names = listOf("Delta", "Epsilon", "Zeta")),
        )

    @Composable
    override fun Content(parameters: TableModelParameters): Unit = Content(parameters.names)

    /** Composes a [Table] over a model remembered for [names]. */
    @Composable
    public fun Content(names: List<String>) {
        Table(
            model = remember(names) { tableModelOf(names) },
            onSelectionChange = {},
            sortable = true,
            onSortChange = {},
            onColumnLayoutChange = {},
        )
    }

    /**
     * Builds a bare [JTable] with listeners that mirror the fixed no-op callbacks, before any of the fixture's values
     * are applied.
     */
    override fun createRawSwing(): JTable =
        JTable().apply {
            selectionModel.addListSelectionListener(SelectionBridge(this) {})
            columnModel.addColumnModelListener(ColumnLayoutBridge(this) {})
        }

    private fun tableModelOf(names: List<String>): DefaultTableModel =
        DefaultTableModel(Array(names.size) { arrayOf(names[it]) }, arrayOf("Name"))

    /**
     * Applies the values of [parameters] to [component]: a fresh model of its data, the column layout the columns were
     * in before the swap put back onto the columns the model rebuilt, and the retained row sorter.
     */
    override fun updateRawSwing(
        component: JTable,
        parameters: TableModelParameters,
    ) {
        val columns = component.columnModel
        val retained =
            TableColumnLayout(
                modelIndices = List(columns.columnCount) { columns.getColumn(it).modelIndex },
                preferredWidths = List(columns.columnCount) { columns.getColumn(it).preferredWidth },
            )
        component.model = tableModelOf(parameters.names)
        component.applyColumnLayout(retained)
        component.keepSorter()
        component.keepSortBridge {}
    }
}

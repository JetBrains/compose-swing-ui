// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.DefaultListSelectionModel
import javax.swing.JTable
import javax.swing.event.ListSelectionListener
import javax.swing.event.RowSorterListener
import javax.swing.event.TableColumnModelListener
import javax.swing.table.DefaultTableColumnModel

/**
 * The parameters [TableListenerFixture] varies: the raw listeners notified of the selection, the sort
 * order and the column layout.
 */
public data class TableListenerParameters(
    val listSelectionListener: ListSelectionListener,
    val rowSorterListener: RowSorterListener?,
    val tableColumnModelListener: TableColumnModelListener?,
)

/**
 * A single-column [Table] over fixed rows, driven by raw listeners, against a raw [JTable] with the same data and
 * raw listeners.
 */
public object TableListenerFixture : ComposableFixture<TableListenerParameters, JTable>(
    initial =
        TableListenerParameters(
            listSelectionListener = {},
            rowSorterListener = {},
            tableColumnModelListener = IdleColumnModelListener(),
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TableListenerParameters> =
        listOf(
            initial.copy(listSelectionListener = {}),
            initial.copy(rowSorterListener = {}),
            initial.copy(tableColumnModelListener = IdleColumnModelListener()),
        )

    @Composable
    override fun Content(parameters: TableListenerParameters): Unit =
        Content(
            parameters.listSelectionListener,
            parameters.rowSorterListener,
            parameters.tableColumnModelListener,
        )

    /** Composes a [Table] over the fixed rows with the raw listeners given. */
    @Composable
    public fun Content(
        listSelectionListener: ListSelectionListener,
        rowSorterListener: RowSorterListener?,
        tableColumnModelListener: TableColumnModelListener?,
    ) {
        Table(
            rows = listOf("Alpha", "Beta", "Gamma"),
            columns = { column("Name") { it } },
            listSelectionListener = listSelectionListener,
            sortable = true,
            rowSorterListener = rowSorterListener,
            tableColumnModelListener = tableColumnModelListener,
        )
    }

    /** Builds a [JTable] over the fixed rows before any of the fixture's values are applied. */
    override fun createRawSwing(): JTable = JTable(RowsModel(listOf("Alpha", "Beta", "Gamma")))

    /** Applies the values of [parameters] to [component]: the retained row sorter and the listeners. */
    override fun updateRawSwing(
        component: JTable,
        parameters: TableListenerParameters,
    ) {
        val selection = component.selectionModel as DefaultListSelectionModel
        val columnModel = component.columnModel as DefaultTableColumnModel
        component.keepSorter()
        component.replaceSortListener(parameters.rowSorterListener)
        (
            selection.listSelectionListeners.firstNotNullOfOrNull { it as? InstalledSelectionListener }
                ?: InstalledSelectionListener(parameters.listSelectionListener)
                    .also { selection.addListSelectionListener(it) }
        ).delegate = parameters.listSelectionListener
        (
            columnModel.columnModelListeners.firstNotNullOfOrNull { it as? InstalledColumnModelListener }
                ?: InstalledColumnModelListener(parameters.tableColumnModelListener)
                    .also { columnModel.addColumnModelListener(it) }
        ).delegate = parameters.tableColumnModelListener
    }
}

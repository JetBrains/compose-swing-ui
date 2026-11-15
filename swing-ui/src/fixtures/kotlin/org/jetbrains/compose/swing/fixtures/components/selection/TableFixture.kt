// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.TableColumnLayout
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.components.defaultOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.styleable
import java.awt.Color
import javax.swing.DefaultListSelectionModel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableModel

/**
 * The parameters [TableFixture] varies: the rows, the declared selection, the table's behavior and look,
 * the header's reordering, and the callbacks that report the selection, the sort order and the column layout.
 */
public data class TableParameters(
    val rows: List<String>,
    val selectedRowIndices: Set<Int>?,
    val onSelectionChange: (Set<Int>) -> Unit,
    val selectionMode: Int,
    val sortable: Boolean,
    val onSortChange: (List<SortKey>) -> Unit,
    val rowFilter: RowFilter<in TableModel, in Int>?,
    val rowHeight: Int?,
    val autoResizeMode: Int,
    val fillsViewportHeight: Boolean,
    val onColumnLayoutChange: (TableColumnLayout) -> Unit,
    val reorderingAllowed: Boolean,
    val enabled: Boolean,
    val foreground: Color,
)

/**
 * A single-column [Table] over rows, with change callbacks and a [Table] header, against a raw [JTable] over
 * a table model with the same rows, selection model, row sorter and listeners.
 */
public object TableFixture : ComposableFixture<TableParameters, JTable>(
    initial =
        TableParameters(
            rows = listOf("Alpha", "Beta", "Gamma"),
            selectedRowIndices = setOf(0),
            onSelectionChange = {},
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
            sortable = true,
            onSortChange = {},
            rowFilter = null,
            rowHeight = 20,
            autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
            fillsViewportHeight = false,
            onColumnLayoutChange = {},
            reorderingAllowed = true,
            enabled = true,
            foreground = Color.BLUE,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TableParameters> =
        listOf(
            initial.copy(rows = listOf("Delta", "Epsilon", "Zeta")),
            initial.copy(selectedRowIndices = setOf(1)),
            initial.copy(onSelectionChange = {}),
            initial.copy(selectionMode = ListSelectionModel.SINGLE_SELECTION),
            initial.copy(sortable = false),
            initial.copy(onSortChange = {}),
            initial.copy(rowFilter = RowFilter.regexFilter("Alpha|Gamma|Delta|Zeta", 0)),
            initial.copy(rowHeight = 24),
            initial.copy(autoResizeMode = JTable.AUTO_RESIZE_OFF),
            initial.copy(fillsViewportHeight = true),
            initial.copy(onColumnLayoutChange = {}),
            initial.copy(reorderingAllowed = false),
            initial.copy(enabled = false),
            initial.copy(foreground = Color.RED),
        )

    @Composable
    override fun Content(parameters: TableParameters): Unit =
        Content(
            parameters.rows,
            parameters.selectedRowIndices,
            parameters.onSelectionChange,
            parameters.selectionMode,
            parameters.sortable,
            parameters.onSortChange,
            parameters.rowFilter,
            parameters.rowHeight,
            parameters.autoResizeMode,
            parameters.fillsViewportHeight,
            parameters.onColumnLayoutChange,
            parameters.reorderingAllowed,
            parameters.enabled,
            parameters.foreground,
        )

    /** Composes a [Table] of the single column of [rows], with the callbacks and header settings given. */
    @Composable
    public fun Content(
        rows: List<String>,
        selectedRowIndices: Set<Int>?,
        onSelectionChange: (Set<Int>) -> Unit,
        selectionMode: Int,
        sortable: Boolean,
        onSortChange: (List<SortKey>) -> Unit,
        rowFilter: RowFilter<in TableModel, in Int>?,
        rowHeight: Int?,
        autoResizeMode: Int,
        fillsViewportHeight: Boolean,
        onColumnLayoutChange: (TableColumnLayout) -> Unit,
        reorderingAllowed: Boolean,
        enabled: Boolean,
        foreground: Color,
    ) {
        Table(
            rows = rows,
            columns = { column("Name") { it } },
            modifier = SwingModifier.styleable { enabled(enabled) },
            style = { foreground(foreground) },
            selectedRowIndices = selectedRowIndices,
            onSelectionChange = onSelectionChange,
            selectionMode = selectionMode,
            sortable = sortable,
            onSortChange = onSortChange,
            rowFilter = rowFilter,
            rowHeight = rowHeight,
            autoResizeMode = autoResizeMode,
            fillsViewportHeight = fillsViewportHeight,
            onColumnLayoutChange = onColumnLayoutChange,
            content = { Header(reorderingAllowed = reorderingAllowed) },
        )
    }

    /** Builds a bare [JTable] before any of the fixture's values are applied. */
    override fun createRawSwing(): JTable = JTable().also { it.putClientProperty(ROW_HEIGHT, it.rowHeight) }

    /**
     * Applies the values of [parameters] to [component]: its model, a row sorter when sortable, the selection
     * and the callbacks that report changes. The selection and column layout listeners stay attached and do not
     * report the values applied here. The row sorter and its listener stay in place while sorting stays on, and a row
     * height that is withdrawn goes back to the one the table was created with.
     */
    override fun updateRawSwing(
        component: JTable,
        parameters: TableParameters,
    ) {
        val selection = component.selectionModel as DefaultListSelectionModel
        val selectionBridge =
            selection.listSelectionListeners.firstNotNullOfOrNull { it as? SelectionBridge }
                ?: SelectionBridge(component) {}.also { selection.addListSelectionListener(it) }
        val columnModel = component.columnModel as DefaultTableColumnModel
        val columnBridge =
            columnModel.columnModelListeners.firstNotNullOfOrNull { it as? ColumnLayoutBridge }
                ?: ColumnLayoutBridge(component) {}.also { columnModel.addColumnModelListener(it) }
        selectionBridge.onChange = parameters.onSelectionChange
        columnBridge.onChange = parameters.onColumnLayoutChange
        selectionBridge.writing = true
        columnBridge.writing = true
        try {
            val model = component.model
            if (model !is RowsModel || model.rows != parameters.rows) component.model = RowsModel(parameters.rows)
            if (parameters.sortable) {
                component.keepSorter(parameters.rowFilter)
                component.keepSortBridge(parameters.onSortChange)
            } else if (component.rowSorter != null) {
                component.rowSorter = null
            }
            component.setSelectionMode(parameters.selectionMode)
            parameters.selectedRowIndices?.let { component.selectModelRows(it) }
            component.rowHeight = parameters.rowHeight ?: component.defaultOf<Int>(ROW_HEIGHT)
            component.autoResizeMode = parameters.autoResizeMode
            component.fillsViewportHeight = parameters.fillsViewportHeight
            component.tableHeader.reorderingAllowed = parameters.reorderingAllowed
            component.isEnabled = parameters.enabled
            component.foreground = parameters.foreground
        } finally {
            selectionBridge.writing = false
            columnBridge.writing = false
        }
    }

    private const val ROW_HEIGHT = "fixtureRowHeight"
}

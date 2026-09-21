// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import org.jetbrains.compose.swing.components.selection.TableColumnLayout
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.RowSorter
import javax.swing.RowSorter.SortKey
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.RowSorterEvent
import javax.swing.event.RowSorterListener
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

/**
 * Selects the rows of the model that [indices] names. The indices are model rows, not the view rows a sorter or a
 * filter shows; a model row the view hides is left out. A selection that already holds exactly those rows is left
 * alone.
 */
internal fun JTable.selectModelRows(indices: Set<Int>) {
    val viewRows = indices.mapNotNullTo(sortedSetOf()) { convertRowIndexToView(it).takeIf { row -> row >= 0 } }
    if (selectedRows.toSet() == viewRows) return
    clearSelection()
    viewRows.forEach { addRowSelectionInterval(it, it) }
}

/**
 * Moves and sizes the columns to [layout]: the column at each position shows the model column that
 * [TableColumnLayout.modelIndices] names for it and has the width [TableColumnLayout.preferredWidths] gives for it.
 */
internal fun JTable.applyColumnLayout(layout: TableColumnLayout) {
    layout.modelIndices.forEachIndexed { position, modelIndex ->
        val from =
            (position until columnModel.columnCount).first { columnModel.getColumn(it).modelIndex == modelIndex }
        if (from != position) columnModel.moveColumn(from, position)
        columnModel.getColumn(position).preferredWidth = layout.preferredWidths[position]
    }
}

/**
 * The row sorter of this table, kept in place while sorting stays on so that a change that does not concern sorting
 * leaves the sorter, the sort order and the selection it holds alone. A sorter built for another [filter] or another
 * model, or none, is replaced by a new one for the current model and [filter].
 */
internal fun JTable.keepSorter(filter: RowFilter<in TableModel, in Int>? = null): RowSorter<*> {
    val sortFilterKey = "fixtureSortFilter"
    return rowSorter?.takeIf { it.model === model && getClientProperty(sortFilterKey) === filter }
        ?: TableRowSorter(model).also {
            it.rowFilter = filter
            rowSorter = it
            putClientProperty(sortFilterKey, filter)
        }
}

/** Replaces the listener this table's raw side gave its row sorter earlier with [next], or with none. */
internal fun JTable.replaceSortListener(next: RowSorterListener?) {
    val sorter = rowSorter ?: return
    (getClientProperty(SORT_LISTENER) as? RowSorterListener)?.let(sorter::removeRowSorterListener)
    next?.let(sorter::addRowSorterListener)
    putClientProperty(SORT_LISTENER, next)
}

/**
 * Has the current row sorter of this table report to [onChange]: the [SortBridge] already listening to it is
 * kept and handed [onChange], and a sorter no bridge listens to gets a new one.
 */
internal fun JTable.keepSortBridge(onChange: (List<SortKey>) -> Unit) {
    val sorter = rowSorter ?: return
    val bridge = getClientProperty(SORT_LISTENER) as? SortBridge
    if (bridge != null && bridge.sorter === sorter) {
        bridge.onChange = onChange
    } else {
        replaceSortListener(SortBridge(sorter, onChange))
    }
}

/**
 * The client-property key under which [replaceSortListener] keeps the [RowSorterListener] it last installed on the row
 * sorter, or removes the entry when none is installed; [keepSortBridge] reads it back to reuse a [SortBridge].
 */
private const val SORT_LISTENER = "fixtureSortListener"

/** A single-column table model over [rows] that remembers them, so that an unchanged list is not rebuilt. */
internal class RowsModel(
    val rows: List<String>,
) : DefaultTableModel(Array(rows.size) { arrayOf(rows[it]) }, arrayOf("Name")) {
    override fun isCellEditable(
        rowIndex: Int,
        columnIndex: Int,
    ): Boolean = false

    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
}

/** Reports the settled selection of [table] to [onChange] as model row indices, except while [writing]. */
internal class SelectionBridge(
    private val table: JTable,
    var onChange: (Set<Int>) -> Unit,
) : ListSelectionListener {
    var writing: Boolean = false

    override fun valueChanged(event: ListSelectionEvent) {
        if (!writing && !event.valueIsAdjusting) {
            onChange(table.selectedRows.map { table.convertRowIndexToModel(it) }.toSet())
        }
    }
}

/** Reports the sort order of [sorter] to [onChange] whenever it changes. */
internal class SortBridge(
    val sorter: RowSorter<*>,
    var onChange: (List<SortKey>) -> Unit,
) : RowSorterListener {
    override fun sorterChanged(event: RowSorterEvent) {
        if (event.type == RowSorterEvent.Type.SORT_ORDER_CHANGED) onChange(event.source.sortKeys)
    }
}

/** Reports the column order and widths of [table] to [onChange] whenever they change, except while [writing]. */
internal class ColumnLayoutBridge(
    private val table: JTable,
    var onChange: (TableColumnLayout) -> Unit,
) : TableColumnModelListener {
    var writing: Boolean = false

    override fun columnAdded(event: TableColumnModelEvent) = Unit

    override fun columnRemoved(event: TableColumnModelEvent) = Unit

    override fun columnMoved(event: TableColumnModelEvent) = report()

    override fun columnMarginChanged(event: ChangeEvent) = report()

    override fun columnSelectionChanged(event: ListSelectionEvent) = Unit

    /**
     * Reports the layout in the order the columns are shown: entry `i` of each list belongs to the column at view
     * position `i`, the model indices naming the model column it shows and the widths its preferred width.
     */
    private fun report() {
        if (writing) return
        val columns = table.columnModel
        onChange(
            TableColumnLayout(
                modelIndices = List(columns.columnCount) { columns.getColumn(it).modelIndex },
                preferredWidths = List(columns.columnCount) { columns.getColumn(it).preferredWidth },
            ),
        )
    }
}

/** A column model listener that ignores every event. */
internal class IdleColumnModelListener : TableColumnModelListener {
    override fun columnAdded(event: TableColumnModelEvent) = Unit

    override fun columnRemoved(event: TableColumnModelEvent) = Unit

    override fun columnMoved(event: TableColumnModelEvent) = Unit

    override fun columnMarginChanged(event: ChangeEvent) = Unit

    override fun columnSelectionChanged(event: ListSelectionEvent) = Unit
}

/**
 * The selection listener a fixture's raw side attaches once, and whose [delegate] it replaces on each update, so a
 * changed delegate registers no listener.
 */
internal class InstalledSelectionListener(
    var delegate: ListSelectionListener,
) : ListSelectionListener {
    override fun valueChanged(event: ListSelectionEvent) = delegate.valueChanged(event)
}

/**
 * The column model listener a fixture's raw side attaches once, and whose [delegate] it replaces on each update, so
 * a changed or null delegate registers no listener. It forwards column moves and margin changes only.
 */
internal class InstalledColumnModelListener(
    var delegate: TableColumnModelListener?,
) : TableColumnModelListener {
    override fun columnAdded(event: TableColumnModelEvent) = Unit

    override fun columnRemoved(event: TableColumnModelEvent) = Unit

    override fun columnMoved(event: TableColumnModelEvent) {
        delegate?.columnMoved(event)
    }

    override fun columnMarginChanged(event: ChangeEvent) {
        delegate?.columnMarginChanged(event)
    }

    override fun columnSelectionChanged(event: ListSelectionEvent) = Unit
}

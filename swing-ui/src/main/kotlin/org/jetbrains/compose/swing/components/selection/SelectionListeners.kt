package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.listener
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableColumnModelListener

/*
 * Internal listener helpers for the selection components' registration sites that the public typed
 * builders do not reach directly: a table's selection model and its column model. Each hands the
 * caller's own listener to the instance-based [listener] seam, so that seam's by-identity contract
 * applies unchanged - the instance the caller keeps is the instance attached and the instance detached.
 */

/**
 * Attaches a [ListSelectionListener] to a `JTable`'s `selectionModel` (where its selection events are
 * published), removing the same instance on detach. The public `listSelectionListener` builder targets
 * `JList`, so a table needs this selection-model-targeted helper.
 */
internal fun SwingModifier.tableSelectionListener(listener: ListSelectionListener): SwingModifier =
    listener<JTable, ListSelectionListener>(
        listener,
        { c, l -> c.selectionModel.addListSelectionListener(l) },
        { c, l -> c.selectionModel.removeListSelectionListener(l) },
    )

/**
 * Attaches a [TableColumnModelListener] to a `JTable`'s `columnModel` (where the order and the widths of
 * its columns are published), removing the same instance on detach. A table keeps one column model across
 * a structure change - the columns are rebuilt inside it - so the registration outlives every rebuild.
 */
internal fun SwingModifier.tableColumnModelListener(listener: TableColumnModelListener): SwingModifier =
    listener<JTable, TableColumnModelListener>(
        listener,
        { c, l -> c.columnModel.addColumnModelListener(l) },
        { c, l -> c.columnModel.removeColumnModelListener(l) },
    )

/**
 * Collects the selected indices of [this] selection model, in ascending order - the same set
 * `JList.getSelectedIndices`/`JTable.getSelectedRows` derive from a selection model. A list/table
 * selection listener's event source is the selection model, so this reads the settled selection from
 * the event without a reference to the owning component.
 */
internal fun ListSelectionModel.selectedIndices(): List<Int> {
    val min = minSelectionIndex
    if (min < 0) return emptyList()
    return (min..maxSelectionIndex).filter { isSelectedIndex(it) }
}

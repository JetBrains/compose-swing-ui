package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.listener
import javax.swing.JTable
import javax.swing.event.TableColumnModelListener

/*
 * Internal listener helper for a registration site the public typed builders do not reach: a table's
 * column model. It goes through the instance-based [listener] seam, so the instance the caller keeps is
 * the instance attached and the instance detached.
 */

/**
 * Attaches a [TableColumnModelListener] to a `JTable`'s `columnModel`, where the order and the widths
 * of its columns are published. A table keeps one column model across a structure change - the columns
 * are rebuilt inside it - so the registration outlives every rebuild.
 */
internal fun SwingModifier.tableColumnModelListener(listener: TableColumnModelListener): SwingModifier =
    listener<JTable, TableColumnModelListener>(
        listener,
        { c, l -> c.columnModel.addColumnModelListener(l) },
        { c, l -> c.columnModel.removeColumnModelListener(l) },
    )

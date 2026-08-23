package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.ModelSwapAware
import org.jetbrains.compose.swing.modifier.listener.SwappableModel
import org.jetbrains.compose.swing.modifier.listener.listener
import javax.swing.JTable
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumnModel

/*
 * Internal listener helper for a registration site the public typed builders do not reach: a table's
 * column model. It goes through the instance-based [listener] seam, so the instance the caller keeps is
 * the instance attached and the instance detached.
 */

/**
 * Attaches a [TableColumnModelListener] to a `JTable`'s `columnModel`, where the order and the widths
 * of its columns are published. The columns of a structure change are rebuilt inside whichever model is
 * current, and a table given another column model takes the registration with it.
 */
internal fun SwingModifier.tableColumnModelListener(listener: ColumnLayoutMirror): SwingModifier =
    listener(listener, TABLE_COLUMN_LISTENERS)

/** A column model listener whose own state describes the column model it is registered on. */
internal interface ColumnLayoutMirror :
    TableColumnModelListener,
    ModelSwapAware<TableColumnModel>

// A table publishes its column order and widths through the column model it holds, which a caller can
// replace.
private val TABLE_COLUMNS =
    SwappableModel<JTable, TableColumnModel, TableColumnModelListener>(
        property = "columnModel",
        modelType = TableColumnModel::class.java,
        model = JTable::getColumnModel,
        add = TableColumnModel::addColumnModelListener,
        remove = TableColumnModel::removeColumnModelListener,
    )

private val TABLE_COLUMN_LISTENERS =
    ListenerRegistration<JTable, ColumnLayoutMirror>(
        { table, mirror -> TABLE_COLUMNS.attachSettling(table, mirror, mirror::adoptModelSwap) },
        TABLE_COLUMNS::detach,
    )

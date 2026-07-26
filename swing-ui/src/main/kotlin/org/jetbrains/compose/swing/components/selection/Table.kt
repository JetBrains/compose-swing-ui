@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.AppliedValue
import org.jetbrains.compose.swing.AppliedWrite
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.constants.SelectionMode
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.declare
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.rememberAppliedValue
import org.jetbrains.compose.swing.rememberAppliedWrite
import org.jetbrains.compose.swing.userOnly
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableColumnModel
import javax.swing.table.TableModel

/**
 * A composable wrapper for `JTable` with declarative, data-driven rows and columns.
 *
 * [rows] are the row data; declare each column in [block] with its header, a value extractor, and
 * (optionally) in-place editing. Rows and columns are **data**: changing [rows] or the declared
 * columns rebuilds the table's model on recomposition. Selection is declared with [selectedRowIndices]
 * and reported through [onSelectionChange], expressed as the general multi-select shape so one
 * component covers all of [SelectionMode]'s modes. Place it in a [ScrollPane] to scroll and to show
 * the column header.
 *
 * ```
 * ScrollPane {
 *     content {
 *         Table(
 *             rows = people,
 *             selectedRowIndices = selection,
 *             onSelectionChange = { selection = it },
 *         ) {
 *             column("Name") { it.name }
 *             column("Age", isEditable = true, onCellEdit = { row, _, v -> update(row, v) }) { it.age }
 *         }
 *     }
 * }
 * ```
 *
 * A cell edit commits through the edited column's `onCellEdit`; the displayed value does not change
 * until the caller updates the backing state and the next composition supplies fresh [rows].
 * [onSelectionChange] reports the user's selection changes only, once per settled change - so
 * dragging across rows produces one callback at the end rather than one per row crossed, and
 * rendering new [rows] or new columns produces none. A declared selection is the composition's state and
 * is re-applied on every pass: it survives such a change (an index the current rows no longer cover is
 * dropped), and a user change the caller does not adopt does not stand. Undeclared, the selection is
 * the user's alone - never imposed, and kept across new rows and new columns all the same; where the new
 * rows are too few to hold it, the rows that fall outside them leave the selection and [onSelectionChange]
 * reports what is left of it.
 *
 * The order and the widths of the columns are the same kind of state, declared with [columnLayout] and
 * reported through [onColumnLayoutChange]: dragging a column header sideways reorders the columns and
 * dragging the divider between two headers resizes them, and each reaches [onColumnLayoutChange] with the
 * layout the columns are then in. New columns rebuild the layout from the declarations, so a declared
 * layout is re-applied on every pass and survives it, and an undeclared one - the user's own - is carried
 * across it as far as the new columns can hold it, with [onColumnLayoutChange] reporting what is left of
 * it where they cannot.
 *
 * @param rows the row data to display
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedRowIndices the selected row indices the caller declares; `null` - the default - leaves
 *   the selection to the user
 * @param onSelectionChange callback invoked when the user settles on a new row selection
 * @param selectionMode how many rows/ranges may be selected
 * @param columnLayout the column order and widths the caller declares; `null` - the default - leaves the
 *   column layout to the user
 * @param onColumnLayoutChange callback invoked when the user reorders or resizes the columns
 * @param block declares the columns; see [TableScope]
 */
@Composable
public fun <R> Table(
    rows: List<R>,
    modifier: SwingModifier = SwingModifier,
    selectedRowIndices: List<Int>? = null,
    onSelectionChange: (List<Int>) -> Unit = {},
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    columnLayout: TableColumnLayout? = null,
    onColumnLayoutChange: (TableColumnLayout) -> Unit = {},
    block: TableScope<R>.() -> Unit,
) {
    Table(
        rows = rows,
        listSelectionListener = rememberSettledSelectionListener(onSelectionChange),
        modifier = modifier,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        columnLayout = columnLayout,
        tableColumnModelListener = rememberColumnLayoutListener(onColumnLayoutChange),
        block = block,
    )
}

/**
 * A [Table] driven by raw listeners instead of the `onSelectionChange`/`onColumnLayoutChange` lambdas. The
 * selection listener observes the table's `selectionModel`, so it sees the adjusting events of a drag as
 * well as the settled one. A listener is notified of the user's own changes only, and of what a rebuild of
 * the rows or the columns took away from the user; each is removed on the same instance, so pass a stable
 * one (e.g. `remember {}`) to avoid churn.
 *
 * @param rows the row data to display
 * @param listSelectionListener the listener notified of the user's selection-model changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedRowIndices the selected row indices the caller declares; `null` - the default - leaves
 *   the selection to the user
 * @param selectionMode how many rows/ranges may be selected
 * @param columnLayout the column order and widths the caller declares; `null` - the default - leaves the
 *   column layout to the user
 * @param tableColumnModelListener the listener notified of the user's column reorders and resizes; `null`
 *   - the default - reports none of them
 * @param block declares the columns; see [TableScope]
 */
@Composable
public fun <R> Table(
    rows: List<R>,
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier = SwingModifier,
    selectedRowIndices: List<Int>? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    columnLayout: TableColumnLayout? = null,
    tableColumnModelListener: TableColumnModelListener? = null,
    block: TableScope<R>.() -> Unit,
) {
    val columns = TableScopeImpl<R>().apply(block).columns
    // The model this composition fills. It is adopted in the update block as well as handed to the
    // factory, because the node is recyclable: the table it hands to a later composition came with the
    // model of the content that built it, and rows refreshed into any other model reach no table.
    val model = remember { ColumnsTableModel<R>() }
    // The column layout keeps marking its own writes through a bare reentrancy counter - it is not the
    // mirrored property here - while the row selection gets a real mirror of its own.
    val appliedColumn = rememberAppliedWrite()
    val appliedSelection = rememberAppliedValue(selectedRowIndices)
    val userSelectionListener = rememberUserSelectionListener(appliedSelection, listSelectionListener)
    val columnChannel = rememberColumnLayoutChannel(tableColumnModelListener)
    val userColumnListener =
        remember(appliedColumn, columnChannel) { appliedColumn.userOnly(columnChannel.listener) }

    SwingNode(
        factory = { JTable(model) },
        update = {
            set(selectionMode) { mode ->
                narrowSelection(appliedSelection, selectedRowIndices, listSelectionListener) {
                    applySelectionMode(mode)
                }
            }
            // A table answers any model event by dropping its selection - a structure change rebuilds the
            // columns, and a data change spans every row - so the selection and the column layout that
            // should stand are restored as part of the same refresh that provokes the drop. Adopting this
            // composition's model is such an event and so belongs inside that refresh: a table takes only a
            // model other than the one it holds, which leaves this nothing to do for the composition the
            // factory already handed the model to, and makes it the swap that gives a reactivated child the
            // model it drives - with what the table was holding carried across it.
            reconcile {
                val table = this
                columnChannel.preserveAcross(columnModel, columnLayout, appliedColumn) {
                    installContent(appliedSelection, selectedRowIndices, listSelectionListener) {
                        table.model = model
                        model.refresh(rows, columns)
                    }
                }
            }
            // Run on every pass regardless of whether a selection is declared, so the set calls this makes
            // always number the same and no later slot in this block shifts when one flips to the other.
            // An undeclared (null) selection settles to itself: applySelection leaves the table alone for a
            // null declaration, so the selection is never imposed, overwritten, or re-asserted for it.
            declare(
                selectedRowIndices,
                appliedSelection,
                { selectedRows.toList() },
                { indices -> applySelection(this, indices) },
            )
            applyModifier(
                modifier
                    .tableSelectionListener(userSelectionListener)
                    .tableColumnModelListener(userColumnListener),
            )
        },
    )
}

/**
 * A [ListSelectionListener] that mirrors every settled selection into [applied] and forwards to [target]
 * whatever arrives outside one of [applied]'s own writes - the adjusting events of a drag as well as the
 * settled one, exactly as a caller's raw listener expects. Only the settled value is worth mirroring: an
 * adjusting one would invalidate this composition, and re-assert the declaration, before the user has let
 * go.
 */
@Composable
private fun rememberUserSelectionListener(
    applied: AppliedValue<List<Int>?>,
    target: ListSelectionListener,
): ListSelectionListener =
    remember(applied, target) {
        ListSelectionListener { event ->
            if (!event.valueIsAdjusting) applied.observed((event.source as ListSelectionModel).selectedIndices())
            if (!applied.isWriting) target.valueChanged(event)
        }
    }

/**
 * A composable wrapper for `JTable` driven by a caller-owned [TableModel].
 *
 * The [model] is displayed as-is: its own columns, values, and editability drive the table, and the
 * library never mutates it. Supplying a new [model] instance swaps it into the table on recomposition.
 * Selection is declared with [selectedRowIndices] and reported through [onSelectionChange], expressed as
 * the general multi-select shape so one component covers all of [SelectionMode]'s modes, and survives a
 * model swap whether declared or not. Place it in a [ScrollPane] to scroll and to show the column
 * header.
 *
 * ```
 * ScrollPane {
 *     content {
 *         Table(
 *             model = myTableModel,
 *             selectedRowIndices = selection,
 *             onSelectionChange = { selection = it },
 *         )
 *     }
 * }
 * ```
 *
 * [onSelectionChange] reports the user's selection changes only, once per settled change - so dragging
 * across rows produces one callback at the end rather than one per row crossed, and installing a new
 * [model] produces none. A declared selection is the composition's state and is re-applied on every pass,
 * so a user change the caller does not adopt does not stand; undeclared, the selection is the user's alone
 * and is never imposed - where the new model has too few rows to hold it, the rows that fall outside it
 * leave the selection and [onSelectionChange] reports what is left of it.
 *
 * The order and the widths of the columns are the same kind of state, declared with [columnLayout] and
 * reported through [onColumnLayoutChange]: dragging a column header sideways reorders the columns and
 * dragging the divider between two headers resizes them, and each reaches [onColumnLayoutChange] with the
 * layout the columns are then in. A new [model] rebuilds the columns from it, so a declared layout is
 * re-applied on every pass and survives the swap, and an undeclared one - the user's own - is carried
 * across it as far as the new model's columns can hold it, with [onColumnLayoutChange] reporting what is
 * left of it where they cannot.
 *
 * @param model the table model to display; owned by the caller and never mutated by the library
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedRowIndices the selected row indices the caller declares; `null` - the default - leaves
 *   the selection to the user
 * @param onSelectionChange callback invoked when the user settles on a new row selection
 * @param selectionMode how many rows/ranges may be selected
 * @param columnLayout the column order and widths the caller declares; `null` - the default - leaves the
 *   column layout to the user
 * @param onColumnLayoutChange callback invoked when the user reorders or resizes the columns
 */
@Composable
public fun Table(
    model: TableModel,
    modifier: SwingModifier = SwingModifier,
    selectedRowIndices: List<Int>? = null,
    onSelectionChange: (List<Int>) -> Unit = {},
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    columnLayout: TableColumnLayout? = null,
    onColumnLayoutChange: (TableColumnLayout) -> Unit = {},
) {
    Table(
        model = model,
        listSelectionListener = rememberSettledSelectionListener(onSelectionChange),
        modifier = modifier,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        columnLayout = columnLayout,
        tableColumnModelListener = rememberColumnLayoutListener(onColumnLayoutChange),
    )
}

/**
 * A model-driven [Table] driven by raw listeners instead of the
 * `onSelectionChange`/`onColumnLayoutChange` lambdas. The selection listener observes the table's
 * `selectionModel`, so it sees the adjusting events of a drag as well as the settled one. A listener is
 * notified of the user's own changes only, and of what a model swap took away from the user; each is
 * removed on the same instance, so pass a stable one (e.g. `remember {}`) to avoid churn.
 *
 * The [model] is displayed as-is and never mutated by the library, and the selection survives a model
 * swap whether declared or not.
 *
 * @param model the table model to display; owned by the caller and never mutated by the library
 * @param listSelectionListener the listener notified of the user's selection-model changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedRowIndices the selected row indices the caller declares; `null` - the default - leaves
 *   the selection to the user
 * @param selectionMode how many rows/ranges may be selected
 * @param columnLayout the column order and widths the caller declares; `null` - the default - leaves the
 *   column layout to the user
 * @param tableColumnModelListener the listener notified of the user's column reorders and resizes; `null`
 *   - the default - reports none of them
 */
@Composable
public fun Table(
    model: TableModel,
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier = SwingModifier,
    selectedRowIndices: List<Int>? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    columnLayout: TableColumnLayout? = null,
    tableColumnModelListener: TableColumnModelListener? = null,
) {
    val appliedColumn = rememberAppliedWrite()
    val appliedSelection = rememberAppliedValue(selectedRowIndices)
    val userSelectionListener = rememberUserSelectionListener(appliedSelection, listSelectionListener)
    val columnChannel = rememberColumnLayoutChannel(tableColumnModelListener)
    val userColumnListener =
        remember(appliedColumn, columnChannel) { appliedColumn.userOnly(columnChannel.listener) }

    SwingNode(
        factory = { JTable(model) },
        update = {
            set(selectionMode) { mode ->
                narrowSelection(appliedSelection, selectedRowIndices, listSelectionListener) {
                    applySelectionMode(mode)
                }
            }
            set(model) { newModel ->
                columnChannel.preserveAcross(columnModel, columnLayout, appliedColumn) {
                    installContent(appliedSelection, selectedRowIndices, listSelectionListener) {
                        this.model = newModel
                    }
                }
            }
            // The column layout is re-asserted every pass regardless of whether it moved, since it has no
            // mirror of its own to invalidate this composition on a move away from it.
            reconcile {
                appliedColumn.write { columnModel.applyColumnLayout(columnLayout) }
                columnChannel.adopt(columnModel.readColumnLayout())
            }
            // Run on every pass regardless of whether a selection is declared, so the set calls this makes
            // always number the same and no later slot in this block shifts when one flips to the other.
            // An undeclared (null) selection settles to itself: applySelection leaves the table alone for a
            // null declaration, so the selection is never imposed, overwritten, or re-asserted for it.
            declare(
                selectedRowIndices,
                appliedSelection,
                { selectedRows.toList() },
                { indices -> applySelection(this, indices) },
            )
            applyModifier(
                modifier
                    .tableSelectionListener(userSelectionListener)
                    .tableColumnModelListener(userColumnListener),
            )
        },
    )
}

/**
 * A stable [ListSelectionListener] that forwards each settled row selection to [onSelectionChange],
 * bridging a lambda-based [Table] overload to the raw-listener overload it delegates to.
 */
@Composable
private fun rememberSettledSelectionListener(onSelectionChange: (List<Int>) -> Unit): ListSelectionListener {
    val callback = rememberUpdatedState(onSelectionChange)
    // The table registers the listener on its selection model, so the event source is the model; read
    // the settled row selection from it once the value stops adjusting.
    return remember {
        ListSelectionListener { event ->
            if (!event.valueIsAdjusting) callback.value((event.source as ListSelectionModel).selectedIndices())
        }
    }
}

/**
 * One table's column-layout channel: the layout the caller and the table currently agree on, and the
 * [listener] through which the user's own reorders and resizes reach the caller's [target] listener.
 *
 * A table publishes a margin change for every width it derives from its columns' preferred widths as well
 * as for a preferred width a resize drag changed, and it derives those widths afresh at every layout pass.
 * An event is therefore news only when the layout it leaves behind differs from the one already agreed -
 * which is what keeps a window resize, which changes every column's width and no column's layout, silent.
 */
private class ColumnLayoutChannel(
    private val target: State<TableColumnModelListener?>,
) {
    private var agreed: TableColumnLayout? = null

    /** Reports the user's own column reorders and resizes. Install it on the table's column model. */
    val listener: TableColumnModelListener =
        object : TableColumnModelListener {
            // A column added or removed is the table rebuilding its columns, never a user gesture: no
            // header drag adds or removes one. A rebuild that a pass of the composition drives has the
            // layout it left behind settled by preserveAcross; one that a caller's own mutation of the
            // model drives has it settled by the next pass.
            override fun columnAdded(event: TableColumnModelEvent) = Unit

            override fun columnRemoved(event: TableColumnModelEvent) = Unit

            override fun columnMoved(event: TableColumnModelEvent) =
                report(event.source as TableColumnModel) { it.columnMoved(event) }

            override fun columnMarginChanged(event: ChangeEvent) =
                report(event.source as TableColumnModel) { it.columnMarginChanged(event) }

            // Column selection is a separate channel from the column layout and belongs to neither the
            // order nor the widths.
            override fun columnSelectionChanged(event: ListSelectionEvent) = Unit
        }

    /** Records [layout] as the layout the caller and the table agree on, so it is never reported back. */
    fun adopt(layout: TableColumnLayout) {
        agreed = layout
    }

    /**
     * Runs [install] - a change that rebuilds the table's columns and so drops the order and the widths
     * they were in - and puts the layout back afterwards: [declared] where the caller declares one, and
     * otherwise the layout the columns were in. A column layout is owned the way a selection is; see
     * [installNarrowing] for the rule and what follows from it.
     *
     * A column that no longer exists cannot hold the part of the layout that named it. Restoring the
     * layout has to follow the rebuild that creates the columns, so it runs as [applied]'s own write and
     * what the new columns were left holding is reported afterwards.
     *
     * [install] marks its own writes through [applied] too, so the losses it has to report itself still
     * reach the caller.
     */
    fun preserveAcross(
        columns: TableColumnModel,
        declared: TableColumnLayout?,
        applied: AppliedWrite,
        install: () -> Unit,
    ) {
        val retained = declared ?: columns.readColumnLayout()
        install()
        applied.write { columns.applyColumnLayout(retained) }
        val settled = columns.readColumnLayout()
        adopt(settled)
        // The columns are put back as this wrapper's own write, so nothing they publish carries the loss
        // out; a margin change over the model they are left in is how the caller hears what they were left
        // holding.
        if (declared == null && !settled.holds(retained)) {
            dispatchToCaller { target.value?.columnMarginChanged(ChangeEvent(columns)) }
        }
    }

    /**
     * Hands the layout [columns] are in to the caller's listener through [deliver], unless it is the one
     * already agreed on.
     */
    private fun report(
        columns: TableColumnModel,
        deliver: (TableColumnModelListener) -> Unit,
    ) {
        val layout = columns.readColumnLayout()
        if (layout == agreed) return
        agreed = layout
        target.value?.let(deliver)
    }
}

/** A [ColumnLayoutChannel] that keeps reporting to the latest [listener] without being rebuilt. */
@Composable
private fun rememberColumnLayoutChannel(listener: TableColumnModelListener?): ColumnLayoutChannel {
    val target = rememberUpdatedState(listener)
    return remember { ColumnLayoutChannel(target) }
}

/**
 * A stable [TableColumnModelListener] that forwards the layout the columns were left in to
 * [onColumnLayoutChange], bridging a lambda-based [Table] overload to the raw-listener overload it
 * delegates to. A column event's source is the column model, so the layout is read back from it.
 *
 * Only a reorder and a resize describe the layout, and those are the only events this listener is handed.
 */
@Composable
private fun rememberColumnLayoutListener(onColumnLayoutChange: (TableColumnLayout) -> Unit): TableColumnModelListener {
    val callback = rememberUpdatedState(onColumnLayoutChange)
    return remember {
        object : TableColumnModelListener {
            override fun columnAdded(event: TableColumnModelEvent) = Unit

            override fun columnRemoved(event: TableColumnModelEvent) = Unit

            override fun columnMoved(event: TableColumnModelEvent) = report(event.source)

            override fun columnMarginChanged(event: ChangeEvent) = report(event.source)

            override fun columnSelectionChanged(event: ListSelectionEvent) = Unit

            private fun report(source: Any) = callback.value((source as TableColumnModel).readColumnLayout())
        }
    }
}

/**
 * Declares the columns of a [Table]. Each [column] call appends one column, in call order.
 *
 * The receiver type parameter [R] is the table's row type; a column's [column] `value` extractor and
 * `onCellEdit` callback are expressed in terms of [R].
 */
public interface TableScope<R> {
    /**
     * Declares one column.
     *
     * @param header the column's header text
     * @param value extracts the cell value to display for a given row
     * @param isEditable whether this column's cells can be edited in place; `false` (the default)
     *   makes the whole column read-only
     * @param onCellEdit invoked when a cell in this column is edited and the edit is committed,
     *   receiving the row, the row index, and the newly entered value; pair it with an [isEditable] of
     *   `true` and update the backing state from here so the next composition reflects the edit
     */
    public fun column(
        header: String,
        isEditable: Boolean = false,
        onCellEdit: (row: R, rowIndex: Int, newValue: Any?) -> Unit = { _, _, _ -> },
        value: (row: R) -> Any?,
    )
}

/** One declared column: its header, value extractor, editability, and edit callback. */
private class ColumnDeclaration<R>(
    val header: String,
    val isEditable: Boolean,
    val onCellEdit: (row: R, rowIndex: Int, newValue: Any?) -> Unit,
    val value: (row: R) -> Any?,
)

private class TableScopeImpl<R> : TableScope<R> {
    val columns: MutableList<ColumnDeclaration<R>> = ArrayList()

    override fun column(
        header: String,
        isEditable: Boolean,
        onCellEdit: (row: R, rowIndex: Int, newValue: Any?) -> Unit,
        value: (row: R) -> Any?,
    ) {
        columns.add(ColumnDeclaration(header, isEditable, onCellEdit, value))
    }
}

/**
 * The [AbstractTableModel] backing a [Table]: it presents [rows] through the [columns]' value
 * extractors and routes a committed cell edit to the edited column's `onCellEdit`.
 *
 * [refresh] takes the latest rows and columns on every recomposition and fires the *narrowest*
 * change event the difference warrants: a structure change only when the column shape (count,
 * headers, editability) differs, a data change when only the rows differ, and nothing at all when
 * neither did, so a recomposition that changed no data leaves the table entirely alone.
 * An in-place edit never mutates [rows] itself, so the displayed value only changes once the caller
 * updates the backing state and a new composition supplies fresh rows.
 */
private class ColumnsTableModel<R> : AbstractTableModel() {
    private var rows: List<R> = emptyList()
    private var columns: List<ColumnDeclaration<R>> = emptyList()

    /** Pushes the latest data into the model, notifying the table of whatever actually changed. */
    fun refresh(
        rows: List<R>,
        columns: List<ColumnDeclaration<R>>,
    ) {
        val structureChanged = columnsDiffer(this.columns, columns)
        val rowsChanged = this.rows != rows
        this.rows = rows
        this.columns = columns
        when {
            structureChanged -> fireTableStructureChanged()
            rowsChanged -> fireTableDataChanged()
        }
    }

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column].header

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any? = columns[columnIndex].value(rows[rowIndex])

    override fun isCellEditable(
        rowIndex: Int,
        columnIndex: Int,
    ): Boolean = columns[columnIndex].isEditable

    override fun setValueAt(
        aValue: Any?,
        rowIndex: Int,
        columnIndex: Int,
    ) {
        columns[columnIndex].onCellEdit(rows[rowIndex], rowIndex, aValue)
    }

    private companion object {
        /**
         * Whether two column declarations describe a different table structure. The value/edit
         * lambdas are rebuilt every composition and so are never reference-equal; comparing only the
         * structural fields (count, header, editability) keeps a routine recomposition from being
         * mistaken for a structure change.
         */
        fun columnsDiffer(
            old: List<ColumnDeclaration<*>>,
            new: List<ColumnDeclaration<*>>,
        ): Boolean {
            if (old.size != new.size) return true
            return old.indices.any { i ->
                old[i].header != new[i].header || old[i].isEditable != new[i].isEditable
            }
        }
    }
}

/**
 * Puts the table's rows and columns in selection [mode] by writing it to the two selection models the mode
 * belongs to.
 *
 * A selection model narrows a selection only as far as the new mode forces: a mode that holds one row keeps
 * the first row that was selected, a wider mode keeps the whole selection, and the mode a model is already in
 * changes nothing - which matters because the mode a caller declares is the composition's state and arrives
 * again on every pass, including the one that reactivates a parked child onto the table it built. The table's
 * own `setSelectionMode` empties the selection before it changes either model, and the selection is not the
 * library's to empty: a list and a table declared alike answer a narrower mode alike.
 */
private fun JTable.applySelectionMode(
    @SelectionMode mode: Int,
) {
    selectionModel.selectionMode = mode
    columnModel.selectionModel.selectionMode = mode
}

/**
 * Gives the table new content through [install], keeping the rows [declared] names selected - or, where the
 * caller declared nothing, the rows the user had - and reporting to [target] the rows the new content is
 * too short to hold. See [installNarrowing].
 */
private fun JTable.installContent(
    applied: AppliedValue<List<Int>?>,
    declared: List<Int>?,
    target: ListSelectionListener,
    install: () -> Unit,
): Unit =
    applied.installNarrowing(
        declared = declared,
        selection = { selectedRows.toList() },
        apply = { indices -> applySelection(this, indices) },
        report = { lost -> reportLostRows(target, lost) },
        install = install,
    )

/**
 * Re-applies [indices] as the table's selected rows, dropping any index the current row count no
 * longer covers. A selection that already matches is left alone, so a recomposition that changed
 * nothing touches the table's selection model not at all, and a `null` declaration leaves it alone
 * entirely.
 */
private fun applySelection(
    table: JTable,
    indices: List<Int>?,
) {
    if (indices == null) return
    val rowCount = table.rowCount
    val valid = indices.filter { it in 0 until rowCount }
    if (table.selectedRows.toList() == valid) return
    val selectionModel = table.selectionModel
    selectionModel.valueIsAdjusting = true
    table.clearSelection()
    for (index in valid) selectionModel.addSelectionInterval(index, index)
    selectionModel.valueIsAdjusting = false
}

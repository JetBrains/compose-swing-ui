@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import javax.swing.JTable
import javax.swing.table.AbstractTableModel

/**
 * Declares the columns of a [Table]. Each [column] call appends one column, in call order.
 *
 * The receiver type parameter [R] is the table's row type; a column's [column] `value` extractor,
 * `cellContent` body and `onCellEdit` callback are expressed in terms of [R].
 *
 * @see javax.swing.table.TableColumn
 */
public sealed interface TableScope<R> {
    /**
     * Appends one fully specified column. Both forms of [column] funnel through here, each filling in
     * what a declaration leaves out.
     *
     * Marked [InternalSwingUiApi]; it may change without notice in any release.
     *
     * [column] carries the guidance on choosing each parameter.
     *
     * @param header the column's header text.
     * @param columnClass the class the table renders and edits this column's cells as.
     * @param isEditable whether the column's cells can be edited in place at all.
     * @param isCellEditable decides that per row while it is declared, or `null` to leave the answer to
     *   [isEditable].
     * @param isSortable whether a click on the header sorts by this column, while the table sorts at all.
     * @param comparator orders this column's values, or `null` to order them as a `TableRowSorter` orders
     *   a column of [columnClass].
     * @param minWidth the narrowest this column may be dragged or squeezed to, in pixels.
     * @param maxWidth the widest this column may be dragged or stretched to, in pixels.
     * @param onCellEdit invoked with the row, its index and the committed value when a cell is edited.
     * @param cellContent renders the cells through a composable body, or `null` to render them through
     *   the renderer the table picks by [columnClass].
     * @param value extracts the value to show for a row.
     */
    @InternalSwingUiApi
    @Suppress("LongParameterList")
    public fun addColumn(
        header: @Nls String,
        columnClass: Class<*>,
        isEditable: Boolean,
        isCellEditable: ((row: R, rowIndex: Int) -> Boolean)?,
        isSortable: Boolean,
        comparator: Comparator<Any?>?,
        minWidth: Int,
        maxWidth: Int,
        onCellEdit: (row: R, rowIndex: Int, newValue: Any?) -> Unit,
        cellContent: (@Composable TableCellScope.(row: R) -> Unit)?,
        value: (row: R) -> Any?,
    )
}

/**
 * Declares one column of [V] values, taking the column's class from the type [value] returns.
 *
 * The class is what the table renders and edits the column's cells as, so a `Boolean` column draws a
 * checkbox and hands [onCellEdit] a `Boolean`, and an `Int` column hands it an `Int`:
 *
 * ```
 * column("Name") { it.name }
 * column("Done", isEditable = true, onCellEdit = { row, _, done -> setDone(row, done) }) { it.isDone }
 * column("Owner", cellContent = { row -> Panel { Label(row.owner) } }) { it.owner }
 * ```
 *
 * Where the class is not the extractor's own, declare the column with the overload that takes it.
 *
 * @param header the column's header text
 * @param isEditable whether this column's cells can be edited in place; `false` (the default) makes the
 *   whole column read-only
 * @param isCellEditable decides per row whether that row's cell in this column can be edited in place,
 *   answering for every row of the column while it is declared; `null` (the default) leaves the answer to
 *   [isEditable]
 * @param isSortable whether a click on this column's header sorts the rows by it; `true` (the default)
 *   lets it, while the table sorts at all
 * @param comparator orders this column's cell values, or `null` (the default) to order them the way a
 *   `TableRowSorter` orders a column of [V]; applies while the table sorts at all. Hold one instance
 *   across passes: a comparator is compared by identity, so one built anew each pass - a capturing
 *   lambda, or a `compareBy` - sorts the rows again each pass
 * @param minWidth the narrowest this column may be dragged or squeezed to, in pixels; `15` by default,
 *   the minimum a `TableColumn` sets for itself
 * @param maxWidth the widest this column may be dragged or stretched to, in pixels; `Int.MAX_VALUE` -
 *   the default - leaves it unbounded
 * @param onCellEdit invoked when a cell in this column is edited and the edit is committed, receiving the
 *   row, the row index, and the newly entered value; pair it with an [isEditable] of `true` and update the
 *   backing state from here so the next composition reflects the edit. An edit still open on a cell a
 *   later composition no longer describes - its row gone, rewritten in place, or its column rebuilt - ends
 *   there and commits nothing. An edit follows its row across rows inserted or removed elsewhere; a
 *   composition that both adds and removes rows ends an edit on any row at or past the first row where the
 *   two lists differ, and one on any row at all where the table is sorted or filtered, since a `JTable`
 *   cancels an editor over a change it cannot map an index across
 * @param cellContent renders this column's cells through a composable body, against a [TableCellScope]
 *   and the row each cell belongs to; `null` (the default) renders a cell through the renderer the table
 *   picks by [V]
 * @param value extracts the cell value to display for a given row
 * @see javax.swing.table.TableColumn
 */
@Suppress("LongParameterList")
// One parameter per independent declarative aspect of a column, all but the header and the value extractor
// optional and named at the call site.
public inline fun <R, reified V : Any> TableScope<R>.column(
    header: @Nls String,
    isEditable: Boolean = false,
    noinline isCellEditable: ((row: R, rowIndex: Int) -> Boolean)? = null,
    isSortable: Boolean = true,
    comparator: Comparator<Any?>? = null,
    minWidth: Int = COLUMN_MIN_WIDTH,
    maxWidth: Int = COLUMN_MAX_WIDTH,
    noinline onCellEdit: (row: R, rowIndex: Int, newValue: V?) -> Unit = { _, _, _ -> },
    noinline cellContent: (@Composable TableCellScope.(row: R) -> Unit)? = null,
    noinline value: (row: R) -> V?,
) {
    val columnClass = V::class.javaObjectType
    addColumn(
        header = header,
        columnClass = columnClass,
        isEditable = isEditable,
        isCellEditable = isCellEditable,
        isSortable = isSortable,
        comparator = comparator,
        minWidth = minWidth,
        maxWidth = maxWidth,
        onCellEdit = { row, rowIndex, newValue -> onCellEdit(row, rowIndex, columnClass.cast(newValue)) },
        cellContent = cellContent,
        value = value,
    )
}

/**
 * Declares one column of [columnClass] values.
 *
 * A column's class is what the table renders and edits its cells as: a `Boolean` column draws a checkbox
 * and commits a `Boolean`, a `Number` column edits through a text field and commits a number of that
 * class. The overload without a class takes it from the type the value extractor returns and is the
 * ordinary way to declare a column; reach for this one where the class is not the extractor's own.
 *
 * @param header the column's header text
 * @param columnClass the class of the values this column holds
 * @param isEditable whether this column's cells can be edited in place; `false` (the default) makes the
 *   whole column read-only
 * @param isCellEditable decides per row whether that row's cell in this column can be edited in place,
 *   answering for every row of the column while it is declared; `null` (the default) leaves the answer to
 *   [isEditable]
 * @param isSortable whether a click on this column's header sorts the rows by it; `true` (the default)
 *   lets it, while the table sorts at all
 * @param comparator orders this column's cell values, or `null` (the default) to order them the way a
 *   `TableRowSorter` orders a column of [columnClass]; applies while the table sorts at all. Hold one
 *   instance across passes: a comparator is compared by identity, so one built anew each pass - a
 *   capturing lambda, or a `compareBy` - sorts the rows again each pass
 * @param minWidth the narrowest this column may be dragged or squeezed to, in pixels; `15` by default,
 *   the minimum a `TableColumn` sets for itself
 * @param maxWidth the widest this column may be dragged or stretched to, in pixels; `Int.MAX_VALUE` -
 *   the default - leaves it unbounded
 * @param onCellEdit invoked when a cell in this column is edited and the edit is committed, receiving the
 *   row, the row index, and the newly entered value; pair it with an [isEditable] of `true` and update the
 *   backing state from here so the next composition reflects the edit. An edit still open on a cell a
 *   later composition no longer describes - its row gone, rewritten in place, or its column rebuilt - ends
 *   there and commits nothing. An edit follows its row across rows inserted or removed elsewhere; a
 *   composition that both adds and removes rows ends an edit on any row at or past the first row where the
 *   two lists differ, and one on any row at all where the table is sorted or filtered, since a `JTable`
 *   cancels an editor over a change it cannot map an index across
 * @param cellContent renders this column's cells through a composable body, against a [TableCellScope]
 *   and the row each cell belongs to; `null` (the default) renders a cell through the renderer the table
 *   picks by [columnClass]
 * @param value extracts the cell value to display for a given row
 * @see javax.swing.table.TableColumn
 */
@Suppress("LongParameterList")
// One parameter per independent declarative aspect of a column, all but the header, the class and the
// value extractor optional and named at the call site.
public fun <R> TableScope<R>.column(
    header: @Nls String,
    columnClass: Class<*>,
    isEditable: Boolean = false,
    isCellEditable: ((row: R, rowIndex: Int) -> Boolean)? = null,
    isSortable: Boolean = true,
    comparator: Comparator<Any?>? = null,
    minWidth: Int = COLUMN_MIN_WIDTH,
    maxWidth: Int = COLUMN_MAX_WIDTH,
    onCellEdit: (row: R, rowIndex: Int, newValue: Any?) -> Unit = { _, _, _ -> },
    cellContent: (@Composable TableCellScope.(row: R) -> Unit)? = null,
    value: (row: R) -> Any?,
) {
    addColumn(
        header = header,
        columnClass = columnClass,
        isEditable = isEditable,
        isCellEditable = isCellEditable,
        isSortable = isSortable,
        comparator = comparator,
        minWidth = minWidth,
        maxWidth = maxWidth,
        onCellEdit = onCellEdit,
        cellContent = cellContent,
        value = value,
    )
}

/**
 * One declared column: its header, class, value extractor, editability, sorting rules, widths, edit
 * callback and cell body.
 */
@Suppress("LongParameterList")
// One field per declared aspect of a column; see TableScope.addColumn, which hands them over one for one.
internal class ColumnDeclaration<R>(
    val header: @Nls String,
    val columnClass: Class<*>,
    val isEditable: Boolean,
    val isCellEditable: ((row: R, rowIndex: Int) -> Boolean)?,
    val isSortable: Boolean,
    val comparator: Comparator<Any?>?,
    val minWidth: Int,
    val maxWidth: Int,
    val onCellEdit: (row: R, rowIndex: Int, newValue: Any?) -> Unit,
    val cellContent: (@Composable TableCellScope.(row: R) -> Unit)?,
    val value: (row: R) -> Any?,
)

internal class TableScopeImpl<R> : TableScope<R> {
    val columns: MutableList<ColumnDeclaration<R>> = ArrayList()

    @Suppress("LongParameterList")
    // The declaration this fills, parameter for parameter; see TableScope.addColumn.
    override fun addColumn(
        header: @Nls String,
        columnClass: Class<*>,
        isEditable: Boolean,
        isCellEditable: ((row: R, rowIndex: Int) -> Boolean)?,
        isSortable: Boolean,
        comparator: Comparator<Any?>?,
        minWidth: Int,
        maxWidth: Int,
        onCellEdit: (row: R, rowIndex: Int, newValue: Any?) -> Unit,
        cellContent: (@Composable TableCellScope.(row: R) -> Unit)?,
        value: (row: R) -> Any?,
    ) {
        columns.add(
            ColumnDeclaration(
                header = header,
                columnClass = columnClass,
                isEditable = isEditable,
                isCellEditable = isCellEditable,
                isSortable = isSortable,
                comparator = comparator,
                minWidth = minWidth,
                maxWidth = maxWidth,
                onCellEdit = onCellEdit,
                cellContent = cellContent,
                value = value,
            ),
        )
    }
}

/**
 * The [AbstractTableModel] backing a [Table]: it presents the rows it was last given through the
 * [columns]' value extractors and routes a committed cell edit to the edited column's `onCellEdit`.
 *
 * The model answers every read - row count, cell value, editability - from the rows it was last given,
 * which are held apart from any list the caller keeps, so what it reports is always what the table was
 * last told, and a read during paint touches no caller state. An in-place edit of the caller's list never
 * writes to them, so the displayed value only changes once the caller updates the backing state and a new
 * composition supplies fresh rows.
 *
 * [refresh] takes the latest rows and columns on every recomposition and fires the *narrowest*
 * change event the difference warrants: a structure change only when the column shape (count,
 * headers, classes, editability, cell bodies) differs, an insert, delete or update naming just the rows
 * that differ, and nothing at all when neither did, so a recomposition that changed no data leaves the
 * table entirely alone.
 */
internal class ColumnsTableModel<R> : AbstractTableModel() {
    /**
     * The rows the model last reported to the table, held apart from whatever list the caller declared
     * them from: a caller may keep that list and mutate it in place, and only rows standing apart from it
     * can tell the new contents from the old.
     */
    private var rows: List<R> = emptyList()
    private var columns: List<ColumnDeclaration<R>> = emptyList()

    /**
     * Pushes the latest data into the model, notifying the table of whatever actually changed.
     *
     * The incoming [rows] are compared against the ones the model last reported, and are adopted before
     * the event goes out: a table answering an insert or a delete reads the row count back off the model,
     * and has to find the count the event implies.
     *
     * The model takes ownership of [rows] and answers every read from it, so the caller has to hand over a
     * list nothing else can mutate.
     */
    fun refresh(
        table: JTable,
        rows: List<R>,
        columns: List<ColumnDeclaration<R>>,
    ) {
        val structureChanged = columnsDiffer(this.columns, columns)
        // The difference between the two row lists is worked out once, before the new one is adopted: it
        // is both what the table is told and what decides whether an edit survives being told it.
        val change = if (structureChanged) null else rowChange(this.rows, rows)
        // An editor commits by index, into whatever the model holds there by then, and nothing fired below
        // takes one off by itself.
        val standsAt = if (table.isEditing) editedRowAfter(table, structureChanged, change) else GONE
        if (table.isEditing && standsAt == GONE) table.endEdit()
        this.rows = rows
        this.columns = columns
        // A table that sorts follows a standing editor across the events fired below, re-pointing the
        // editing row itself through `JTable.restoreSortingEditingRow`; one that does not leaves it at
        // the index the editor was opened on, which a row arriving or leaving above it has left naming
        // another row, so the index the edited row moved to is written here. Without a sorter the
        // editor's view row is the model's, and the events below repaint the run it lies in, which
        // repositions the editor.
        if (table.isEditing && table.rowSorter == null) table.editingRow = standsAt
        // A structure change rebuilds the columns and repaints every cell on its own, which is every row
        // an insert, a delete or an update could still have to name.
        if (structureChanged) {
            fireTableStructureChanged()
            return
        }
        // The change goes out as the narrowest event that describes it. One that both moves the row count
        // and rewrites rows falls back to the wholesale change, which costs a full repaint and empties the
        // table's selection.
        if (change == null) return
        val head = change.head
        when {
            change.removed == 0 -> fireTableRowsInserted(head, head + change.added - 1)
            change.added == 0 -> fireTableRowsDeleted(head, head + change.removed - 1)
            change.removed == change.added -> fireTableRowsUpdated(head, head + change.added - 1)
            else -> fireTableDataChanged()
        }
    }

    /**
     * How [new] differs from [old], or `null` where it does not differ at all.
     *
     * The rows the two lists share as a leading and a trailing run are the rows that did not move or
     * change, and what lies between those runs is the whole of the difference: a run present in only one
     * of the lists was inserted or deleted, and one of equal length in both was edited in place.
     *
     * Two runs and their lengths are all this walks the lists for, and the list a pass declaring the same
     * rows hands over is the one already held, so such a pass compares nothing and allocates nothing.
     */
    private fun rowChange(
        old: List<R>,
        new: List<R>,
    ): RowChange? {
        if (old === new) return null
        val shared = minOf(old.size, new.size)
        var head = 0
        while (head < shared && old[head] == new[head]) head++
        var tail = 0
        while (tail < shared - head && old[old.lastIndex - tail] == new[new.lastIndex - tail]) tail++
        val removed = old.size - head - tail
        val added = new.size - head - tail
        return if (removed == 0 && added == 0) null else RowChange(head, removed, added)
    }

    /**
     * The model row the cell [table] is editing stands on once these declarations are adopted, or [GONE]
     * where the edit cannot follow it. Call it only while [table] is editing.
     *
     * [change] answers where the row went - a `null` change having left every row where it was - and
     * [GONE] where it was taken away or rewritten in place: the editor names a position, and rows are
     * told apart by equality here as everywhere else in this model, so the row that took its place is a
     * different row and a commit must not reach it.
     *
     * A structure change answers [GONE] on its own: it rebuilds the columns, the edited one among them.
     */
    private fun editedRowAfter(
        table: JTable,
        structureChanged: Boolean,
        change: RowChange?,
    ): Int {
        if (structureChanged) return GONE
        val editedRow = table.convertRowIndexToModel(table.editingRow)
        return change?.indexAfter(editedRow) ?: editedRow
    }

    /** The row [index] names, or `null` where the model holds no such row. */
    fun rowAt(index: Int): R? = rows.getOrNull(index)

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): @Nls String = columns[column].header

    // A table asks for a column's class once per cell, for the renderer and again for the editor, so the
    // answer is the one the declaration already carries rather than anything derived per call.
    override fun getColumnClass(columnIndex: Int): Class<*> = columns[columnIndex].columnClass

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any? = columns[columnIndex].value(rows[rowIndex])

    // A column that decides editability per row answers for every one of its rows, since that is what a
    // per-row answer is for; a column that does not is editable, or not, as a whole.
    override fun isCellEditable(
        rowIndex: Int,
        columnIndex: Int,
    ): Boolean {
        val column = columns[columnIndex]
        val perRow = column.isCellEditable ?: return column.isEditable
        return perRow(rows[rowIndex], rowIndex)
    }

    override fun setValueAt(
        aValue: Any?,
        rowIndex: Int,
        columnIndex: Int,
    ) {
        columns[columnIndex].onCellEdit(rows[rowIndex], rowIndex, aValue)
    }

    /**
     * One row list's difference from the one before it: [removed] rows taken out at [head] and [added] put
     * in there, with every row outside that run the one it already was.
     */
    private class RowChange(
        val head: Int,
        val removed: Int,
        val added: Int,
    ) {
        /**
         * Where the row that stood at model index [index] stands once this change is adopted, or [GONE]
         * where it was taken away or rewritten. Follows what `JTable.convertRowIndexToView` works out
         * for the row an editor stands on, save for the sorter's part of it: a row before the run keeps
         * its index, one inside it is gone, and one after it shifts by what the run added or removed.
         *
         * A change that both moves the row count and rewrites rows goes out as the wholesale change, which
         * names no run for anything to shift by, so only the rows before it are left where they were.
         */
        fun indexAfter(index: Int): Int =
            when {
                index < head -> index
                removed > 0 && added > 0 && removed != added -> GONE
                index < head + removed -> GONE
                else -> index + added - removed
            }
    }

    private companion object {
        /** The answer [RowChange.indexAfter] gives for a row the change leaves the model without. */
        const val GONE = -1

        /**
         * Whether two column declarations describe a different table structure. The value/edit/cell
         * lambdas are rebuilt every composition and so are never reference-equal; comparing only the
         * structural fields (count, header, class, editability, and whether the column composes its own
         * cells) keeps a routine recomposition from being mistaken for a structure change.
         *
         * A cell body counts because a structure change is what builds the columns a renderer is
         * installed on, so a column that takes one up or gives it up is a column the table has to build
         * again.
         */
        fun columnsDiffer(
            old: List<ColumnDeclaration<*>>,
            new: List<ColumnDeclaration<*>>,
        ): Boolean {
            if (old.size != new.size) return true
            return old.indices.any { i ->
                old[i].header != new[i].header ||
                    old[i].columnClass != new[i].columnClass ||
                    old[i].isEditable != new[i].isEditable ||
                    (old[i].cellContent == null) != (new[i].cellContent == null)
            }
        }
    }
}

/**
 * Puts onto the table's columns the widths each of [columns] says its own may be left at.
 *
 * A column the declarations no longer reach is one the table is about to rebuild, and is left alone.
 */
internal fun <R> JTable.applyDeclaredColumnWidths(columns: List<ColumnDeclaration<R>>) {
    for (position in 0 until columnModel.columnCount) {
        val column = columnModel.getColumn(position)
        val declaration = columns.getOrNull(column.modelIndex) ?: continue
        // A column holds each of these two widths inside the other, so the one that widens the range goes
        // first: written the other way round, a column would clamp the second width to the range the first
        // one left it in and settle on neither declared width.
        if (declaration.minWidth <= column.minWidth) {
            column.minWidth = declaration.minWidth
            column.maxWidth = declaration.maxWidth
        } else {
            column.maxWidth = declaration.maxWidth
            column.minWidth = declaration.minWidth
        }
    }
}

/**
 * The narrowest a column may be dragged or squeezed to unless its declaration says otherwise: what
 * `TableColumn`'s own constructor leaves a column of the width it also chooses holding. The constructor
 * writes the number as a literal, so there is no constant of Swing's own to name here instead.
 */
@PublishedApi
internal const val COLUMN_MIN_WIDTH: Int = 15

/**
 * The widest a column may be dragged or stretched to unless its declaration says otherwise: the
 * `Integer.MAX_VALUE` that `TableColumn`'s own constructor sets.
 */
@PublishedApi
internal const val COLUMN_MAX_WIDTH: Int = Int.MAX_VALUE

/**
 * Ends the edit this table is showing. An editor that answers `cancelCellEditing` by telling the table
 * takes itself off; one that does not is taken off here, so no editor is left standing over a cell the
 * table no longer names.
 */
internal fun JTable.endEdit() {
    cellEditor?.cancelCellEditing()
    if (isEditing) removeEditor()
}

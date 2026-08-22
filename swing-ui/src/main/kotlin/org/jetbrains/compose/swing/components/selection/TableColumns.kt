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
     */
    @InternalSwingUiApi
    @Suppress("LongParameterList")
    // The declaration this appends, parameter for parameter; see column for what each one means.
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
 * column("Owner", cellContent = { row -> FlowPanel { Label(row.owner) } }) { it.owner }
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
 *   `TableRowSorter` orders a column of [V]; applies while the table sorts at all
 * @param minWidth the narrowest this column may be dragged or squeezed to, in pixels
 * @param maxWidth the widest this column may be dragged or stretched to, in pixels
 * @param onCellEdit invoked when a cell in this column is edited and the edit is committed, receiving the
 *   row, the row index, and the newly entered value; pair it with an [isEditable] of `true` and update the
 *   backing state from here so the next composition reflects the edit
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
 *   `TableRowSorter` orders a column of [columnClass]; applies while the table sorts at all
 * @param minWidth the narrowest this column may be dragged or squeezed to, in pixels
 * @param maxWidth the widest this column may be dragged or stretched to, in pixels
 * @param onCellEdit invoked when a cell in this column is edited and the edit is committed, receiving the
 *   row, the row index, and the newly entered value; pair it with an [isEditable] of `true` and update the
 *   backing state from here so the next composition reflects the edit
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
 * headers, classes, editability, cell bodies) differs, a data change when only the rows differ, and
 * nothing at all when neither did, so a recomposition that changed no data leaves the table entirely alone.
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
     * The incoming [rows] are compared against the ones the model last reported, and a pass that finds
     * them different adopts the new list along with firing the event.
     *
     * The model takes ownership of [rows] and answers every read from it, so the caller has to hand over a
     * list nothing else can mutate.
     */
    fun refresh(
        rows: List<R>,
        columns: List<ColumnDeclaration<R>>,
    ) {
        val structureChanged = columnsDiffer(this.columns, columns)
        val rowsChanged = this.rows != rows
        if (rowsChanged) this.rows = rows
        this.columns = columns
        when {
            structureChanged -> fireTableStructureChanged()
            rowsChanged -> fireTableDataChanged()
        }
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

    private companion object {
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

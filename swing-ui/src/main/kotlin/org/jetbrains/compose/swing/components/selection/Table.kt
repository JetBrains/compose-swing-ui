@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.annotations.AutoResizeMode
import org.jetbrains.compose.swing.annotations.SelectionMode
import org.jetbrains.compose.swing.components.rememberDeclaredList
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.node.settleWhenDue
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.event.ListSelectionListener
import javax.swing.event.RowSorterListener
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableModel

/**
 * A grid the user reads, sorts, and selects in: a `JTable` reporting the selection, the sort order, and
 * the column layout the user leaves it in.
 *
 * [rows] are the row data; declare each column in [columns] with its header, a value extractor, and
 * (optionally) in-place editing. Rows and columns are **data**: changing [rows] or the declared
 * columns rebuilds the table's model on recomposition. Selection is declared with [selectedRowIndices]
 * and reported through [onSelectionChange], expressed as the general multi-select shape so one
 * component covers all of [SelectionMode]'s modes. Place it in a
 * [org.jetbrains.compose.swing.components.layout.ScrollPane] to scroll and to show the column header.
 *
 * ```
 * ScrollPane {
 *     Viewport {
 *         Table(
 *             rows = people,
 *             columns = {
 *                 column("Name") { it.name }
 *                 column("Age", onCellEdit = { row, _, v -> update(row, v) }) { it.age }
 *             },
 *             selectedRowIndices = selection,
 *             onSelectionChange = { selection = it },
 *         ) {
 *             Header(reorderingAllowed = false)
 *         }
 *     }
 * }
 * ```
 *
 * The trailing [content] configures the parts the table builds itself, such as its header; see
 * [TableContentScope].
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
 * A selected row is named by its index into [rows] - the model's own row space, the space
 * [TableColumnLayout.modelIndices] names its columns in - and never by the position it is drawn at:
 * sorting and filtering move where a row is shown and leave the row each index names alone.
 *
 * Sorting is off until [sortable] turns it on, as it is on a bare `JTable`. With it on, clicking a column
 * header sorts the rows by that column and [onSortChange] reports the order the click leaves them in, and
 * [rowFilter] decides which of them are shown at all. Each column brings its own `isSortable` and
 * `comparator` to that sorting. The order is the user's: it is never imposed, and new rows and new columns
 * keep it.
 *
 * Dragging a column header sideways reorders the columns and dragging the divider between two headers
 * resizes them, and each reaches [onColumnLayoutChange] with the layout the columns are then in. The layout
 * is the user's too. New columns rebuild the layout from the declarations, and the user's layout is carried
 * across the rebuild as far as the new columns can hold it, with [onColumnLayoutChange] reporting what is
 * left of it where they cannot. A column's own `minWidth` and `maxWidth` bound every width it can be left
 * at, and [autoResizeMode] decides how the columns share a width change between them.
 *
 * To set the order or the layout, drive the table with a [TableState]. To refuse a gesture, turn it off:
 * [sortable] or a column's `isSortable` for a sort, and the [TableContentScope.Header] for a reorder or a
 * resize.
 *
 * A column renders its cells through the renderer the table picks by the column's class until its
 * `cellContent` gives it a composable cell of its own. A table gives every one of its rows the same
 * height and never measures one by what its cells ask for, so a cell taller than the text a table sizes
 * its rows for is what [rowHeight] is for.
 *
 * @param rows the row data to display
 * @param columns declares the columns; see [TableScope]
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedRowIndices the indices into [rows] of the selected rows the caller declares; `null` - the
 *   default - leaves the selection to the user
 * @param onSelectionChange callback invoked with the indices into [rows] the user settles on
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [rows] declares and its column headers inert
 * @param onSortChange callback invoked with the order the user's header click leaves the rows in
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height of every row; `null` - the default - leaves it to the look and feel
 * @param autoResizeMode how the columns share out a change to the table's width;
 *   `AUTO_RESIZE_SUBSEQUENT_COLUMNS` - the default - takes the change out of the columns right of the
 *   one resized, while `AUTO_RESIZE_OFF` leaves every column its width and scrolls instead
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds; `false` - the default - leaves it as tall as its rows
 * @param onColumnLayoutChange callback invoked when the user reorders or resizes the columns
 * @param content configures the parts the table builds itself, or none when `null`; see [TableContentScope]
 * @see javax.swing.JTable
 */
@Composable
public fun <R> Table(
    rows: List<R>,
    columns: TableScope<R>.() -> Unit,
    modifier: SwingModifier = SwingModifier,
    selectedRowIndices: Set<Int>? = null,
    onSelectionChange: (Set<Int>) -> Unit = {},
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    onSortChange: (List<SortKey>) -> Unit = {},
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    onColumnLayoutChange: (TableColumnLayout) -> Unit = {},
    content: (@Composable TableContentScope.() -> Unit)? = null,
) {
    TableRowsImpl(
        rows = rows,
        listSelectionListener = settledRowSelectionListener(onSelectionChange),
        modifier = modifier,
        state = null,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        sortable = sortable,
        rowSorterListener = rememberSortKeysListener(onSortChange),
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        tableColumnModelListener = columnLayoutListener(onColumnLayoutChange),
        block = columns,
        content = content,
    )
}

/**
 * A [Table] driven by raw listeners instead of the `onSelectionChange`/`onSortChange`/`onColumnLayoutChange`
 * lambdas. The selection listener observes the table's `selectionModel`, so it sees the adjusting events of
 * a drag as well as the settled one. A listener is notified of the user's own changes only, and of what a
 * rebuild of the rows or the columns took away from the user; each is removed on the same instance, so pass
 * a stable one (e.g. `remember {}`) to avoid churn.
 *
 * A selection event is handed on with the table as its source, so the selection is read back from it the way
 * a list's is read back from the list. Its `firstIndex` and `lastIndex` stay the numbers the selection model
 * published - screen rows, which a sort order or a filter parts from the model rows [selectedRowIndices]
 * names - because renumbering a widget's own event would say something the widget never said. A loss is
 * the one event the widget did not number: the rows it names are the ones the user lost, so it carries
 * them as model rows, the only space that still describes a row the table may no longer show.
 *
 * @param rows the row data to display
 * @param columns declares the columns; see [TableScope]
 * @param listSelectionListener the listener notified of the user's selection-model changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedRowIndices the indices into [rows] of the selected rows the caller declares; `null` - the
 *   default - leaves the selection to the user
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [rows] declares and its column headers inert
 * @param rowSorterListener the listener notified of the user's sort-order changes; `null` - the default -
 *   reports none of them
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height of every row; `null` - the default - leaves it to the look and feel
 * @param autoResizeMode how the columns share out a change to the table's width;
 *   `AUTO_RESIZE_SUBSEQUENT_COLUMNS` - the default - takes the change out of the columns right of the
 *   one resized, while `AUTO_RESIZE_OFF` leaves every column its width and scrolls instead
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds; `false` - the default - leaves it as tall as its rows
 * @param tableColumnModelListener the listener notified of the user's column reorders and resizes; `null`
 *   - the default - reports none of them
 * @param content configures the parts the table builds itself, or none when `null`; see [TableContentScope]
 * @see javax.swing.JTable
 */
@Composable
public fun <R> Table(
    rows: List<R>,
    columns: TableScope<R>.() -> Unit,
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier = SwingModifier,
    selectedRowIndices: Set<Int>? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    rowSorterListener: RowSorterListener? = null,
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    tableColumnModelListener: TableColumnModelListener? = null,
    content: (@Composable TableContentScope.() -> Unit)? = null,
) {
    TableRowsImpl(
        rows = rows,
        listSelectionListener = listSelectionListener,
        modifier = modifier,
        state = null,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        sortable = sortable,
        rowSorterListener = rowSorterListener,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        tableColumnModelListener = tableColumnModelListener,
        block = columns,
        content = content,
    )
}

/**
 * The `JTable` both rows-driven [Table] overloads render, taking the listeners the lambda overload builds
 * and the raw overload is handed.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun <R> TableRowsImpl(
    rows: List<R>,
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier,
    state: TableState?,
    selectedRowIndices: Set<Int>?,
    @SelectionMode selectionMode: Int,
    sortable: Boolean,
    rowSorterListener: RowSorterListener?,
    rowFilter: RowFilter<in TableModel, in Int>?,
    rowHeight: Int?,
    @AutoResizeMode autoResizeMode: Int,
    fillsViewportHeight: Boolean,
    tableColumnModelListener: TableColumnModelListener?,
    noinline block: TableScope<R>.() -> Unit,
    noinline content: (@Composable TableContentScope.() -> Unit)?,
) {
    val declaredRows = rememberDeclaredList(rows)
    val heldSortKeys = state?.sortKeys
    val heldColumnLayout = state?.columnLayout
    val columns = TableScopeImpl<R>().apply(block).columns
    // The model this composition fills. It is handed to the factory so the table is built already
    // showing it, rather than a default model briefly standing in before the update block's own
    // set(model) call installs this one.
    val model = remember { ColumnsTableModel<R>() }
    // One cell composition per column that declares a composable cell, following the declarations pass
    // by pass. Each resolves the row a render names against the rows the model holds then.
    val cellCompositions = rememberTableCellCompositions(columns) { rowIndex -> model.rowAt(rowIndex) }

    TableNode(
        listSelectionListener = listSelectionListener,
        modifier = modifier,
        state = state,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        rowSorterListener = rowSorterListener,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        tableColumnModelListener = tableColumnModelListener,
        model = model,
        cellCompositions = cellCompositions,
        parts = content,
    ) { selectionMirror, columnMirror, sortChannel, columnChannel ->
        // The refresh that gives the table this composition's model, rows, columns and cell renderers,
        // named step by step, run when the rows, the columns or sortable change - the content it installs.
        // The selection it puts back is settled on every pass in TableNode, and the order and the layout a
        // state holds are applied there when assigned, so a change to them alone needs no refresh. The steps
        // nest instead of running in sequence: each wraps the ones that can undo what it is putting back, so
        // its own restore runs only once those have already run - which is why none of the four can be
        // pulled out as a set() of its own. The last line's nesting order is the whole contract; a step's own
        // comment says what it is guarding against.
        set(listOf(declaredRows, columns, sortable)) {
            val table = this

            // Adopting this composition's model belongs inside the refresh: a table takes only a model
            // other than the one it holds, which leaves this nothing to do for the composition the
            // factory already handed the model to, and makes it the swap that gives a reactivated child
            // the model it drives - with what the table was holding carried across it. A sorter is
            // welded to the model it was built for, so the one the table carries comes off ahead of the
            // swap. The columns a structure change built are the ones the declarations describe, and a
            // width they clamp is a width the layout is put back into afterwards, so the declared widths
            // are applied here, ahead of that restore.
            val swapInDeclaredContent = {
                sortChannel.unbindFrom(table, model)
                table.model = model
                model.refresh(table, declaredRows, columns)
                columnMirror.write { table.applyDeclaredColumnWidths(columns) }
            }

            // The sorter is built over the model the refresh leaves the table holding, so it is bound
            // outside the swap rather than in it.
            val preservingSortOrder = { refresh: () -> Unit ->
                sortChannel.preserveAcross(table, sortable, heldSortKeys, columns, refresh)
            }

            // A table drops its selection on a model event that spans it - a structure change rebuilds
            // the columns, and a wholesale data change covers every row - and taking a sorter on or off
            // empties it as well, so the selection that should stand is put back outside both.
            val preservingSelection = { refresh: () -> Unit ->
                installContent(selectionMirror, selectedRowIndices, listSelectionListener, refresh)
            }

            // A structure change drops the order and the widths the columns were in, so the layout that
            // should stand is put back outside everything that provokes one - and outside the
            // declarations, whose widths bound the layout that is put back.
            val preservingColumnLayout = { refresh: () -> Unit ->
                columnChannel.preserveAcross(table, heldColumnLayout, refresh)
            }

            preservingColumnLayout { preservingSelection { preservingSortOrder { swapInDeclaredContent() } } }
        }
    }
}

/**
 * A grid the user reads, sorts, and selects in, over a [TableModel] the caller owns: a `JTable`
 * reporting the selection, the sort order, and the column layout the user leaves it in.
 *
 * The [model] is displayed as-is: its own columns, values, and editability drive the table, and the
 * library never mutates it. Supplying a new [model] instance swaps it into the table on recomposition.
 * Selection is declared with [selectedRowIndices] and reported through [onSelectionChange], expressed as
 * the general multi-select shape so one component covers all of [SelectionMode]'s modes, and survives a
 * model swap whether declared or not. Place it in a
 * [org.jetbrains.compose.swing.components.layout.ScrollPane] to scroll and to show the column header.
 *
 * ```
 * ScrollPane {
 *     Viewport {
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
 * A selected row is named by its index in [model] - the model's own row space, the space
 * [TableColumnLayout.modelIndices] names its columns in - and never by the position it is drawn at:
 * sorting and filtering move where a row is shown and leave the row each index names alone.
 *
 * Sorting is off until [sortable] turns it on, as it is on a bare `JTable`. With it on, clicking a column
 * header sorts the rows by that column and [onSortChange] reports the order the click leaves them in, and
 * [rowFilter] decides which of them are shown at all. The order is the user's: it is never imposed, and a
 * new [model] keeps it.
 *
 * Dragging a column header sideways reorders the columns and dragging the divider between two headers
 * resizes them, and each reaches [onColumnLayoutChange] with the layout the columns are then in. The layout
 * is the user's too. A new [model] rebuilds the columns from it, and the user's layout is carried across
 * the swap as far as the new model's columns can hold it, with [onColumnLayoutChange] reporting what is
 * left of it where they cannot.
 *
 * To set the order or the layout, drive the table with a [TableState]. To refuse a gesture, turn it off:
 * [sortable] for a sort, and the [TableContentScope.Header] for a reorder or a resize.
 *
 * @param model the table model to display; owned by the caller and never mutated by the library
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedRowIndices the indices in [model] of the selected rows the caller declares; `null` - the
 *   default - leaves the selection to the user
 * @param onSelectionChange callback invoked with the indices in [model] the user settles on
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [model] holds them in and its column headers inert
 * @param onSortChange callback invoked with the order the user's header click leaves the rows in
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height of every row; `null` - the default - leaves it to the look and feel
 * @param autoResizeMode how the columns share out a change to the table's width;
 *   `AUTO_RESIZE_SUBSEQUENT_COLUMNS` - the default - takes the change out of the columns right of the
 *   one resized, while `AUTO_RESIZE_OFF` leaves every column its width and scrolls instead
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds; `false` - the default - leaves it as tall as its rows
 * @param onColumnLayoutChange callback invoked when the user reorders or resizes the columns
 * @param content configures the parts the table builds itself, or none when `null`; see [TableContentScope]
 * @see javax.swing.JTable
 */
@Composable
public fun Table(
    model: TableModel,
    modifier: SwingModifier = SwingModifier,
    selectedRowIndices: Set<Int>? = null,
    onSelectionChange: (Set<Int>) -> Unit = {},
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    onSortChange: (List<SortKey>) -> Unit = {},
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    onColumnLayoutChange: (TableColumnLayout) -> Unit = {},
    content: (@Composable TableContentScope.() -> Unit)? = null,
) {
    TableImpl(
        model = model,
        listSelectionListener = settledRowSelectionListener(onSelectionChange),
        modifier = modifier,
        state = null,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        sortable = sortable,
        rowSorterListener = rememberSortKeysListener(onSortChange),
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        tableColumnModelListener = columnLayoutListener(onColumnLayoutChange),
        content = content,
    )
}

/**
 * A model-driven [Table] driven by raw listeners instead of the
 * `onSelectionChange`/`onSortChange`/`onColumnLayoutChange` lambdas. The selection listener observes the
 * table's `selectionModel`, so it sees the adjusting events of a drag as well as the settled one. A listener
 * is notified of the user's own changes only, and of what a model swap took away from the user; each is
 * removed on the same instance, so pass a stable one (e.g. `remember {}`) to avoid churn.
 *
 * The [model] is displayed as-is and never mutated by the library, and the selection survives a model
 * swap whether declared or not.
 *
 * A selection event is handed on with the table as its source, numbered the same way as in the row-based
 * [Table] overload: `firstIndex` and `lastIndex` stay screen rows, and a loss is reported as a model row.
 *
 * @param model the table model to display; owned by the caller and never mutated by the library
 * @param listSelectionListener the listener notified of the user's selection-model changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedRowIndices the indices in [model] of the selected rows the caller declares; `null` - the
 *   default - leaves the selection to the user
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [model] holds them in and its column headers inert
 * @param rowSorterListener the listener notified of the user's sort-order changes; `null` - the default -
 *   reports none of them
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height of every row; `null` - the default - leaves it to the look and feel
 * @param autoResizeMode how the columns share out a change to the table's width;
 *   `AUTO_RESIZE_SUBSEQUENT_COLUMNS` - the default - takes the change out of the columns right of the
 *   one resized, while `AUTO_RESIZE_OFF` leaves every column its width and scrolls instead
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds; `false` - the default - leaves it as tall as its rows
 * @param tableColumnModelListener the listener notified of the user's column reorders and resizes; `null`
 *   - the default - reports none of them
 * @param content configures the parts the table builds itself, or none when `null`; see [TableContentScope]
 * @see javax.swing.JTable
 */
@Composable
public fun Table(
    model: TableModel,
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier = SwingModifier,
    selectedRowIndices: Set<Int>? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    rowSorterListener: RowSorterListener? = null,
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    tableColumnModelListener: TableColumnModelListener? = null,
    content: (@Composable TableContentScope.() -> Unit)? = null,
) {
    TableImpl(
        model = model,
        listSelectionListener = listSelectionListener,
        modifier = modifier,
        state = null,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        sortable = sortable,
        rowSorterListener = rowSorterListener,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        tableColumnModelListener = tableColumnModelListener,
        content = content,
    )
}

/**
 * The `JTable` both [Table] overloads render, taking the listeners the lambda overload builds and the
 * raw overload is handed. Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun TableImpl(
    model: TableModel,
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier,
    state: TableState?,
    selectedRowIndices: Set<Int>?,
    @SelectionMode selectionMode: Int,
    sortable: Boolean,
    rowSorterListener: RowSorterListener?,
    rowFilter: RowFilter<in TableModel, in Int>?,
    rowHeight: Int?,
    @AutoResizeMode autoResizeMode: Int,
    fillsViewportHeight: Boolean,
    tableColumnModelListener: TableColumnModelListener?,
    noinline content: (@Composable TableContentScope.() -> Unit)?,
) {
    val heldSortKeys = state?.sortKeys
    val heldColumnLayout = state?.columnLayout
    TableNode(
        listSelectionListener = listSelectionListener,
        modifier = modifier,
        state = state,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        rowSorterListener = rowSorterListener,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        tableColumnModelListener = tableColumnModelListener,
        model = model,
        cellCompositions = null,
        parts = content,
    ) { selectionMirror, _, sortChannel, columnChannel ->
        set(model) { newModel ->
            val table = this
            columnChannel.preserveAcross(table, heldColumnLayout) {
                installContent(selectionMirror, selectedRowIndices, listSelectionListener) {
                    sortChannel.preserveAcross(table, sortable, heldSortKeys) {
                        sortChannel.unbindFrom(table, newModel)
                        table.model = newModel
                    }
                }
            }
        }
        // Taking a sorter on or off empties the table's selection the way a new model does, so it is put
        // back through the same install. The columns are left where they are by either.
        set(sortable) { enabled ->
            val table = this
            installContent(selectionMirror, selectedRowIndices, listSelectionListener) {
                sortChannel.preserveAcross(table, enabled, heldSortKeys)
            }
        }
    }
}

/**
 * A [Table] driven by a [TableState] instead of a declared `selectedRowIndices` and change lambdas. The
 * state owns the selection, the sort order and the column layout: assigning one applies it to the table,
 * the user's own selecting, header clicks and column drags are written back into it, and it is where a row
 * is revealed from. The order and the layout it holds outlive the table component, so a table built again
 * over the same state starts on them.
 *
 * ```
 * val state = rememberTableState()
 *
 * ScrollPane {
 *     Viewport {
 *         Table(rows = people, columns = { column("Name") { it.name } }, state = state)
 *     }
 * }
 * Label("Selected: ${state.selectedRowIndices.size}")
 * ```
 *
 * @param rows the row data to display
 * @param columns declares the columns; see [TableScope]
 * @param state the hoistable state the table applies and reports its selection, sort order and column
 *   layout into; see [TableState]
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [rows] declares and its column headers inert
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height of every row; `null` - the default - leaves it to the look and feel
 * @param autoResizeMode how the columns share out a change to the table's width;
 *   `AUTO_RESIZE_SUBSEQUENT_COLUMNS` - the default - takes the change out of the columns right of the
 *   one resized, while `AUTO_RESIZE_OFF` leaves every column its width and scrolls instead
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds; `false` - the default - leaves it as tall as its rows
 * @param content configures the parts the table builds itself, or none when `null`; see [TableContentScope]
 * @see javax.swing.JTable
 */
@Composable
public fun <R> Table(
    rows: List<R>,
    columns: TableScope<R>.() -> Unit,
    state: TableState,
    modifier: SwingModifier = SwingModifier,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    content: (@Composable TableContentScope.() -> Unit)? = null,
) {
    TableRowsImpl(
        rows = rows,
        listSelectionListener = settledRowSelectionListener { indices -> state.selectedRowIndices = indices },
        modifier = modifier.tableStateBinding(state),
        state = state,
        selectedRowIndices = state.selectedRowIndices,
        selectionMode = selectionMode,
        sortable = sortable,
        rowSorterListener = rememberSortKeysListener { keys -> state.sortKeys = keys },
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        tableColumnModelListener = columnLayoutListener { layout -> state.columnLayout = layout },
        block = columns,
        content = content,
    )
}

/**
 * A model-driven [Table] driven by a [TableState] instead of a declared `selectedRowIndices` and change
 * lambdas. The state owns the selection, the sort order and the column layout: assigning one applies it to
 * the table, the user's own selecting, header clicks and column drags are written back into it, and it is
 * where a row is revealed from. The order and the layout it holds outlive the table component, so a table
 * built again over the same state starts on them.
 *
 * The [model] is displayed as-is and never mutated by the library; the selection survives a model swap.
 *
 * @param model the table model to display; owned by the caller and never mutated by the library
 * @param state the hoistable state the table applies and reports its selection, sort order and column
 *   layout into; see [TableState]
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [model] holds them in and its column headers inert
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height of every row; `null` - the default - leaves it to the look and feel
 * @param autoResizeMode how the columns share out a change to the table's width;
 *   `AUTO_RESIZE_SUBSEQUENT_COLUMNS` - the default - takes the change out of the columns right of the
 *   one resized, while `AUTO_RESIZE_OFF` leaves every column its width and scrolls instead
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds; `false` - the default - leaves it as tall as its rows
 * @param content configures the parts the table builds itself, or none when `null`; see [TableContentScope]
 * @see javax.swing.JTable
 */
@Composable
public fun Table(
    model: TableModel,
    state: TableState,
    modifier: SwingModifier = SwingModifier,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    content: (@Composable TableContentScope.() -> Unit)? = null,
) {
    TableImpl(
        model = model,
        listSelectionListener = settledRowSelectionListener { indices -> state.selectedRowIndices = indices },
        modifier = modifier.tableStateBinding(state),
        state = state,
        selectedRowIndices = state.selectedRowIndices,
        selectionMode = selectionMode,
        sortable = sortable,
        rowSorterListener = rememberSortKeysListener { keys -> state.sortKeys = keys },
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        tableColumnModelListener = columnLayoutListener { layout -> state.columnLayout = layout },
        content = content,
    )
}

/**
 * The `JTable` node every [Table] overload renders: all of it but the content, which [installContent]
 * declares - a rows-and-columns refresh in one family of overloads, the caller's own model in the other -
 * with [parts] composed under it. [installContent] is handed the [MirrorState]s mirroring the row selection
 * and the column layout, and the [RowSortChannel] and [ColumnLayoutChannel] that carry the sort order and the
 * column layout across whatever change [installContent] makes, since giving the table new content is one of
 * the changes that unsettles all four. They carry the order and the layout [state] holds where there is a
 * [state], and the table's own otherwise.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Composable
private inline fun TableNode(
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier,
    state: TableState?,
    selectedRowIndices: Set<Int>?,
    @SelectionMode selectionMode: Int,
    rowSorterListener: RowSorterListener?,
    rowFilter: RowFilter<in TableModel, in Int>?,
    rowHeight: Int?,
    @AutoResizeMode autoResizeMode: Int,
    fillsViewportHeight: Boolean,
    tableColumnModelListener: TableColumnModelListener?,
    model: TableModel,
    cellCompositions: TableCellCompositions<*>?,
    noinline parts: (@Composable TableContentScope.() -> Unit)?,
    crossinline installContent: SwingNodeUpdater<JTable>.(
        MirrorState<Set<Int>?>,
        MirrorState<TableColumnLayout?>,
        RowSortChannel,
        ColumnLayoutChannel,
    ) -> Unit,
) {
    val contentScope = remember { TableContentScopeImpl() }
    // The layout the columns are in is seeded null rather than from the declaration: a table has no columns
    // until it has a model, so there is no layout to mirror until the first refresh has built them.
    val columnMirror = rememberMirrorState<TableColumnLayout?>(null)
    val selectionMirror = rememberMirrorState(selectedRowIndices)
    val columnChannel = rememberColumnLayoutChannel(columnMirror, tableColumnModelListener)
    val sortChannel = rememberRowSortChannel(rowSorterListener)

    val tableModifier =
        modifier
            .userSelectionListener(selectionMirror, listSelectionListener)
            .tableColumnModelListener(columnChannel.listener)
            .tableRowHeight(rowHeight)
    SwingNode(
        factory = { JTable(model) },
        modifier =
            if (cellCompositions == null) tableModifier else tableModifier.composableColumnCells(cellCompositions),
        update = {
            applyMirror(selectionMirror)
            set(selectionMode) { mode ->
                narrowSelection(selectionMirror, selectedRowIndices, listSelectionListener) {
                    applySelectionMode(mode)
                }
            }
            set(autoResizeMode) { mode -> this.autoResizeMode = mode }
            set(fillsViewportHeight) { fills -> this.fillsViewportHeight = fills }
            installContent(selectionMirror, columnMirror, sortChannel, columnChannel)
            declareRowFilter(sortChannel, rowFilter, selectionMirror, selectedRowIndices, listSelectionListener)
            // The order and the layout a state holds are applied when they were assigned, and never put back
            // over the user's own change, which the channels stamp as they write it back. A caller's swap of
            // the column model is stamped as the layout the table holds, and reading the column mirror is
            // what brings the pass that puts the state's layout onto it.
            if (state != null) columnMirror.subscribe()
            settleWhenDue(
                if (
                    state != null &&
                    (state.sortKeys != sortChannel.appliedSortKeys || state.columnLayout != columnChannel.appliedLayout)
                ) {
                    HeldArrangement(state.sortKeys, state.columnLayout)
                } else {
                    null
                },
            ) { held ->
                sortChannel.applyHeld(held.sortKeys)
                columnChannel.applyHeld(this, held.columnLayout)
            }
        },
        childPlacement = TableRegions,
        content = { if (parts != null) contentScope.parts() },
    )
}

/** One due apply of what a [TableState] holds: the order to sort the rows by and the layout of the columns. */
private class HeldArrangement(
    val sortKeys: List<SortKey>,
    val columnLayout: TableColumnLayout?,
)

/**
 * Folds in the row height a `JTable` leaves to the UI delegate of its look and feel as a modifier
 * element, while the caller declares one and dropped the moment the declaration goes back to `null`.
 *
 * A `JTable` takes an explicit row height as the client's own and stops accepting the one its look and
 * feel installs, which is what makes a folded-in element outrank the look and feel while it stays in the
 * modifier; removing the element restores the height the table carried before it was folded in - the one
 * its look and feel chose - through the same capture-on-attach, restore-on-detach every modifier
 * property follows. Detaching on release, reuse and deactivate as well as on withdrawal is what gives a
 * parked table its look and feel's height back too.
 *
 * One element carries the height for every [Table] overload, since a modifier element's slot is the
 * class of the accessor written here and two accessors would be two slots writing one property.
 */
private val RowHeightProperty =
    ComponentPropertyDescriptor<JTable, Int>(
        name = "rowHeight",
        read = { it.rowHeight },
        write = { table, height -> table.rowHeight = height },
    )

private fun SwingModifier.tableRowHeight(rowHeight: Int?): SwingModifier =
    if (rowHeight == null) this else property(RowHeightProperty, rowHeight)

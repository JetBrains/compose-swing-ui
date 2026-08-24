@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.constants.AutoResizeMode
import org.jetbrains.compose.swing.constants.SelectionMode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.ModelSwapAware
import org.jetbrains.compose.swing.modifier.listener.SwappableModel
import org.jetbrains.compose.swing.modifier.propertyElement
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.AppliedWrite
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberAppliedValue
import org.jetbrains.compose.swing.node.rememberAppliedWrite
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.RowSorterListener
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableModel

/**
 * A composable wrapper for `JTable` with declarative, data-driven rows and columns.
 *
 * [rows] are the row data; declare each column in [block] with its header, a value extractor, and
 * (optionally) in-place editing. Rows and columns are **data**: changing [rows] or the declared
 * columns rebuilds the table's model on recomposition. Selection is declared with [selectedRowIndices]
 * and reported through [onSelectionChange], expressed as the general multi-select shape so one
 * component covers all of [SelectionMode]'s modes. Place it in a
 * [org.jetbrains.compose.swing.components.layout.ScrollPane] to scroll and to show the column header.
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
 * A selected row is named by its index into [rows] - the model's own row space, the space
 * [TableColumnLayout.modelIndices] names its columns in - and never by the position it is drawn at:
 * sorting and filtering move where a row is shown and leave the row each index names alone.
 *
 * Sorting is off until [sortable] turns it on, as it is on a bare `JTable`. With it on, clicking a column
 * header sorts the rows by that column, [sortKeys] declares the order they are in and [onSortChange]
 * reports the order a header click leaves them in, and [rowFilter] decides which of them are shown at all.
 * A declared order is the composition's state and is re-applied on every pass, so a header click the caller
 * does not adopt does not stand; undeclared, the order is the user's alone and is never imposed. Each
 * column brings its own `isSortable` and `comparator` to that sorting.
 *
 * The order and the widths of the columns are the same kind of state, declared with [columnLayout] and
 * reported through [onColumnLayoutChange]: dragging a column header sideways reorders the columns and
 * dragging the divider between two headers resizes them, and each reaches [onColumnLayoutChange] with the
 * layout the columns are then in. New columns rebuild the layout from the declarations, so a declared
 * layout is re-applied on every pass and survives it, and an undeclared one - the user's own - is carried
 * across it as far as the new columns can hold it, with [onColumnLayoutChange] reporting what is left of
 * it where they cannot. A column's own `minWidth` and `maxWidth` bound every width it can be left at, a
 * drag's as much as a declaration's, and [autoResizeMode] decides how the columns share a width change
 * between them.
 *
 * A column renders its cells through the renderer the table picks by the column's class until its
 * `cellContent` gives it a composable cell of its own. A table gives every one of its rows the same
 * height and never measures one by what its cells ask for, so a cell taller than the text a table sizes
 * its rows for is what [rowHeight] is for.
 *
 * @param rows the row data to display
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedRowIndices the indices into [rows] of the selected rows the caller declares; `null` - the
 *   default - leaves the selection to the user
 * @param onSelectionChange callback invoked with the indices into [rows] the user settles on
 * @param selectionMode how many rows/ranges may be selected
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [rows] declares and its column headers inert
 * @param sortKeys the sort order the caller declares; `null` - the default - leaves the order to the user
 * @param onSortChange callback invoked with the order the user's header click leaves the rows in
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height in pixels of every row; `null` - the default - leaves it to the look and feel
 * @param autoResizeMode how the columns share out a change to the table's width
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds
 * @param columnLayout the column order and widths the caller declares; `null` - the default - leaves the
 *   column layout to the user
 * @param onColumnLayoutChange callback invoked when the user reorders or resizes the columns
 * @param block declares the columns; see [TableScope]
 * @see javax.swing.JTable
 */
@Composable
public fun <R> Table(
    rows: List<R>,
    modifier: SwingModifier = SwingModifier,
    selectedRowIndices: Set<Int>? = null,
    onSelectionChange: (Set<Int>) -> Unit = {},
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    sortKeys: List<SortKey>? = null,
    onSortChange: (List<SortKey>) -> Unit = {},
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    columnLayout: TableColumnLayout? = null,
    onColumnLayoutChange: (TableColumnLayout) -> Unit = {},
    block: TableScope<R>.() -> Unit,
) {
    Table(
        rows = rows,
        listSelectionListener = settledSelectionListener(onSelectionChange),
        modifier = modifier,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        sortable = sortable,
        sortKeys = sortKeys,
        rowSorterListener = rememberSortKeysListener(onSortChange),
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        columnLayout = columnLayout,
        tableColumnModelListener = columnLayoutListener(onColumnLayoutChange),
        block = block,
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
 * @param listSelectionListener the listener notified of the user's selection-model changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedRowIndices the indices into [rows] of the selected rows the caller declares; `null` - the
 *   default - leaves the selection to the user
 * @param selectionMode how many rows/ranges may be selected
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [rows] declares and its column headers inert
 * @param sortKeys the sort order the caller declares; `null` - the default - leaves the order to the user
 * @param rowSorterListener the listener notified of the user's sort-order changes; `null` - the default -
 *   reports none of them
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height in pixels of every row; `null` - the default - leaves it to the look and feel
 * @param autoResizeMode how the columns share out a change to the table's width
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds
 * @param columnLayout the column order and widths the caller declares; `null` - the default - leaves the
 *   column layout to the user
 * @param tableColumnModelListener the listener notified of the user's column reorders and resizes; `null`
 *   - the default - reports none of them
 * @param block declares the columns; see [TableScope]
 * @see javax.swing.JTable
 */
@Composable
public fun <R> Table(
    rows: List<R>,
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier = SwingModifier,
    selectedRowIndices: Set<Int>? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    sortKeys: List<SortKey>? = null,
    rowSorterListener: RowSorterListener? = null,
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    columnLayout: TableColumnLayout? = null,
    tableColumnModelListener: TableColumnModelListener? = null,
    block: TableScope<R>.() -> Unit,
) {
    val columns = TableScopeImpl<R>().apply(block).columns
    // The model this composition fills. It is handed to the factory so the table is built already
    // showing it, rather than a default model briefly standing in before the update block's own
    // set(model) call installs this one.
    val model = remember { ColumnsTableModel<R>() }
    // One cell island per column that declares a composable cell, following the declarations pass by
    // pass. Each island resolves the row a stamp names against the rows the model holds then.
    val cellIslands = rememberTableCellIslands(columns) { rowIndex -> model.rowAt(rowIndex) }

    TableNode(
        listSelectionListener = listSelectionListener,
        modifier = modifier,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        sortKeys = sortKeys,
        rowSorterListener = rowSorterListener,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        tableColumnModelListener = tableColumnModelListener,
        model = model,
        cellIslands = cellIslands,
    ) { appliedSelection, appliedColumn, sortChannel, columnChannel ->
        // The refresh that gives the table this composition's model, rows, columns and cell renderers,
        // named step by step. The steps nest instead of running in sequence: each wraps the ones that can
        // undo what it is putting back, so its own restore runs only once those have already run - which
        // is why none of the four can be pulled out as an independent set(). The last line's nesting order
        // is the whole contract; a step's own comment says what it is guarding against.
        reconcile {
            val table = this

            // Adopting this composition's model belongs inside the refresh: a table takes only a model
            // other than the one it holds, which leaves this nothing to do for the composition the
            // factory already handed the model to, and makes it the swap that gives a reactivated child
            // the model it drives - with what the table was holding carried across it. A sorter is
            // welded to the model it was built for, so the one the table carries comes off ahead of the
            // swap. The columns a structure change built are the ones the declarations describe, and a
            // width they clamp is a width the layout is put back into afterwards, so the declared widths
            // are applied here, ahead of that restore.
            fun swapInDeclaredContent() {
                sortChannel.unbindFrom(table, model)
                table.model = model
                model.refresh(rows, columns)
                appliedColumn.write { table.applyDeclaredColumnWidths(columns) }
            }

            // The sorter is built over the model the refresh leaves the table holding, so it is bound
            // outside the swap rather than in it.
            fun preservingSortOrder(refresh: () -> Unit) =
                sortChannel.preserveAcross(table, sortable, sortKeys, columns, refresh)

            // A table answers any model event by dropping its selection - a structure change rebuilds
            // the columns, and a data change spans every row - and taking a sorter on or off empties it
            // as well, so the selection that should stand is put back outside both.
            fun preservingSelection(refresh: () -> Unit) =
                installContent(appliedSelection, selectedRowIndices, listSelectionListener, refresh)

            // A structure change drops the order and the widths the columns were in, so the layout that
            // should stand is put back outside everything that provokes one - and outside the
            // declarations, whose widths bound the layout that is put back.
            fun preservingColumnLayout(refresh: () -> Unit) =
                columnChannel.preserveAcross(columnModel, columnLayout, appliedColumn, refresh)

            preservingColumnLayout { preservingSelection { preservingSortOrder { swapInDeclaredContent() } } }
        }
    }
}

/**
 * Installs a [ListSelectionListener] on a `JTable`'s `selectionModel` that mirrors every settled selection
 * into [applied] and forwards to [target] whatever arrives outside one of that mirror's own writes - the
 * adjusting events of a drag as well as the settled one, exactly as a caller's raw listener expects. Only
 * the settled value is worth mirroring: an adjusting one would invalidate this composition, and re-assert
 * the declaration, before the user has let go.
 *
 * A table publishes its selection through its selection model, which knows only the rows on screen; the
 * node this installs reads the table straight off the modifier chain it is attached to, so the event handed
 * on to the target is sourced at the table and the selection it carries can be read back off it the way a
 * list's is read back from the list.
 */
private fun SwingModifier.userSelectionListener(
    applied: AppliedValue<Set<Int>?>,
    target: ListSelectionListener,
): SwingModifier = this then UserSelectionListenerElement(applied, target)

// A table publishes its selection through the selection model it holds, which a caller can replace.
private val TABLE_SELECTION =
    SwappableModel<JTable, ListSelectionModel, ListSelectionListener>(
        property = "selectionModel",
        modelType = ListSelectionModel::class.java,
        model = JTable::getSelectionModel,
        add = ListSelectionModel::addListSelectionListener,
        remove = ListSelectionModel::removeListSelectionListener,
    )

/**
 * The additive [SwingModifier.NodeElement] backing [userSelectionListener].
 *
 * Both halves are compared by identity, so this is not a data class: the node forwards to [target]
 * itself, and a caller's listener may carry an `equals` of its own - a function reference does - under
 * which two listeners the node must tell apart compare equal. The element would skip, and the node
 * would keep forwarding to the listener the caller replaced.
 */
private class UserSelectionListenerElement(
    val applied: AppliedValue<Set<Int>?>,
    val target: ListSelectionListener,
) : SwingModifier.NodeElement<JTable, UserSelectionListenerElement.Node>() {
    override fun equals(other: Any?): Boolean =
        other is UserSelectionListenerElement && applied === other.applied && target === other.target

    override fun hashCode(): Int = 31 * System.identityHashCode(applied) + System.identityHashCode(target)

    override val targetType: Class<JTable> get() = JTable::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node(applied)

    override fun update(node: Node) {
        node.applied = applied
        node.target = target
    }

    /**
     * The node takes the mirror at creation because its listener settles against it while attaching,
     * before the first update lands. Both halves are pushed on every pass and read when an event fires,
     * so a table mirrors into the [AppliedValue] and forwards to the listener the composition declares
     * now.
     */
    class Node(
        var applied: AppliedValue<Set<Int>?>,
    ) : SwingModifier.Node<JTable>() {
        var target: ListSelectionListener = ListSelectionListener {}

        private val listener =
            object : ListSelectionListener, ModelSwapAware {
                override fun valueChanged(event: ListSelectionEvent) {
                    val table = component
                    if (!event.valueIsAdjusting) applied.observed(table.selectedModelRows())
                    if (!applied.isWriting) {
                        target.valueChanged(
                            ListSelectionEvent(table, event.firstIndex, event.lastIndex, event.valueIsAdjusting),
                        )
                    }
                }

                override fun adoptModelSwap(model: Any) {
                    applied.observed(component.selectedModelRows())
                }
            }

        override fun onAttach(): Unit = TABLE_SELECTION.attach(component, listener)

        override fun onDetach(): Unit = TABLE_SELECTION.detach(component, listener)
    }
}

/**
 * A composable wrapper for `JTable` driven by a caller-owned [TableModel].
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
 * A selected row is named by its index in [model] - the model's own row space, the space
 * [TableColumnLayout.modelIndices] names its columns in - and never by the position it is drawn at:
 * sorting and filtering move where a row is shown and leave the row each index names alone.
 *
 * Sorting is off until [sortable] turns it on, as it is on a bare `JTable`. With it on, clicking a column
 * header sorts the rows by that column, [sortKeys] declares the order they are in and [onSortChange]
 * reports the order a header click leaves them in, and [rowFilter] decides which of them are shown at all.
 * A declared order is the composition's state and is re-applied on every pass, so a header click the caller
 * does not adopt does not stand; undeclared, the order is the user's alone and is never imposed.
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
 * @param selectedRowIndices the indices in [model] of the selected rows the caller declares; `null` - the
 *   default - leaves the selection to the user
 * @param onSelectionChange callback invoked with the indices in [model] the user settles on
 * @param selectionMode how many rows/ranges may be selected
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [model] holds them in and its column headers inert
 * @param sortKeys the sort order the caller declares; `null` - the default - leaves the order to the user
 * @param onSortChange callback invoked with the order the user's header click leaves the rows in
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height in pixels of every row; `null` - the default - leaves it to the look and feel
 * @param autoResizeMode how the columns share out a change to the table's width
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds
 * @param columnLayout the column order and widths the caller declares; `null` - the default - leaves the
 *   column layout to the user
 * @param onColumnLayoutChange callback invoked when the user reorders or resizes the columns
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
    sortKeys: List<SortKey>? = null,
    onSortChange: (List<SortKey>) -> Unit = {},
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    columnLayout: TableColumnLayout? = null,
    onColumnLayoutChange: (TableColumnLayout) -> Unit = {},
) {
    Table(
        model = model,
        listSelectionListener = settledSelectionListener(onSelectionChange),
        modifier = modifier,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        sortable = sortable,
        sortKeys = sortKeys,
        rowSorterListener = rememberSortKeysListener(onSortChange),
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        columnLayout = columnLayout,
        tableColumnModelListener = columnLayoutListener(onColumnLayoutChange),
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
 * @param selectionMode how many rows/ranges may be selected
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [model] holds them in and its column headers inert
 * @param sortKeys the sort order the caller declares; `null` - the default - leaves the order to the user
 * @param rowSorterListener the listener notified of the user's sort-order changes; `null` - the default -
 *   reports none of them
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height in pixels of every row; `null` - the default - leaves it to the look and feel
 * @param autoResizeMode how the columns share out a change to the table's width
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds
 * @param columnLayout the column order and widths the caller declares; `null` - the default - leaves the
 *   column layout to the user
 * @param tableColumnModelListener the listener notified of the user's column reorders and resizes; `null`
 *   - the default - reports none of them
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
    sortKeys: List<SortKey>? = null,
    rowSorterListener: RowSorterListener? = null,
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    columnLayout: TableColumnLayout? = null,
    tableColumnModelListener: TableColumnModelListener? = null,
) {
    TableNode(
        listSelectionListener = listSelectionListener,
        modifier = modifier,
        selectedRowIndices = selectedRowIndices,
        selectionMode = selectionMode,
        sortKeys = sortKeys,
        rowSorterListener = rowSorterListener,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        tableColumnModelListener = tableColumnModelListener,
        model = model,
        cellIslands = null,
    ) { appliedSelection, appliedColumn, sortChannel, columnChannel ->
        set(model) { newModel ->
            val table = this
            columnChannel.preserveAcross(columnModel, columnLayout, appliedColumn) {
                installContent(appliedSelection, selectedRowIndices, listSelectionListener) {
                    sortChannel.preserveAcross(table, sortable, sortKeys) {
                        sortChannel.unbindFrom(table, newModel)
                        table.model = newModel
                    }
                }
            }
        }
        // Taking a sorter on or off empties the table's selection the way a new model does, so it is put
        // back through the same install. The columns are left where they are by either, and the layout
        // is re-asserted below on every pass regardless.
        set(sortable) { enabled ->
            val table = this
            installContent(appliedSelection, selectedRowIndices, listSelectionListener) {
                sortChannel.preserveAcross(table, enabled, sortKeys)
            }
        }
        // The column layout is re-asserted every pass regardless of whether it moved, since it has no
        // mirror of its own to invalidate this composition on a move away from it.
        reconcile {
            appliedColumn.write { columnModel.applyColumnLayout(columnLayout) }
            columnChannel.adopt(columnModel.readColumnLayout())
        }
    }
}

/**
 * The `JTable` node every [Table] overload renders: all of it but the content, which [installContent]
 * declares - a rows-and-columns refresh in one family of overloads, the caller's own model in the other.
 * [installContent] is handed the [AppliedValue] mirroring the row selection, the [AppliedWrite] marking the
 * column layout's own writes, and the [RowSortChannel] and [ColumnLayoutChannel] that carry the sort order
 * and the column layout across whatever change [installContent] makes, since giving the table new content
 * is one of the changes that unsettles all four.
 */
@Composable
private fun TableNode(
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier,
    selectedRowIndices: Set<Int>?,
    @SelectionMode selectionMode: Int,
    sortKeys: List<SortKey>?,
    rowSorterListener: RowSorterListener?,
    rowFilter: RowFilter<in TableModel, in Int>?,
    rowHeight: Int?,
    @AutoResizeMode autoResizeMode: Int,
    fillsViewportHeight: Boolean,
    tableColumnModelListener: TableColumnModelListener?,
    model: TableModel,
    cellIslands: TableCellIslands<*>?,
    installContent: SwingNodeUpdater<JTable>.(
        AppliedValue<Set<Int>?>,
        AppliedWrite,
        RowSortChannel,
        ColumnLayoutChannel,
    ) -> Unit,
) {
    // The column layout keeps marking its own writes through a bare reentrancy counter - it is not the
    // mirrored property here - while the row selection and the sort order each get a real mirror of their own.
    val appliedColumn = rememberAppliedWrite()
    val appliedSelection = rememberAppliedValue(selectedRowIndices)
    val appliedSort = rememberAppliedValue(sortKeys)
    val columnChannel = rememberColumnLayoutChannel(tableColumnModelListener)
    val sortChannel = rememberRowSortChannel(appliedSort, rowSorterListener)
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
            set(autoResizeMode) { mode -> this.autoResizeMode = mode }
            set(fillsViewportHeight) { fills -> this.fillsViewportHeight = fills }
            installContent(appliedSelection, appliedColumn, sortChannel, columnChannel)
            declareRowFilter(sortChannel, rowFilter, appliedSelection, selectedRowIndices, listSelectionListener)
            // Run on every pass regardless of whether a sort order or a selection is declared, so the set
            // calls these make always number the same and no later slot in this block shifts when one flips
            // to the other. An undeclared (null) value settles to itself: applySortKeys and applySelection
            // leave the table alone for a null declaration, so neither is imposed, overwritten, or
            // re-asserted for it.
            declare(sortKeys, appliedSort, { sortChannel.sortKeys() }, { keys -> sortChannel.applySortKeys(keys) })
            val tableModifier =
                modifier
                    .userSelectionListener(appliedSelection, listSelectionListener)
                    .tableColumnModelListener(userColumnListener)
                    .tableRowHeight(rowHeight)
            applyModifier(
                if (cellIslands == null) tableModifier else tableModifier.composableColumnCells(cellIslands),
            )
        },
    )
}

/**
 * Folds in the row height a `JTable` leaves to the UI delegate of its look and feel as a modifier
 * element, while the caller declares one and dropped the moment the declaration goes back to `null`.
 *
 * A `JTable` takes an explicit row height as the client's own and stops accepting the one its look and
 * feel installs, which is what makes a folded-in element outrank the look and feel while it stays in the
 * chain; removing the element restores the height the table carried before it was folded in - the one
 * its look and feel chose - through the same capture-on-attach, restore-on-detach every modifier
 * property follows. Detaching on release, reuse and deactivate as well as on withdrawal is what gives a
 * parked table its look and feel's height back too.
 *
 * One element carries the height for every [Table] overload, since a modifier element's slot is the
 * class of the accessor written here and two accessors would be two slots writing one property.
 */
private fun SwingModifier.tableRowHeight(rowHeight: Int?): SwingModifier =
    if (rowHeight == null) {
        this
    } else {
        this then
            propertyElement<JTable, Int>(
                rowHeight,
                read = { it.rowHeight },
                write = { table, height -> table.rowHeight = height },
            )
    }

/**
 * The [ListSelectionListener] forwarding each settled row selection to [onSelectionChange], bridging a
 * lambda-based [Table] overload to the raw-listener overload it delegates to. A selection event is handed
 * on with the table as its source, so the settled selection is read back from the table - in the model's
 * row space - once the value stops adjusting.
 *
 * Rebuilt per pass rather than remembered: every place a [Table] takes it reads it live -
 * [UserSelectionListenerElement] holds it in a node field, and the rest call it while the pass that built
 * it runs.
 */
private fun settledSelectionListener(onSelectionChange: (Set<Int>) -> Unit): ListSelectionListener =
    ListSelectionListener { event ->
        if (!event.valueIsAdjusting) onSelectionChange((event.source as JTable).selectedModelRows())
    }

/**
 * Gives the table new content through [install], keeping the rows [declared] names selected - or, where the
 * caller declared nothing, the rows the user had - and reporting to [target] the rows the new content is
 * too short to hold. See [installNarrowing].
 */
internal fun JTable.installContent(
    applied: AppliedValue<Set<Int>?>,
    declared: Set<Int>?,
    target: ListSelectionListener,
    install: () -> Unit,
): Unit =
    applied.installNarrowing(
        declared = declared,
        selection = { selectedModelRows() },
        apply = { indices -> applySelection(this, indices) },
        report = { lost -> reportLostRows(target, lost) },
        install = install,
    )

/**
 * Re-applies [indices] as the table's selected rows. The indices name rows of the model, so a row the model
 * no longer holds is dropped and a row the current filter hides has no screen row to select and is dropped
 * too. A selection that already matches is left alone, so a recomposition that changed nothing touches the
 * table's selection model not at all, and a `null` declaration leaves it alone entirely.
 *
 * The rows are selected in ascending screen order, whatever order [indices] iterates in, so every set that
 * names the same rows leaves the table on the same selection and on the same lead row - the highest of them
 * on screen, left behind by the last interval added.
 */
private fun applySelection(
    table: JTable,
    indices: Set<Int>?,
) {
    if (indices == null) return
    val rowCount = table.model.rowCount
    val valid =
        indices
            .filter { it in 0 until rowCount }
            .map { table.convertRowIndexToView(it) }
            .filterTo(sortedSetOf()) { it >= 0 }
    if (table.selectedRows.toSet() == valid) return
    val selectionModel = table.selectionModel
    selectionModel.valueIsAdjusting = true
    table.clearSelection()
    for (index in valid) selectionModel.addSelectionInterval(index, index)
    selectionModel.valueIsAdjusting = false
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.annotations.AutoResizeMode
import org.jetbrains.compose.swing.annotations.SelectionMode
import org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi
import org.jetbrains.compose.swing.components.rememberDeclaredList
import org.jetbrains.compose.swing.components.style.Tables
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.event.ListSelectionListener
import javax.swing.table.TableModel

/**
 * A grid the user reads, sorts, and selects in: a `JTable` whose selection, sort order and column layout
 * are declared and read through a [TableState].
 *
 * [rows] are the row data; declare each column in [columns] with its header, a value extractor, and
 * (optionally) in-place editing. Rows and columns are **data**: changing [rows] or the declared
 * columns rebuilds the table's model on recomposition. The [state] owns the selection, the sort order
 * and the column layout: assigning one applies it to the table, the user's own selecting, header clicks
 * and column drags are written back into it, and it is where a row is revealed from. The selection is
 * expressed as the general multi-select shape so one component covers all of [SelectionMode]'s modes.
 * The order and the layout the state holds outlive the table component, so a table built again over the
 * same state starts on them. Place the table in a
 * [org.jetbrains.compose.swing.components.layout.ScrollPane] to scroll and to show the column header.
 *
 * ```
 * val state = rememberTableState()
 *
 * ScrollPane {
 *     Viewport {
 *         Table(
 *             rows = people,
 *             columns = {
 *                 column("Name") { it.name }
 *                 column("Age", onCellEdit = { row, _, v -> update(row, v) }) { it.age }
 *             },
 *             state = state,
 *         ) {
 *             Header(reorderingAllowed = false)
 *         }
 *     }
 * }
 * Label("Selected: ${state.selectedRowIndices.size}")
 * ```
 *
 * The trailing [content] configures the parts the table builds itself, such as its header; see
 * [TableContentScope].
 *
 * A cell edit commits through the edited column's `onCellEdit`; the displayed value does not change
 * until the caller updates the backing state and the next composition supplies fresh [rows].
 * The user's selecting is written into [state] once per settled change - so dragging across rows
 * produces one write at the end rather than one per row crossed, and rendering new [rows] or new columns
 * produces none. The selection the state holds is the composition's own and is re-applied on every pass:
 * it survives such a change (a row the current rows do not cover is left out of the table while the state
 * goes on naming it), and a user change the state does not hold does not stand. Where the new rows are
 * too few to hold the user's selection, the rows that fall outside them leave the selection and the
 * state is written with what is left of it.
 *
 * A selected row is named by its index into [rows] - the model's own row space, the space
 * [TableColumnLayout.modelIndices] names its columns in - and never by the position it is drawn at:
 * sorting and filtering move where a row is shown and leave the row each index names alone.
 *
 * Sorting is off until [sortable] turns it on, as it is on a bare `JTable`. With it on, clicking a column
 * header sorts the rows by that column and the order the click leaves them in is written into
 * [state], and [rowFilter] decides which of them are shown at all. Each column brings its own `isSortable`
 * and `comparator` to that sorting. New rows and new columns keep the order the state holds.
 *
 * Dragging a column header sideways reorders the columns and dragging the divider between two headers
 * resizes them, and each writes the layout the columns are then in into [state]. New columns rebuild
 * the layout from the declarations, and the layout the state holds is carried across the rebuild as far
 * as the new columns can hold it. A column's own `minWidth` and `maxWidth` bound every width it can be left
 * at, and [autoResizeMode] decides how the columns share a width change between them.
 *
 * To refuse a gesture, turn it off: [sortable] or a column's `isSortable` for a sort, and the
 * [TableContentScope.Header] for a reorder or a resize.
 *
 * A column renders its cells through the renderer the table picks by the column's class until its
 * `cellContent` gives it a composable cell of its own. A table gives every one of its rows the same
 * height and never measures one by what its cells ask for, so a cell taller than the text a table sizes
 * its rows for is what [rowHeight] is for.
 *
 * @param rows the row data to display
 * @param columns declares the columns; see [TableScope]
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param state the hoistable state the table applies and reports its selection, sort order and column
 *   layout into; see [TableState]. `null` - the default - leaves the selection, the sort keys and the column
 *   layout to Swing: none is declared, and the user's changes to them are not reported
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
@NonRestartableComposable
@Composable
public fun <R> Table(
    rows: List<R>,
    columns: TableScope<R>.() -> Unit,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
    state: TableState? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    content: (@Composable TableContentScope.() -> Unit)? = null,
) {
    Table(
        factory = { JTable() },
        rows = rows,
        columns = columns,
        modifier = modifier,
        style = style,
        state = state,
        selectionMode = selectionMode,
        sortable = sortable,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        content = content,
    )
}

/**
 * A [Table] over [rows] whose selection, sort order and column layout are declared and read through a
 * [TableState], built on the `JTable` that [factory] returns instead of a stock one.
 *
 * [factory] runs once for each node and returns a new component on every call. A changed capture is not
 * applied, as with [SwingNode]: to build another component, wrap the call in `key(...)`. What the other
 * parameters declare is written over what the factory set.
 * That includes the columns: they are built from [columns] whatever column model or
 * `autoCreateColumnsFromModel` the factory set. The header [TableContentScope.Header] configures is the one the
 * component builds.
 *
 * @param factory builds the component this composable drives. Its model is replaced with the one [rows] fill
 * @param rows the row data to display
 * @param columns declares the columns; see [TableScope]
 * @param state the hoistable state the table applies and reports its selection, sort order and column
 *   layout into; see [TableState]. `null` leaves the selection, the sort keys and the column
 *   layout to Swing: none is declared, and the user's changes to them are not reported
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [rows] declares and its column headers inert
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height of every row; `null` - the default - leaves the factory's row height, and
 *   withdrawing a declared height restores that height
 * @param autoResizeMode how the columns share out a change to the table's width;
 *   `AUTO_RESIZE_SUBSEQUENT_COLUMNS` - the default - takes the change out of the columns right of the
 *   one resized, while `AUTO_RESIZE_OFF` leaves every column its width and scrolls instead
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds; `false` - the default - leaves it as tall as its rows
 * @param content configures the parts the table builds itself, or none when `null`; see [TableContentScope]
 * @see javax.swing.JTable
 */
@SwingComponentLibraryApi
@Composable
public fun <R> Table(
    factory: () -> JTable,
    rows: List<R>,
    columns: TableScope<R>.() -> Unit,
    state: TableState?,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    content: (@Composable TableContentScope.() -> Unit)? = null,
) {
    val declaredRows = rememberDeclaredList(rows)
    val heldSortKeys = state?.sortKeys
    val heldColumnLayout = state?.columnLayout
    val declaredColumns = TableScopeImpl<R>().apply(columns).columns
    // Handed to the factory so the table is built already showing the model this composition fills.
    val model = remember { ColumnsTableModel<R>() }
    // One cell composition per column that declares a composable cell, following the declarations pass
    // by pass. Each resolves the row a render names against the rows the model holds then.
    val cellCompositions = rememberTableCellCompositions(declaredColumns) { rowIndex -> model.rowAt(rowIndex) }

    TableNode(
        modifier = modifier,
        style = style,
        state = state,
        selectionMode = selectionMode,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        model = model,
        factory = { shown ->
            factory().also {
                it.autoCreateColumnsFromModel = true
                it.model = shown
            }
        },
        cellCompositions = cellCompositions,
        parts = content,
    ) { selectionMirror, columnMirror, sortChannel, columnChannel, selectedRowIndices, listSelectionListener ->
        // The refresh that gives the table this composition's model, rows, columns and cell renderers,
        // named step by step, run when the rows, the columns or sortable change - the content it installs.
        // The selection it puts back is settled on every pass in TableNode, and the order and the layout a
        // state holds are applied there when assigned, so a change to them alone needs no refresh. The steps
        // nest instead of running in sequence: each wraps the ones that can undo what it is putting back, so
        // its own restore runs only once those have already run - which is why none of the four can be
        // pulled out as a set() of its own. The last line's nesting order is the whole contract; a step's own
        // comment says what it is guarding against.
        set(listOf(declaredRows, declaredColumns, sortable)) {
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
                model.refresh(table, declaredRows, declaredColumns)
                columnMirror.write { table.applyDeclaredColumnWidths(declaredColumns) }
            }

            // The sorter is built over the model the refresh leaves the table holding, so it is bound
            // outside the swap rather than in it.
            val preservingSortOrder = { refresh: () -> Unit ->
                sortChannel.preserveAcross(table, sortable, heldSortKeys, declaredColumns, refresh)
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
 * A grid the user reads, sorts, and selects in, over a [TableModel] the caller owns: a `JTable` whose
 * selection, sort order and column layout are declared and read through a [TableState].
 *
 * The [model] is displayed as-is: its own columns, values, and editability drive the table, and the
 * library never mutates it. Supplying a new [model] instance swaps it into the table on recomposition.
 * The [state] owns the selection, the sort order and the column layout: assigning one applies it to the
 * table, the user's own selecting, header clicks and column drags are written back into it, and it is
 * where a row is revealed from. The selection is expressed as the general multi-select shape so one
 * component covers all of [SelectionMode]'s modes, and survives a model swap. The order and the layout
 * the state holds outlive the table component, so a table built again over the same state starts on them.
 * Place the table in a [org.jetbrains.compose.swing.components.layout.ScrollPane] to scroll and to show
 * the column header.
 *
 * ```
 * val state = rememberTableState()
 *
 * ScrollPane {
 *     Viewport { Table(model = myTableModel, state = state) }
 * }
 * ```
 *
 * The user's selecting is written into [state] once per settled change - so dragging across rows
 * produces one write at the end rather than one per row crossed, and installing a new [model] produces
 * none. The selection the state holds is the composition's own and is re-applied on every pass, so a
 * user change the state does not hold does not stand; where the new model has too few rows to hold the
 * user's selection, the rows that fall outside it leave the selection and the state is written with what
 * is left of it.
 *
 * A selected row is named by its index in [model] - the model's own row space, the space
 * [TableColumnLayout.modelIndices] names its columns in - and never by the position it is drawn at:
 * sorting and filtering move where a row is shown and leave the row each index names alone.
 *
 * Sorting is off until [sortable] turns it on, as it is on a bare `JTable`. With it on, clicking a column
 * header sorts the rows by that column and the order the click leaves them in is written into
 * [state], and [rowFilter] decides which of them are shown at all. A new [model] keeps the order the
 * state holds.
 *
 * Dragging a column header sideways reorders the columns and dragging the divider between two headers
 * resizes them, and each writes the layout the columns are then in into [state]. A new [model] rebuilds
 * the columns from it, and the layout the state holds is carried across the swap as far as the new
 * model's columns can hold it.
 *
 * To refuse a gesture, turn it off: [sortable] for a sort, and the [TableContentScope.Header] for a
 * reorder or a resize.
 *
 * @param model the table model to display; owned by the caller and never mutated by the library
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param state the hoistable state the table applies and reports its selection, sort order and column
 *   layout into; see [TableState]. `null` - the default - leaves the selection, the sort keys and the column
 *   layout to Swing: none is declared, and the user's changes to them are not reported
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
@NonRestartableComposable
@Composable
public fun Table(
    model: TableModel,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
    state: TableState? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    content: (@Composable TableContentScope.() -> Unit)? = null,
) {
    Table(
        factory = { JTable() },
        model = model,
        modifier = modifier,
        style = style,
        state = state,
        selectionMode = selectionMode,
        sortable = sortable,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        content = content,
    )
}

/**
 * A [Table] over a [TableModel] whose selection, sort order and column layout are declared and read
 * through a [TableState], built on the `JTable` that [factory] returns instead of a stock one.
 *
 * [factory] runs once for each node and returns a new component on every call. A changed capture is not
 * applied, as with [SwingNode]: to build another component, wrap the call in `key(...)`. What the other
 * parameters declare is written over what the factory set.
 * The columns are the exception: the table keeps the column model the factory set, and [model]'s columns are
 * built for it only where `autoCreateColumnsFromModel` is on. The header [TableContentScope.Header] configures
 * is the one the component builds.
 *
 * @param factory builds the component this composable drives. Its model is replaced with [model]
 * @param model the table model to display; owned by the caller and never mutated by the library
 * @param state the hoistable state the table applies and reports its selection, sort order and column
 *   layout into; see [TableState]. `null` leaves the selection, the sort keys and the column
 *   layout to Swing: none is declared, and the user's changes to them are not reported
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param sortable whether the table sorts and filters its rows; `false` - the default - leaves them in the
 *   order [model] holds them in and its column headers inert
 * @param rowFilter which of the rows the table shows, or `null` - the default - to show all of them; a
 *   filter is adopted by identity, so pass a stable one (e.g. `remember {}`) to avoid churn
 * @param rowHeight the height of every row; `null` - the default - leaves the factory's row height, and
 *   withdrawing a declared height restores that height
 * @param autoResizeMode how the columns share out a change to the table's width;
 *   `AUTO_RESIZE_SUBSEQUENT_COLUMNS` - the default - takes the change out of the columns right of the
 *   one resized, while `AUTO_RESIZE_OFF` leaves every column its width and scrolls instead
 * @param fillsViewportHeight whether the table stretches to the full height of the viewport showing it,
 *   rather than to the height of the rows it holds; `false` - the default - leaves it as tall as its rows
 * @param content configures the parts the table builds itself, or none when `null`; see [TableContentScope]
 * @see javax.swing.JTable
 */
@SwingComponentLibraryApi
@Composable
public fun Table(
    factory: () -> JTable,
    model: TableModel,
    state: TableState?,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    sortable: Boolean = false,
    rowFilter: RowFilter<in TableModel, in Int>? = null,
    rowHeight: Int? = null,
    @AutoResizeMode autoResizeMode: Int = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
    fillsViewportHeight: Boolean = false,
    content: (@Composable TableContentScope.() -> Unit)? = null,
) {
    val heldSortKeys = state?.sortKeys
    val heldColumnLayout = state?.columnLayout
    TableNode(
        modifier = modifier,
        style = style,
        state = state,
        selectionMode = selectionMode,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        model = model,
        factory = { shown -> factory().also { it.model = shown } },
        cellCompositions = null,
        parts = content,
        applyModel = { table, newModel, selectionMirror, sortChannel, columnChannel, selectedRowIndices, listener ->
            columnChannel.preserveAcross(table, heldColumnLayout) {
                table.installContent(selectionMirror, selectedRowIndices, listener) {
                    sortChannel.preserveAcross(table, sortable, heldSortKeys) {
                        sortChannel.unbindFrom(table, newModel)
                        table.model = newModel
                    }
                }
            }
        },
    ) { selectionMirror, _, sortChannel, _, selectedRowIndices, listSelectionListener ->
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

/** [SwingComponentStyleScope] for Table styles. No component state is supplied. */
private val TableKind =
    nestingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    ) {
        applyNestedStyle(Tables)
    }

/**
 * What a [Table] over a caller's model does when the model it is handed is another one: the table, the new
 * model, the selection mirror, the sort and column-layout channels, the rows a state declares selected and the
 * listener that writes the user's selecting back.
 */
private typealias TableModelApplier = (
    JTable,
    TableModel,
    MirrorState<Set<Int>?>,
    RowSortChannel,
    ColumnLayoutChannel,
    Set<Int>?,
    ListSelectionListener,
) -> Unit

/**
 * The `JTable` node every [Table] overload renders: all of it but the content, which [installContent]
 * declares - a rows-and-columns refresh in one overload, the caller's own model in the other - with
 * [parts] composed under it. [installContent] is handed the [MirrorState]s mirroring the row selection
 * and the column layout, and the [RowSortChannel] and [ColumnLayoutChannel] that carry the sort order and the
 * column layout across whatever change [installContent] makes, since giving the table new content is one of
 * the changes that unsettles all four. They carry the order and the layout [state] holds. It is also handed
 * the rows [state] declares selected and the listener that writes the user's selecting back into it.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Composable
@SwingComponentLibraryApi
private inline fun TableNode(
    modifier: SwingModifier,
    style: SwingStyle<SwingComponentStyleScope>,
    state: TableState?,
    @SelectionMode selectionMode: Int,
    rowFilter: RowFilter<in TableModel, in Int>?,
    rowHeight: Int?,
    @AutoResizeMode autoResizeMode: Int,
    fillsViewportHeight: Boolean,
    model: TableModel,
    crossinline factory: (TableModel) -> JTable,
    cellCompositions: TableCellCompositions<*>?,
    noinline parts: (@Composable TableContentScope.() -> Unit)?,
    noinline applyModel: TableModelApplier? = null,
    crossinline installContent: SwingNodeUpdater<JTable>.(
        MirrorState<Set<Int>?>,
        MirrorState<TableColumnLayout?>,
        RowSortChannel,
        ColumnLayoutChannel,
        Set<Int>?,
        ListSelectionListener,
    ) -> Unit,
) {
    val selectedRowIndices = state?.selectedRowIndices
    val listSelectionListener = settledRowSelectionListener { indices -> state?.selectedRowIndices = indices }
    val rowSorterListener = rememberSortKeysListener { keys -> state?.sortKeys = keys }
    val tableColumnModelListener = columnLayoutListener { layout -> state?.columnLayout = layout }
    val contentScope = remember { TableContentScopeImpl() }
    // The layout the columns are in is seeded null rather than from the declaration: a table has no columns
    // until it has a model, so there is no layout to mirror until the first refresh has built them.
    val columnMirror = rememberMirrorState<TableColumnLayout?>(null)
    val selectionMirror = rememberMirrorState(selectedRowIndices)
    val columnChannel = rememberColumnLayoutChannel(columnMirror, tableColumnModelListener)
    val sortChannel = rememberRowSortChannel(rowSorterListener)

    val tableModifier =
        (if (state == null) modifier else modifier.tableStateBinding(state))
            .userSelectionListener(selectionMirror, listSelectionListener)
            .tableColumnModelListener(columnChannel.listener)
            .tableRowHeight(rowHeight)
    SwingNode(
        factory = { factory(model) },
        modifier =
            (if (cellCompositions == null) tableModifier else tableModifier.composableColumnCells(cellCompositions))
                .styleable(TableKind, style),
        update = {
            applyMirror(selectionMirror)
            set(selectionMode) { mode ->
                narrowSelection(selectionMirror, selectedRowIndices, listSelectionListener) {
                    applySelectionMode(mode)
                }
            }
            set(autoResizeMode) { mode -> this.autoResizeMode = mode }
            set(fillsViewportHeight) { fills -> this.fillsViewportHeight = fills }
            set(model) { newModel ->
                if (applyModel != null) {
                    applyModel(
                        this,
                        newModel,
                        selectionMirror,
                        sortChannel,
                        columnChannel,
                        selectedRowIndices,
                        listSelectionListener,
                    )
                }
            }
            installContent(
                selectionMirror,
                columnMirror,
                sortChannel,
                columnChannel,
                selectedRowIndices,
                listSelectionListener,
            )
            declareRowFilter(sortChannel, rowFilter, selectionMirror, selectedRowIndices, listSelectionListener)
            settleHeldArrangement(state, columnMirror, sortChannel, columnChannel)
        },
        childPlacement = TableRegions,
        content = { if (parts != null) contentScope.parts() },
    )
}

/**
 * Applies the order and the layout [state] holds when they were assigned, and never puts them back over the
 * user's own change, which the channels stamp as they write it back. A caller's swap of the column model is
 * stamped as the layout the table holds, and reading [columnMirror] is what brings the pass that puts the
 * state's layout onto it.
 */
private fun SwingNodeUpdater<JTable>.settleHeldArrangement(
    state: TableState?,
    columnMirror: MirrorState<TableColumnLayout?>,
    sortChannel: RowSortChannel,
    columnChannel: ColumnLayoutChannel,
) {
    if (state != null) columnMirror.subscribe()
    val due =
        state != null &&
            (state.sortKeys != sortChannel.appliedSortKeys || state.columnLayout != columnChannel.appliedLayout)
    val sortKeys = state?.sortKeys
    val columnLayout = state?.columnLayout
    set(if (due) HeldArrangementApply() else null) { token ->
        if (token != null && sortKeys != null) {
            sortChannel.applyHeld(sortKeys)
            columnChannel.applyHeld(this, columnLayout)
        }
    }
}

/** Stands for one due apply of what a [TableState] holds, so the slot that carries it never holds back a due pass. */
private class HeldArrangementApply

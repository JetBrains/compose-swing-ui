@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.constants.AutoResizeMode
import org.jetbrains.compose.swing.constants.SelectionMode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.table.TableModel

/**
 * A hoistable state holder for what a [Table] has selected, carrying the gesture that brings one of its
 * rows into view.
 *
 * Each row is named by its index into the table's rows - the model's own row space, never the position the
 * row is drawn at - so a sort order and a row filter move where a row is shown and leave the index that
 * names it alone.
 *
 * [selectedRowIndices] is two-way: assigning it selects those rows, and the user selecting other ones - by
 * click, drag or keyboard - writes them back here. It is snapshot-observable, so reading it inside a
 * composable (or a `snapshotFlow` collector) subscribes to the user's later selecting as well.
 *
 * The rows this state names are the composition's own and are re-applied on every pass, so a table driven
 * by a state never stands on a selection the state does not hold, and a row the table's rows do not reach
 * is left out of the selection while it goes on being named here - rows that reach it again show it
 * selected.
 *
 * [revealRow] brings one row into view when the application decides to - a row just added, a search hit:
 *
 * ```
 * val state = rememberTableState()
 *
 * Button("Add", onClick = { people = people + Person() })
 * LaunchedEffect(people) { state.revealRow(people.lastIndex) }
 * ScrollPane {
 *     Table(rows = people, state = state, modifier = SwingModifier.viewport()) {
 *         column("Name") { it.name }
 *     }
 * }
 * ```
 *
 * [rowCount] and [shownSelectedRowIndices] answer for the table instead of for what this state holds.
 * Each reads the bound table where it is called, so what it reports is what the table stands on - which is
 * not always what was declared, since a selection mode narrower than the declaration keeps only part of
 * it and a row filter takes the rows it hides out of the selection. They are not snapshot state, so
 * reading one subscribes to nothing; a composable that has to follow the user reads [selectedRowIndices].
 * An unbound state has no table to answer for and reports no rows and nothing selected.
 *
 * A state drives at most one table: passing it to a second one moves it there and leaves the first
 * unbound.
 *
 * The order and the widths of the columns are not this state's: [Table] declares them through its own
 * `columnLayout`.
 *
 * @param initialSelectedRowIndices the rows selected until the caller or the user moves the selection.
 * @see javax.swing.JTable
 */
@Stable
public class TableState
    @RememberInComposition
    constructor(
        initialSelectedRowIndices: Set<Int> = emptySet(),
    ) {
        /**
         * The selected rows as indices into the table's rows, expressed as the general multi-select shape
         * so one state covers every one of [org.jetbrains.compose.swing.constants.SelectionMode]'s modes.
         *
         * @see javax.swing.JTable.setRowSelectionInterval
         */
        public var selectedRowIndices: Set<Int> by mutableStateOf(initialSelectedRowIndices)

        // The table this state drives, or null when unbound. Only the binding modifier node writes it,
        // whose lifecycle owns the relationship.
        private var target: JTable? = null

        /**
         * How many rows the table shows: the rows of its model, less the ones a row filter hides. `0` while
         * no table is bound.
         *
         * @see javax.swing.JTable.getRowCount
         */
        public val rowCount: Int get() = target?.rowCount ?: 0

        /**
         * The rows the table has selected, as indices into its rows. Empty while no table is bound.
         *
         * @see javax.swing.JTable.getSelectedRows
         */
        public val shownSelectedRowIndices: Set<Int>
            get() = target?.selectedModelRows().orEmpty()

        /**
         * Brings the row [rowIndex] names into view, and returns whether it was reached.
         *
         * Revealing is a gesture rather than a declaration: it scrolls where it is called and leaves nothing
         * behind, so no later pass scrolls back and where the user scrolls afterwards stands.
         *
         * A row is revealed once the table holds it, which is what an effect keyed on the data runs after:
         * the rows a click declares reach the table on the composition that click triggers.
         *
         * `false` means nothing was revealed: no table is bound, the rows the table currently holds have no
         * such row, or a row filter hides it, which leaves it nowhere to be shown. `true` means the table
         * was asked to show it, which scrolls the pane the table is in; a table in no scroll pane has
         * nowhere to scroll.
         *
         * @see javax.swing.JTable.scrollRectToVisible
         */
        public fun revealRow(rowIndex: Int): Boolean {
            val table = target ?: return false
            val holds = rowIndex in 0 until table.model.rowCount
            val viewRow = if (holds) table.convertRowIndexToView(rowIndex) else -1
            if (viewRow >= 0) table.scrollRectToVisible(table.getCellRect(viewRow, 0, true))
            return viewRow >= 0
        }

        internal fun bind(table: JTable) {
            target = table
        }

        internal fun unbind(table: JTable) {
            if (target === table) target = null
        }
    }

/**
 * Creates and remembers a [TableState] starting on [initialSelectedRowIndices].
 *
 * A later change to [initialSelectedRowIndices] neither recreates the state nor moves the selection; select
 * afterwards through the returned state's [TableState.selectedRowIndices].
 *
 * @param initialSelectedRowIndices the rows selected until the caller or the user moves the selection.
 */
@Composable
public fun rememberTableState(initialSelectedRowIndices: Set<Int> = emptySet()): TableState =
    remember { TableState(initialSelectedRowIndices) }

/**
 * A [Table] driven by a [TableState] instead of a declared `selectedRowIndices` and an `onSelectionChange`
 * lambda. The state owns the selection: the rows it holds are what the table shows selected, the user's own
 * selecting is written back into it, and it is where a row is revealed from.
 *
 * ```
 * val state = rememberTableState()
 *
 * ScrollPane {
 *     Table(rows = people, state = state, modifier = SwingModifier.viewport()) {
 *         column("Name") { it.name }
 *     }
 * }
 * Label("Selected: ${state.selectedRowIndices.size}")
 * ```
 *
 * @param rows the row data to display
 * @param state the hoistable selection state the table applies and reports into; see [TableState]
 * @param modifier the [SwingModifier] applied to the underlying component
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
    state: TableState,
    modifier: SwingModifier = SwingModifier,
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
        modifier = modifier.tableStateBinding(state),
        selectedRowIndices = state.selectedRowIndices,
        onSelectionChange = { indices -> state.selectedRowIndices = indices },
        selectionMode = selectionMode,
        sortable = sortable,
        sortKeys = sortKeys,
        onSortChange = onSortChange,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        columnLayout = columnLayout,
        onColumnLayoutChange = onColumnLayoutChange,
        block = block,
    )
}

/**
 * A model-driven [Table] driven by a [TableState] instead of a declared `selectedRowIndices` and an
 * `onSelectionChange` lambda. The state owns the selection: the rows it holds are what the table shows
 * selected, the user's own selecting is written back into it, and it is where a row is revealed from.
 *
 * The [model] is displayed as-is and never mutated by the library; the selection survives a model swap.
 *
 * @param model the table model to display; owned by the caller and never mutated by the library
 * @param state the hoistable selection state the table applies and reports into; see [TableState]
 * @param modifier the [SwingModifier] applied to the underlying component
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
    state: TableState,
    modifier: SwingModifier = SwingModifier,
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
        modifier = modifier.tableStateBinding(state),
        selectedRowIndices = state.selectedRowIndices,
        onSelectionChange = { indices -> state.selectedRowIndices = indices },
        selectionMode = selectionMode,
        sortable = sortable,
        sortKeys = sortKeys,
        onSortChange = onSortChange,
        rowFilter = rowFilter,
        rowHeight = rowHeight,
        autoResizeMode = autoResizeMode,
        fillsViewportHeight = fillsViewportHeight,
        columnLayout = columnLayout,
        onColumnLayoutChange = onColumnLayoutChange,
    )
}

/** Binds [state] to the composable's table through the modifier chain; see [binding]. */
internal fun SwingModifier.tableStateBinding(state: TableState): SwingModifier =
    binding(JTable::class.java, state, TableState::bind, TableState::unbind)

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
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import javax.swing.JTable
import javax.swing.RowSorter.SortKey

/**
 * A hoistable state holder for what a [Table] has selected, the order it sorts its rows in and the layout of
 * its columns, carrying the gesture that brings one of its rows into view.
 *
 * Each row is named by its index into the table's rows - the model's own row space, never the position the
 * row is drawn at - so a sort order and a row filter move where a row is shown and leave the index that
 * names it alone.
 *
 * [selectedRowIndices], [sortKeys] and [columnLayout] are two-way: assigning one applies it to the table,
 * and the user's own change - selecting rows by click, drag or keyboard, sorting by a header click,
 * dragging a column header or divider - is written back here. All three are snapshot-observable, so
 * reading one inside a composable (or a `snapshotFlow` collector) subscribes to the user's later changes as
 * well.
 *
 * The rows this state names are the composition's own and are re-applied on every pass, so a table driven
 * by a state never stands on a selection the state does not hold, and a row the table's rows do not reach
 * is left out of the selection while it goes on being named here - rows that reach it again show it
 * selected.
 *
 * The sort order and the column layout are applied when they are assigned, and this state is the only place
 * they are held. A table built again over this state starts on them, and so does a table that turns
 * sorting on. The rows and columns a table is declared with, a new model and a new column model keep them.
 * A column the table no longer has goes on being named here, so columns that bring it back put it in its
 * place again. A structure change the caller makes to their own model in place starts the order and the
 * columns over, as it does on a `JTable`, and this state is written back with the result.
 *
 * [revealRow] brings one row into view when the application decides to - a row just added, a search hit:
 *
 * ```
 * val state = rememberTableState()
 *
 * Button("Add", onClick = { people = people + Person() })
 * LaunchedEffect(people) { state.revealRow(people.lastIndex) }
 * ScrollPane {
 *     Viewport {
 *         Table(rows = people, columns = { column("Name") { it.name } }, state = state)
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
 * @param initialSelectedRowIndices the rows selected until the caller or the user moves the selection.
 * @param initialSortKeys the order the rows are sorted in until the caller or the user sorts them again.
 * @param initialColumnLayout the layout the columns start in; `null` holds none, leaving them in their
 *   declared layout.
 * @see javax.swing.JTable
 */
@Stable
public class TableState
    @RememberInComposition
    constructor(
        initialSelectedRowIndices: Set<Int> = emptySet(),
        initialSortKeys: List<SortKey> = emptyList(),
        initialColumnLayout: TableColumnLayout? = null,
    ) {
        /**
         * The selected rows as indices into the table's rows, expressed as the general multi-select shape
         * so one state covers every one of [org.jetbrains.compose.swing.annotations.SelectionMode]'s modes.
         *
         * @see javax.swing.JTable.setRowSelectionInterval
         */
        public var selectedRowIndices: Set<Int> by mutableStateOf(initialSelectedRowIndices)

        /**
         * The order the rows are sorted in while the table is `sortable`; an empty list leaves them unsorted,
         * in the order the table's rows hold them.
         *
         * @see javax.swing.RowSorter.setSortKeys
         */
        public var sortKeys: List<SortKey> by mutableStateOf(initialSortKeys)

        /**
         * The order and the widths of the columns; `null` holds no layout, leaving the columns in the
         * layout they are already in, which starts as their declared layout.
         *
         * @see javax.swing.table.TableColumnModel
         */
        public var columnLayout: TableColumnLayout? by mutableStateOf(initialColumnLayout)

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
         * @param rowIndex the row to bring into view, named the way [selectedRowIndices] names one - by its
         *   index into the table's rows, not the position it is drawn at.
         * @return whether the table shows that row and was scrolled to it.
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
 * Creates and remembers a [TableState] starting on [initialSelectedRowIndices], [initialSortKeys] and
 * [initialColumnLayout].
 *
 * A later change to any of the three neither recreates the state nor changes the table; change them
 * afterwards through the returned state's [TableState.selectedRowIndices], [TableState.sortKeys] and
 * [TableState.columnLayout].
 *
 * @param initialSelectedRowIndices the rows selected until the caller or the user moves the selection.
 * @param initialSortKeys the order the rows are sorted in until the caller or the user sorts them again.
 * @param initialColumnLayout the layout the columns start in; `null` holds none, leaving them in their
 *   declared layout.
 */
@Composable
public fun rememberTableState(
    initialSelectedRowIndices: Set<Int> = emptySet(),
    initialSortKeys: List<SortKey> = emptyList(),
    initialColumnLayout: TableColumnLayout? = null,
): TableState = remember { TableState(initialSelectedRowIndices, initialSortKeys, initialColumnLayout) }

/** Binds [state] to the composable's table through the modifier chain; see [binding]. */
internal fun SwingModifier.tableStateBinding(state: TableState): SwingModifier =
    binding(JTable::class.java, "tableState", state, TableState::bind, TableState::unbind)

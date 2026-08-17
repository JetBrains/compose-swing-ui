package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.event.ListSelectionListener
import javax.swing.event.RowSorterEvent
import javax.swing.event.RowSorterListener
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

/**
 * One table's row-sorting channel: the sorter that orders and filters the table's rows while sorting is on,
 * and the listener through which the user's own header clicks reach the caller's [target] listener.
 *
 * A sorter is welded to the model it was built for - it holds that model's row count and answers that
 * model's events - so it comes off before the table takes another model and is built again over the model
 * the table is left holding. Sorting off leaves the table without a sorter at all, which is what a bare
 * `JTable` is, and the sort order, the row filter and the columns' own sorting rules all reach the table
 * through that sorter, so they stand exactly while it does.
 *
 * [applied] mirrors the order the rows are in, which is what makes a header click an ordinary composition
 * dependency and what tells that click from the writes this channel makes itself.
 */
internal class RowSortChannel(
    private val applied: AppliedValue<List<SortKey>?>,
    private val target: State<RowSorterListener?>,
) {
    private var sorter: TableRowSorter<TableModel>? = null

    /** Reports the user's own sort-order changes. Installed on every sorter this channel builds. */
    private val listener =
        RowSorterListener { event ->
            if (event.type == RowSorterEvent.Type.SORT_ORDER_CHANGED) applied.observed(event.source.sortKeys.toList())
            if (!applied.isWriting) target.value?.sorterChanged(event)
        }

    /** The order the rows are in, or no order at all while sorting is off. */
    fun sortKeys(): List<SortKey> = sorter?.sortKeys?.toList().orEmpty()

    /**
     * Sorts by [keys], dropping a key that names a column the model does not hold - a sorter takes only
     * columns it has. A `null` declaration leaves the order alone entirely, so it is never imposed, and
     * while sorting is off there is no sorter for any order to reach.
     */
    fun applySortKeys(keys: List<SortKey>?) {
        val current = sorter ?: return
        if (keys == null) return
        current.sortKeys = keys.filter { it.column in 0 until current.model.columnCount }
    }

    /**
     * Takes the sorter off [table] where the model it was built for is not the [model] the table is about to
     * hold. Left in place, it would answer that model's events over row and column counts it never had. Call
     * it from the install this brackets, right before the table takes the model.
     */
    fun unbindFrom(
        table: JTable,
        model: TableModel,
    ) {
        if (table.model === model) return
        detach(table)
    }

    /**
     * Gives [table] new content through [install] over the sorter [sortable] asks for, puts the sorting
     * rules of [columns] on that sorter, and leaves the rows in the order that should stand: [declared]
     * where the caller declares one, and otherwise the order they were in before.
     *
     * Taking a sorter on or off empties the table's selection, and new content resets the order its rows
     * were in, which is why this belongs inside the write that puts the selection back and why the order is
     * put back here - after the columns' own rules, since those are what the ordering is worked out by. All
     * of it runs as this channel's own write, so nothing the sorter publishes for it is reported as the
     * user's.
     */
    fun preserveAcross(
        table: JTable,
        sortable: Boolean,
        declared: List<SortKey>?,
        columns: List<ColumnDeclaration<*>> = emptyList(),
        install: () -> Unit = {},
    ) {
        val retained = declared ?: sortKeys()
        applied.write {
            install()
            bind(table, sortable)
            sorter?.let { current ->
                columns.forEachIndexed { index, column ->
                    current.setSortable(index, column.isSortable)
                    current.setComparator(index, column.comparator)
                }
            }
            applySortKeys(retained)
        }
        applied.observed(sortKeys())
    }

    /**
     * Puts [rowFilter] onto the sorter. A filter re-orders and re-filters every row it is handed to, so it
     * is written only where the caller declares another one than the sorter is already filtering by.
     */
    fun applyRowFilter(rowFilter: RowFilter<in TableModel, in Int>?) {
        val current = sorter ?: return
        if (current.rowFilter === rowFilter) return
        applied.write { current.rowFilter = rowFilter }
    }

    /** Builds the sorter [table] is missing, or takes away the one it should no longer have. */
    private fun bind(
        table: JTable,
        sortable: Boolean,
    ) {
        if (!sortable) {
            detach(table)
            return
        }
        val held = sorter
        if (held != null && held === table.rowSorter && held.model === table.model) return
        detach(table)
        val fresh = TableRowSorter(table.model)
        sorter = fresh
        fresh.addRowSorterListener(listener)
        table.rowSorter = fresh
    }

    private fun detach(table: JTable) {
        sorter?.removeRowSorterListener(listener)
        sorter = null
        if (table.rowSorter != null) table.rowSorter = null
    }
}

/**
 * Puts [rowFilter] onto the table through [sortChannel], inside the write that puts the table's selection
 * back: a filter takes the rows it hides out of the selection, so what a selection [declared] by the caller
 * loses to it is re-asserted the moment the filter admits the row again, and what an undeclared one loses is
 * gone for good and is handed to [target] once.
 *
 * This settles the table's row selection as well - putting the selection back is the whole of what
 * [declare] would do for it - so a table declares its selection through this call and not a second time.
 */
internal fun SwingNodeUpdater<JTable>.declareRowFilter(
    sortChannel: RowSortChannel,
    rowFilter: RowFilter<in TableModel, in Int>?,
    applied: AppliedValue<Set<Int>?>,
    declared: Set<Int>?,
    target: ListSelectionListener,
) {
    // The filter, the declared selection put back around it, and the selection the table itself holds move
    // independently, and one install answers for all three: they are one key. A filter the sorter already
    // has is not written again, so a pass that only the selection moved puts the selection back and does
    // nothing else.
    set(Triple(rowFilter, declared, applied.current)) {
        installContent(applied, declared, target) { sortChannel.applyRowFilter(rowFilter) }
    }
}

/** A [RowSortChannel] that keeps reporting to the latest [listener] without being rebuilt. */
@Composable
internal fun rememberRowSortChannel(
    applied: AppliedValue<List<SortKey>?>,
    listener: RowSorterListener?,
): RowSortChannel {
    val target = rememberUpdatedState(listener)
    return remember(applied) { RowSortChannel(applied, target) }
}

/**
 * A stable [RowSorterListener] that forwards the order the rows were sorted into to [onSortChange], bridging
 * a lambda-based table overload to the raw-listener overload it delegates to. A sort event's source is the
 * sorter, so the order is read back from it.
 *
 * Only a change of the sort order describes that order; the event a re-sort of unchanged keys publishes
 * carries no new one.
 */
@Composable
internal fun rememberSortKeysListener(onSortChange: (List<SortKey>) -> Unit): RowSorterListener {
    val callback = rememberUpdatedState(onSortChange)
    return remember {
        RowSorterListener { event ->
            if (event.type == RowSorterEvent.Type.SORT_ORDER_CHANGED) callback.value(event.source.sortKeys.toList())
        }
    }
}

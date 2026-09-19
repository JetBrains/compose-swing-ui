package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import java.awt.Component
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
 * [mirror] mirrors the order the rows are in, which is what makes a header click an ordinary composition
 * dependency and what tells that click from the writes this channel makes itself.
 */
internal class RowSortChannel(
    private val mirror: MirrorState<List<SortKey>?>,
    target: State<RowSorterListener?>,
) {
    private var sorter: TableRowSorter<TableModel>? = null

    /** The filter this channel was last declared with, which every sorter it builds starts out on. */
    private var declaredFilter: RowFilter<in TableModel, in Int>? = null

    /**
     * The comparators, by column, this channel last put on the sorter in place; see [isRecompared]. Held
     * in a reused array, since it is read and written on every pass.
     */
    private var writtenComparators = arrayOfNulls<Comparator<Any?>>(0)

    /**
     * Whether [filter] differs from the one this channel was last declared with, recording it either way.
     * The record answers for whatever sorter is in place, since a sorter this channel builds starts out on
     * it.
     */
    fun redeclareRowFilter(filter: RowFilter<in TableModel, in Int>?): Boolean {
        if (filter == declaredFilter) return false
        declaredFilter = filter
        return true
    }

    /** Reports the user's own sort-order changes. Installed on every sorter this channel builds. */
    private val listener =
        RowSorterListener { event ->
            if (event.type == RowSorterEvent.Type.SORT_ORDER_CHANGED) mirror.observed(event.source.sortKeys.toList())
            if (!mirror.isWriting) target.value?.sorterChanged(event)
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
     * put back here - after the columns' own rules, since those are what the ordering is worked out by. The
     * write and the read recording the order it left the rows in are one settlement of the mirror, so
     * nothing the sorter publishes for it is reported as the user's, and the order it landed on is an
     * answer rather than news.
     */
    fun preserveAcross(
        table: JTable,
        sortable: Boolean,
        declared: List<SortKey>?,
        columns: List<ColumnDeclaration<*>> = emptyList(),
        install: () -> Unit = {},
    ) {
        val retained = declared ?: sortKeys()
        mirror.settle {
            mirror.write {
                install()
                bind(table, sortable)
                sorter?.let { current ->
                    // The order the rows are in ahead of the one this pass leaves them in.
                    // `DefaultRowSorter.getSortKeys` hands back the list it holds and `setSortKeys` puts
                    // a new one in its place, so this stays what the sorter was on.
                    val standing = current.sortKeys
                    var resortDue = false
                    growRecord(columns.size)
                    columns.forEachIndexed { index, column ->
                        current.setSortable(index, column.isSortable)
                        if (isRecompared(current, index, column.comparator)) {
                            current.setComparator(index, column.comparator)
                            writtenComparators[index] = column.comparator
                            resortDue = resortDue || standing.any { it.column == index }
                        }
                    }
                    applySortKeys(retained)
                    // A sorter only stores a comparator, so a column sorted by a new one keeps the order
                    // the old one produced until it is told to sort again. An order that changed here has
                    // already sorted by the new comparators, which is why the two are told apart.
                    if (resortDue && current.sortKeys == standing) sortKeepingAnchor(table, current)
                }
            }
            answered(sortKeys())
        }
    }

    /**
     * Whether [comparator] is other than the one column [index] of [sorter] is ordered by.
     *
     * A declared comparator is asked of the sorter, which hands back what it was given. An undeclared one
     * is answered from [writtenComparators]: `TableRowSorter.getComparator` answers for a column it was
     * given nothing for with a comparator of its own, so a `null` declaration would otherwise read as a
     * change on every pass. The sorter's own answer still decides where it has one, since a model
     * structure change drops the comparators it holds - `DefaultRowSorter.modelStructureChanged` clears
     * them - while the record stands.
     *
     * The record holds column [index], which [growRecord] answers for.
     *
     * Identity is the whole of the comparison: two comparators carry no equality to compare by, so an
     * equal ordering under a new instance cannot be told from a new one.
     */
    private fun isRecompared(
        sorter: TableRowSorter<TableModel>,
        index: Int,
        comparator: Comparator<Any?>?,
    ): Boolean =
        if (comparator != null) {
            sorter.getComparator(index) !== comparator
        } else {
            writtenComparators[index] != null
        }

    /**
     * Grows the record to hold [columns] columns. It is never shrunk: a column count changes through a
     * structure change, which leaves the sorter unsorted, so an entry left over past the columns can only
     * have this channel write a `null` comparator the sorter is already on.
     */
    private fun growRecord(columns: Int) {
        if (writtenComparators.size < columns) writtenComparators = writtenComparators.copyOf(columns)
    }

    /**
     * Puts [rowFilter] onto the sorter. A filter re-orders and re-filters every row it is handed to, so it
     * is written only where the caller declares another one than the sorter is already filtering by.
     */
    fun applyRowFilter(rowFilter: RowFilter<in TableModel, in Int>?) {
        val current = sorter ?: return
        if (current.rowFilter === rowFilter) return
        mirror.write { current.rowFilter = rowFilter }
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
        // A filter stands only as long as the sorter carrying it, so a sorter built here starts out on the
        // declared one: an unchanged declaration is never written again, and would otherwise be left behind
        // with the sorter that came off. Filtering it before the table takes it is what spares the table
        // the rows this filter rejects.
        declaredFilter?.let { fresh.rowFilter = it }
        sorter = fresh
        writtenComparators.fill(null)
        fresh.addRowSorterListener(listener)
        // The table holds no sorter here, so an editor's row is the model row it stands on, and the sorter
        // it is about to take answers where that row lands.
        endEditWhereTheRowMoves(table) { fresh.convertRowIndexToView(it) }
        table.rowSorter = fresh
    }

    /**
     * Takes the sorter off [table]. Without it the rows are drawn in the order the model holds them, which
     * is what the sorter's own mapping answers for the row an editor stands on.
     */
    private fun detach(table: JTable) {
        sorter?.removeRowSorterListener(listener)
        sorter = null
        val standing = table.rowSorter ?: return
        endEditWhereTheRowMoves(table) { standing.convertRowIndexToModel(it) }
        table.rowSorter = null
    }

    /**
     * Ends an edit [table] is showing where the sorter it is about to take or lose draws the edited row
     * somewhere else, [movedTo] answering where. An editor names a row of the view and commits into
     * whatever that row resolves to when it stops, and a swap publishes no event for the table to follow
     * one by - `JTable.setRowSorter` disposes the sort manager and clears the selection, leaving the
     * editing row naming a row of the model it was never opened on. A row drawn where it already was
     * keeps its edit, as it does across a re-sort the table does hear about.
     */
    private inline fun endEditWhereTheRowMoves(
        table: JTable,
        movedTo: (Int) -> Int,
    ) {
        if (!table.isEditing) return
        val editing = table.editingRow
        if (editing !in 0 until table.rowCount || movedTo(editing) != editing) table.endEdit()
    }
}

/**
 * Sorts [sorter]'s rows again, leaving the table's selection anchored on the row it was anchored on.
 *
 * A sort restores the selection through `JTable.restoreSortingSelection`, which captures the lead row
 * alone and puts the anchor on it, so a selection of more than one row comes out of the sort anchored
 * on its lead. The anchor is carried across in the model's row space, which is what makes it the same
 * row where the order genuinely changes. A header click of the user's own collapses the anchor the
 * same way, and is left to: that is what a `JTable` does.
 *
 * `setAnchorSelectionIndex` publishes a selection event, which belongs to the write this runs inside;
 * an anchor that did not move is written back unchanged, and a selection model publishes nothing for
 * that.
 */
private fun sortKeepingAnchor(
    table: JTable,
    sorter: TableRowSorter<TableModel>,
) {
    val selection = table.selectionModel
    val anchor = selection.anchorSelectionIndex
    // The anchor names a row of the view, which one left past the rows the sorter admits is not.
    val anchorRow = if (anchor in 0 until sorter.viewRowCount) sorter.convertRowIndexToModel(anchor) else -1
    sorter.sort()
    // A row the order this sort landed on hides has no view row left to anchor on.
    val landed = if (anchorRow >= 0) sorter.convertRowIndexToView(anchorRow) else -1
    if (landed >= 0) selection.anchorSelectionIndex = landed
}

/**
 * Puts [rowFilter] onto the table through [sortChannel], inside the write that puts the table's selection
 * back: a filter takes the rows it hides out of the selection, so what a selection [declared] by the caller
 * loses to it is re-asserted the moment the filter admits the row again, and what an undeclared one loses is
 * gone for good and is handed to [target] once.
 *
 * This settles the table's row selection as well - putting the selection back is the whole of what
 * [org.jetbrains.compose.swing.node.declare] would do for it - so a table declares its selection through
 * this call and not a second time.
 */
internal fun SwingNodeUpdater<JTable>.declareRowFilter(
    sortChannel: RowSortChannel,
    rowFilter: RowFilter<in TableModel, in Int>?,
    mirror: MirrorState<Set<Int>?>,
    declared: Set<Int>?,
    target: ListSelectionListener,
) {
    // The filter, the declared selection put back around it, and the selection the table itself holds
    // change independently, and one install answers for all three. The filter is compared by value; the selection
    // is compared in place on its mirror, which holds the pairing the last install left the table on rather
    // than the one this pass happened to read - so a change the user repeats is answered every time they
    // make
    // it. Both are redeclared whatever either answers, so each records the pairing this pass makes. A filter
    // the sorter already has is not written again, so an install the selection alone asked for puts the
    // selection back and does nothing else.
    val selectionChanged = mirror.redeclare(declared)
    val filterChanged = sortChannel.redeclareRowFilter(rowFilter)
    settleWhenDue(selectionChanged || filterChanged, { RowFilterInstall(rowFilter) }) { due ->
        installContent(mirror, declared, target) { sortChannel.applyRowFilter(due.filter) }
    }
}

/**
 * Carries the settlement [token] builds when [due], and runs [settle] against the component with it once
 * the composition applies its changes. Nothing runs on a pass where nothing is due.
 *
 * This is how a declaration whose settling depends on more than its own value reaches its widget: what such
 * a pass is compared against lives on the mirrors it settles rather than in the composition, so the slot
 * carries a token standing for a due settlement instead of the declaration behind it. A fresh token is
 * never what the slot already holds, so a settle that is due always runs; the one comparison the slot can
 * hold back is the null that follows a settlement, and that pass has nothing to do.
 *
 * The slot is taken whether or not anything is due, so a call inside a conditional shifts every later slot
 * of the same `update` block. State the condition in [due], not in whether the call happens.
 */
internal inline fun <C : Component, D : Any> SwingNodeUpdater<C>.settleWhenDue(
    due: Boolean,
    token: () -> D,
    crossinline settle: C.(D) -> Unit,
): Unit = set(if (due) token() else null) { pending -> if (pending != null) settle(pending) }

/** One due install of a row filter: the filter to leave the sorter on. */
private class RowFilterInstall(
    val filter: RowFilter<in TableModel, in Int>?,
)

/** A [RowSortChannel] that keeps reporting to the latest [listener] without being rebuilt. */
@Composable
internal fun rememberRowSortChannel(
    mirror: MirrorState<List<SortKey>?>,
    listener: RowSorterListener?,
): RowSortChannel {
    val target = rememberUpdatedState(listener)
    return remember { RowSortChannel(mirror, target) }
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

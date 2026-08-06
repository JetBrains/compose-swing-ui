package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.node.AppliedWrite
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumnModel

/**
 * One table's column-layout channel: the layout the caller and the table currently agree on, and the
 * [listener] through which the user's own reorders and resizes reach the caller's [target] listener.
 *
 * A table publishes a margin change for every width it derives from its columns' preferred widths as well
 * as for a preferred width a resize drag changed, and it derives those widths afresh at every layout pass.
 * An event is therefore news only when the layout it leaves behind differs from the one already agreed -
 * which is what keeps a window resize, which changes every column's width and no column's layout, silent.
 */
internal class ColumnLayoutChannel(
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
internal fun rememberColumnLayoutChannel(listener: TableColumnModelListener?): ColumnLayoutChannel {
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
internal fun rememberColumnLayoutListener(onColumnLayoutChange: (TableColumnLayout) -> Unit): TableColumnModelListener {
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

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.settleWhenDue
import javax.swing.JTable
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumnModel

/**
 * One table's column-layout channel: the [listener] through which the user's own reorders and resizes
 * reach the caller's [target] listener, mirrored into [applied] the way every two-way property is.
 *
 * A table publishes a margin change for every width it derives from its columns' preferred widths as well
 * as for a preferred width a resize drag changed, and it derives those widths afresh at every layout pass.
 * An event is therefore news only when the layout it leaves behind differs from the one the mirror holds -
 * which is what keeps a window resize, which changes every column's width and no column's layout, silent.
 */
internal class ColumnLayoutChannel(
    private val applied: AppliedValue<TableColumnLayout?>,
    private val target: State<TableColumnModelListener?>,
) {
    /**
     * Reports the user's own column reorders and resizes, and mirrors every layout the columns are left in
     * - this wrapper's own writes included, so the mirror answers with what the columns hold now. Install
     * it on the table's column model.
     */
    val listener: ColumnLayoutMirror =
        object : ColumnLayoutMirror {
            // A table handed another column model publishes whatever layout that model arrives in. It is
            // the caller's own doing, so it is mirrored rather than reported back - and the mirror moving
            // is what has the next pass put the declaration onto the model that arrived.
            override fun adoptModelSwap(model: TableColumnModel) {
                applied.observed(model.readColumnLayout())
            }

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

    /**
     * Settles the columns of [columns] on [declared], and records the layout they were left in as the one
     * this pass answered for. A `null` declaration leaves the columns where they are.
     *
     * The whole of it is one settlement of the mirror, so the layout the columns are left in is not news:
     * this pass asked for it and read it back.
     */
    fun settle(
        columns: TableColumnModel,
        declared: TableColumnLayout?,
    ) {
        applied.settle {
            applied.write { columns.applyColumnLayout(declared) }
            answered(columns.layoutHeld(applied.value))
        }
    }

    /**
     * Runs [install] - a change that rebuilds the table's columns and so drops the order and the widths
     * they were in - and puts the layout back afterwards: [declared] where the caller declares one, and
     * otherwise the layout the columns were in. A column layout is owned the way a selection is; see
     * [installNarrowing] for the rule and what follows from it.
     *
     * A column that no longer exists cannot hold the part of the layout that named it, so restoring the
     * layout must follow the rebuild that creates the columns and runs as [applied]'s own write.
     *
     * [install] marks its own writes through [applied] too, so the losses it has to report itself still
     * reach the caller.
     */
    fun preserveAcross(
        columns: TableColumnModel,
        declared: TableColumnLayout?,
        install: () -> Unit,
    ) {
        val lost =
            applied.settle {
                val retained = declared ?: columns.layoutHeld(applied.value)
                install()
                applied.write { columns.applyColumnLayout(retained) }
                val settled = columns.layoutHeld(retained)
                answered(settled)
                declared == null && !settled.holds(retained)
            }
        // The columns are put back as this wrapper's own write, so nothing they publish carries the loss
        // out; a margin change over the model they are left in is how the caller hears what they were left
        // holding.
        if (lost) dispatchToCaller { target.value?.columnMarginChanged(ChangeEvent(columns)) }
    }

    /**
     * Mirrors the layout [columns] are in and hands it to the caller's listener through [deliver], unless
     * the mirror already held it or this wrapper's own write left the columns in it.
     */
    private fun report(
        columns: TableColumnModel,
        deliver: (TableColumnModelListener) -> Unit,
    ) {
        val settled = columns.layoutHeld(applied.value)
        if (applied.observed(settled)) target.value?.let(deliver)
    }
}

/**
 * [held] where the columns of [this] model are already in it, and a fresh [readColumnLayout] snapshot
 * otherwise. Every column event answers this, and most answer with the layout already held: a table
 * derives its columns' widths afresh at every layout pass and publishes a margin change for each, so the
 * in-place walk is what keeps a window resize - which changes every width and no column's layout - free of
 * both a report and a snapshot.
 */
private fun TableColumnModel.layoutHeld(held: TableColumnLayout?): TableColumnLayout =
    held?.takeIf { it.holdsInPlace(this) } ?: readColumnLayout()

/** Whether [columns] are already in [this] layout, walked column by column against the layout's own lists. */
private fun TableColumnLayout.holdsInPlace(columns: TableColumnModel): Boolean =
    columns.columnCount == modelIndices.size &&
        modelIndices.indices.all { position ->
            val column = columns.getColumn(position)
            column.modelIndex == modelIndices[position] && column.preferredWidth == preferredWidths[position]
        }

/**
 * Settles the table's columns on [columnLayout] whenever the declaration or the layout the columns are in
 * has moved since the pair this mirror last answered for, and does nothing at all on a pass where neither
 * did. Reading the mirror here is what subscribes the composition to a user's own reorder or resize, so a
 * declared layout is put back on the pass that follows their moving away from it.
 */
internal fun SwingNodeUpdater<JTable>.declareColumnLayout(
    applied: AppliedValue<TableColumnLayout?>,
    columnLayout: TableColumnLayout?,
    channel: ColumnLayoutChannel,
) {
    settleWhenDue(applied.redeclare(columnLayout), { ColumnLayoutSettlement(columnLayout) }) { due ->
        channel.settle(columnModel, due.layout)
    }
}

/** One due settlement of a table's column layout: the layout to leave the columns in. */
private class ColumnLayoutSettlement(
    val layout: TableColumnLayout?,
)

/** A [ColumnLayoutChannel] that keeps reporting to the latest [listener] without being rebuilt. */
@Composable
internal fun rememberColumnLayoutChannel(
    applied: AppliedValue<TableColumnLayout?>,
    listener: TableColumnModelListener?,
): ColumnLayoutChannel {
    val target = rememberUpdatedState(listener)
    return remember { ColumnLayoutChannel(applied, target) }
}

/**
 * The [TableColumnModelListener] forwarding the layout the columns were left in to
 * [onColumnLayoutChange], bridging a lambda-based [Table] overload to the raw-listener overload it
 * delegates to. A column event's source is the column model, so the layout is read back from it.
 *
 * Rebuilt per pass rather than remembered: it is never registered on a component, and a
 * [ColumnLayoutChannel] reads its target listener live.
 */
internal fun columnLayoutListener(onColumnLayoutChange: (TableColumnLayout) -> Unit): TableColumnModelListener =
    object : TableColumnModelListener {
        override fun columnAdded(event: TableColumnModelEvent) = Unit

        override fun columnRemoved(event: TableColumnModelEvent) = Unit

        override fun columnMoved(event: TableColumnModelEvent) = report(event.source)

        override fun columnMarginChanged(event: ChangeEvent) = report(event.source)

        override fun columnSelectionChanged(event: ListSelectionEvent) = Unit

        private fun report(source: Any) = onColumnLayoutChange((source as TableColumnModel).readColumnLayout())
    }

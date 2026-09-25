package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.node.MirrorState
import javax.swing.JTable
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumnModel

/**
 * One table's column-layout channel: the [listener] through which the user's own reorders and resizes
 * reach the caller's [target] listener, mirrored into [mirror] the way every two-way property is.
 *
 * A table publishes a margin change for every width it derives from its columns' preferred widths as well
 * as for a preferred width a resize drag changed, and it derives those widths afresh at every layout pass.
 * An event is therefore news only when the layout it leaves behind differs from the one the mirror holds -
 * which is what keeps a window resize, which changes every column's width and no column's layout, silent.
 */
internal class ColumnLayoutChannel(
    private val mirror: MirrorState<TableColumnLayout?>,
    private val target: State<TableColumnModelListener?>,
) {
    /**
     * The layout last applied to the columns or adopted from them - by [applyHeld], by the user's reorder
     * or resize, by a rebuild that took part of the user's layout away, or by a column model the caller
     * swaps in - against which a [TableState]'s layout is judged due. A user's change is stamped as it is
     * written back, so it is never applied again as if the state had been assigned, and a state assigned
     * back to the layout it held before that change is still applied.
     */
    var appliedLayout: TableColumnLayout? = null
        private set

    /**
     * Reports the user's own column reorders and resizes, and mirrors every layout the columns are left in
     * - this wrapper's own writes included, so the mirror answers with what the columns hold now. Install
     * it on the table's column model.
     */
    val listener: ColumnLayoutMirror =
        object : ColumnLayoutMirror {
            // A table handed another column model publishes whatever layout that model arrives in. It is
            // the caller's own doing, so it is mirrored and stamped rather than reported back.
            override fun adoptModelSwap(model: TableColumnModel) {
                val arrived = model.readColumnLayout()
                mirror.observed(arrived)
                appliedLayout = arrived
            }

            // A column added or removed is the table rebuilding its columns, never a user gesture: no
            // header drag adds or removes one. A rebuild that a pass of the composition drives has the
            // layout it left behind settled by preserveAcross; one that a caller's own mutation of the
            // model drives reaches the caller through the margin changes the new columns publish.
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
     * Runs [install] - a change that rebuilds the table's columns and so drops the order and the widths
     * they were in - and puts the layout back afterwards: [held] where a [TableState] holds one, and
     * otherwise the layout the columns were in. A layout the state holds stays the state's even where the
     * new columns cannot hold all of it; what is lost of the columns' own layout is reported, the way
     * [installNarrowing] reports a lost selection.
     *
     * A column that no longer exists cannot hold the part of the layout that named it, so restoring the
     * layout must follow the rebuild that creates the columns and runs as [mirror]'s own write.
     *
     * [install] marks its own writes through [mirror] too, so the losses it has to report itself still
     * reach the caller.
     */
    fun preserveAcross(
        table: JTable,
        held: TableColumnLayout?,
        install: () -> Unit,
    ) {
        val columns = table.columnModel
        val lost =
            mirror.settle {
                val retained = held ?: columns.layoutHeld(mirror.value)
                install()
                mirror.write { table.applyColumnLayout(retained) }
                val settled = columns.layoutHeld(retained)
                answered(settled)
                if (held == null && !settled.holds(retained)) settled else null
            }
        // The columns are put back as this wrapper's own write, so nothing they publish carries the loss
        // out; a margin change over the model they are left in is how the caller hears what they were left
        // holding.
        if (lost != null) {
            appliedLayout = lost
            dispatchToCaller { target.value?.columnMarginChanged(ChangeEvent(columns)) }
        }
    }

    /**
     * Puts [table]'s columns into [layout], the layout a [TableState] was assigned. A `null` layout leaves
     * the columns where they are.
     */
    fun applyHeld(
        table: JTable,
        layout: TableColumnLayout?,
    ) {
        if (layout == appliedLayout) return
        mirror.settle {
            mirror.write { table.applyColumnLayout(layout) }
            answered(table.columnModel.layoutHeld(mirror.value))
        }
        appliedLayout = layout
    }

    /**
     * Mirrors the layout [columns] are in and hands it to the caller's listener through [deliver], unless
     * the mirror already held it or this wrapper's own write left the columns in it.
     */
    private fun report(
        columns: TableColumnModel,
        deliver: (TableColumnModelListener) -> Unit,
    ) {
        val settled = columns.layoutHeld(mirror.value)
        if (mirror.observed(settled)) {
            appliedLayout = settled
            target.value?.let(deliver)
        }
    }
}

/**
 * [mirrored] where the columns of [this] model are already in it, and a fresh [readColumnLayout] snapshot
 * otherwise. Every column event answers this, and most answer with the layout already held: a table
 * derives its columns' widths afresh at every layout pass and publishes a margin change for each, so the
 * in-place walk is what keeps a window resize - which changes every width and no column's layout - free of
 * both a report and a snapshot.
 */
private fun TableColumnModel.layoutHeld(mirrored: TableColumnLayout?): TableColumnLayout =
    mirrored?.takeIf { it.holdsInPlace(this) } ?: readColumnLayout()

/** Whether [columns] are already in [this] layout, walked column by column against the layout's own lists. */
private fun TableColumnLayout.holdsInPlace(columns: TableColumnModel): Boolean =
    columns.columnCount == modelIndices.size &&
        modelIndices.indices.all { position ->
            val column = columns.getColumn(position)
            column.modelIndex == modelIndices[position] && column.preferredWidth == preferredWidths[position]
        }

/** A [ColumnLayoutChannel] that keeps reporting to the latest [listener] without being rebuilt. */
@Composable
internal fun rememberColumnLayoutChannel(
    mirror: MirrorState<TableColumnLayout?>,
    listener: TableColumnModelListener?,
): ColumnLayoutChannel {
    val target = rememberUpdatedState(listener)
    return remember { ColumnLayoutChannel(mirror, target) }
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

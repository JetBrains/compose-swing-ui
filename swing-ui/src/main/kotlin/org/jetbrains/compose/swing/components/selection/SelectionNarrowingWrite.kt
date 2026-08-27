package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.constants.SelectionMode
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.node.MirrorState
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreePath

/*
 * The two writes a selection component makes to its widget that the widget answers by dropping selection,
 * and the ownership rule both follow.
 *
 * A declared selection is the composition's state, re-asserted on every pass: what such a write leaves of it
 * is the composition's own doing, and reporting it would hand the caller's state holder a selection the user
 * never chose, for the next pass to apply in place of the declaration. An undeclared selection is the user's
 * alone and the part of it the write drops is gone for good, so the loss reaches the caller exactly once,
 * handed over by the wrapper itself once the write has returned. Both writes run as the wrapper's own, so
 * the widget's event for them is recognized as such rather than reported a second time as the user's.
 *
 * Handing it over rather than leaving it to the widget's own event is what makes the loss reach the caller
 * on the pass that reactivates a parked node: the listeners a modifier installs are detached there, and the
 * pass applies these writes before it reinstalls them.
 */

/**
 * Runs [block] - a property write its widget answers by dropping selection the property no longer lets it
 * hold - as the wrapper's own write, and where [declaredSelection] is absent hands the part of the user's
 * selection the write dropped to [report]. [selection] reads what the widget currently holds.
 *
 * [report] reaches the caller's own listener directly rather than from inside a write, so it runs contained
 * the same way, and a throw out of it is reported rather than left to end the composition.
 */
internal fun <S> MirrorState<Set<S>?>.writeNarrowing(
    declaredSelection: Set<S>?,
    selection: () -> Set<S>,
    report: (Set<S>) -> Unit,
    block: () -> Unit,
) {
    if (declaredSelection != null) {
        write(block)
        return
    }
    val held = selection()
    write(block)
    val settled = selection()
    observed(settled)
    val lost = held - settled
    if (lost.isNotEmpty()) dispatchToCaller { report(lost) }
}

/**
 * Gives a widget new content through [install] and leaves it holding the selection that should stand:
 * [declared] where the caller declares one, and otherwise the selection the widget held before, read through
 * [selection] and put back through [apply]. New content drops a widget's selection, and the selection is not
 * the library's to destroy.
 *
 * Content the user's selection reaches past is the one case where part of that selection is gone for good.
 * Putting the selection back has to follow the install that drops it, so it runs as this wrapper's own
 * write, and the part of the selection the new content could not hold is handed to [report] once that
 * write has returned, the same way [writeNarrowing] hands off a loss.
 *
 * The mirror is read back through [selection] once the write is done, the same way [MirrorState.settle]
 * does - a listener attached to the widget would record the same value as it happens, but this holds
 * regardless of whether one is attached to catch it. That one read is both what the mirror records and
 * what the loss is measured against: nothing between them touches the widget, so a second read would
 * walk the same selection to the same answer. This runs on every pass a filter is reconciled over, and a
 * selection read is a walk of every selected row.
 *
 * The whole of it is one settlement of the mirror, so the selection the widget is left on invalidates
 * nothing: this pass chose that selection, put it back and read it, and there is no more for a pass of
 * its own to do about it. Left as an unanswered change, it would invalidate whoever read the mirror to
 * build this
 * install and have the same content installed a second time to reach the same widget.
 */
internal fun <S> MirrorState<Set<S>?>.installNarrowing(
    declared: Set<S>?,
    selection: () -> Set<S>,
    apply: (Set<S>) -> Unit,
    report: (Set<S>) -> Unit,
    install: () -> Unit,
) {
    val lost =
        settle {
            val retained = declared ?: selection()
            write {
                install()
                apply(retained)
            }
            val settled = selection()
            answered(settled)
            if (declared == null) retained - settled else emptySet()
        }
    if (lost.isNotEmpty()) dispatchToCaller { report(lost) }
}

/**
 * Applies through [block] a property the list answers by dropping rows the property no longer lets it hold,
 * reporting to [target] the rows the user loses to it where [declared] leaves the selection theirs. See
 * [writeNarrowing].
 */
internal fun JList<*>.narrowSelection(
    mirror: MirrorState<Set<Int>?>,
    declared: Set<Int>?,
    target: ListSelectionListener,
    block: () -> Unit,
): Unit =
    mirror.writeNarrowing(
        declaredSelection = declared,
        selection = { selectedIndices.toSet() },
        report = { lost -> reportLostRows(target, lost) },
        block = block,
    )

/**
 * Tells [target] that [lost] left the list's selection.
 *
 * A list re-fires its selection model's event as its own, with itself as the source, and that is the event a
 * listener installed on the list is handed. The lowest and the highest of the rows that left the selection
 * bound the range the event describes as changed.
 */
internal fun JList<*>.reportLostRows(
    target: ListSelectionListener,
    lost: Set<Int>,
) = target.valueChanged(ListSelectionEvent(this, lost.min(), lost.max(), false))

/**
 * The rows [this] table has selected, named in the model's own row space - the space a [Table]'s declared
 * and reported row selection is expressed in. A screen row is a position that a sort order and a row filter
 * both move, so the two spaces part company the moment either is in play; a table with neither shows the
 * model row by row, which makes them the same numbers.
 */
internal fun JTable.selectedModelRows(): Set<Int> {
    // `getSelectedRows` hands out an array of its own, so the conversion is done in that array: one walk
    // of the selection and one boxing of it, on a path reconciled over every pass.
    val rows = selectedRows
    for (index in rows.indices) rows[index] = convertRowIndexToModel(rows[index])
    return rows.toSet()
}

/**
 * Puts the table's rows and columns in selection [mode] by writing it to the two selection models the mode
 * belongs to.
 *
 * A selection model narrows a selection only as far as the new mode forces: a mode that holds one row keeps
 * the first row that was selected, a wider mode keeps the whole selection, and the mode a model is already in
 * changes nothing - which matters because the mode a caller declares is the composition's state and arrives
 * again on every pass, including the one that reactivates a parked child onto the table it built. The table's
 * own `setSelectionMode` empties the selection before it changes either model, and the selection is not the
 * library's to empty: a list and a table declared alike answer a narrower mode alike.
 */
internal fun JTable.applySelectionMode(
    @SelectionMode mode: Int,
) {
    selectionModel.selectionMode = mode
    columnModel.selectionModel.selectionMode = mode
}

/**
 * Applies through [block] a property the table answers by dropping rows the property no longer lets it hold,
 * reporting to [target] the rows the user loses to it where [declared] leaves the selection theirs. See
 * [writeNarrowing].
 */
internal fun JTable.narrowSelection(
    mirror: MirrorState<Set<Int>?>,
    declared: Set<Int>?,
    target: ListSelectionListener,
    block: () -> Unit,
): Unit =
    mirror.writeNarrowing(
        declaredSelection = declared,
        selection = { selectedModelRows() },
        report = { lost -> reportLostRows(target, lost) },
        block = block,
    )

/**
 * Tells [target] that [lost] left the table's selection, as a selection event of the table's own - the event
 * a table's raw listener is handed. The lowest and the highest of the rows that left the selection bound the
 * range the event describes as changed; that range names model rows, the only space left for a row the table
 * no longer shows.
 */
internal fun JTable.reportLostRows(
    target: ListSelectionListener,
    lost: Set<Int>,
) = target.valueChanged(ListSelectionEvent(this, lost.min(), lost.max(), false))

/**
 * Applies through [block] a property the tree answers by dropping nodes the property no longer lets it hold,
 * reporting to [target] the nodes the user loses to it where [declared] leaves the selection theirs. See
 * [writeNarrowing].
 */
internal fun JTree.narrowSelection(
    mirror: MirrorState<Set<List<Int>>?>,
    declared: Set<List<Int>>?,
    target: TreeSelectionListener,
    block: () -> Unit,
) {
    val held = heldSelection(named = declared == null)
    mirror.writeNarrowing(
        declaredSelection = declared,
        selection = { readSelection(this, model) },
        report = { lost -> reportLostPaths(target, held.nodes, held.indices, lost, held.lead) },
        block = block,
    )
}

/**
 * The tree's selection as it stood before a write that can narrow it: the selected [nodes], the [indices]
 * naming the same nodes as index paths in the structure the write starts from, and the [lead] the selection
 * was led from. A loss is reported as the paths the caller knows the nodes by, and is recognized by the
 * indices, resolved against whatever structure the write leaves.
 */
internal class HeldSelection(
    val nodes: Array<out TreePath>,
    val indices: List<List<Int>>,
    val lead: TreePath?,
)

/**
 * The selection [this] tree holds now, walked for the nodes it names only where [named] asks for them.
 *
 * Naming every selected node is a walk of the model for each, and only a selection the caller has not
 * declared is measured against them: a declared one is the composition's state, so what a write takes off
 * it is the composition's own doing and is not reported node by node. The lead is a field read either way,
 * and every report names it whether the nodes were walked or not.
 */
internal fun JTree.heldSelection(named: Boolean): HeldSelection {
    val nodes = if (named) selectionPaths.orEmpty() else emptyArray()
    return HeldSelection(nodes, nodes.map { pathToIndices(model, it) }, leadSelectionPath)
}

/**
 * Tells [target] that [lost] left the tree's selection: the nodes among [selectedNodes] whose matching
 * entry in [selectedIndices] names one of the lost index paths, as removed from a selection event of the
 * tree's own, with [oldLead] and the tree's current lead path.
 *
 * A tree re-fires its selection model's event as its own, with itself as the source, and that is the event
 * a listener installed on the tree is handed.
 */
internal fun JTree.reportLostPaths(
    target: TreeSelectionListener,
    selectedNodes: Array<out TreePath>,
    selectedIndices: List<List<Int>>,
    lost: Set<List<Int>>,
    oldLead: TreePath?,
) {
    val nodes = selectedNodes.filterIndexed { position, _ -> selectedIndices[position] in lost }
    val removed = BooleanArray(nodes.size)
    target.valueChanged(TreeSelectionEvent(this, nodes.toTypedArray(), removed, oldLead, leadSelectionPath))
}

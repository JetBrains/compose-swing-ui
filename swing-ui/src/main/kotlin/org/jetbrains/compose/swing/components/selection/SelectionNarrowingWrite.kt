package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.node.AppliedValue
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener

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
internal fun <S> AppliedValue<List<S>?>.writeNarrowing(
    declaredSelection: List<S>?,
    selection: () -> List<S>,
    report: (List<S>) -> Unit,
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
    val lost = held.filterNot { it in settled }
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
 * write has returned - reporting it rather than leaving it to the widget's own event is what makes the
 * loss reach the caller on the pass that reactivates a parked node, where the listeners a modifier
 * installs are detached. [report] reaches the caller's own listener directly rather than from inside a
 * write, so it runs contained the same way, and a throw out of it is reported rather than left to end the
 * composition.
 *
 * The mirror is read back through [selection] once the write is done, the same way [AppliedValue.settle]
 * does - a listener attached to the widget would record the same value as it happens, but this holds
 * regardless of whether one is attached to catch it.
 */
internal fun <S> AppliedValue<List<S>?>.installNarrowing(
    declared: List<S>?,
    selection: () -> List<S>,
    apply: (List<S>) -> Unit,
    report: (List<S>) -> Unit,
    install: () -> Unit,
) {
    val retained = declared ?: selection()
    write {
        install()
        apply(retained)
    }
    observed(selection())
    if (declared != null) return
    val settled = selection()
    val lost = retained.filterNot { it in settled }
    if (lost.isNotEmpty()) dispatchToCaller { report(lost) }
}

/**
 * Applies through [block] a property the list answers by dropping rows the property no longer lets it hold,
 * reporting to [target] the rows the user loses to it where [declared] leaves the selection theirs. See
 * [writeNarrowing].
 */
internal fun JList<*>.narrowSelection(
    applied: AppliedValue<List<Int>?>,
    declared: List<Int>?,
    target: ListSelectionListener,
    block: () -> Unit,
): Unit =
    applied.writeNarrowing(
        declaredSelection = declared,
        selection = { selectedIndices.toList() },
        report = { lost -> reportLostRows(target, lost) },
        block = block,
    )

/**
 * Tells [target] that [lost] left the list's selection.
 *
 * A list re-fires its selection model's event as its own, with itself as the source, and that is the event a
 * listener installed on the list is handed. Both index lists are in ascending row order, so the rows that
 * left the selection span the range the event describes as changed.
 */
internal fun JList<*>.reportLostRows(
    target: ListSelectionListener,
    lost: List<Int>,
) = target.valueChanged(ListSelectionEvent(this, lost.first(), lost.last(), false))

/**
 * Applies through [block] a property the table answers by dropping rows the property no longer lets it hold,
 * reporting to [target] the rows the user loses to it where [declared] leaves the selection theirs. See
 * [writeNarrowing].
 */
internal fun JTable.narrowSelection(
    applied: AppliedValue<List<Int>?>,
    declared: List<Int>?,
    target: ListSelectionListener,
    block: () -> Unit,
): Unit =
    applied.writeNarrowing(
        declaredSelection = declared,
        selection = { selectedRows.toList() },
        report = { lost -> reportLostRows(target, lost) },
        block = block,
    )

/**
 * Tells [target] that [lost] left the table's selection. Both index lists are in ascending row order, so the
 * rows that left the selection span the range the event describes as changed.
 */
internal fun JTable.reportLostRows(
    target: ListSelectionListener,
    lost: List<Int>,
) = target.valueChanged(ListSelectionEvent(selectionModel, lost.first(), lost.last(), false))

/**
 * Applies through [block] a property the tree answers by dropping nodes the property no longer lets it hold,
 * reporting to [target] the nodes the user loses to it where [declared] leaves the selection theirs. See
 * [writeNarrowing].
 */
internal fun JTree.narrowSelection(
    applied: AppliedValue<List<List<Int>>?>,
    declared: List<List<Int>>?,
    target: TreeSelectionListener,
    block: () -> Unit,
) {
    val oldLead = leadSelectionPath
    // The nodes the tree has selected, alongside the index paths they resolve to: the paths name what a loss
    // has to be reported as, and the indices are what the loss is recognized by. The model stays as it is
    // across this write, so both keep resolving to the same nodes.
    val selectedNodes = selectionPaths.orEmpty()
    val selectedIndices = selectedNodes.map { pathToIndices(model, it) }
    applied.writeNarrowing(
        declaredSelection = declared,
        selection = { readSelection(this, model) },
        report = { lost ->
            // A tree re-fires its selection model's event as its own, with itself as the source, and that is
            // the event a listener installed on the tree is handed. The nodes are the ones the property took
            // out of the selection, so none of them is a node the event adds.
            val nodes = selectedNodes.filterIndexed { position, _ -> selectedIndices[position] in lost }
            val removed = BooleanArray(nodes.size)
            target.valueChanged(
                TreeSelectionEvent(this, nodes.toTypedArray(), removed, oldLead, leadSelectionPath),
            )
        },
        block = block,
    )
}

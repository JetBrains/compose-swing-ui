// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.runReportingUncaught
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreePath

/*
 * The path by which the nodes a `Tree` could not keep selected reach the caller: what a write took off an
 * undeclared selection, and what a collapse of the state's took over. Each is named as the path it was held
 * by, and handed over as a selection event of the tree's own.
 */

/**
 * Tells [target] that the nodes among [heldNodes] the tree no longer has selected left its selection, each
 * named as the path it was held by, with [oldLead] as the node the selection was led from before. Nothing is
 * reported for a write that took nothing away.
 */
internal fun JTree.reportDropped(
    target: TreeSelectionListener,
    heldNodes: Array<out TreePath>,
    oldLead: TreePath?,
) {
    val standing = selectionPaths.orEmpty().toHashSet()
    val dropped = heldNodes.filterNot { it in standing }
    if (dropped.isEmpty()) return
    runReportingUncaught { target.valueChanged(selectionLoss(dropped, oldLead)) }
}

/**
 * The selection event telling a listener that [nodes] left the tree's selection, with [oldLead] as the node
 * the selection was led from before and the tree's current lead path.
 *
 * A tree re-fires its selection model's event as its own, with itself as the source, and that is the event
 * a listener installed on the tree is handed.
 */
internal fun JTree.selectionLoss(
    nodes: List<TreePath>,
    oldLead: TreePath?,
): TreeSelectionEvent {
    // Every flag is false: each of the nodes was removed from the selection.
    val removed = BooleanArray(nodes.size)
    return TreeSelectionEvent(this, nodes.toTypedArray(), removed, oldLead, leadSelectionPath)
}

/**
 * Applies through [block] a property the tree answers by dropping nodes it can no longer hold, reporting to
 * [target] the ones the user loses to it where [declared] leaves the selection theirs, and leaves [mirror]
 * mirroring the selection the tree was left with.
 *
 * The read that records what survived the write stays outside any settlement of the mirror's, because what
 * it records is news: the tree has dropped nodes the composition may still declare, and the pass that puts
 * them back is the one this recording asks for. A settlement here would mark the loss as already answered
 * and leave a declared selection lying where the write dropped it.
 */
internal fun JTree.settleNarrowing(
    mirror: MirrorState<Set<List<Int>>?>,
    declared: Set<List<Int>>?,
    target: TreeSelectionListener,
    block: () -> Unit,
) {
    narrowSelection(mirror, declared, target, block)
    mirror.observed(readSelection(this, model))
}

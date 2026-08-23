package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.core.dispatchToCaller
import javax.swing.JTree
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreePath

/*
 * The path by which the nodes a `Tree` could not keep selected reach the caller: what a write took off an
 * undeclared selection, and what a declared collapse took over from a declared one. Each is named as the
 * path the structure the write left resolves it to, and handed over as a selection event of the tree's own.
 */

/**
 * Hands [declarations]'s listener the nodes of its declared selection that a declared collapse has taken
 * over and it has not been told of yet - see [TreeMirrors.takeNarrowing] - naming each as the path the
 * structure the write left resolves it to. A selection the caller did not declare is not narrowed but lost,
 * and is reported through [reportDropped] instead.
 */
internal fun JTree.reportNarrowing(
    declarations: TreeDeclarations,
    hidden: Set<List<Int>>,
    oldLead: TreePath?,
) {
    val declaredSelection = declarations.declaredSelection ?: return
    val taken = declarations.mirrors.takeNarrowing(declaredSelection, hidden)
    reportLost(declarations.target, resolveAll(taken), oldLead)
}

/**
 * Hands [target] the nodes among [heldNodes] the tree no longer has selected, each named as the path it
 * was held by. [heldIndices] names the same nodes as index paths in the structure the write started from,
 * and [oldLead] is the node the selection was led from before it.
 */
internal fun JTree.reportDropped(
    target: TreeSelectionListener,
    heldNodes: Array<out TreePath>,
    heldIndices: List<List<Int>>,
    oldLead: TreePath?,
) {
    reportLost(target, selectionDropped(heldNodes, heldIndices), oldLead)
}

/**
 * Tells [target] that the nodes of [left] - each with the index path that named it - left the tree's
 * selection, with [oldLead] as the node the selection was led from before. Nothing is reported for a write
 * that took nothing away.
 */
private fun JTree.reportLost(
    target: TreeSelectionListener,
    left: List<Pair<TreePath, List<Int>>>,
    oldLead: TreePath?,
) {
    if (left.isEmpty()) return
    val indices = left.map { it.second }
    dispatchToCaller {
        reportLostPaths(target, left.map { it.first }.toTypedArray(), indices, indices.toSet(), oldLead)
    }
}

/**
 * The nodes among [heldNodes] the tree no longer has selected, each with the index path from [heldIndices]
 * that named it in the structure the write started from.
 */
private fun JTree.selectionDropped(
    heldNodes: Array<out TreePath>,
    heldIndices: List<List<Int>>,
): List<Pair<TreePath, List<Int>>> {
    val standing = selectionPaths.orEmpty().toHashSet()
    return heldNodes.indices.filter { heldNodes[it] !in standing }.map { heldNodes[it] to heldIndices[it] }
}

/** [indexPaths] as the nodes they name, dropping the ones the current structure no longer has. */
private fun JTree.resolveAll(indexPaths: Set<List<Int>>): List<Pair<TreePath, List<Int>>> =
    indexPaths.mapNotNull { indices -> resolvePath(model, indices)?.let { it to indices } }

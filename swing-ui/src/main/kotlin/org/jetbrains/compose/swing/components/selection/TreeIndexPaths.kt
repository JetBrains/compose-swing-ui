package org.jetbrains.compose.swing.components.selection

import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/*
 * Translates between a `Tree`'s declared index paths and the `TreePath` of nodes a `JTree` works in, and
 * reads and applies the selection and expansion expressed in them. Every walk goes through the model's
 * own accessors, so any `TreeModel` resolves regardless of the node type it exposes.
 */

/**
 * Re-asserts on the tree what the caller declared: a declaration is the composition's state, so a user
 * change the caller does not adopt is undone, while an undeclared selection or expansion is left standing.
 *
 * The selection is applied after the expansion, because a tree drops the selection inside a subtree it is
 * asked to collapse; applied first, it would not outlast the expansion that follows.
 */
internal fun JTree.applyDeclarations(
    declaredSelection: Set<List<Int>>?,
    declaredExpansion: Set<List<Int>>?,
) {
    applyExpansion(this, model, declaredExpansion)
    applySelection(this, model, declaredSelection)
}

/**
 * Resolves a path of child indices (from the root down) to a [TreePath] of nodes in [model], or `null`
 * if any index is out of range for the current structure. Index `[]` is the root; `[i]` is the root's
 * i-th child; and so on.
 */
internal fun resolvePath(
    model: TreeModel,
    indices: List<Int>,
): TreePath? {
    var node: Any? = model.root
    val nodes = ArrayList<Any>(indices.size + 1)
    for (index in indices) {
        if (node == null || index !in 0 until model.getChildCount(node)) return null
        nodes.add(node)
        node = model.getChild(node, index)
    }
    return node?.let { TreePath((nodes + it).toTypedArray()) }
}

/**
 * Converts a [TreePath] of nodes back to its chain of child indices from the root (the root itself
 * contributes no index, so the root path maps to the empty list).
 */
internal fun pathToIndices(
    model: TreeModel,
    path: TreePath,
): List<Int> {
    val nodes = path.path
    return (1 until nodes.size).map { i -> model.getIndexOfChild(nodes[i - 1], nodes[i]) }
}

/**
 * Reads the tree's current selection back as index paths, each the chain of child indices from the root to
 * a selected node.
 */
internal fun readSelection(
    tree: JTree,
    model: TreeModel,
): Set<List<Int>> = tree.selectionPaths?.mapTo(mutableSetOf()) { pathToIndices(model, it) }.orEmpty()

/**
 * Index paths in document order: a node before its descendants, siblings by child position. Applying a
 * selection or an expansion in it is what leaves a tree the same - the same lead node, and the same order
 * of the expansions a will-expand listener is announced - for every set that names the same nodes.
 */
private val documentOrder =
    Comparator<List<Int>> { left, right ->
        for (depth in 0 until minOf(left.size, right.size)) {
            val step = left[depth].compareTo(right[depth])
            if (step != 0) return@Comparator step
        }
        left.size.compareTo(right.size)
    }

/**
 * Re-applies [selectedPaths] as the tree's selection. Paths that no longer resolve against the current
 * structure are dropped, and a `null` declaration leaves the tree's selection alone.
 */
internal fun applySelection(
    tree: JTree,
    model: TreeModel,
    selectedPaths: Set<List<Int>>?,
) {
    if (selectedPaths == null) return
    val resolved = selectedPaths.sortedWith(documentOrder).mapNotNull { resolvePath(model, it) }
    if (readSelection(tree, model) == resolved.mapTo(mutableSetOf()) { pathToIndices(model, it) }) return
    if (resolved.isEmpty()) {
        tree.clearSelection()
    } else {
        tree.selectionPaths = resolved.toTypedArray()
    }
}

/**
 * Reads every expanded node of the tree back as index paths, walking the structure from the root. A node
 * under a collapsed one is not expanded, and so is not reported.
 */
internal fun readExpansion(
    tree: JTree,
    model: TreeModel,
): Set<List<Int>> = expandedNodes(tree, model).mapTo(mutableSetOf()) { it.second }

/**
 * Every expanded node of the tree, each as the [TreePath] the walk reached it by paired with the chain
 * of child indices that names it, in document order.
 */
private fun expandedNodes(
    tree: JTree,
    model: TreeModel,
): List<Pair<TreePath, List<Int>>> {
    val root = model.root ?: return emptyList()
    val expanded = ArrayList<Pair<TreePath, List<Int>>>()
    collectExpanded(tree, model, TreePath(root), emptyList(), expanded)
    return expanded
}

/** Appends the node at [path] and each of its expanded descendants to [into], in document order. */
private fun collectExpanded(
    tree: JTree,
    model: TreeModel,
    path: TreePath,
    indices: List<Int>,
    into: MutableList<Pair<TreePath, List<Int>>>,
) {
    if (!tree.isExpanded(path)) return
    into.add(path to indices)
    val node = path.lastPathComponent
    for (index in 0 until model.getChildCount(node)) {
        collectExpanded(tree, model, path.pathByAddingChild(model.getChild(node, index)), indices + index, into)
    }
}

/**
 * Re-applies [expandedPaths] as the tree's expansion: every named node is expanded, and every other one
 * collapsed save for an ancestor of a named node, which has to stay expanded for that node to be
 * reachable. Paths that no longer resolve against the current structure are dropped, and a `null`
 * declaration leaves the tree's expansion alone.
 */
internal fun applyExpansion(
    tree: JTree,
    model: TreeModel,
    expandedPaths: Set<List<Int>>?,
) {
    if (expandedPaths == null) return
    val declared = expandPaths(tree, model, expandedPaths)
    // Deepest first: a tree opens every ancestor of a path it is asked to collapse, so collapsing an
    // ancestor before its descendant would re-open the ancestor.
    for ((path, _) in expandedNodes(tree, model).sortedByDescending { it.second.size }) {
        if (declared.none { path.isDescendant(it) }) tree.collapsePath(path)
    }
}

/**
 * Expands every node [indexPaths] names that the current structure still resolves, and returns the paths
 * it resolved. The nodes are opened in document order, so an ancestor opens before its descendants.
 */
private fun expandPaths(
    tree: JTree,
    model: TreeModel,
    indexPaths: Set<List<Int>>,
): List<TreePath> {
    val resolved = indexPaths.sortedWith(documentOrder).mapNotNull { resolvePath(model, it) }
    for (path in resolved) tree.expandPath(path)
    return resolved
}

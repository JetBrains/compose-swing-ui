package org.jetbrains.compose.swing.components.selection

import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/*
 * The translation between a `Tree`'s declared index paths - each the chain of child positions from the
 * root - and the `TreePath` of nodes a `JTree` works in, together with the reads and applies of the
 * selection and the expansion that are expressed in them. Every walk goes through the model's own
 * accessors, so any `TreeModel` resolves regardless of the node type it exposes.
 */

/**
 * Re-asserts on the tree what the caller declared: a declaration is the composition's state, so a user
 * change the caller does not adopt is undone, while an undeclared selection or expansion is left standing.
 *
 * The selection is applied after the expansion, because a tree drops the selection inside a subtree it is
 * asked to collapse; applied first, it would not outlast the expansion that follows.
 */
internal fun JTree.applyDeclarations(
    declaredSelection: List<List<Int>>?,
    declaredExpansion: List<List<Int>>?,
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
 * Reads the tree's current selection back as index paths (each the chain of child indices from the
 * root to a selected node), in the order the selection model reports them.
 */
internal fun readSelection(
    tree: JTree,
    model: TreeModel,
): List<List<Int>> = tree.selectionPaths?.map { pathToIndices(model, it) }.orEmpty()

/**
 * Re-applies [selectedPaths] as the tree's selection. Paths that no longer resolve against the current
 * structure are dropped, and a `null` declaration leaves the tree's selection alone.
 */
internal fun applySelection(
    tree: JTree,
    model: TreeModel,
    selectedPaths: List<List<Int>>?,
) {
    if (selectedPaths == null) return
    val resolved = selectedPaths.mapNotNull { resolvePath(model, it) }
    if (readSelection(tree, model) == resolved.map { pathToIndices(model, it) }) return
    if (resolved.isEmpty()) {
        tree.clearSelection()
    } else {
        tree.selectionPaths = resolved.toTypedArray()
    }
}

/**
 * Reads every expanded node of the tree back as index paths, walking the structure from the root so
 * they come out in document order: a node before its children, siblings in child order. A node under a
 * collapsed one is not expanded, and so is not reported.
 */
internal fun readExpansion(
    tree: JTree,
    model: TreeModel,
): List<List<Int>> {
    val root = model.root ?: return emptyList()
    val expanded = ArrayList<List<Int>>()
    collectExpanded(tree, model, TreePath(root), emptyList(), expanded)
    return expanded
}

/** Appends the node at [path] and each of its expanded descendants to [into], in document order. */
private fun collectExpanded(
    tree: JTree,
    model: TreeModel,
    path: TreePath,
    indices: List<Int>,
    into: MutableList<List<Int>>,
) {
    if (!tree.isExpanded(path)) return
    into.add(indices)
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
    expandedPaths: List<List<Int>>?,
) {
    if (expandedPaths == null) return
    val declared = expandPaths(tree, model, expandedPaths)
    // Deepest first: a tree opens every ancestor of a path it is asked to collapse, so collapsing an
    // ancestor before its descendant would re-open the ancestor.
    for (indices in readExpansion(tree, model).sortedByDescending { it.size }) {
        val path = resolvePath(model, indices) ?: continue
        if (declared.none { path.isDescendant(it) }) tree.collapsePath(path)
    }
}

/**
 * Expands every node [indexPaths] names that the current structure still resolves, and returns the paths
 * it resolved.
 */
private fun expandPaths(
    tree: JTree,
    model: TreeModel,
    indexPaths: List<List<Int>>,
): List<TreePath> {
    val resolved = indexPaths.mapNotNull { resolvePath(model, it) }
    for (path in resolved) tree.expandPath(path)
    return resolved
}

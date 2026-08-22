package org.jetbrains.compose.swing.components.selection

import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/*
 * Translates between a `Tree`'s declared index paths and the `TreePath` of nodes a `JTree` works in, and
 * reads and applies the selection expressed in them. Every walk goes through the model's own accessors, so
 * any `TreeModel` resolves regardless of the node type it exposes.
 */

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
    val depth = path.pathCount - 1
    if (depth <= 0) return emptyList()
    // The components are read one at a time: a path hands out its nodes as an array of its own, copied
    // for each caller, and this runs once for every selected node.
    val indices = ArrayList<Int>(depth)
    var parent = path.getPathComponent(0)
    for (step in 1..depth) {
        val node = path.getPathComponent(step)
        indices.add(model.getIndexOfChild(parent, node))
        parent = node
    }
    return indices
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
internal val treeDocumentOrder =
    Comparator<List<Int>> { left, right ->
        for (depth in 0 until minOf(left.size, right.size)) {
            val step = left[depth].compareTo(right[depth])
            if (step != 0) return@Comparator step
        }
        left.size.compareTo(right.size)
    }

/**
 * Re-applies [selectedPaths] as the tree's selection, and answers whether the tree had to be written to.
 * Paths that no longer resolve against the current structure are dropped, and a `null` declaration leaves
 * the tree's selection alone.
 */
internal fun applySelection(
    tree: JTree,
    model: TreeModel,
    selectedPaths: Set<List<Int>>?,
): Boolean {
    if (selectedPaths == null) return false
    return selectNodes(tree, selectedPaths.sortedWith(treeDocumentOrder).mapNotNull { resolvePath(model, it) })
}

/**
 * Leaves the tree selecting exactly [resolved], skipping the write where it already holds those nodes, and
 * answers whether it was written to.
 *
 * What it holds is compared as nodes rather than as the index paths naming them: a `TreePath` carries the
 * nodes it was built from, so two of them are equal exactly when they end at the same node by the same way
 * down, and answering takes no walk of the model.
 */
internal fun selectNodes(
    tree: JTree,
    resolved: List<TreePath>,
): Boolean {
    val standing = tree.selectionPaths.orEmpty()
    val holdsExactly =
        standing.size == resolved.size && (resolved.isEmpty() || standing.toHashSet().containsAll(resolved))
    if (holdsExactly) return false
    if (resolved.isEmpty()) {
        tree.clearSelection()
    } else {
        tree.selectionPaths = resolved.toTypedArray()
    }
    return true
}

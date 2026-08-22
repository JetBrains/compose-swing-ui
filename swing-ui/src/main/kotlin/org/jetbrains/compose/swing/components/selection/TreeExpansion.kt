package org.jetbrains.compose.swing.components.selection

import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/*
 * Reads and applies the nodes a `Tree` shows open, expressed as the index paths its expansion is declared
 * and reported in.
 */

/**
 * Reads every expanded node of the tree back as index paths, walking the structure from the root. A node
 * under a collapsed one is not expanded, and so is not reported.
 */
internal fun readExpansion(
    tree: JTree,
    model: TreeModel,
): Set<List<Int>> {
    val root = model.root ?: return emptySet()
    val expanded = LinkedHashSet<List<Int>>()
    collectExpanded(tree, model, TreePath(root), ArrayList(), expanded)
    return expanded
}

/**
 * Adds the node at [path] and each of its expanded descendants to [into], in document order. [indices]
 * names [path] and is walked along with it, one index pushed on the way down and taken off on the way
 * back, so the walk copies it only for the nodes it reports.
 */
private fun collectExpanded(
    tree: JTree,
    model: TreeModel,
    path: TreePath,
    indices: ArrayList<Int>,
    into: MutableSet<List<Int>>,
) {
    if (!tree.isExpanded(path)) return
    into.add(ArrayList(indices))
    val node = path.lastPathComponent
    for (index in 0 until model.getChildCount(node)) {
        indices.add(index)
        collectExpanded(tree, model, path.pathByAddingChild(model.getChild(node, index)), indices, into)
        indices.removeAt(indices.size - 1)
    }
}

/**
 * Re-applies [expandedPaths] as the tree's expansion: every named node is expanded, and every other one
 * collapsed save for an ancestor of a named node, which has to stay expanded for that node to be
 * reachable. Paths that no longer resolve against the current structure are dropped.
 *
 * Only the nodes whose state has to change are touched - the ones that have to be open and are closed, and
 * the ones the tree shows open that the declaration does not name. Every expansion runs before every
 * collapse, each in document order, which is what leaves the tree the same for every set naming the same
 * nodes.
 *
 * Answers with the nodes the tree is left showing open, or `null` where a refused collapse leaves one open
 * that no declaration names, which only reading the tree can name.
 */
internal fun applyExpansion(
    tree: JTree,
    model: TreeModel,
    expandedPaths: Set<List<Int>>,
): Set<List<Int>>? {
    val root = model.root ?: return emptySet()
    val wanted = nodesToOpen(model, expandedPaths)
    openNodes(tree, wanted, shownOpen(tree, root))
    // The nodes to close are read again after the expansions: a node the tree remembers as open comes back
    // open with the ancestor that was hiding it, and is only then something the tree shows.
    val refused = closeNodes(tree, shownOpen(tree, root), wanted.values.toHashSet())
    return if (refused) {
        null
    } else {
        wanted.entries.mapNotNullTo(LinkedHashSet()) { (indices, path) -> indices.takeIf { tree.isExpanded(path) } }
    }
}

/**
 * The nodes that have to be open for [expandedPaths] to stand, each with the path of nodes the tree holds
 * it by: every path the current structure still resolves, and every node on the way down to one. A path
 * the structure no longer has is dropped, and asks nothing of the nodes above it.
 */
private fun nodesToOpen(
    model: TreeModel,
    expandedPaths: Set<List<Int>>,
): Map<List<Int>, TreePath> {
    val wanted = HashMap<List<Int>, TreePath>()
    for (indices in expandedPaths) {
        var path: TreePath? = resolvePath(model, indices) ?: continue
        var depth = indices.size
        // A node already recorded was recorded with the whole chain above it, which is not walked again.
        while (path != null && wanted.put(indexPathOf(indices, depth), path) == null) {
            path = path.parentPath
            depth--
        }
    }
    return wanted
}

/**
 * The first [depth] indices of [indices] as the index path naming a node at that depth. The whole of
 * [indices] is handed back as it is, so a declared path stays the list the caller named it by.
 */
private fun indexPathOf(
    indices: List<Int>,
    depth: Int,
): List<Int> = if (depth == indices.size) indices else indices.subList(0, depth).toList()

/** The nodes the tree currently shows open, as the paths of nodes it holds them by. */
private fun shownOpen(
    tree: JTree,
    root: Any,
): Set<TreePath> {
    val rootPath = TreePath(root)
    // Nothing under a closed root is shown at all, so a closed root leaves the tree showing nothing open.
    if (!tree.isExpanded(rootPath)) return emptySet()
    val shown = HashSet<TreePath>()
    shown.add(rootPath)
    // A tree names the open descendants of a node it shows open, which the root has just been found to be.
    val descendants = tree.getExpandedDescendants(rootPath)
    while (descendants.hasMoreElements()) shown.add(descendants.nextElement())
    return shown
}

/**
 * Opens each node of [wanted] that [shown] does not already hold open, shallowest first, so a node is
 * reached with the nodes above it already open.
 *
 * A node that does not open - a leaf, or one a listener refuses - takes everything under it with it: a
 * tree opens every ancestor of a node it is asked to open, so asking for a descendant would put the same
 * refusal to the listener again.
 */
private fun openNodes(
    tree: JTree,
    wanted: Map<List<Int>, TreePath>,
    shown: Set<TreePath>,
) {
    val opening = wanted.entries.filter { it.value !in shown }.sortedWith(compareBy(treeDocumentOrder) { it.key })
    var closedAt: List<Int>? = null
    for ((indices, path) in opening) {
        if (closedAt != null && indices.startsWith(closedAt)) continue
        closedAt = null
        tree.expandPath(path)
        if (!tree.isExpanded(path)) closedAt = indices
    }
}

/**
 * Closes each node of [shown] that [wanted] does not name, deepest first: a tree opens every ancestor of a
 * node it is asked to close, so closing an ancestor before its descendant would re-open the ancestor.
 *
 * Answers whether a listener refused one of the collapses, which leaves the tree showing a node open that
 * the declaration does not name.
 */
private fun closeNodes(
    tree: JTree,
    shown: Set<TreePath>,
    wanted: Set<TreePath>,
): Boolean {
    // A row is the tree's own document order. Every node it shows open has one save a hidden root, whose
    // absent row sorts last - which is where the shallowest node of all belongs.
    val closing = shown.filterNot { it in wanted }.sortedByDescending { tree.getRowForPath(it) }
    var refused = false
    for (path in closing) {
        tree.collapsePath(path)
        if (tree.isExpanded(path)) refused = true
    }
    return refused
}

/** Whether [prefix] names this node or one of the nodes above it. */
private fun List<Int>.startsWith(prefix: List<Int>): Boolean =
    size >= prefix.size && prefix.indices.all { depth -> this[depth] == prefix[depth] }

package org.jetbrains.compose.swing.components.selection

import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/** The [TreePath] reached by following [indices] (child positions) from the root of [this] tree. */
internal fun JTree.pathTo(vararg indices: Int): TreePath = model.pathTo(*indices)

/**
 * The [TreePath] reached by following [indices] (child positions) from the root of [this] model.
 *
 * The walk goes through the model's own accessors, so it resolves against a model whose nodes are not
 * [javax.swing.tree.DefaultMutableTreeNode]s just as well, and against a model no tree is showing.
 */
internal fun TreeModel.pathTo(vararg indices: Int): TreePath {
    var node = root
    var path = TreePath(node)
    for (index in indices) {
        node = getChild(node, index)
        path = path.pathByAddingChild(node)
    }
    return path
}

/** The text of every row the tree shows, top to bottom. */
internal fun JTree.rowLabels(): List<String> =
    (0 until rowCount).map { row -> getPathForRow(row).lastPathComponent.toString() }

/** The labels of the nodes the tree has selected. */
internal fun JTree.selectedLabels(): List<String> = selectionPaths.orEmpty().map { it.lastPathComponent.toString() }

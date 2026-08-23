package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.State
import org.jetbrains.annotations.Nls
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/*
 * The data a value-driven `Tree` describes its structure with, and the model that data builds.
 */

/**
 * The data one pass of a value-driven [Tree] describes its structure with: the root value, the accessors
 * that walk and label it, and the answer that decides which of its values are branches. Each part moves the
 * structure the tree shows, so a change to any of them is walked into the nodes.
 */
internal data class TreeContent<T>(
    val root: T,
    val children: (T) -> List<T>,
    val label: (T) -> @Nls String,
    val hasChildren: ((T) -> Boolean)?,
) {
    /**
     * The model this content renders as, reporting an edit committed on one of its nodes to [onNodeEdit].
     *
     * A node answers for its own leafness only while [hasChildren] is declared, which is what asking a
     * node whether it allows children expresses; without it a node is a leaf exactly when it has none.
     */
    fun toModel(onNodeEdit: State<(value: T, path: List<Int>, newValue: Any?) -> Unit>): DeclaredTreeModel<T> =
        DeclaredTreeModel(this, onNodeEdit)

    /**
     * Whether [other] reaches the same nodes from the same values: the accessors are compared by identity,
     * since two of them answer alike only where they are the same one.
     */
    fun walksAlike(other: TreeContent<T>): Boolean =
        children === other.children && label === other.label && hasChildren === other.hasChildren

    /**
     * Builds a [DefaultMutableTreeNode] tree from [value] by recursively visiting the child accessor. Each
     * node's user object is a [TreeNodeValue] pairing the value with the label applied to it: the label is
     * what the node renders as text through any renderer that asks a node for it, and the value is what a
     * composable node is handed. The returned node mirrors the data tree one-to-one in structure and child
     * order.
     *
     * A value the data gives children is a branch whatever the branch answer says of it - a node that
     * allows none could not hold the children it has.
     */
    fun buildNode(value: T): DefaultMutableTreeNode {
        val childValues = children(value)
        val branch = childValues.isNotEmpty() || (hasChildren?.invoke(value) ?: true)
        val node = DefaultMutableTreeNode(TreeNodeValue(value, label(value)), branch)
        for (child in childValues) {
            node.add(buildNode(child))
        }
        return node
    }

    /**
     * Walks this content into the nodes [model] holds: a node takes over the value it now stands for, a
     * child the data no longer has is taken out of its parent and one it gained is put in, each through the
     * model's own event, which is what carries the change into the rows the tree shows.
     *
     * A child value handed over unchanged - the same object - describes the subtree under it unchanged too,
     * and that whole subtree is passed over. Which holds of the accessors it was last walked with alone, so
     * [walkEvery] is what a new one asks for.
     *
     * Answers whether a node left the structure or joined it, which renames by index every node after it.
     */
    fun syncInto(
        model: DeclaredTreeModel<T>,
        walkEvery: Boolean,
    ): Boolean = syncNode(model, model.root as DefaultMutableTreeNode, root, walkEvery)

    private fun syncNode(
        model: DefaultTreeModel,
        node: DefaultMutableTreeNode,
        value: T,
        walkEvery: Boolean,
    ): Boolean {
        val carried = carriedBy(node)
        if (!walkEvery && carried.value === value) return false
        val childValues = children(value)
        val text = label(value)
        val branch = childValues.isNotEmpty() || (hasChildren?.invoke(value) ?: true)
        // A row renders from the value as well as from the label - a composable node is handed the value
        // itself - so a node standing on a value that differs is a row the tree has to paint again. The
        // values are compared rather than identified: a caller that rebuilds its data every pass hands
        // back a fresh object per node, and repainting each of them costs a node dimension and a path
        // through the tree's layout cache for a row that renders the same.
        val shown = carried.value != value || carried.toString() != text || node.allowsChildren != branch
        // A node that allows no children throws away the ones it holds, so it is widened before children
        // arrive and narrowed once the ones it had are gone, even when syncing them fails partway.
        if (branch) node.allowsChildren = true
        val moved =
            try {
                syncChildren(model, node, childValues, walkEvery)
            } finally {
                if (!branch) node.allowsChildren = false
            }
        carried.carry(value, text)
        if (shown) model.nodeChanged(node)
        return moved
    }

    /**
     * Settles [node]'s children on [childValues]. The children the data hands over unchanged at the front
     * and at the back keep their nodes; of what lies between, as many nodes as there are values left take
     * those values over, and the surplus on either side is removed or inserted.
     */
    private fun syncChildren(
        model: DefaultTreeModel,
        node: DefaultMutableTreeNode,
        childValues: List<T>,
        walkEvery: Boolean,
    ): Boolean {
        val had = node.childCount
        val has = childValues.size
        var head = 0
        while (head < had && head < has && carriedBy(node.getChildAt(head)).value === childValues[head]) {
            head++
        }
        var tail = 0
        while (
            head + tail < had &&
            head + tail < has &&
            carriedBy(node.getChildAt(had - 1 - tail)).value === childValues[has - 1 - tail]
        ) {
            tail++
        }
        val reused = minOf(had, has) - head - tail
        val firstStale = head + reused
        val removed = removeChildren(model, node, firstStale, had - tail - firstStale)
        val inserted = insertChildren(model, node, firstStale, childValues.subList(firstStale, has - tail))
        var moved = removed || inserted
        for (index in 0 until has) {
            if (syncNode(model, node.getChildAt(index) as DefaultMutableTreeNode, childValues[index], walkEvery)) {
                moved = true
            }
        }
        return moved
    }

    private fun removeChildren(
        model: DefaultTreeModel,
        node: DefaultMutableTreeNode,
        from: Int,
        count: Int,
    ): Boolean {
        if (count == 0) return false
        val positions = IntArray(count) { from + it }
        val taken = Array<Any>(count) { node.getChildAt(from + it) }
        repeat(count) { node.remove(from) }
        model.nodesWereRemoved(node, positions, taken)
        return true
    }

    private fun insertChildren(
        model: DefaultTreeModel,
        node: DefaultMutableTreeNode,
        from: Int,
        values: List<T>,
    ): Boolean {
        if (values.isEmpty()) return false
        for ((offset, value) in values.withIndex()) node.insert(buildNode(value), from + offset)
        model.nodesWereInserted(node, IntArray(values.size) { from + it })
        return true
    }

    /** The [TreeNodeValue] [node] carries; every node a value-driven [Tree] builds carries one. */
    private fun carriedBy(node: TreeNode): TreeNodeValue<T> {
        @Suppress("UNCHECKED_CAST")
        return (node as DefaultMutableTreeNode).userObject as TreeNodeValue<T>
    }
}

/**
 * The model a value-driven [Tree] builds from the caller's data, and walks each later structure into.
 *
 * An edit committed on a node is reported through [onNodeEdit] and changes nothing here: the node goes on
 * carrying the value it was built from, so the row follows the data alone and moves once a composition
 * supplies data that has moved. [onNodeEdit] is read through a [State], so the model reports to the callback
 * the composition last declared.
 */
internal class DeclaredTreeModel<T>(
    declared: TreeContent<T>,
    private val onNodeEdit: State<(value: T, path: List<Int>, newValue: Any?) -> Unit>,
) : DefaultTreeModel(declared.buildNode(declared.root), declared.hasChildren != null) {
    /** The content these nodes were last walked from. */
    var content: TreeContent<T> = declared

    /**
     * Whether [next] can be walked into these nodes. A node answers for its own leafness only while a branch
     * answer is declared, and that is settled when the model is built: content that withdraws the answer, or
     * makes one, asks for nodes of another shape and gets a model of its own.
     */
    fun accepts(next: TreeContent<T>): Boolean = (content.hasChildren == null) == (next.hasChildren == null)

    override fun valueForPathChanged(
        path: TreePath,
        newValue: Any?,
    ) {
        onNodeEdit.value(valueAt(path), pathToIndices(this, path), newValue)
    }
}

/**
 * The value the node at the end of [path] stands for. Every node a value-driven [Tree] builds carries a
 * [TreeNodeValue] holding a value of that tree's own element type, and these are the nodes of such a tree.
 */
internal fun <T> valueAt(path: TreePath): T {
    @Suppress("UNCHECKED_CAST")
    val carried = (path.lastPathComponent as DefaultMutableTreeNode).userObject as TreeNodeValue<T>
    return carried.value
}

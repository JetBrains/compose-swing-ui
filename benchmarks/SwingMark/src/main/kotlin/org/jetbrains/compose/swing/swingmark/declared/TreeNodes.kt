package org.jetbrains.compose.swing.swingmark.declared

/** Nodes `TreeTest` builds, and the children it gives a node before moving on to the next level. */
internal const val TARGET_NODES = 200
private const val BRANCHING = 5

/** A node of the tree the test declares: a value and its children, both immutable. */
internal data class TreeNode(
    val id: Int,
    val children: List<TreeNode> = emptyList(),
)

/**
 * The parent each node is added under, in the order `TreeTest` adds them: level by level, five children
 * to a node, until the tree holds [TARGET_NODES] of them.
 *
 * Index paths rather than nodes, because the node a path names is a different object on every pass - a
 * declared tree is rebuilt around each change rather than mutated.
 */
internal val ADD_ORDER: List<List<Int>> = additionOrder()

/** The tree those additions leave behind, which the row order below is read off. */
internal val BUILT_TREE: TreeNode =
    ADD_ORDER.foldIndexed(TreeNode(0)) { index, tree, parent -> tree.withChildAt(parent, TreeNode(index + 1)) }

/** Every node's path in row order: with everything expanded, a tree's rows are its nodes in preorder. */
internal val PREORDER: List<List<Int>> = preorder(BUILT_TREE, emptyList())

/** The paths of the nodes on the way to [path], each of which has to be open for [path] to be a row. */
internal fun ancestorsOf(path: List<Int>): List<List<Int>> = path.indices.map { path.take(it) }

/** This tree with a child appended under [path]; every node on the way there is rebuilt around it. */
internal fun TreeNode.withChildAt(
    path: List<Int>,
    child: TreeNode,
): TreeNode =
    if (path.isEmpty()) {
        copy(children = children + child)
    } else {
        val index = path.first()
        copy(children = children.replaceAt(index, children[index].withChildAt(path.drop(1), child)))
    }

/** This tree with the node [path] names taken out of its parent's children. */
internal fun TreeNode.withoutPath(path: List<Int>): TreeNode {
    val index = path.first()
    return if (path.size == 1) {
        copy(children = children.filterIndexed { at, _ -> at != index })
    } else {
        copy(children = children.replaceAt(index, children[index].withoutPath(path.drop(1))))
    }
}

/**
 * The path of the leftmost deepest leaf: the node a depth-first removal takes next.
 *
 * Removing by this rule needs no precomputed paths, which a removal invalidates as later siblings shift
 * down, and still takes every node exactly once.
 */
internal fun TreeNode.firstLeafPath(): List<Int> {
    val path = ArrayList<Int>()
    var node = this
    while (node.children.isNotEmpty()) {
        path += 0
        node = node.children.first()
    }
    return path
}

private fun <T> List<T>.replaceAt(
    index: Int,
    value: T,
): List<T> = toMutableList().also { it[index] = value }

private fun preorder(
    node: TreeNode,
    path: List<Int>,
): List<List<Int>> = listOf(path) + node.children.flatMapIndexed { index, child -> preorder(child, path + index) }

private fun additionOrder(): List<List<Int>> {
    val order = ArrayList<List<Int>>()
    val counts = HashMap<List<Int>, Int>()
    var level = listOf(emptyList<Int>())
    var nodes = 1
    while (nodes < TARGET_NODES) {
        val next = ArrayList<List<Int>>()
        for (parent in level) {
            while (counts.getOrDefault(parent, 0) < BRANCHING && nodes < TARGET_NODES) {
                val index = counts.getOrDefault(parent, 0)
                counts[parent] = index + 1
                order += parent
                next += parent + index
                nodes++
            }
        }
        level = next
    }
    return order
}

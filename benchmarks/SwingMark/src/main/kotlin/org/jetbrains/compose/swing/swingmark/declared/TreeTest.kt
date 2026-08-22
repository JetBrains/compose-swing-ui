package org.jetbrains.compose.swing.swingmark.declared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.TreeState
import org.jetbrains.compose.swing.components.selection.rememberTreeState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.swingmark.harness.change
import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * `TreeTest`: nodes added one at a time, every node scrolled into view, the rows selected under each of
 * the three selection modes, every node collapsed again, and every node removed one at a time.
 *
 * Structure, expansion and selection are state: the test writes an immutable tree and two sets of index
 * paths, and the library works out which nodes moved, which opened and which are selected. Scrolling is
 * [TreeState.revealPath].
 *
 * The original expands by scrolling: its expander asks the tree to show each node in turn, which opens
 * that node's ancestors. Here those ancestors are declared and the reveal follows, so the expansion is
 * one the tree is told about rather than one it is left in.
 *
 * A selection step names rows, not paths, because that is what the original names, and because rows are
 * what the tree can be asked about while a change is settling.
 */
internal class TreeTest : DeclaredTest() {
    override val testName: String = "Tree"

    private var tree by mutableStateOf(TreeNode(0))
    private var selectionMode by mutableIntStateOf(TreeSelectionModel.SINGLE_TREE_SELECTION)
    private lateinit var state: TreeState

    /** BorderLayout's center, as `TreeTest` lays its own panel out: the scroller takes the whole tab. */
    @Composable
    override fun Content() {
        state = rememberTreeState()
        BorderPanel {
            ScrollPane(modifier = SwingModifier.center()) {
                Tree(
                    root = tree,
                    children = { it.children },
                    state = state,
                    modifier = SwingModifier.viewport(),
                    label = { it.id.toString() },
                    selectionMode = selectionMode,
                )
            }
        }
    }

    /**
     * Declares every node [addNodes] adds, in the order it adds them, and opens what [expandNodes]
     * opens - as one write rather than as one change apiece.
     */
    override fun buildUp() {
        tree =
            ADD_ORDER.foldIndexed(tree) { index, root, parentPath ->
                root.withChildAt(parentPath, TreeNode(index + 1))
            }
        state.expandedPaths = PREORDER.flatMap(::ancestorsOf).toSet()
    }

    override fun runTest() {
        val widget = widget(JTree::class.java)
        addNodes(widget)
        val expanded = expandNodes(widget)
        val selected = selectRows(widget)
        collapseNodes(widget, expanded, selected)
        removeNodes(widget)
    }

    private fun addNodes(widget: JTree) {
        ADD_ORDER.forEachIndexed { index, parentPath ->
            val node = TreeNode(index + 1)
            val expected = index + 2
            change(
                apply = { tree = tree.withChildAt(parentPath, node) },
                reached = { widget.countNodes() == expected },
            )
        }
    }

    /** Opens every node's ancestors and scrolls to it, returning the paths left open. */
    private fun expandNodes(widget: JTree): Set<List<Int>> {
        var expanded = emptySet<List<Int>>()
        for (path in PREORDER) {
            val opening = ancestorsOf(path).filterNot { it in expanded }
            expanded = expanded + opening
            val declared = expanded
            change(
                apply = {
                    state.expandedPaths = declared
                    revealOnApply { check(state.revealPath(path)) }
                },
                reached = { widget.areOpen(opening) },
                describe = { "opening the ancestors of $path" },
            )
        }
        check(widget.rowCount == TARGET_NODES) {
            "the tree shows ${widget.rowCount} rows where all $TARGET_NODES nodes should be open"
        }
        return expanded
    }

    /** Walks the rows under each selection mode, and answers with the selection the last step declares. */
    private fun selectRows(widget: JTree): Set<List<Int>> {
        var selected = emptySet<List<Int>>()
        for ((mode, steps) in selectionPhases(TARGET_NODES)) {
            change(
                apply = {
                    selectionMode = mode
                    state.selectedPaths = emptySet()
                },
                reached = { widget.selectionModel.selectionMode == mode && widget.selectionCount == 0 },
            )
            for (step in steps) {
                val declared = step.rows.map { PREORDER[it] }.toSet()
                selected = declared
                change(
                    apply = {
                        state.selectedPaths = declared
                        revealOnApply { check(state.revealPath(PREORDER[step.revealRow])) }
                    },
                    reached = { widget.selectionRows?.toSet().orEmpty() == step.rows },
                )
            }
        }
        return selected
    }

    /**
     * Closes every node, deepest row first, dropping from the selection whatever each closing node hides.
     *
     * The tree settles expansion first and selection second, and a `JTree` opens the parents of the
     * selection it is given, so a node still holding a selected descendant is opened again the moment it
     * is closed. Dropping those paths - and selecting the closing node in their place - is what a `JTree`
     * does for itself when the collapse is a call rather than a declaration.
     */
    private fun collapseNodes(
        widget: JTree,
        expanded: Set<List<Int>>,
        selection: Set<List<Int>>,
    ) {
        var open = expanded
        var selected = selection
        for (path in PREORDER.reversed()) {
            open = open - setOf(path)
            val hidden = selected.filter { it.size > path.size && it.take(path.size) == path }.toSet()
            if (hidden.isNotEmpty()) selected = selected - hidden + setOf(path)
            val declaredOpen = open
            val declaredSelection = selected
            change(
                apply = {
                    state.expandedPaths = declaredOpen
                    state.selectedPaths = declaredSelection
                },
                reached = { widget.isClosed(path) && widget.tookOverSelection(path, hidden) },
                describe = { "closing $path" },
            )
        }
        check(widget.rowCount == 1 && widget.selectionRows?.toSet() == setOf(0)) {
            "the tree shows ${widget.rowCount} rows with ${widget.selectionCount} selected, where the " +
                "closed root alone should be left, and selected"
        }
    }

    private fun removeNodes(widget: JTree) {
        repeat(TARGET_NODES) { removed ->
            // The last pass takes the root, which the original's remover leaves alone: a node is removed
            // from its parent, and the root has none.
            val expected = maxOf(TARGET_NODES - removed - 1, 1)
            change(
                apply = { if (tree.children.isNotEmpty()) tree = tree.withoutPath(tree.firstLeafPath()) },
                reached = { widget.countNodes() == expected },
            )
        }
    }
}

/** One selection step: the rows selected after it, and the row it scrolls to. */
private class TreeSelectionStep(
    val rows: Set<Int>,
    val revealRow: Int,
)

private const val CONTIGUOUS_STRIDE = 4
private const val DISCONTIGUOUS_STRIDE = 5
private const val ROWS_PER_STEP = 3

/**
 * The three selection phases, each the mode `TreeSelector` installs and the steps it walks under it.
 *
 * Each phase starts from no selection, because the original installs a fresh `DefaultTreeSelectionModel`
 * for it. A single-selection model keeps the one row it is given. A contiguous model refuses rows that
 * do not touch what it holds, and takes them as a new selection instead, which every step here does. A
 * discontiguous model keeps everything it is given.
 */
private fun selectionPhases(rowCount: Int): List<Pair<Int, List<TreeSelectionStep>>> {
    var accumulated = emptySet<Int>()
    return listOf(
        TreeSelectionModel.SINGLE_TREE_SELECTION to
            List(rowCount) { row -> TreeSelectionStep(setOf(row), row) },
        TreeSelectionModel.CONTIGUOUS_TREE_SELECTION to
            List(rowCount / CONTIGUOUS_STRIDE) { step ->
                val first = step * CONTIGUOUS_STRIDE
                TreeSelectionStep((first until first + ROWS_PER_STEP).toSet(), first + ROWS_PER_STEP - 1)
            },
        TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION to
            List(rowCount / DISCONTIGUOUS_STRIDE) { step ->
                val first = step * DISCONTIGUOUS_STRIDE
                accumulated = accumulated + (first until first + ROWS_PER_STEP)
                TreeSelectionStep(accumulated, first + ROWS_PER_STEP - 1)
            },
    )
}

/*
 * A step is settled by what it declared that the step before it did not, because that is the only part of
 * a declaration the applier has to bring about: everything else was already on the tree when the previous
 * step settled, and asking about it would answer before the applier had run. Each phase closes with a
 * check on the whole tree, which is what the per-step questions add up to.
 */

/** Whether every node [paths] names is open. */
private fun JTree.areOpen(paths: List<List<Int>>): Boolean = paths.all { pathTo(it)?.let(::isExpanded) == true }

/** Whether the node [path] names is closed, which a path the current structure no longer resolves is. */
private fun JTree.isClosed(path: List<Int>): Boolean = pathTo(path)?.let(::isExpanded) != true

/**
 * Whether the selection a closing [path] takes over has moved: the [hidden] nodes are off the selection
 * and [path] stands in their place. A node hiding no selected node moves none, and has none to wait for.
 */
private fun JTree.tookOverSelection(
    path: List<Int>,
    hidden: Set<List<Int>>,
): Boolean = hidden.isEmpty() || (isSelected(path) && hidden.none { isSelected(it) })

/** Whether the node [path] names is selected. */
private fun JTree.isSelected(path: List<Int>): Boolean = pathTo(path)?.let(::isPathSelected) == true

/** The `TreePath` [path] names in the tree's current model, or null when the model no longer has it. */
private fun JTree.pathTo(path: List<Int>): TreePath? {
    var node: Any? = model.root
    val nodes = ArrayList<Any>(path.size + 1)
    for (index in path) {
        if (node == null || index !in 0 until model.getChildCount(node)) return null
        nodes += node
        node = model.getChild(node, index)
    }
    return node?.let { TreePath((nodes + it).toTypedArray()) }
}

/** How many nodes the tree's model holds, walked from its root. */
private fun JTree.countNodes(): Int {
    val root = model.root ?: return 0
    return model.count(root)
}

private fun TreeModel.count(node: Any): Int = 1 + (0 until getChildCount(node)).sumOf { count(getChild(node, it)) }

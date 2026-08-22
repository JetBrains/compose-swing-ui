package org.jetbrains.compose.swing.swingmark.raw

import org.jetbrains.compose.swing.swingmark.harness.rest
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * `TreeTest`: nodes added one at a time, every node scrolled into view, the rows selected under each of
 * the three selection modes, every node collapsed again, and every node removed one at a time.
 */
internal class TreeTest(
    private val blitScrolling: Boolean,
) : RawTest() {
    override val testName: String = "Tree"

    private lateinit var tree: JTree
    private var totalChildCount = 0

    override fun testComponent(): JComponent {
        val panel = JPanel()
        panel.layout = BorderLayout()

        val top = DefaultMutableTreeNode(0)
        totalChildCount++

        val model = DefaultTreeModel(top)

        tree = CountTree(model)
        tree.isLargeModel = USE_LARGE_MODEL
        if (USE_LARGE_MODEL) {
            tree.rowHeight = LARGE_MODEL_ROW_HEIGHT
        }
        val scroller = JScrollPane(tree)

        if (blitScrolling) {
            scroller.viewport.putClientProperty(ENABLE_WINDOW_BLIT, true)
        }

        panel.add(scroller, BorderLayout.CENTER)

        return panel
    }

    override fun runTest() {
        testTree()
    }

    /**
     * Adds every node the run adds and opens every one of them, which is where its expand phase leaves
     * the tree - reached here directly rather than a node at a time.
     */
    override fun buildUp() {
        val adder = TreeNodeAdder(tree.model as DefaultTreeModel, add = true)
        addChild(listOf(tree.model.root as DefaultMutableTreeNode), adder) { it.run() }
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    private fun testTree() {
        val adder = TreeNodeAdder(tree.model as DefaultTreeModel, add = true)
        addChild(listOf(tree.model.root as DefaultMutableTreeNode), adder, ::postAndWait)

        val expender = TreeExpender(tree, expand = true)
        val path = tree.getPathForRow(0)
        val root = path.lastPathComponent as DefaultMutableTreeNode
        expandNodes(root, expender)

        selectSingle()
        val selector = selectContiguous()
        selectDiscontiguous(selector)

        collapseNodes()

        val remover = TreeNodeAdder(tree.model as DefaultTreeModel, add = false)
        removeNodes(tree.model.root as DefaultMutableTreeNode, remover)
        tree.repaint()
    }

    /**
     * Fills each node in [nodeList] to [INTERVAL] children, then does the same for the children it added.
     *
     * The suite posts each addition and waits, which is the step it is timed over; a caller already on
     * the event dispatch thread runs them where it stands and reaches the same tree.
     */
    private fun addChild(
        nodeList: List<DefaultMutableTreeNode>,
        adder: TreeNodeAdder,
        run: (Runnable) -> Unit,
    ) {
        val newVec = ArrayList<DefaultMutableTreeNode>()

        for (node in nodeList) {
            while (node.childCount < INTERVAL) {
                if (totalChildCount >= TARGET_CHILD_COUNT) return

                adder.setNode(node, totalChildCount)
                run(adder)
                totalChildCount++

                newVec.add(node.getChildAt(node.childCount - 1) as DefaultMutableTreeNode)
            }
        }
        addChild(newVec, adder, run)
    }

    /** Scrolls to every node in turn, which is what opens the tree: a scroll opens the node's ancestors. */
    private fun expandNodes(
        node: DefaultMutableTreeNode,
        expender: TreeExpender,
    ) {
        expender.setPath(TreePath(node.path))
        postAndWait(expender)

        val children = node.children()
        while (children.hasMoreElements()) {
            expandNodes(children.nextElement() as DefaultMutableTreeNode, expender)
        }
    }

    /** Selects each row on its own under a single-selection model. */
    private fun selectSingle() {
        val selector = TreeSelector(tree, TreeSelectionModel.SINGLE_TREE_SELECTION)
        for (i in 0 until tree.rowCount) {
            selector.addSelectionRows(intArrayOf(i))
            post(selector)
            rest()
        }
    }

    /** Selects three neighboring rows out of every four under a contiguous model. */
    private fun selectContiguous(): TreeSelector {
        val selector = TreeSelector(tree, TreeSelectionModel.CONTIGUOUS_TREE_SELECTION)
        val count = tree.rowCount / CONTIGUOUS_STRIDE
        for (i in 0 until count) {
            selector.addSelectionRows(rowsFrom(i * CONTIGUOUS_STRIDE))
            postAndWait(selector)
        }
        return selector
    }

    /**
     * Selects three neighboring rows out of every five under a discontiguous model.
     *
     * The model is installed by a selector the original builds and drops on the floor; the passes go
     * through [selector], the one the contiguous phase left, which drives the same tree.
     */
    private fun selectDiscontiguous(selector: TreeSelector) {
        TreeSelector(tree, TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        val count = tree.rowCount / DISCONTIGUOUS_STRIDE
        for (i in 0 until count) {
            selector.addSelectionRows(rowsFrom(i * DISCONTIGUOUS_STRIDE))
            postAndWait(selector)
        }
    }

    /** Closes every node, last row first, over the rows the tree showed when the phase began. */
    private fun collapseNodes() {
        val collapser = TreeExpender(tree, expand = false)
        val paths = Array(tree.rowCount) { row -> tree.getPathForRow(row) }

        for (i in paths.indices.reversed()) {
            collapser.setPath(paths[i])
            postAndWait(collapser)
        }
    }

    /** Removes every node under [node], deepest first. The root stays: a node is removed from its parent. */
    private fun removeNodes(
        node: DefaultMutableTreeNode,
        remover: TreeNodeAdder,
    ) {
        val nodeList = ArrayList<DefaultMutableTreeNode>()
        val nodes = node.depthFirstEnumeration()
        while (nodes.hasMoreElements()) {
            nodeList.add(nodes.nextElement() as DefaultMutableTreeNode)
        }

        for (nodeToRemove in nodeList) {
            remover.setNode(nodeToRemove, -1)
            postAndWait(remover)
        }
    }

    private inner class CountTree(
        model: TreeModel,
    ) : JTree(model) {
        override fun paint(g: Graphics) {
            super.paint(g)
            paintCount++
        }
    }
}

private const val TARGET_CHILD_COUNT = 200
private const val INTERVAL = 5
private const val USE_LARGE_MODEL = false

/** The fixed row height a large-model tree needs, every row being the same height under that model. */
private const val LARGE_MODEL_ROW_HEIGHT = 18
private const val CONTIGUOUS_STRIDE = 4
private const val DISCONTIGUOUS_STRIDE = 5
private const val ROWS_PER_STEP = 3

/** The rows one selection pass names: [first] and the two rows after it. */
private fun rowsFrom(first: Int): IntArray = IntArray(ROWS_PER_STEP) { first + it }

/** Adds a numbered node to a parent, or takes a node off its parent, which each pass posts. */
private class TreeNodeAdder(
    private val treeModel: DefaultTreeModel,
    private val add: Boolean,
) : Runnable {
    private var currentNode: DefaultMutableTreeNode? = null
    private var totalChildCount = 0

    fun setNode(
        node: DefaultMutableTreeNode,
        totalCount: Int,
    ) {
        currentNode = node
        totalChildCount = totalCount
    }

    override fun run() {
        val node = currentNode ?: return
        if (add) {
            treeModel.insertNodeInto(DefaultMutableTreeNode(totalChildCount), node, node.childCount)
        } else if (node.parent != null) {
            treeModel.removeNodeFromParent(node)
        }
    }
}

/**
 * Opens a path and scrolls to it, or scrolls to it and closes it.
 *
 * Opening is the scroll: a `JTree` asked to show a node opens its ancestors, and the `expandPath` before it
 * is reached only for a path the tree already has open.
 */
private class TreeExpender(
    private val tree: JTree,
    private val expand: Boolean,
) : Runnable {
    private var currentPath: TreePath? = null

    fun setPath(path: TreePath) {
        currentPath = path
    }

    override fun run() {
        val path = currentPath ?: return
        if (expand) {
            if (tree.isExpanded(path)) tree.expandPath(path)
            tree.scrollPathToVisible(path)
        } else if (!tree.isCollapsed(path)) {
            tree.scrollPathToVisible(path)
            tree.collapsePath(path)
        }
    }
}

/**
 * Adds rows to the tree's selection and scrolls to the last of them, which each selection pass posts.
 *
 * Building one installs a fresh selection model of the given mode on the tree, so the phase it opens starts
 * from an empty selection.
 */
private class TreeSelector(
    private val tree: JTree,
    mode: Int,
) : Runnable {
    private var rows: IntArray = IntArray(0)

    init {
        val selectionModel = DefaultTreeSelectionModel()
        selectionModel.selectionMode = mode
        tree.selectionModel = selectionModel
    }

    fun addSelectionRows(rows: IntArray) {
        this.rows = rows
    }

    override fun run() {
        tree.addSelectionRows(rows)
        tree.scrollRowToVisible(rows[rows.size - 1])
    }
}

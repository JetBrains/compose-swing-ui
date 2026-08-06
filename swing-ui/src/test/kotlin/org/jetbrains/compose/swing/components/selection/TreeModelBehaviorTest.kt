package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Behavioral tests for the model-driven `Tree(model, ...)` overloads, driven through the real
 * composition pipeline and asserting against the live `JTree`.
 *
 * The central guarantees: a caller-supplied [TreeModel] backs the tree as-is; user selection fires
 * `onSelectionChange` with each selected node's index path; a controlled `selectedPaths` re-applies
 * after a model swap even though `setModel` clears the selection; and a controlled selection update
 * does not echo back as a spurious callback. Paths resolve through the model's own accessors, so a
 * model that does not use [DefaultMutableTreeNode] nodes still selects correctly.
 */
class TreeModelBehaviorTest {
    /** Builds a `root -> {fruit -> {apple, pear}, veg -> {carrot}}` tree backed by a [DefaultTreeModel]. */
    private fun sampleModel(): DefaultTreeModel {
        val root = DefaultMutableTreeNode("root")
        val fruit = DefaultMutableTreeNode("fruit")
        fruit.add(DefaultMutableTreeNode("apple"))
        fruit.add(DefaultMutableTreeNode("pear"))
        val veg = DefaultMutableTreeNode("veg")
        veg.add(DefaultMutableTreeNode("carrot"))
        root.add(fruit)
        root.add(veg)
        return DefaultTreeModel(root)
    }

    @Test
    fun callerModelBacksTheTreeAsIs() = runComposeSwingTest {
        val model = sampleModel()
        setContent { Tree(model = model) }

        val tree = onNodeOfType<JTree>().fetch()
        assertSame(model, tree.model, "the caller-supplied model should back the tree as-is")
        assertEquals(2, model.getChildCount(model.root), "the root should have two children")
    }

    @Test
    fun selectingANodeFiresOnSelectionChange() = runComposeSwingTest {
        val model = sampleModel()
        val reported = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                model = model,
                onSelectionChange = { reported += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        // Selecting "pear" (root -> fruit -> pear) drives the real selection model, which fires the
        // TreeSelectionListener exactly as a user click would.
        tree.selectionPath = model.pathTo(0, 1)
        awaitIdle()

        assertEquals(
            listOf(setOf(listOf(0, 1))),
            reported,
            "the selected node should be reported once, as its index path",
        )
    }

    @Test
    fun controlledSelectionReAppliesAfterModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(sampleModel())
        setContent {
            Tree(
                model = model,
                selectedPaths = setOf(listOf(0, 1)),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            model.pathTo(0, 1),
            tree.selectionPath,
            "initial selection applied",
        )

        model = sampleModel()
        awaitIdle()

        assertSame(model, tree.model, "the swapped-in model should back the tree")
        assertEquals(
            model.pathTo(0, 1),
            tree.selectionPath,
            "controlled selection survives the model swap",
        )
    }

    /** A plain tree node - deliberately not a [DefaultMutableTreeNode]. */
    private class Node(
        val label: String,
        val children: List<Node> = emptyList(),
    )

    /**
     * A read-only [TreeModel] over [Node]s, so path resolution can only work through the model's own
     * accessors - any cast to [DefaultMutableTreeNode] would fail against these nodes.
     */
    private class NodeTreeModel(
        private val rootNode: Node,
    ) : TreeModel {
        override fun getRoot(): Any = rootNode

        override fun getChild(
            parent: Any,
            index: Int,
        ): Any = (parent as Node).children[index]

        override fun getChildCount(parent: Any): Int = (parent as Node).children.size

        override fun isLeaf(node: Any): Boolean = (node as Node).children.isEmpty()

        override fun getIndexOfChild(
            parent: Any?,
            child: Any?,
        ): Int = (parent as Node).children.indexOf(child)

        override fun valueForPathChanged(
            path: TreePath,
            newValue: Any?,
        ) = Unit

        override fun addTreeModelListener(listener: TreeModelListener?) = Unit

        override fun removeTreeModelListener(listener: TreeModelListener?) = Unit
    }

    @Test
    fun selectionResolvesForAModelWithoutDefaultMutableTreeNodeNodes() = runComposeSwingTest {
        val pear = Node("pear")
        val model =
            NodeTreeModel(
                Node(
                    "root",
                    listOf(
                        Node("fruit", listOf(Node("apple"), pear)),
                        Node("veg", listOf(Node("carrot"))),
                    ),
                ),
            )
        val reported = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                model = model,
                selectedPaths = setOf(listOf(0, 1)),
                onSelectionChange = { reported += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertSame(pear, tree.selectionPath?.lastPathComponent, "controlled path [0,1] resolves to the pear node")

        tree.selectionPath = model.pathTo(1, 0)
        awaitIdle()
        assertEquals(setOf(listOf(1, 0)), reported.last(), "the selected node reads back as its index path")
    }

    @Test
    fun reApplyingTheSameControlledSelectionDoesNotEcho() = runComposeSwingTest {
        val model = sampleModel()
        val reported = mutableListOf<Set<List<Int>>>()
        var trigger by mutableStateOf(0)
        setContent {
            // Recompose without changing selectedPaths: the echo-guard must skip re-setting an
            // unchanged selection so the programmatic set never re-enters the selection listener.
            trigger
            Tree(
                model = remember { model },
                selectedPaths = setOf(listOf(0, 1)),
                onSelectionChange = { reported += it },
            )
        }
        assertEquals(emptyList(), reported, "rendering the controlled selection must not fire onSelectionChange")

        trigger = 1
        awaitIdle()
        assertEquals(emptyList(), reported, "re-applying an unchanged controlled selection must not echo")
        assertEquals(
            model.pathTo(0, 1),
            onNodeOfType<JTree>().fetch().selectionPath,
            "the controlled selection should remain applied",
        )
    }
}

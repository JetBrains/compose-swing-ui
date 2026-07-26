package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A small data tree used to feed [Tree] from nested values: each [Node] yields its [children], and its
 * [name] is what the row renders.
 */
private data class Node(
    val name: String,
    val children: List<Node> = emptyList(),
)

/**
 * Behavioral coverage for [Tree] over a real applier. Each test asserts what an observer of the live
 * [JTree] sees: the rendered node structure, the value the user's callback receives when the selection
 * changes, and the structure after a state-driven data change.
 */
class TreeBehaviorTest {
    /** The displayed label of the node reached by following [indices] (child positions) from the root. */
    private fun JTree.labelAt(indices: List<Int>): String {
        var node = model.root as DefaultMutableTreeNode
        for (index in indices) {
            node = node.getChildAt(index) as DefaultMutableTreeNode
        }
        return node.userObject.toString()
    }

    private val sample =
        Node(
            "root",
            listOf(
                Node("fruit", listOf(Node("apple"), Node("pear"))),
                Node("veg", listOf(Node("carrot"))),
            ),
        )

    @Test
    fun nestedDataRendersTheExpectedNodeStructure() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                modifier = SwingModifier.name("tree"),
            )
        }

        val tree = onNodeWithName("tree").fetch<JTree>()
        val root = tree.model.root as DefaultMutableTreeNode
        assertEquals("root", root.userObject, "the root node should render its label")
        assertEquals(2, root.childCount, "the root should have two children")
        assertEquals("fruit", tree.labelAt(listOf(0)), "node [0] should be fruit")
        assertEquals("veg", tree.labelAt(listOf(1)), "node [1] should be veg")
        assertEquals("apple", tree.labelAt(listOf(0, 0)), "node [0,0] should be apple")
        assertEquals("pear", tree.labelAt(listOf(0, 1)), "node [0,1] should be pear")
        assertEquals("carrot", tree.labelAt(listOf(1, 0)), "node [1,0] should be carrot")
    }

    @Test
    fun selectingANodeFiresOnSelectionChange() = runComposeSwingTest {
        val reported = mutableListOf<List<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                modifier = SwingModifier.name("tree"),
                onSelectionChange = { reported += it },
            )
        }

        val tree = onNodeWithName("tree").fetch<JTree>()
        // Selecting "pear" (root -> fruit -> pear) on the EDT drives the real selection model, which
        // fires the TreeSelectionListener exactly as a user click would.
        tree.selectionPath = pathTo(tree, listOf(0, 1))
        awaitIdle()

        assertEquals(listOf(listOf(listOf(0, 1))), reported)
    }

    @Test
    fun stateDrivenDataChangeRebuildsTheTree() = runComposeSwingTest {
        var data by mutableStateOf(sample)
        setContent {
            Tree(
                root = data,
                children = { it.children },
                label = { it.name },
                modifier = SwingModifier.name("tree"),
            )
        }

        val tree = onNodeWithName("tree").fetch<JTree>()
        assertEquals(
            2,
            (tree.model.root as DefaultMutableTreeNode).childCount,
            "the tree should start with two children",
        )

        data = Node("root", listOf(Node("only", listOf(Node("leaf")))))
        awaitIdle()

        val root = tree.model.root as DefaultMutableTreeNode
        assertEquals(1, root.childCount, "the rebuilt tree should have one child")
        assertEquals("only", tree.labelAt(listOf(0)), "the rebuilt node [0] should be only")
        assertEquals("leaf", tree.labelAt(listOf(0, 0)), "the rebuilt node [0,0] should be leaf")
    }

    /** Builds the live `TreePath` to the node reached by following [indices] from the root. */
    private fun pathTo(
        tree: JTree,
        indices: List<Int>,
    ): TreePath {
        var node = tree.model.root as DefaultMutableTreeNode
        val nodes = ArrayList<DefaultMutableTreeNode>()
        nodes.add(node)
        for (index in indices) {
            node = node.getChildAt(index) as DefaultMutableTreeNode
            nodes.add(node)
        }
        return TreePath(nodes.toTypedArray())
    }
}

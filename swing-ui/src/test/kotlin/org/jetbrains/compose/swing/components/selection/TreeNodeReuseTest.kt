package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A parked [ReusableContentHost] child detaches its `JTree`, and reactivation builds a fresh one from
 * the node's own factory: a controlled selection reaches the fresh tree the same way it reaches any
 * freshly composed one.
 *
 * A composable node is owned by the composition too: the island stamping it lives only while the node is
 * in the composition, so a tree that outlives its own composable node - parked before it is torn down -
 * paints through the renderer its UI delegate builds, and the fresh tree reactivation builds stamps the
 * composable node again.
 */
class TreeNodeReuseTest {
    private val leaves = listOf("apple", "pear")

    /** A caller-owned model whose root holds the first [leafCount] of [leaves]. */
    private fun treeModel(leafCount: Int = leaves.size): DefaultTreeModel {
        val root = DefaultMutableTreeNode("root")
        for (leaf in leaves.take(leafCount)) root.add(DefaultMutableTreeNode(leaf))
        return DefaultTreeModel(root)
    }

    /** The labels of the nodes the tree has selected. */
    private fun JTree.selectedLabels(): List<String> = selectionPaths.orEmpty().map { it.lastPathComponent.toString() }

    /** Renders the node at [row] through the renderer this tree carries, as a `JTree` does when it paints it. */
    private fun JTree.stampRow(row: Int): Component {
        val node = getPathForRow(row).lastPathComponent
        return cellRenderer.getTreeCellRendererComponent(
            this,
            node,
            isRowSelected(row),
            isExpanded(row),
            model.isLeaf(node),
            row,
            hasFocus(),
        )
    }

    @Test
    fun aReactivatedTreeStillHoldsItsControlledSelection() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val model = treeModel()
        setContent {
            ReusableContentHost(active = active) {
                Tree(model = model, selectedPaths = setOf(listOf(1)))
            }
        }
        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("pear"), tree.selectedLabels(), "the controlled selection should be applied")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf("pear"),
            onNodeOfType<JTree>().fetch().selectedLabels(),
            "the fresh tree should hold the controlled selection",
        )
    }

    @Test
    fun aParkedTreeRendersItsOwnNodesAndTheFreshOneStampsTheComposableNodeAgain() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                Tree(
                    root = "root",
                    children = { if (it == "root") leaves else emptyList() },
                    label = { it },
                    expandedPaths = setOf(emptyList()),
                ) { value -> Label("<$value>") }
            }
        }
        val tree = onNodeOfType<JTree>().fetch()
        assertEquals("<apple>", tree.stampRow(row = 1).firstLabelText(), "the composable node should render row 1")

        active = false
        awaitIdle()

        // A parked tree keeps painting through whatever renderer it carries once the island behind its
        // composable node is gone: the renderer its UI delegate builds is what has to be back on it by
        // then.
        val parked = tree.stampRow(row = 1)
        assertTrue(parked is JLabel, "a parked tree should render rows through the renderer of its own")
        assertEquals("apple", (parked as JLabel).text, "the tree's own renderer renders the node's own text")

        active = true
        awaitIdle()

        assertEquals(
            "<pear>",
            onNodeOfType<JTree>().fetch().stampRow(row = 2).firstLabelText(),
            "the fresh tree should stamp the composable node",
        )
    }
}

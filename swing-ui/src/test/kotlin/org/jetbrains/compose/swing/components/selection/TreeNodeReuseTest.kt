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
import javax.swing.tree.TreeSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tree node is recyclable: a parked [ReusableContentHost] child reactivates onto the `JTree` the node
 * already holds, and the node's factory does not run again.
 *
 * A reactivation re-applies every declared parameter onto a tree that already holds a selection, so this
 * is where the two owners of a selection pull hardest against each other. A declared selection is the
 * composition's state, so it is re-asserted. An undeclared selection is the user's; reaching the same
 * declaration through a reactivation is not a reason for it to disappear. When the reactivated structure
 * cannot hold the selection, what is left of it still reaches the caller: the listeners a modifier
 * installs are detached while a node is parked, so a loss reported only through the widget's own event
 * would otherwise be lost with them.
 *
 * A composable node is owned the same way: the island stamping it lives only while the node is in the
 * composition, so a reactivation stamps the composable node again.
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
            "a reactivated tree should still hold the controlled selection",
        )
    }

    @Test
    fun aReactivatedTreeKeepsTheSelectionTheUserMade() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val received = mutableListOf<Set<List<Int>>>()
        val model = treeModel()
        setContent {
            ReusableContentHost(active = active) {
                Tree(model = model, onSelectionChange = { received += it })
            }
        }
        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(1))
        received.clear()

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf("pear"),
            onNodeOfType<JTree>().fetch().selectedLabels(),
            "an undeclared selection should survive a reactivation",
        )
        assertEquals(emptyList(), received, "a reactivation that keeps the selection has nothing to report")
    }

    @Test
    fun aReactivationThatDropsTheUsersSelectedNodeReportsWhatIsLeftOfIt() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var leafCount by mutableStateOf(leaves.size)
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            ReusableContentHost(active = active) {
                Tree(
                    // The root value itself carries the leaf count, so a change to it is a change to the
                    // declared data rather than to a lambda the composition memoizes.
                    root = "root of $leafCount",
                    children = { if (it.startsWith("root")) leaves.take(leafCount) else emptyList() },
                    onSelectionChange = { received += it },
                )
            }
        }
        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(1))
        received.clear()

        active = false
        awaitIdle()
        leafCount = 1
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            emptyList(),
            onNodeOfType<JTree>().fetch().selectedLabels(),
            "the node the new structure cannot hold leaves the selection",
        )
        assertEquals(listOf(emptySet()), received, "the selection the reactivation left should be reported once")
    }

    @Test
    fun aReactivationWithAModelThatDropsTheUsersSelectedNodeReportsWhatIsLeftOfIt() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var model by mutableStateOf(treeModel())
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            ReusableContentHost(active = active) {
                Tree(model = model, onSelectionChange = { received += it })
            }
        }
        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(1))
        received.clear()

        active = false
        awaitIdle()
        model = treeModel(leafCount = 1)
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            emptyList(),
            onNodeOfType<JTree>().fetch().selectedLabels(),
            "the node the new model cannot hold leaves the selection",
        )
        assertEquals(listOf(emptySet()), received, "the selection the reactivation left should be reported once")
    }

    @Test
    fun aReactivationWithANarrowerModeReportsTheSelectionItLeaves() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var mode by mutableStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        val received = mutableListOf<Set<List<Int>>>()
        val model = treeModel()
        setContent {
            ReusableContentHost(active = active) {
                Tree(model = model, onSelectionChange = { received += it }, selectionMode = mode)
            }
        }
        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(0), tree.pathTo(1))
        received.clear()

        // A mode narrowed while the node is parked reaches the tree on the reactivation pass, which applies
        // it before the modifier reinstalls the listeners the tree reports through.
        active = false
        awaitIdle()
        mode = TreeSelectionModel.SINGLE_TREE_SELECTION
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(1, onNodeOfType<JTree>().fetch().selectionCount, "the node the narrower mode holds stays")
        assertEquals(listOf(setOf(listOf(0))), received, "the node the reactivation left should be reported once")
    }

    @Test
    fun aParkedTreeRendersItsOwnNodesAndStampsTheComposableOneAgainOnReactivation() = runComposeSwingTest {
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

        // A parked tree keeps its place in the Swing tree and goes on painting, while the island behind
        // its composable node is gone, so the renderer its UI delegate builds has to be back on it by
        // then.
        val parked = tree.stampRow(row = 1)
        assertTrue(parked is JLabel, "a parked tree should render rows through the renderer of its own")
        assertEquals("apple", (parked as JLabel).text, "the tree's own renderer renders the node's own text")

        active = true
        awaitIdle()

        assertEquals(
            "<pear>",
            onNodeOfType<JTree>().fetch().stampRow(row = 2).firstLabelText(),
            "a reactivated tree should stamp the composable node again",
        )
    }
}

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A tree node is recyclable: a parked [ReusableContentHost] child is reactivated onto the `JTree` the node
 * already holds, and the node's factory does not run a second time.
 *
 * A reactivation re-applies every declared parameter onto a tree that is already holding a selection, so it
 * is where the two owners of a selection pull hardest against each other. A declared selection is the
 * composition's state and is re-asserted; an undeclared one is the user's, and reaching the same
 * declarations by way of a reactivation is not a reason for it to disappear. Where the structure the
 * reactivation brings really cannot hold it, what is left of it reaches the caller all the same - the
 * listeners a modifier installs are detached while a node is parked, so a loss reported through the widget's
 * own event would be lost with them.
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

    @Test
    fun aReactivatedTreeStillHoldsItsControlledSelection() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val model = treeModel()
        setContent {
            ReusableContentHost(active = active) {
                Tree(model = model, selectedPaths = listOf(listOf(1)))
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
        val received = mutableListOf<List<List<Int>>>()
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
        val received = mutableListOf<List<List<Int>>>()
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
        assertEquals(listOf(emptyList()), received, "the selection the reactivation left should be reported once")
    }

    @Test
    fun aReactivationWithAModelThatDropsTheUsersSelectedNodeReportsWhatIsLeftOfIt() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var model by mutableStateOf(treeModel())
        val received = mutableListOf<List<List<Int>>>()
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
        assertEquals(listOf(emptyList()), received, "the selection the reactivation left should be reported once")
    }
}

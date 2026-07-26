package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import javax.swing.LookAndFeel
import javax.swing.tree.DefaultMutableTreeNode
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
 * [JTree] sees: the rendered node structure, the rows a hidden or shown root leaves at the top, the value
 * the user's callback receives when the selection changes, and the structure after a state-driven data
 * change.
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
    fun anUndeclaredRootHandleChoiceStaysOpenToTheLookAndFeel() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        val installed = !tree.showsRootHandles
        // A look and feel installs this property through installProperty, which a JTree honours only
        // while the property has never been set explicitly. Asking the tree to take a look-and-feel
        // value is therefore what distinguishes "left to the look and feel" from "set to the same
        // value", and it does so whatever the host's look and feel happens to default to.
        LookAndFeel.installProperty(tree, "showsRootHandles", installed)
        assertEquals(
            installed,
            tree.showsRootHandles,
            "an undeclared choice should still accept the look and feel's value",
        )
    }

    @Test
    fun aDeclaredRootHandleChoiceOverridesTheLookAndFeel() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                showsRootHandles = true,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(true, tree.showsRootHandles, "a declared choice should be applied")
        LookAndFeel.installProperty(tree, "showsRootHandles", false)
        assertEquals(
            true,
            tree.showsRootHandles,
            "a declared choice should outrank the look and feel",
        )
    }

    @Test
    fun aWithdrawnRootHandleChoiceGoesBackToTheLookAndFeelsOwn() = runComposeSwingTest {
        var declared: Boolean? by mutableStateOf(null)
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                showsRootHandles = declared,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        // The choice the look and feel made for this tree, whatever the host's happens to be. Declaring
        // its opposite is what makes the value the withdrawal has to reach tell the two apart.
        val ownChoice = tree.showsRootHandles

        declared = !ownChoice
        awaitIdle()
        assertEquals(!ownChoice, tree.showsRootHandles, "a declared choice should be applied")

        declared = null
        awaitIdle()
        assertEquals(
            ownChoice,
            tree.showsRootHandles,
            "withdrawing the declaration should give the look and feel's own choice back",
        )
    }

    @Test
    fun aRootHandleChoiceWithdrawnWhileParkedGoesBackOnTheReactivation() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var declared: Boolean? by mutableStateOf(null)
        setContent {
            ReusableContentHost(active = active) {
                Tree(
                    root = sample,
                    children = { it.children },
                    label = { it.name },
                    showsRootHandles = declared,
                )
            }
        }

        val ownChoice = onNodeOfType<JTree>().fetch().showsRootHandles
        declared = !ownChoice
        awaitIdle()

        assertEquals(
            !ownChoice,
            onNodeOfType<JTree>().fetch().showsRootHandles,
            "a declared root-handle choice should displace the look and feel's own",
        )

        active = false
        awaitIdle()
        declared = null
        active = true
        awaitIdle()

        assertEquals(
            ownChoice,
            onNodeOfType<JTree>().fetch().showsRootHandles,
            "a reactivated tree should give the look and feel's own choice back",
        )
    }

    @Test
    fun nestedDataRendersTheExpectedNodeStructure() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
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
                onSelectionChange = { reported += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        // Selecting "pear" (root -> fruit -> pear) on the EDT drives the real selection model, which
        // fires the TreeSelectionListener exactly as a user click would.
        tree.selectionPath = tree.pathTo(0, 1)
        awaitIdle()

        assertEquals(
            listOf(listOf(listOf(0, 1))),
            reported,
            "the selected node should be reported once, as its index path",
        )
    }

    @Test
    fun aSelectionTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                selectedPaths = listOf(listOf(0)),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPath = tree.pathTo(1)
        awaitIdle()

        // The tree is already settled back onto the declared selection by the time the move's own
        // recomposition finishes - not just once some later, unrelated recomposition happens to run.
        assertEquals(
            listOf(tree.pathTo(0)),
            tree.selectionPaths?.toList(),
            "an unadopted selection change does not stand",
        )
    }

    @Test
    fun stateDrivenDataChangeRebuildsTheTree() = runComposeSwingTest {
        var data by mutableStateOf(sample)
        setContent {
            Tree(
                root = data,
                children = { it.children },
                label = { it.name },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
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

    @Test
    fun aHiddenRootLeavesItsChildrenAsTheTopRows() = runComposeSwingTest {
        var rootVisible by mutableStateOf(false)
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                rootVisible = rootVisible,
                expandedPaths = listOf(emptyList()),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            listOf("fruit", "veg"),
            (0 until tree.rowCount).map { tree.getPathForRow(it).lastPathComponent.toString() },
            "a hidden root leaves its children as the tree's top-level rows",
        )

        rootVisible = true
        awaitIdle()
        assertEquals(
            listOf("root", "fruit", "veg"),
            (0 until tree.rowCount).map { tree.getPathForRow(it).lastPathComponent.toString() },
            "showing the root again puts it back at the top",
        )
    }

    @Test
    fun theLatestCallbacksAreTheOnesThatRun() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        val firstSelection: (List<List<Int>>) -> Unit = { reported += "first selection" }
        val secondSelection: (List<List<Int>>) -> Unit = { reported += "second selection" }
        val firstExpansion: (List<List<Int>>) -> Unit = { reported += "first expansion" }
        val secondExpansion: (List<List<Int>>) -> Unit = { reported += "second expansion" }
        var second by mutableStateOf(false)
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                onSelectionChange = if (second) secondSelection else firstSelection,
                onExpansionChange = if (second) secondExpansion else firstExpansion,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        second = true
        awaitIdle()

        tree.selectionPath = tree.pathTo(0)
        tree.expandPath(tree.pathTo(0))
        awaitIdle()

        assertEquals(
            listOf("second selection", "second expansion"),
            reported,
            "a callback the recomposition replaced is not the one that runs",
        )
    }
}

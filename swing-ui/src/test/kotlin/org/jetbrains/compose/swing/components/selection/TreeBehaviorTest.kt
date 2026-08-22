package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsNeverPainted
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import javax.swing.LookAndFeel
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        // installProperty on a JTree only takes effect while the property has never been set explicitly,
        // so asking the tree to take a look-and-feel value is what distinguishes "left to the look and
        // feel" from "set to the same value", whatever the host's look and feel defaults to.
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
    fun anUndeclaredRowHeightStaysOpenToTheLookAndFeel() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        // installProperty applies the same way here: only while rowHeight was never set explicitly.
        val installed = tree.rowHeight + 7
        LookAndFeel.installProperty(tree, "rowHeight", installed)
        assertEquals(
            installed,
            tree.rowHeight,
            "an undeclared row height should still accept the look and feel's value",
        )
    }

    @Test
    fun aWithdrawnRowHeightGoesBackToTheLookAndFeelsOwn() = runComposeSwingTest {
        var declared: Int? by mutableStateOf(null)
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                rowHeight = declared,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        // Declaring a height different from the look and feel's own is what makes the withdrawal observable.
        val ownHeight = tree.rowHeight

        declared = ownHeight + 9
        awaitIdle()
        assertEquals(ownHeight + 9, tree.rowHeight, "a declared row height should be applied")

        declared = null
        awaitIdle()
        assertEquals(
            ownHeight,
            tree.rowHeight,
            "withdrawing the declaration should give the look and feel's own height back",
        )
    }

    @Test
    fun anUndeclaredTreeIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { Tree(root = "r", children = { emptyList() }) }
        onNodeOfType<JTree>().assertTreeMatches(JTree(DefaultMutableTreeNode("r")))
    }

    @Test
    fun undeclaredSizingLeavesTheTreesOwnDefaults() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        val own = JTree()
        assertEquals(own.visibleRowCount, tree.visibleRowCount, "the visible row count should be a JTree's own")
        assertEquals(own.toggleClickCount, tree.toggleClickCount, "the toggle click count should be a JTree's own")
    }

    @Test
    fun theSizingParametersAreReAppliedOnRecomposition() = runComposeSwingTest {
        var rowHeight: Int? by mutableStateOf(24)
        var visibleRowCount by mutableStateOf(12)
        var toggleClickCount by mutableStateOf(1)
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                rowHeight = rowHeight,
                visibleRowCount = visibleRowCount,
                toggleClickCount = toggleClickCount,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(24, tree.rowHeight, "the declared row height should reach the tree")
        assertEquals(12, tree.visibleRowCount, "the declared visible row count should reach the tree")
        assertEquals(1, tree.toggleClickCount, "the declared toggle click count should reach the tree")

        rowHeight = 0
        visibleRowCount = 5
        toggleClickCount = 3
        awaitIdle()
        assertEquals(0, tree.rowHeight, "a row height of zero should reach the tree, so each row sizes itself")
        assertEquals(5, tree.visibleRowCount, "a changed visible row count should be re-applied")
        assertEquals(3, tree.toggleClickCount, "a changed toggle click count should be re-applied")
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
        assertEquals("root", tree.labelAt(emptyList()), "the root node should render its label")
        assertEquals(2, root.childCount, "the root should have two children")
        assertEquals("fruit", tree.labelAt(listOf(0)), "node [0] should be fruit")
        assertEquals("veg", tree.labelAt(listOf(1)), "node [1] should be veg")
        assertEquals("apple", tree.labelAt(listOf(0, 0)), "node [0,0] should be apple")
        assertEquals("pear", tree.labelAt(listOf(0, 1)), "node [0,1] should be pear")
        assertEquals("carrot", tree.labelAt(listOf(1, 0)), "node [1,0] should be carrot")
    }

    @Test
    fun selectingANodeFiresOnSelectionChange() = runComposeSwingTest {
        val reported = mutableListOf<Set<List<Int>>>()
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
            listOf(setOf(listOf(0, 1))),
            reported,
            "the selected node should be reported once, as its index path",
        )
    }

    /**
     * The tree twin of
     * [TableBehaviorTest.aSelectionTheCallerDoesNotAdoptDoesNotStandWhenItIsMadeAgain], over selection
     * paths rather than row indices.
     */
    @Test
    fun aSelectionTheCallerDoesNotAdoptDoesNotStandWhenItIsMadeAgain() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                selectedPaths = setOf(listOf(0)),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        repeat(2) {
            tree.selectionPath = tree.pathTo(1)
            awaitIdle()
        }

        assertEquals(
            listOf(tree.pathTo(0)),
            tree.selectionPaths?.toList(),
            "an unadopted selection change does not stand, however often it is made",
        )
    }

    @Test
    fun stateDrivenDataChangeMovesTheRows() = runComposeSwingTest {
        var data by mutableStateOf(sample)
        setContent {
            Tree(
                root = data,
                children = { it.children },
                label = { it.name },
                expandedPaths = setOf(emptyList(), listOf(0)),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            listOf("root", "fruit", "apple", "pear", "veg"),
            tree.rowLabels(),
            "the tree should start on the rows the first data describes",
        )

        data = Node("root", listOf(Node("only", listOf(Node("leaf")))))
        awaitIdle()

        assertEquals(
            listOf("root", "only", "leaf"),
            tree.rowLabels(),
            "the rows should be the ones the new data describes",
        )
    }

    /**
     * The expansion twin of the selection case: a node the caller declares open is reopened for every
     * collapse made against that declaration, not only for the first one.
     */
    @Test
    fun aCollapseTheCallerDoesNotAdoptDoesNotStandWhenItIsMadeAgain() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = setOf(emptyList(), listOf(0)),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        repeat(2) {
            tree.collapsePath(tree.pathTo(0))
            awaitIdle()
        }

        assertTrue(
            tree.isExpanded(tree.pathTo(0)),
            "an unadopted collapse does not stand, however often it is made",
        )
    }

    @Test
    fun aHiddenRootLeavesItsChildrenAsTheTopRows() = runComposeSwingTest {
        var rootVisible by mutableStateOf(false)
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = setOf(emptyList()),
                rootVisible = rootVisible,
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
        val firstSelection: (Set<List<Int>>) -> Unit = { reported += "first selection" }
        val secondSelection: (Set<List<Int>>) -> Unit = { reported += "second selection" }
        val firstExpansion: (Set<List<Int>>) -> Unit = { reported += "first expansion" }
        val secondExpansion: (Set<List<Int>>) -> Unit = { reported += "second expansion" }
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

    @Test
    fun aSelectionTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        assertUnadoptedChangeIsNeverPainted(
            type = JTree::class.java,
            declared = emptyList<Int>(),
            content = { report ->
                Tree(
                    root = "root",
                    children = { node -> if (node == "root") listOf("one", "two") else emptyList() },
                    selectedPaths = emptySet(),
                    onSelectionChange = { report() },
                )
            },
            change = { it.setSelectionRow(0) },
            read = { it.selectionRows?.toList().orEmpty() },
        )
    }

    @Test
    fun anExpansionTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        assertUnadoptedChangeIsNeverPainted(
            type = JTree::class.java,
            declared = false,
            content = { report ->
                Tree(
                    root = "root",
                    children = { node -> if (node == "root") listOf("one", "two") else emptyList() },
                    expandedPaths = emptySet(),
                    onExpansionChange = { report() },
                )
            },
            change = { it.expandRow(0) },
            read = { it.isExpanded(0) },
        )
    }
}

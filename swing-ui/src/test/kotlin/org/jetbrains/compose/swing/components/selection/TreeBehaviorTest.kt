package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsNeverPainted
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import javax.swing.LookAndFeel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

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
        onNodeOfType<JTree>().assertProperty(
            installed,
            "an undeclared choice should still accept the look and feel's value",
        ) { showsRootHandles }
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
        onNodeOfType<JTree>().assertProperty(true, "a declared choice should be applied") { showsRootHandles }
        LookAndFeel.installProperty(tree, "showsRootHandles", false)
        onNodeOfType<JTree>().assertProperty(
            true,
            "a declared choice should outrank the look and feel",
        ) { showsRootHandles }
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
        onNodeOfType<JTree>().assertProperty(!ownChoice, "a declared choice should be applied") { showsRootHandles }

        declared = null
        awaitIdle()
        assertSame(tree, onNodeOfType<JTree>().fetch(), "the component is updated in place, not rebuilt")
        onNodeOfType<JTree>().assertProperty(
            ownChoice,
            "withdrawing the declaration should give the look and feel's own choice back",
        ) { showsRootHandles }
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

        onNodeOfType<JTree>().assertProperty(
            !ownChoice,
            "a declared root-handle choice should displace the look and feel's own",
        ) { showsRootHandles }
        active = false
        awaitIdle()
        declared = null
        active = true
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            ownChoice,
            "a reactivated tree should give the look and feel's own choice back",
        ) { showsRootHandles }
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

        // Declaring a height different from the look and feel's own is what makes the withdrawal observable.
        val ownHeight = onNodeOfType<JTree>().fetch().rowHeight

        val declaredHeight = 31
        assertNotEquals(declaredHeight, ownHeight, "the declared height must differ from the look and feel's")
        declared = declaredHeight
        awaitIdle()
        onNodeOfType<JTree>().assertProperty(declaredHeight, "a declared row height should be applied") { rowHeight }

        declared = null
        awaitIdle()
        onNodeOfType<JTree>().assertProperty(
            ownHeight,
            "withdrawing the declaration should give the look and feel's own height back",
        ) { rowHeight }
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

        val own = JTree()
        onNodeOfType<JTree>().assertProperty(own.visibleRowCount, "the visible row count should be a JTree's own") {
            visibleRowCount
        }
        onNodeOfType<JTree>().assertProperty(own.toggleClickCount, "the toggle click count should be a JTree's own") {
            toggleClickCount
        }
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

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(24, "the declared row height should reach the tree") { this.rowHeight }
        tree.assertProperty(12, "the declared visible row count should reach the tree") { this.visibleRowCount }
        tree.assertProperty(1, "the declared toggle click count should reach the tree") { this.toggleClickCount }

        rowHeight = 0
        visibleRowCount = 5
        toggleClickCount = 3
        awaitIdle()
        tree.assertProperty(0, "a row height of zero should reach the tree, so each row sizes itself") {
            this.rowHeight
        }
        tree.assertProperty(5, "a changed visible row count should be re-applied") { this.visibleRowCount }
        tree.assertProperty(3, "a changed toggle click count should be re-applied") { this.toggleClickCount }
    }

    @Test
    fun theDeclaredStructureReplacesTheModelAFactorysTreeCarried() = runComposeSwingTest {
        setContent {
            Tree(
                factory = { JTree() },
                root = sample,
                children = { it.children },
                label = { it.name },
                state = remember { TreeState(initialExpandedPaths = setOf(emptyList())) },
            )
        }

        onNodeOfType<JTree>().assertProperty(
            listOf("root", "fruit", "veg"),
            "the declared structure should replace the model the factory's tree started on",
        ) { rowLabels() }
    }

    @Test
    fun aRootHandleChoiceAndARowHeightWinOverAFactoryUntilTheyAreWithdrawn() = runComposeSwingTest {
        var showsRootHandles: Boolean? by mutableStateOf(null)
        var rowHeight: Int? by mutableStateOf(null)
        setContent {
            Tree(
                factory = {
                    JTree().apply {
                        this.showsRootHandles = false
                        this.rowHeight = 30
                    }
                },
                root = sample,
                children = { it.children },
                state = remember { TreeState() },
                showsRootHandles = showsRootHandles,
                rowHeight = rowHeight,
            )
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(false, "an undeclared choice should leave the factory's") { this.showsRootHandles }
        tree.assertProperty(30, "an undeclared height should leave the factory's") { this.rowHeight }

        showsRootHandles = true
        rowHeight = 40
        awaitIdle()
        tree.assertProperty(true, "a declared choice should win over the factory's") { this.showsRootHandles }
        tree.assertProperty(40, "a declared height should win over the factory's") { this.rowHeight }

        showsRootHandles = null
        rowHeight = null
        awaitIdle()
        tree.assertProperty(false, "a withdrawn choice should give the factory's back") { this.showsRootHandles }
        tree.assertProperty(30, "a withdrawn height should give the factory's back") { this.rowHeight }
    }

    @Test
    fun aSelectionModeAndEditabilityAreWrittenOverAFactorys() = runComposeSwingTest {
        var selectionMode by mutableIntStateOf(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION)
        var isEditable by mutableStateOf(false)
        setContent {
            Tree(
                factory = {
                    JTree().apply {
                        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
                        this.isEditable = true
                    }
                },
                root = sample,
                children = { it.children },
                state = remember { TreeState() },
                isEditable = isEditable,
                selectionMode = selectionMode,
            )
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(
            TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
            "the declared mode should be written over the factory's",
        ) { selectionModel.selectionMode }
        tree.assertProperty(false, "the declared editability should be written over the factory's") { this.isEditable }

        selectionMode = TreeSelectionModel.CONTIGUOUS_TREE_SELECTION
        isEditable = true
        awaitIdle()
        tree.assertProperty(
            TreeSelectionModel.CONTIGUOUS_TREE_SELECTION,
            "a recomposition should write the next declared mode",
        ) { selectionModel.selectionMode }
        tree.assertProperty(true, "a recomposition should write the next declared editability") { this.isEditable }
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

        val tree = onNodeOfType<JTree>()
        tree.assertProperty("root", "the root node should render its label") { labelAt(emptyList()) }
        tree.assertProperty(
            2,
            "the root should have two children",
        ) { model.getChildCount(model.root) }
        tree.assertProperty("fruit", "node [0] should be fruit") { labelAt(listOf(0)) }
        tree.assertProperty("veg", "node [1] should be veg") { labelAt(listOf(1)) }
        tree.assertProperty("apple", "node [0,0] should be apple") { labelAt(listOf(0, 0)) }
        tree.assertProperty("pear", "node [0,1] should be pear") { labelAt(listOf(0, 1)) }
        tree.assertProperty("carrot", "node [1,0] should be carrot") { labelAt(listOf(1, 0)) }
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
        val state = TreeState(initialExpandedPaths = setOf(emptyList(), listOf(0)))
        setContent {
            Tree(root = data, children = { it.children }, state = state, label = { it.name })
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(
            listOf("root", "fruit", "apple", "pear", "veg"),
            "the tree should start on the rows the first data describes",
        ) { rowLabels() }
        val treeBefore = tree.fetch()

        data = Node("root", listOf(Node("only", listOf(Node("leaf")))))
        awaitIdle()
        assertSame(treeBefore, tree.fetch(), "the component is updated in place, not rebuilt")

        tree.assertProperty(
            listOf("root", "only", "leaf"),
            "the rows should be the ones the new data describes",
        ) { rowLabels() }
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
            )
        }

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(
            listOf("fruit", "veg"),
            "a hidden root leaves its children as the tree's top-level rows",
        ) { (0 until rowCount).map { getPathForRow(it).lastPathComponent.toString() } }

        rootVisible = true
        awaitIdle()
        tree.assertProperty(
            listOf("root", "fruit", "veg"),
            "showing the root again puts it back at the top",
        ) { (0 until rowCount).map { getPathForRow(it).lastPathComponent.toString() } }
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
}

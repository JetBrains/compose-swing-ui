package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A value tree: each entry yields its [children], and its [name] is what the row renders. */
private data class Entry(
    val name: String,
    val children: List<Entry> = emptyList(),
)

/** The expansion listeners the library installed on the tree, as opposed to the tree UI's own. */
private fun JTree.libraryExpansionListeners(): List<TreeExpansionListener> =
    treeExpansionListeners.filter { it.javaClass.name.startsWith("org.jetbrains.compose.swing") }

/**
 * Expansion is state: [Tree] applies the expansion the composition declares, reports the expansion the
 * user reaches, and keeps a declared expansion across a structure change, which a plain `JTree` discards
 * along with the model it belonged to.
 *
 * Tests drive expansion through `JTree.expandPath`, the same call the tree's UI makes when the user
 * clicks a handle.
 */
class TreeExpansionTest {
    private val sample =
        Entry(
            "root",
            listOf(
                Entry("fruit", listOf(Entry("apple"), Entry("pear"))),
                Entry("veg", listOf(Entry("carrot"))),
            ),
        )

    private val deep = Entry("root", listOf(Entry("a", listOf(Entry("b", listOf(Entry("c")))))))

    private fun sampleModel(rootLabel: String): DefaultTreeModel {
        fun node(entry: Entry): DefaultMutableTreeNode =
            DefaultMutableTreeNode(entry.name).apply { for (child in entry.children) add(node(child)) }
        return DefaultTreeModel(node(sample.copy(name = rootLabel)))
    }

    @Test
    fun expandingANodeReportsEveryExpandedNode() = runComposeSwingTest {
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                onExpansionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        awaitIdle()

        assertEquals(listOf(setOf(emptyList(), listOf(0))), received, "every expanded node is reported")

        tree.collapsePath(tree.pathTo(0))
        awaitIdle()

        assertEquals(setOf(emptyList()), received.last(), "a collapse reports the expansion that remains")
    }

    @Test
    fun aDeclaredExpansionReachesTheTree() = runComposeSwingTest {
        var expansion by mutableStateOf(setOf(emptyList<Int>(), listOf(1)))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = expansion,
                onExpansionChange = { received += it },
            )
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(1)), "the declared node should be expanded")
        assertFalse(tree.isExpanded(tree.pathTo(0)), "an undeclared node should be collapsed")

        // Every node the declaration leaves out collapses again: expansion is controlled, not only additive.
        expansion = setOf(emptyList(), listOf(0))
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertTrue(
            tree.isExpanded(tree.pathTo(0)),
            "the one pass that declares the node should already have opened it",
        )
        assertFalse(
            tree.isExpanded(tree.pathTo(1)),
            "the one pass that drops the node from the declaration should already have closed it",
        )
        assertEquals(emptyList(), received, "applying a declared expansion reported it back as the user's")
    }

    @Test
    fun aDeclaredExpansionKeepsItsAncestorsOpen() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = setOf(listOf(0)),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the declared node should be expanded")
        assertTrue(tree.isExpanded(tree.pathTo()), "its ancestor has to stay expanded to reach it")
    }

    @Test
    fun aDeclaredExpansionSurvivesAStructureChange() = runComposeSwingTest {
        var label by mutableStateOf("root")
        var expansion by mutableStateOf(setOf(emptyList<Int>(), listOf(0)))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample.copy(name = label),
                children = { it.children },
                label = { it.name },
                expandedPaths = expansion,
                onExpansionChange = {
                    received += it
                    expansion = it
                },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the declared node should be expanded")

        label = "trunk"
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "expansion survives a structure change")
        assertEquals(setOf(emptyList(), listOf(0)), expansion, "the controlled expansion survives")
        assertEquals(emptyList(), received, "a structure change reported an expansion change")
    }

    @Test
    fun anUndeclaredExpansionIsLeftToTheUser() = runComposeSwingTest {
        var rootVisible by mutableStateOf(true)
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                rootVisible = rootVisible,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))

        // A recomposition that declares no expansion has no opinion, so what the user opened stays open.
        rootVisible = false
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "an undeclared expansion should be left alone")
    }

    @Test
    fun anUndeclaredExpansionSurvivesAStructureChange() = runComposeSwingTest {
        var label by mutableStateOf("root")
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample.copy(name = label),
                children = { it.children },
                label = { it.name },
                onExpansionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        received.clear()

        label = "trunk"
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "an undeclared expansion is the user's to keep")
        assertEquals(emptyList(), received, "a structure change reported an expansion change")
    }

    @Test
    fun anUndeclaredExpansionSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(sampleModel("root"))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(model = model, onExpansionChange = { received += it })
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        received.clear()

        model = sampleModel("trunk")
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "an undeclared expansion is the user's to keep")
        assertEquals(emptyList(), received, "a model swap reported an expansion change")
    }

    @Test
    fun anUndeclaredCollapseSurvivesAStructureChange() = runComposeSwingTest {
        var label by mutableStateOf("root")
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample.copy(name = label),
                children = { it.children },
                label = { it.name },
                onExpansionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.collapsePath(tree.pathTo())
        assertFalse(tree.isExpanded(tree.pathTo()), "the user's collapse reaches the tree")
        received.clear()

        label = "trunk"
        awaitIdle()

        assertFalse(tree.isExpanded(tree.pathTo()), "a collapse the user made survives a structure change")
        assertEquals(emptyList(), received, "a structure change reported an expansion change")
    }

    @Test
    fun anUndeclaredCollapseSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(sampleModel("root"))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(model = model, onExpansionChange = { received += it })
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.collapsePath(tree.pathTo())
        assertFalse(tree.isExpanded(tree.pathTo()), "the user's collapse reaches the tree")
        received.clear()

        model = sampleModel("trunk")
        awaitIdle()

        assertFalse(tree.isExpanded(tree.pathTo()), "a collapse the user made survives a model swap")
        assertEquals(emptyList(), received, "a model swap reported an expansion change")
    }

    @Test
    fun aRefusedExpansionIsRestored() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                modifier = SwingModifier.name(label),
                label = { it.name },
                expandedPaths = setOf(emptyList()),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the user's expansion reaches the tree")

        label = "second"
        awaitIdle()

        assertFalse(tree.isExpanded(tree.pathTo(0)), "the declared expansion is re-applied")
    }

    @Test
    fun aNarrowedExpansionCollapsesTheDeepestNodesFirst() = runComposeSwingTest {
        var expansion by mutableStateOf(setOf(emptyList(), listOf(0), listOf(0, 0)))
        setContent {
            Tree(root = deep, children = { it.children }, label = { it.name }, expandedPaths = expansion)
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0, 0)), "the declared nodes should be expanded")

        expansion = setOf(emptyList())
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo()), "the still-declared root stays expanded")
        assertFalse(tree.isExpanded(tree.pathTo(0)), "the node dropped from the declaration collapses")
        assertFalse(tree.isExpanded(tree.pathTo(0, 0)), "its dropped child collapses too")
    }

    /**
     * A tree remembers a node it was showing open under one it is asked to close, and brings it back open
     * with the ancestor that was hiding it. Only what the tree shows open once the declaration's own
     * expansions have run says which nodes the declaration leaves out.
     */
    @Test
    fun aDescendantTheTreeRemembersOpenIsClosedWhenTheDeclarationReopensItsAncestor() = runComposeSwingTest {
        var expansion by mutableStateOf<Set<List<Int>>?>(null)
        setContent {
            Tree(root = deep, children = { it.children }, label = { it.name }, expandedPaths = expansion)
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        tree.expandPath(tree.pathTo(0, 0))
        tree.collapsePath(tree.pathTo(0))
        awaitIdle()
        assertEquals(listOf("root", "a"), tree.rowLabels(), "an undeclared collapse is the user's and stands")

        expansion = setOf(emptyList(), listOf(0))
        awaitIdle()

        assertEquals(
            listOf("root", "a", "b"),
            tree.rowLabels(),
            "the node the declaration leaves out is closed even where reopening its ancestor brought it back",
        )
        assertFalse(tree.isExpanded(tree.pathTo(0, 0)), "so its own child has no row")
    }

    @Test
    fun aDeclaredCollapseTakesOverTheSelectionItHides() = runComposeSwingTest {
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                // The declared expansion leaves the subtree holding the declared selection closed.
                expandedPaths = setOf(emptyList()),
                selectedPaths = setOf(listOf(0, 0)),
                onSelectionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertFalse(tree.isExpanded(tree.pathTo(0)), "the node the declaration closes stays closed")
        assertEquals(
            listOf(tree.pathTo(0)),
            tree.selectionPaths?.toList(),
            "the closed node holds the selection its hidden descendant cannot",
        )
        assertEquals(listOf(setOf(listOf(0))), received, "and the selection it took over is reported once")
    }

    @Test
    fun installingAModelNeverOpensTheNodeTheDeclarationCloses() = runComposeSwingTest {
        var model by mutableStateOf(sampleModel("root"))
        val received = mutableListOf<Set<List<Int>>>()
        val opened = mutableListOf<TreePath>()
        setContent {
            Tree(
                model = model,
                // The declared expansion leaves the subtree holding the declared selection closed.
                expandedPaths = setOf(emptyList()),
                selectedPaths = setOf(listOf(0, 0)),
                onSelectionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf(setOf(listOf(0))), received, "the closed node takes over the selection it hides")
        // A listener of the test's own is handed the wrapper's writes as well as the user's, so a node
        // opened and closed again inside one install still shows up here.
        tree.addTreeExpansionListener(
            object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent) {
                    opened += event.path
                }

                override fun treeCollapsed(event: TreeExpansionEvent) = Unit
            },
        )

        model = sampleModel("trunk")
        awaitIdle()

        assertEquals(emptyList(), opened, "installing a model opens no node the declared expansion closes")
        assertFalse(tree.isExpanded(tree.pathTo(0)), "the node the declaration closes stays closed")
        assertEquals(
            listOf(tree.pathTo(0)),
            tree.selectionPaths?.toList(),
            "and holds the selection its hidden descendant cannot",
        )
    }

    @Test
    fun aDeclaredCollapseReportsTheSelectionTheUserLosesToIt() = runComposeSwingTest {
        var expansion by mutableStateOf(setOf(emptyList(), listOf(0)))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = expansion,
                onSelectionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPath = tree.pathTo(0, 0)
        received.clear()

        expansion = setOf(emptyList())
        awaitIdle()

        assertEquals(
            listOf(tree.pathTo(0)),
            tree.selectionPaths?.toList(),
            "the closed node holds the selection its hidden descendant cannot",
        )
        assertEquals(listOf(setOf(listOf(0))), received, "the selection the user loses to the collapse is reported")
    }

    @Test
    fun aSecondCollapseUnderTheSameSelectionIsReportedToo() = runComposeSwingTest {
        var expansion by mutableStateOf(setOf(emptyList(), listOf(0), listOf(1)))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = expansion,
                selectedPaths = setOf(listOf(0, 0), listOf(1, 0)),
                onSelectionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            listOf(tree.pathTo(0, 0), tree.pathTo(1, 0)),
            tree.selectionPaths?.toList(),
            "both declared nodes start selected, each under an open parent",
        )
        assertEquals(emptyList(), received, "a selection the tree can hold whole reports nothing")

        expansion = setOf(emptyList(), listOf(1))
        awaitIdle()

        assertEquals(
            listOf(setOf(listOf(0), listOf(1, 0))),
            received,
            "the selection the first collapse takes over is reported",
        )

        // The selection declaration has not moved: what the second collapse takes over is a further loss
        // all the same.
        expansion = setOf(emptyList())
        awaitIdle()

        assertEquals(
            listOf(tree.pathTo(0), tree.pathTo(1)),
            tree.selectionPaths?.toList(),
            "each closed node holds the selection its hidden descendant cannot",
        )
        assertEquals(
            listOf(setOf(listOf(0), listOf(1, 0)), setOf(listOf(0), listOf(1))),
            received,
            "and the selection the second collapse takes over is reported as well",
        )
    }

    /**
     * What a collapse has already been reported to have taken over is measured against the declaration it
     * was hidden out of. A later declaration is a selection of its own, so the nodes standing in for it are
     * reported again even where the collapse that hides them has not moved.
     */
    @Test
    fun aChangedSelectionUnderTheSameStandingCollapseIsReportedTakenOverAgain() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(listOf(0, 0), listOf(1, 0)))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                // Only the root is open, so each selected node is stood in for by the closed node above it.
                expandedPaths = setOf(emptyList()),
                selectedPaths = selection,
                onSelectionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            setOf(tree.pathTo(0), tree.pathTo(1)),
            tree.selectionPaths?.toSet(),
            "each closed node holds the selection its hidden descendant cannot",
        )

        selection = setOf(listOf(0, 0))
        awaitIdle()

        assertEquals(
            listOf(tree.pathTo(0)),
            tree.selectionPaths?.toList(),
            "the node the narrowed declaration leaves out drops off the selection",
        )
        assertEquals(
            listOf(setOf(listOf(0), listOf(1)), setOf(listOf(0))),
            received,
            "a declaration the standing collapse takes over is reported however much of it was reported before",
        )
    }

    @Test
    fun aRawListenerHearsTheCollapseTakeOverAsNodesLeavingTheSelection() = runComposeSwingTest {
        val removed = mutableListOf<List<String>>()
        val listener =
            TreeSelectionListener { event ->
                removed +=
                    event.paths.filterIndexed { at, _ -> !event.isAddedPath(at) }.map {
                        it.lastPathComponent.toString()
                    }
            }
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                treeSelectionListener = listener,
                expandedPaths = setOf(emptyList()),
                selectedPaths = setOf(listOf(0, 0)),
            )
        }

        assertEquals(
            listOf(listOf("apple")),
            removed,
            "the node the collapse hid is named to the raw listener as one that left the selection",
        )
    }

    @Test
    fun aSelectionDeclaredUnderAStandingCollapseIsReportedTakenOver() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(listOf(0)))
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = setOf(emptyList()),
                selectedPaths = selection,
                onSelectionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf(tree.pathTo(0)), tree.selectionPaths?.toList(), "the visible node is selected")
        assertEquals(emptyList(), received, "a selection the tree can hold whole reports nothing")

        // The node stays closed, so the descendant now declared cannot be shown selected either.
        selection = setOf(listOf(0, 1))
        awaitIdle()

        assertEquals(
            listOf(tree.pathTo(0)),
            tree.selectionPaths?.toList(),
            "the closed node goes on holding the selection",
        )
        assertEquals(listOf(setOf(listOf(0))), received, "and the declaration it cannot hold is reported once")
    }

    @Test
    fun aDeclaredSelectionOpensItsAncestorsWhereNoExpansionIsDeclared() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                selectedPaths = setOf(listOf(0, 0)),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            listOf(tree.pathTo(0, 0)),
            tree.selectionPaths?.toList(),
            "the declared selection reaches the tree",
        )
        // A tree keeps what it selects reachable, so the ancestors of the selection end up open.
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the selection's ancestor is open")
    }

    @Test
    fun theExpansionListenerIsAlwaysInstalled() = runComposeSwingTest {
        var listener by mutableStateOf<TreeExpansionListener?>(null)
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                treeSelectionListener = remember { TreeSelectionListener { } },
                label = { it.name },
                treeExpansionListener = listener,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            1,
            tree.libraryExpansionListeners().size,
            "the wrapper keeps its own listener installed to track expansion even with no listener declared",
        )

        listener =
            object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent): Unit = Unit

                override fun treeCollapsed(event: TreeExpansionEvent): Unit = Unit
            }
        awaitIdle()

        assertEquals(
            1,
            tree.libraryExpansionListeners().size,
            "a declared listener replaces the wrapper's, not adds to it",
        )

        listener = null
        awaitIdle()

        assertEquals(
            1,
            tree.libraryExpansionListeners().size,
            "dropping the listener leaves the wrapper's own in place",
        )
    }

    @Test
    fun aDeclaredExpansionStandsWithNoExpansionListenerDeclared() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                treeSelectionListener = remember { TreeSelectionListener { } },
                label = { it.name },
                expandedPaths = setOf(emptyList()),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        awaitIdle()

        assertFalse(
            tree.isExpanded(tree.pathTo(0)),
            "the declared expansion stands against a user expansion even with no listener to report it",
        )
    }

    @Test
    fun anOpenNodeTheStructureNoLongerHoldsIsNotReported() = runComposeSwingTest {
        val root = DefaultMutableTreeNode("root")
        val fruit = DefaultMutableTreeNode("fruit").apply { add(DefaultMutableTreeNode("apple")) }
        root.add(fruit)
        root.add(DefaultMutableTreeNode("veg").apply { add(DefaultMutableTreeNode("carrot")) })
        val model = DefaultTreeModel(root)
        val reported = mutableListOf<Set<List<Int>>>()
        setContent { Tree(model = model, onExpansionChange = { reported += it }) }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        tree.expandPath(tree.pathTo(1))
        awaitIdle()

        // Taken out of the structure and announced as a change rather than as a removal, which is what
        // leaves the tree still holding it open: a tree prunes what it remembers open only for the events
        // that name a removal, and a caller's model is free to publish any event it likes.
        root.remove(fruit)
        model.nodeChanged(root)
        awaitIdle()
        reported.clear()

        tree.collapsePath(tree.pathTo(0))
        awaitIdle()

        assertEquals(
            listOf(setOf(emptyList<Int>())),
            reported,
            "the node the structure dropped stands at no child position, so only the root is reported",
        )
    }
}

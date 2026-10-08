package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.mockk.mockk
import io.mockk.verify
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
 * Expansion is the user's, or held by a [TreeState]: [Tree] reports the expansion the user reaches, applies
 * the one a state is assigned, and keeps either across a structure change, which a plain `JTree` discards
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
    fun assigningTheStateExpansionReachesTheTreeInOnePass() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList(), listOf(1)))
        setContent { Tree(root = sample, children = { it.children }, state = state, label = { it.name }) }
        awaitIdle()
        mainClock.autoAdvance = false

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(1)), "the node the state names should be expanded")
        assertFalse(tree.isExpanded(tree.pathTo(0)), "a node it does not name should be collapsed")

        // Every node the state leaves out collapses again: the expansion is set, not only added to.
        state.expandedPaths = setOf(emptyList(), listOf(0))
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertTrue(
            tree.isExpanded(tree.pathTo(0)),
            "the one pass that carries the assignment should already have opened the node",
        )
        assertFalse(
            tree.isExpanded(tree.pathTo(1)),
            "the one pass that carries the assignment should already have closed the node it drops",
        )
        assertEquals(
            setOf(emptyList(), listOf(0)),
            state.expandedPaths,
            "applying the assignment wrote nothing back as the user's",
        )
    }

    @Test
    fun theStateExpansionKeepsItsAncestorsOpen() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(listOf(0)))
        setContent { Tree(root = sample, children = { it.children }, state = state, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the node the state names should be expanded")
        assertTrue(tree.isExpanded(tree.pathTo()), "its ancestor has to stay expanded to reach it")
        assertEquals(setOf(listOf(0)), state.expandedPaths, "the ancestor opened on the way is not written back")
    }

    @Test
    fun theStateExpansionSurvivesAStructureChange() = runComposeSwingTest {
        var label by mutableStateOf("root")
        val state = TreeState(initialExpandedPaths = setOf(emptyList(), listOf(0)))
        setContent {
            Tree(root = sample.copy(name = label), children = { it.children }, state = state, label = { it.name })
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the node the state names should be expanded")

        label = "trunk"
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "expansion survives a structure change")
        assertEquals(setOf(emptyList(), listOf(0)), state.expandedPaths, "and the state still holds it")
    }

    @Test
    fun aNodeTheStateNamesOpensOnTheStructureThatGainsIt() = runComposeSwingTest {
        var root by mutableStateOf(sample)
        val state = TreeState(initialExpandedPaths = setOf(emptyList(), listOf(2)))
        setContent { Tree(root = root, children = { it.children }, state = state, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("root", "fruit", "veg"), tree.rowLabels(), "the structure has no node the state names")

        root = sample.copy(children = sample.children + Entry("nuts", listOf(Entry("almond"))))
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(2)), "the structure that gains the node opens it")
        assertEquals(setOf(emptyList(), listOf(2)), state.expandedPaths, "the state named it all along")
    }

    @Test
    fun theUsersOpeningAndClosingIsWrittenIntoTheState() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        setContent { Tree(root = sample, children = { it.children }, state = state, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(1))
        awaitIdle()

        assertEquals(setOf(emptyList(), listOf(1)), state.expandedPaths, "the node the user opened reaches the state")

        tree.collapsePath(tree.pathTo(1))
        awaitIdle()

        assertEquals(setOf(emptyList<Int>()), state.expandedPaths, "and so does the node the user closed")
        assertFalse(tree.isExpanded(tree.pathTo(1)), "the closed node stays closed, the state holding it so")
    }

    @Test
    fun theUsersCollapseOverASelectionWritesBothFacetsIntoTheState() = runComposeSwingTest {
        val state =
            TreeState(initialSelectedPaths = setOf(listOf(0, 0)), initialExpandedPaths = setOf(emptyList(), listOf(0)))
        setContent { Tree(root = sample, children = { it.children }, state = state, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        tree.collapsePath(tree.pathTo(0))

        assertEquals(setOf(emptyList<Int>()), state.expandedPaths, "the user's collapse reaches the state at once")
        assertEquals(setOf(listOf(0)), state.selectedPaths, "and so does the selection the collapse moved")

        awaitIdle()

        assertFalse(tree.isExpanded(tree.pathTo(0)), "the state holds the collapse, so it stands")
        assertEquals(listOf(tree.pathTo(0)), tree.selectionPaths?.toList(), "and the closed node stays selected")
    }

    /**
     * The user's change is written into the state and marked as in sync with the tree in the same step, so
     * a caller that assigns the state back before the next pass is a change of its own, and is applied.
     */
    @Test
    fun aRevertAssignedBeforeThePassThatFollowsTheUsersChangeIsApplied() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList(), listOf(0)))
        setContent { Tree(root = sample, children = { it.children }, state = state, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        tree.collapsePath(tree.pathTo(0))
        assertEquals(setOf(emptyList<Int>()), state.expandedPaths, "the user's collapse reaches the state at once")
        state.expandedPaths = setOf(emptyList(), listOf(0))
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "the expansion the caller assigned back is applied")
    }

    @Test
    fun theStateExpansionSurvivesRecreatingTheTree() = runComposeSwingTest {
        var generation by mutableIntStateOf(0)
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        setContent {
            key(generation) {
                Tree(root = sample, children = { it.children }, state = state, label = { it.name })
            }
        }

        val first = onNodeOfType<JTree>().fetch()
        first.expandPath(first.pathTo(1))
        awaitIdle()

        generation++
        awaitIdle()

        val second = onNodeOfType<JTree>().fetch()
        assertTrue(first !== second, "keying the tree to a new generation recreates it")
        assertTrue(second.isExpanded(second.pathTo(1)), "the recreated tree opens the node the user opened")
        assertFalse(second.isExpanded(second.pathTo(0)), "and only that node")
    }

    @Test
    fun theUsersExpansionStandsAcrossARecomposition() = runComposeSwingTest {
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

        // Without a state the expansion is the user's, so a recomposition leaves what they opened open.
        rootVisible = false
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "the user's expansion should be left alone")
    }

    @Test
    fun theUsersExpansionSurvivesAStructureChange() = runComposeSwingTest {
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

        assertTrue(tree.isExpanded(tree.pathTo(0)), "the user's expansion is theirs to keep")
        assertEquals(emptyList(), received, "a structure change reported an expansion change")
    }

    @Test
    fun theUsersExpansionSurvivesAModelSwap() = runComposeSwingTest {
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

        assertTrue(tree.isExpanded(tree.pathTo(0)), "the user's expansion is theirs to keep")
        assertEquals(emptyList(), received, "a model swap reported an expansion change")
    }

    @Test
    fun theUsersCollapseSurvivesAStructureChange() = runComposeSwingTest {
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
    fun theUsersCollapseSurvivesAModelSwap() = runComposeSwingTest {
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
    fun aNarrowedStateExpansionCollapsesTheDeepestNodesFirst() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList(), listOf(0), listOf(0, 0)))
        setContent { Tree(root = deep, children = { it.children }, state = state, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0, 0)), "the nodes the state names should be expanded")

        state.expandedPaths = setOf(emptyList())
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo()), "the root the state still names stays expanded")
        assertFalse(tree.isExpanded(tree.pathTo(0)), "the node dropped from the state collapses")
        assertFalse(tree.isExpanded(tree.pathTo(0, 0)), "its dropped child collapses too")
    }

    /**
     * A tree remembers a node it was showing open under one it is asked to close, and brings it back open
     * with the ancestor that was hiding it. Only what the tree shows open once the state's own expansions
     * have run says which nodes the state leaves out.
     */
    @Test
    fun aDescendantTheTreeRemembersOpenIsClosedWhenTheStateReopensItsAncestor() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        setContent { Tree(root = deep, children = { it.children }, state = state, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        tree.expandPath(tree.pathTo(0, 0))
        tree.collapsePath(tree.pathTo(0))
        awaitIdle()
        assertEquals(listOf("root", "a"), tree.rowLabels(), "the user's collapse stands")

        state.expandedPaths = setOf(emptyList(), listOf(0))
        awaitIdle()

        assertEquals(
            listOf("root", "a", "b"),
            tree.rowLabels(),
            "the node the state leaves out is closed even where reopening its ancestor brought it back",
        )
        assertFalse(tree.isExpanded(tree.pathTo(0, 0)), "so its own child has no row")
    }

    @Test
    fun aSelectionUnderAClosedNodeOpensItAndTheStateTakesTheOpenedNode() = runComposeSwingTest {
        val state = TreeState(initialSelectedPaths = setOf(listOf(0, 0)), initialExpandedPaths = setOf(emptyList()))
        setContent { Tree(root = sample, children = { it.children }, state = state, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf(tree.pathTo(0, 0)), tree.selectionPaths?.toList(), "the node the state names is selected")
        assertTrue(tree.isExpanded(tree.pathTo(0)), "and the tree opens the node above it to show it")
        assertEquals(setOf(emptyList(), listOf(0)), state.expandedPaths, "the state takes the node the tree opened")

        state.selectedPaths = setOf(listOf(1, 0))
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(1)), "a selection assigned under a closed node opens it as well")
        assertEquals(
            setOf(emptyList(), listOf(0), listOf(1)),
            state.expandedPaths,
            "and the state takes that node too",
        )
    }

    @Test
    fun installingAModelOpensNothingTheStateKeepsClosed() = runComposeSwingTest {
        var model by mutableStateOf(sampleModel("root"))
        val state = TreeState(initialSelectedPaths = setOf(listOf(1)), initialExpandedPaths = setOf(emptyList()))
        setContent { Tree(model = model, state = state) }

        val tree = onNodeOfType<JTree>().fetch()
        // A listener of the test's own is handed the wrapper's writes as well as the user's, so a node
        // opened and closed again inside one install still shows up here.
        val expansionListener = mockk<TreeExpansionListener>(relaxed = true)
        tree.addTreeExpansionListener(expansionListener)

        model = sampleModel("trunk")
        awaitIdle()

        verify(exactly = 0) { expansionListener.treeExpanded(any()) }
        assertFalse(tree.isExpanded(tree.pathTo(0)), "the node the state keeps closed stays closed")
        assertEquals(listOf(tree.pathTo(1)), tree.selectionPaths?.toList(), "and the selection goes back on")
    }

    @Test
    fun aCollapseTheStateCommandsMovesTheSelectionItHidesOntoTheClosedNode() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList(), listOf(0)))
        setContent { Tree(root = sample, children = { it.children }, state = state, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPath = tree.pathTo(0, 0)
        awaitIdle()
        assertEquals(setOf(listOf(0, 0)), state.selectedPaths, "the user's selection reaches the state")

        state.expandedPaths = setOf(emptyList())
        awaitIdle()

        assertFalse(tree.isExpanded(tree.pathTo(0)), "the node the state closes is closed")
        assertEquals(listOf(tree.pathTo(0)), tree.selectionPaths?.toList(), "the closed node takes the selection")
        assertEquals(setOf(listOf(0)), state.selectedPaths, "and the state takes what the tree selected")
    }

    @Test
    fun eachCollapseTheStateCommandsMovesTheSelectionItHides() = runComposeSwingTest {
        val state =
            TreeState(
                initialSelectedPaths = setOf(listOf(0, 0), listOf(1, 0)),
                initialExpandedPaths = setOf(emptyList(), listOf(0), listOf(1)),
            )
        setContent { Tree(root = sample, children = { it.children }, state = state, label = { it.name }) }

        state.expandedPaths = setOf(emptyList(), listOf(1))
        awaitIdle()

        assertEquals(setOf(listOf(0), listOf(1, 0)), state.selectedPaths, "the first collapse takes its node over")

        state.expandedPaths = setOf(emptyList())
        awaitIdle()

        assertEquals(setOf(listOf(0), listOf(1)), state.selectedPaths, "and the second collapse takes over its own")
    }

    @Test
    fun aSelectionAssignedWithACollapseGoesOnAfterIt() = runComposeSwingTest {
        val state =
            TreeState(initialSelectedPaths = setOf(listOf(0, 0)), initialExpandedPaths = setOf(emptyList(), listOf(0)))
        setContent { Tree(root = sample, children = { it.children }, state = state, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        state.selectedPaths = setOf(listOf(1))
        state.expandedPaths = setOf(emptyList())
        awaitIdle()

        assertFalse(tree.isExpanded(tree.pathTo(0)), "the node the state closes is closed")
        assertEquals(listOf(tree.pathTo(1)), tree.selectionPaths?.toList(), "the selection assigned with it stands")
        assertEquals(setOf(listOf(1)), state.selectedPaths, "and the state keeps it")
    }

    @Test
    fun aDeclaredSelectionOpensItsAncestorsWhereNoStateHoldsTheExpansion() = runComposeSwingTest {
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
    fun aDeclaredSelectionTheCallerKeepsReopensTheNodeTheUserClosedOverIt() = runComposeSwingTest {
        val received = mutableListOf<Set<List<Int>>>()
        val expansions = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                selectedPaths = setOf(listOf(0, 0)),
                onSelectionChange = { received += it },
                onExpansionChange = { expansions += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.collapsePath(tree.pathTo(0))
        awaitIdle()

        assertEquals(setOf(listOf(0)), received.last(), "the tree selects the node the user closed, and says so")
        assertEquals(
            listOf(tree.pathTo(0, 0)),
            tree.selectionPaths?.toList(),
            "the declared selection the caller kept goes back on",
        )
        assertTrue(tree.isExpanded(tree.pathTo(0)), "and the tree opens the node again to show it")
        assertEquals(setOf(emptyList(), listOf(0)), expansions.last(), "and reports the node it opened")
    }

    @Test
    fun aSelectionDeclaredWithANewModelReportsTheNodeItOpens() = runComposeSwingTest {
        var model by mutableStateOf(sampleModel("root"))
        var selection by mutableStateOf(emptySet<List<Int>>())
        val expansions = mutableListOf<Set<List<Int>>>()
        setContent { Tree(model = model, selectedPaths = selection, onExpansionChange = { expansions += it }) }

        val tree = onNodeOfType<JTree>().fetch()
        model = sampleModel("trunk")
        selection = setOf(listOf(1, 0))
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(1)), "the tree opens the node above the selection to show it")
        assertEquals(setOf(emptyList(), listOf(1)), expansions.last(), "and reports the node it opened")
    }

    @Test
    fun theNodesOneSelectionOpensAreReportedInOneCall() = runComposeSwingTest {
        val expansions = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = deep,
                children = { it.children },
                label = { it.name },
                selectedPaths = setOf(listOf(0, 0, 0)),
                onExpansionChange = { expansions += it },
            )
        }
        awaitIdle()

        assertEquals(
            listOf(setOf(emptyList(), listOf(0), listOf(0, 0))),
            expansions,
            "opening both ancestors of the selection is one change of the expansion, reported once",
        )
    }

    @Test
    fun theExpansionListenerHearsEachNodeASelectionOpens() = runComposeSwingTest {
        val opened = mutableListOf<TreePath>()
        setContent {
            Tree(
                root = deep,
                children = { it.children },
                treeSelectionListener = remember { TreeSelectionListener { } },
                label = { it.name },
                selectedPaths = setOf(listOf(0, 0, 0)),
                treeExpansionListener =
                    remember {
                        object : TreeExpansionListener {
                            override fun treeExpanded(event: TreeExpansionEvent) {
                                opened += event.path
                            }

                            override fun treeCollapsed(event: TreeExpansionEvent): Unit = Unit
                        }
                    },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            listOf(tree.pathTo(0), tree.pathTo(0, 0)),
            opened,
            "each node the tree opens to show the selection is announced, shallowest first",
        )
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

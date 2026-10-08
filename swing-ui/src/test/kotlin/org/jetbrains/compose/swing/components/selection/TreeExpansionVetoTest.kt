package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.treeWillExpandListener
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.ExpandVetoException
import javax.swing.tree.TreePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A value tree: each section yields its [children], and its [name] is what the row renders. */
private data class Section(
    val name: String,
    val children: List<Section> = emptyList(),
)

/**
 * A node opens only if it is allowed to. The callback is asked before every expansion - the user's click
 * and the expansion a [TreeState] applies alike - and refusing one leaves the node closed.
 *
 * Expanding through `JTree.expandPath` is what the tree's UI does when the user clicks a handle, so
 * driving expansion that way exercises the same path a click takes.
 */
class TreeExpansionVetoTest {
    private val sample =
        Section(
            "root",
            listOf(
                Section("fruit", listOf(Section("apple"), Section("pear"))),
                Section("veg", listOf(Section("carrot"))),
            ),
        )

    @Test
    fun aNodeUnderARefusedOneIsNeverAskedFor() = runComposeSwingTest {
        val nested = Section("root", listOf(Section("fruit", listOf(Section("apple", listOf(Section("seed")))))))
        val asked = mutableListOf<String>()
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        setContent {
            Tree(
                root = nested,
                children = { it.children },
                state = state,
                label = { it.name },
                onWillExpand = { value, _ ->
                    asked += value.name
                    value.name != "fruit"
                },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        state.expandedPaths = setOf(emptyList(), listOf(0), listOf(0, 0))
        awaitIdle()
        assertFalse(tree.isExpanded(tree.pathTo(0)), "the refused node stays closed")
        assertEquals(1, asked.count { it == "fruit" }, "the refused node is asked once: $asked")
        assertEquals(0, asked.count { it == "apple" }, "the node below it is never asked: $asked")
    }

    @Test
    fun aRefusedExpansionLeavesTheNodeClosed() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                onWillExpand = { value, _ -> value.name != "veg" },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        assertTrue(tree.isExpanded(tree.pathTo(0)), "an expansion the callback allows goes through")

        tree.expandPath(tree.pathTo(1))
        assertFalse(tree.isExpanded(tree.pathTo(1)), "the one it refuses does not")
    }

    @Test
    fun theNodeAboutToOpenIsNamedByItsValueAndIndexPath() = runComposeSwingTest {
        val asked = mutableListOf<Pair<String, List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                onWillExpand = { value, path ->
                    asked += value.name to path
                    true
                },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(1))

        assertEquals(listOf("veg" to listOf(1)), asked, "the node about to open, by value and index path")
    }

    @Test
    fun anAssignedExpansionIsRefusedTheSameWay() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                state = state,
                label = { it.name },
                onWillExpand = { value, _ -> value.name != "veg" },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        state.expandedPaths = setOf(emptyList(), listOf(0), listOf(1))
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "the node the callback allows opens")
        assertFalse(tree.isExpanded(tree.pathTo(1)), "the node it refuses stays closed")
    }

    @Test
    fun anExpansionTheStateStartsOnIsRefusedTheSameWay() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                state = rememberTreeState(initialExpandedPaths = setOf(emptyList(), listOf(0), listOf(1))),
                label = { it.name },
                onWillExpand = { value, _ -> value.name != "veg" },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the node the callback allows opens as the tree is built")
        assertFalse(tree.isExpanded(tree.pathTo(1)), "the one it refuses stays closed from the start")
    }

    @Test
    fun aFailingCallbackAnswersNothingAndLeavesTheCompositionAlive() = runComposeSwingTest {
        var label by mutableStateOf("root")
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        setContent {
            Tree(
                root = sample.copy(name = label),
                children = { it.children },
                state = state,
                label = { it.name },
                onWillExpand = { _, _ -> error("the will-expand callback fails") },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        // Applying the state's expansion is the wrapper's own write, so the callback is reached from
        // inside the pass that applies it - where a throw would otherwise end the composition.
        state.expandedPaths = setOf(emptyList(), listOf(0))
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "a failure is no refusal, so the expansion stands")

        label = "trunk"
        awaitIdle()

        assertEquals(
            "trunk",
            (tree.model.root as DefaultMutableTreeNode).userObject.toString(),
            "the composition still applies what a later pass declares",
        )
        val failures = takeCallerFailures()
        assertTrue(
            failures.any { "the will-expand callback fails" in it.message.orEmpty() },
            "the callback's failure should be contained and reported, but was: $failures",
        )
    }

    @Test
    fun aRefusedCollapseLeavesTheNodeOpenAndTheTreeGoesOnAnsweringForIt() = runComposeSwingTest {
        var refusing = true
        val state = TreeState(initialExpandedPaths = setOf(emptyList(), listOf(0)))
        val refusal = mockk<TreeWillExpandListener>(relaxed = true)
        every { refusal.treeWillCollapse(any()) } answers {
            if (refusing) throw ExpandVetoException(firstArg())
        }
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                state = state,
                modifier = SwingModifier.treeWillExpandListener(refusal),
                label = { it.name },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the node the state names opens")

        state.expandedPaths = setOf(emptyList())
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "the node the listener refuses to close stays open")
        assertEquals(
            setOf(emptyList(), listOf(0)),
            state.expandedPaths,
            "the tree, not the assignment, says what is open, and the state takes what it says",
        )

        // The node the refusal kept open is still the user's to close, and closing it reaches the state.
        refusing = false
        tree.collapsePath(tree.pathTo(0))
        awaitIdle()

        assertFalse(tree.isExpanded(tree.pathTo(0)), "the collapse the listener allows goes through")
        assertEquals(setOf(emptyList<Int>()), state.expandedPaths, "and the user's collapse reaches the state")
    }

    @Test
    fun aListenerFailingPartWayThroughAnAssignedExpansionLeavesTheTreeAnsweringForWhatOpened() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        val failing = mockk<TreeWillExpandListener>(relaxed = true)
        every { failing.treeWillExpand(any()) } answers {
            check(firstArg<TreeExpansionEvent>().path.lastPathComponent.toString() != "veg") { "the listener fails" }
        }
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                state = state,
                modifier = SwingModifier.treeWillExpandListener(failing),
                label = { it.name },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        state.expandedPaths = setOf(emptyList(), listOf(0), listOf(1))
        awaitIdle()
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the node opened before the failure stays open")
        assertFalse(tree.isExpanded(tree.pathTo(1)), "the node whose listener failed stays closed")

        tree.collapsePath(tree.pathTo(0))
        awaitIdle()

        assertFalse(tree.isExpanded(tree.pathTo(0)), "the user's collapse of the node that did open stands")
        assertEquals(setOf(emptyList<Int>()), state.expandedPaths, "and reaches the state")
        val failures = takeCallerFailures()
        assertTrue(
            failures.any { "the listener fails" in it.message.orEmpty() },
            "the listener's failure should be contained and reported, but was: $failures",
        )
    }

    @Test
    fun aSelectionWhoseOpeningIsRefusedReportsNoExpansion() = runComposeSwingTest {
        val expansions = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                selectedPaths = setOf(listOf(0, 0)),
                onExpansionChange = { expansions += it },
                onWillExpand = { value, _ -> value.name != "fruit" },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertFalse(tree.isExpanded(tree.pathTo(0)), "the node above the selection is refused and stays closed")
        assertEquals(emptyList(), expansions, "a refused opening changes no expansion, so none is reported")
    }

    @Test
    fun aNodeRefusedWhileOpeningForASelectionIsNotAnnounced() = runComposeSwingTest {
        val nested = Section("root", listOf(Section("fruit", listOf(Section("apple", listOf(Section("seed")))))))
        val opened = mutableListOf<TreePath>()
        setContent {
            Tree(
                root = nested,
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
                // Refuses the node two levels down, so the tree opens the one above it and no further.
                treeWillExpandListener =
                    remember {
                        object : TreeWillExpandListener {
                            override fun treeWillExpand(event: TreeExpansionEvent) {
                                if (event.path.pathCount == 3) throw ExpandVetoException(event)
                            }

                            override fun treeWillCollapse(event: TreeExpansionEvent): Unit = Unit
                        }
                    },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the node the listener allows opens")
        assertFalse(tree.isExpanded(tree.pathTo(0, 0)), "the node it refuses stays closed")
        assertEquals(listOf(tree.pathTo(0)), opened, "only the node that opened is announced")
    }

    @Test
    fun aRawWillExpandListenerVetoesWithItsOwnException() = runComposeSwingTest {
        val listener = mockk<TreeWillExpandListener>(relaxed = true)
        every { listener.treeWillExpand(any()) } answers { throw ExpandVetoException(firstArg()) }
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                treeSelectionListener = remember { TreeSelectionListener { } },
                label = { it.name },
                treeWillExpandListener = listener,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(
            tree.treeWillExpandListeners.any { it === listener },
            "the exact declared listener should be installed on the tree",
        )

        tree.expandPath(tree.pathTo(0))
        assertFalse(tree.isExpanded(tree.pathTo(0)), "the listener's veto leaves the node closed")
    }
}

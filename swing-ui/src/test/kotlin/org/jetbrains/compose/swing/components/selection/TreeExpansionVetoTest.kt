package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.treeWillExpandListener
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
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

        state.expandedPaths = setOf(emptyList(), listOf(0), listOf(0, 0))
        awaitIdle()
        onNodeOfType<JTree>().assertProperty(false, "the refused node stays closed") { isExpanded(pathTo(0)) }
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
        onNodeOfType<JTree>().assertProperty(
            true,
            "an expansion the callback allows goes through",
        ) { isExpanded(pathTo(0)) }

        tree.expandPath(tree.pathTo(1))
        onNodeOfType<JTree>().assertProperty(false, "the one it refuses does not") { isExpanded(pathTo(1)) }
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

        val tree = onNodeOfType<JTree>()
        state.expandedPaths = setOf(emptyList(), listOf(0), listOf(1))
        awaitIdle()

        tree.assertProperty(true, "the node the callback allows opens") { isExpanded(pathTo(0)) }
        tree.assertProperty(false, "the node it refuses stays closed") { isExpanded(pathTo(1)) }
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

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(true, "the node the callback allows opens as the tree is built") { isExpanded(pathTo(0)) }
        tree.assertProperty(false, "the one it refuses stays closed from the start") { isExpanded(pathTo(1)) }
    }

    @Test
    fun aFailingCallbackAnswersNothingAndLeavesTheCompositionAlive() = runComposeSwingTest {
        var label by mutableStateOf("root")
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        val failures = mutableListOf<Throwable>()
        setContent {
            Tree(
                root = sample.copy(name = label),
                children = { it.children },
                state = state,
                label = { it.name },
                onWillExpand = { _, _ ->
                    val failure = IllegalStateException("the will-expand callback fails")
                    failures += failure
                    throw failure
                },
            )
        }

        val tree = onNodeOfType<JTree>()
        // Applying the state's expansion is the wrapper's own write, so the callback is reached from
        // inside the pass that applies it - where a throw would otherwise end the composition.
        state.expandedPaths = setOf(emptyList(), listOf(0))
        val reported = assertFailsWith<IllegalStateException> { awaitIdle() }

        assertEquals(failures, listOf(reported) + reported.suppressed, "every callback failure is reported once")
        assertTrue("the will-expand callback fails" in reported.message.orEmpty())
        tree.assertProperty(true, "a failure is no refusal, so the expansion stands") { isExpanded(pathTo(0)) }

        label = "trunk"
        awaitIdle()

        tree.assertProperty(
            "trunk",
            "the composition still applies what a later pass declares",
        ) { assertIs<DefaultMutableTreeNode>(model.root, "expected a mutable tree root").userObject.toString() }
    }

    /** A will-change callback that throws what [throws] makes of the event, which is reported only if [isReported]. */
    private class WillChangeThrow(
        val name: String,
        val isReported: Boolean,
        val throws: (TreeExpansionEvent) -> Throwable,
    )

    @TestFactory
    fun aThrowingWillChangeCallbackVetoesTheChangeAndOnlyAFailureIsReported(): List<DynamicTest> = listOf(
        WillChangeThrow("a failure", isReported = true) { IllegalStateException("the will-change callback fails") },
        WillChangeThrow("an ExpandVetoException", isReported = false) { ExpandVetoException(it) },
    ).map { case ->
        DynamicTest.dynamicTest(case.name) {
            runComposeSwingTest {
                val thrown = mutableListOf<Throwable>()
                setContent {
                    Tree(
                        root = sample,
                        children = { it.children },
                        label = { it.name },
                        modifier =
                            SwingModifier.treeWillExpandListener { event ->
                                throw case.throws(event).also { thrown += it }
                            },
                    )
                }
                val tree = onNodeOfType<JTree>().fetch()
                val rootPath = TreePath(tree.model.root)

                tree.expandPath(tree.pathTo(0))
                tree.collapsePath(rootPath)
                val reported = runCatching { awaitIdle() }.exceptionOrNull()

                assertEquals(
                    if (case.isReported) thrown else emptyList(),
                    listOfNotNull(reported) + reported?.suppressed.orEmpty(),
                    "only a failure is reported, as thrown",
                )
                assertEquals(2, thrown.size, "the callback is asked about the expansion and the collapse")
                onNodeOfType<JTree>().assertProperty(
                    false,
                    "the callback's throw vetoes the expansion",
                ) { isExpanded(pathTo(0)) }
                onNodeOfType<JTree>().assertProperty(
                    true,
                    "the callback's throw vetoes the collapse",
                ) { isExpanded(rootPath) }
            }
        }
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
        val failure = IllegalStateException("the listener fails")
        every { failing.treeWillExpand(any()) } answers {
            if (firstArg<TreeExpansionEvent>().path.lastPathComponent.toString() == "veg") throw failure
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
        assertSame(
            failure,
            assertFailsWith<IllegalStateException> { awaitIdle() },
            "the listener's failure is reported as it was thrown",
        )
        awaitIdle()
        onNodeOfType<JTree>().assertProperty(
            true,
            "the node opened before the failure stays open",
        ) { isExpanded(pathTo(0)) }
        onNodeOfType<JTree>().assertProperty(
            false,
            "the node whose listener failed stays closed",
        ) { isExpanded(pathTo(1)) }

        tree.collapsePath(tree.pathTo(0))
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            false,
            "the user's collapse of the node that did open stands",
        ) { isExpanded(pathTo(0)) }
        assertEquals(setOf(emptyList<Int>()), state.expandedPaths, "and reaches the state")
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

        onNodeOfType<JTree>().assertProperty(
            false,
            "the node above the selection is refused and stays closed",
        ) { isExpanded(pathTo(0)) }
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
        onNodeOfType<JTree>().assertProperty(
            true,
            "the exact declared listener should be installed on the tree",
        ) { treeWillExpandListeners.any { it === listener } }

        tree.expandPath(tree.pathTo(0))
        onNodeOfType<JTree>().assertProperty(
            false,
            "the listener's veto leaves the node closed",
        ) { isExpanded(pathTo(0)) }
    }
}

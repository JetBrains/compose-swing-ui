package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.ExpandVetoException
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
 * and the composition's own applied expansion alike - and refusing one leaves the node closed.
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
    fun aDeclaredExpansionIsRefusedTheSameWay() = runComposeSwingTest {
        var expansion by mutableStateOf(setOf(emptyList<Int>()))
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = expansion,
                onWillExpand = { value, _ -> value.name != "veg" },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        expansion = setOf(emptyList(), listOf(0), listOf(1))
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "the declared node the callback allows opens")
        assertFalse(tree.isExpanded(tree.pathTo(1)), "the declared node it refuses stays closed")
    }

    @Test
    fun aFailingCallbackAnswersNothingAndLeavesTheCompositionAlive() = runComposeSwingTest {
        var label by mutableStateOf("root")
        var expansion by mutableStateOf(setOf(emptyList<Int>()))
        setContent {
            Tree(
                root = sample.copy(name = label),
                children = { it.children },
                label = { it.name },
                expandedPaths = expansion,
                onWillExpand = { _, _ -> error("the will-expand callback fails") },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        // Applying a declared expansion is the wrapper's own write, so the callback is reached from
        // inside the pass that applies it - where a throw would otherwise end the composition.
        expansion = setOf(emptyList(), listOf(0))
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
        var expansion by mutableStateOf(setOf(emptyList<Int>(), listOf(0)))
        val collapsed = mutableListOf<TreeExpansionEvent>()
        val refusal =
            object : TreeWillExpandListener {
                override fun treeWillExpand(event: TreeExpansionEvent): Unit = Unit

                override fun treeWillCollapse(event: TreeExpansionEvent) {
                    if (refusing) throw ExpandVetoException(event)
                }
            }
        val reports =
            object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent): Unit = Unit

                override fun treeCollapsed(event: TreeExpansionEvent) {
                    collapsed += event
                }
            }
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                treeSelectionListener = remember { TreeSelectionListener { } },
                expandedPaths = expansion,
                treeExpansionListener = reports,
                treeWillExpandListener = refusal,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the declared node opens")

        expansion = setOf(emptyList())
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "the node the listener refuses to close stays open")

        // The tree, not the declaration, says what is open: the node the refusal kept open is still the
        // user's to close, and closing it is news the listener has not been told yet.
        refusing = false
        tree.collapsePath(tree.pathTo(0))
        awaitIdle()

        assertFalse(tree.isExpanded(tree.pathTo(0)), "the collapse the listener allows goes through")
        assertEquals(1, collapsed.size, "and it reaches the expansion listener as a change")
    }

    @Test
    fun aRawWillExpandListenerVetoesWithItsOwnException() = runComposeSwingTest {
        val listener =
            object : TreeWillExpandListener {
                override fun treeWillExpand(event: TreeExpansionEvent): Unit = throw ExpandVetoException(event)

                override fun treeWillCollapse(event: TreeExpansionEvent): Unit = Unit
            }
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

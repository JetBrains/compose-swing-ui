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
 * user reaches, and keeps a declared expansion across a structure change - which a `JTree` otherwise
 * discards along with the model it belonged to.
 *
 * Expanding through `JTree.expandPath` is what the tree's UI does when the user clicks a handle, so
 * driving expansion that way exercises the same path a click takes.
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

    /** Three levels of expandable nodes: root, then `a`, then `b`, each holding the next. */
    private val deep = Entry("root", listOf(Entry("a", listOf(Entry("b", listOf(Entry("c")))))))

    private fun sampleModel(rootLabel: String): DefaultTreeModel {
        fun node(entry: Entry): DefaultMutableTreeNode =
            DefaultMutableTreeNode(entry.name).apply { for (child in entry.children) add(node(child)) }
        return DefaultTreeModel(node(sample.copy(name = rootLabel)))
    }

    @Test
    fun expandingANodeReportsEveryExpandedNode() = runComposeSwingTest {
        val received = mutableListOf<List<List<Int>>>()
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

        assertEquals(listOf(listOf(emptyList(), listOf(0))), received, "expanded nodes in document order")

        tree.collapsePath(tree.pathTo(0))
        awaitIdle()

        assertEquals(listOf(emptyList()), received.last(), "a collapse reports the expansion that remains")
    }

    @Test
    fun aDeclaredExpansionReachesTheTree() = runComposeSwingTest {
        var expansion by mutableStateOf(listOf(emptyList<Int>(), listOf(1)))
        val received = mutableListOf<List<List<Int>>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = expansion,
                onExpansionChange = { received += it },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(1)), "the declared node should be expanded")
        assertFalse(tree.isExpanded(tree.pathTo(0)), "an undeclared node should be collapsed")

        // Every node the declaration leaves out is collapsed again, so expansion is genuinely
        // controlled rather than only additive.
        expansion = listOf(emptyList(), listOf(0))
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "the newly declared node should be expanded")
        assertFalse(tree.isExpanded(tree.pathTo(1)), "the node dropped from the declaration should collapse")
        assertEquals(emptyList(), received, "applying a declared expansion reported it back as the user's")
    }

    @Test
    fun aDeclaredExpansionKeepsItsAncestorsOpen() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = listOf(listOf(0)),
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the declared node should be expanded")
        assertTrue(tree.isExpanded(tree.pathTo()), "its ancestor has to stay expanded to reach it")
    }

    @Test
    fun aDeclaredExpansionSurvivesAStructureChange() = runComposeSwingTest {
        var label by mutableStateOf("root")
        var expansion by mutableStateOf(listOf(emptyList<Int>(), listOf(0)))
        val received = mutableListOf<List<List<Int>>>()
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
        assertEquals(listOf(emptyList(), listOf(0)), expansion, "the controlled expansion survives")
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

        // A recomposition that declares no expansion has no opinion about it, so what the user opened
        // stays open.
        rootVisible = false
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "an undeclared expansion should be left alone")
    }

    @Test
    fun anUndeclaredExpansionSurvivesAStructureChange() = runComposeSwingTest {
        var label by mutableStateOf("root")
        val received = mutableListOf<List<List<Int>>>()
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
        val received = mutableListOf<List<List<Int>>>()
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
        val received = mutableListOf<List<List<Int>>>()
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
        val received = mutableListOf<List<List<Int>>>()
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
                label = { it.name },
                modifier = SwingModifier.name(label),
                expandedPaths = listOf(emptyList()),
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
        var expansion by mutableStateOf(listOf(emptyList(), listOf(0), listOf(0, 0)))
        setContent {
            Tree(root = deep, children = { it.children }, label = { it.name }, expandedPaths = expansion)
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo(0, 0)), "the declared nodes should be expanded")

        expansion = listOf(emptyList())
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo()), "the still-declared root stays expanded")
        assertFalse(tree.isExpanded(tree.pathTo(0)), "the node dropped from the declaration collapses")
        assertFalse(tree.isExpanded(tree.pathTo(0, 0)), "its dropped child collapses too")
    }

    @Test
    fun aDeclaredSelectionOutlastsTheExpansionAppliedWithIt() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                // The declared expansion leaves the subtree holding the declared selection closed.
                expandedPaths = listOf(emptyList()),
                selectedPaths = listOf(listOf(0, 0)),
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
    fun anUndeclaredExpansionListenerInstallsNone() = runComposeSwingTest {
        var listener by mutableStateOf<TreeExpansionListener?>(null)
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                treeSelectionListener = remember { TreeSelectionListener { } },
                treeExpansionListener = listener,
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(emptyList(), tree.libraryExpansionListeners(), "no listener was declared, so none is installed")

        listener =
            object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent): Unit = Unit

                override fun treeCollapsed(event: TreeExpansionEvent): Unit = Unit
            }
        awaitIdle()

        assertEquals(1, tree.libraryExpansionListeners().size, "a declared listener is installed")

        listener = null
        awaitIdle()

        assertEquals(emptyList(), tree.libraryExpansionListeners(), "dropping the listener removes it")
    }
}

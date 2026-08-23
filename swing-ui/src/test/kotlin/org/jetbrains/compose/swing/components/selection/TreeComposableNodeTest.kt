package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreePath
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A small data tree used to feed [Tree] from nested values: each [Branch] yields its [children], and
 * its [name] is what the row's label renders. No label derives from [badge], so it reaches a row only
 * through a node that renders the value itself.
 */
private data class Branch(
    val name: String,
    val children: List<Branch> = emptyList(),
    val badge: String = "",
)

/**
 * Behavioral tests for [Tree]'s composable `nodeContent`. They prove the rubber-stamp mechanism end to
 * end: stamping a row through the installed [javax.swing.tree.TreeCellRenderer] realizes the composable
 * node into a real Swing subtree, that node sees the row it is being stamped for through its
 * [TreeNodeScope], and a `null` `nodeContent` renders rows through the renderer the tree itself carries
 * - whether it is `null` from the start or becomes `null` on a later composition.
 *
 * The node's component lives outside the composition root - it is what the renderer hands the tree - so
 * these drive the renderer directly (as `JTree` does when it paints a row) and inspect what it returns.
 */
class TreeComposableNodeTest {
    /** Renders the node at [row] through the renderer this tree carries, with the inputs the tree reports for it. */
    private fun JTree.stampRow(row: Int): Component {
        val node = getPathForRow(row).lastPathComponent
        return cellRenderer.getTreeCellRendererComponent(
            this,
            node,
            isRowSelected(row),
            isExpanded(row),
            model.isLeaf(node),
            row,
            hasFocus(),
        )
    }

    /** Records each node a model reports as changed, as the path naming that node. */
    private fun nodeChangeRecorder(into: MutableList<TreePath>): TreeModelListener = object : TreeModelListener {
        override fun treeNodesChanged(event: TreeModelEvent) {
            // A model names a changed node by its parent's path and its position under it; the root,
            // which has no parent, is named by its own path.
            into += event.children?.map { event.treePath.pathByAddingChild(it) } ?: listOf(event.treePath)
        }

        override fun treeNodesInserted(event: TreeModelEvent) = Unit

        override fun treeNodesRemoved(event: TreeModelEvent) = Unit

        override fun treeStructureChanged(event: TreeModelEvent) = Unit
    }

    private val sample =
        Branch(
            "root",
            listOf(
                Branch("fruit", listOf(Branch("apple"), Branch("pear"))),
                Branch("veg", listOf(Branch("carrot"))),
            ),
        )

    @Test
    fun nodeContentRealizesAComposableNodePerRow() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = setOf(emptyList()),
            ) { value ->
                FlowPanel { Label("${value.name} (${value.children.size})") }
            }
        }

        val tree = onNodeOfType<JTree>().fetch()
        // The whole value reaches the node, not just the text its label renders.
        assertEquals(
            "root (2)",
            tree.stampRow(0).firstLabelText(),
            "the composable node for row 0 should have realized a JLabel built from that row's value",
        )
        assertEquals(
            "fruit (2)",
            tree.stampRow(1).firstLabelText(),
            "the reused node should restamp the next row",
        )
    }

    @Test
    fun aNullValueRendersThroughTheNodeBodyLikeAnyOther() = runComposeSwingTest {
        val root: Branch? = Branch("root")
        setContent {
            Tree(
                root = root,
                children = { parent -> if (parent === root) listOf(null, Branch("leaf")) else emptyList() },
                label = { it?.name ?: "(none)" },
                expandedPaths = setOf(emptyList()),
            ) { value ->
                Label(value?.name ?: "(none)")
            }
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            "(none)",
            tree.stampRow(1).firstLabelText(),
            "a node whose value is null is a node the node body renders",
        )
        assertEquals("leaf", tree.stampRow(2).firstLabelText(), "the next node renders its own value")
    }

    @Test
    fun aValueMovingUnderAnUnchangedLabelRepaintsItsRow() = runComposeSwingTest {
        var apple by mutableStateOf(Branch("apple", badge = "ripe"))
        val changed = mutableListOf<TreePath>()
        setContent {
            Tree(
                root = Branch("root", listOf(apple)),
                children = { it.children },
                label = { it.name },
                expandedPaths = setOf(emptyList()),
            ) { value ->
                Label("${value.name} ${value.badge}")
            }
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals("apple ripe", tree.stampRow(1).firstLabelText(), "the node renders the value it stands for")
        tree.model.addTreeModelListener(nodeChangeRecorder(changed))

        apple = Branch("apple", badge = "eaten")
        awaitIdle()

        assertContains(
            changed,
            tree.pathTo(0),
            "the row renders from the value, so a node that took over another one has to be painted again",
        )
        assertEquals("apple eaten", tree.stampRow(1).firstLabelText(), "and renders the value that stands")
    }

    @Test
    fun aRebuiltButEqualSiblingIsNotPaintedAgain() = runComposeSwingTest {
        var apple by mutableStateOf(Branch("apple", badge = "ripe"))
        val changed = mutableListOf<TreePath>()
        setContent {
            Tree(
                // Pear is built inline, so every pass hands the tree a fresh object holding the data the
                // node already stands on.
                root = Branch("root", listOf(apple, Branch("pear"))),
                children = { it.children },
                label = { it.name },
                expandedPaths = setOf(emptyList()),
            ) { value ->
                Label("${value.name} ${value.badge}")
            }
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.model.addTreeModelListener(nodeChangeRecorder(changed))

        apple = Branch("apple", badge = "eaten")
        awaitIdle()

        assertContains(changed, tree.pathTo(0), "the node whose value moved is repainted")
        assertFalse(
            tree.pathTo(1) in changed,
            "a node handed a fresh object holding the same data renders what it already did, so it is " +
                "not painted again",
        )
    }

    @Test
    fun aComposableNodeKeepsTheLabelAsTheNodesText() = runComposeSwingTest {
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name.uppercase() },
            ) { value ->
                Label(value.name)
            }
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            "ROOT",
            tree.getPathForRow(0).lastPathComponent.toString(),
            "a node rendered by a composable still carries its label as the node's text",
        )
    }

    @Test
    fun theNodeScopeReflectsSelection() = runComposeSwingTest {
        var selection by mutableStateOf<Set<List<Int>>>(emptySet())
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                selectedPaths = selection,
                expandedPaths = setOf(emptyList()),
            ) { value ->
                Label(if (isSelected) "${value.name}*" else value.name)
            }
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals("veg", tree.stampRow(2).firstLabelText(), "an unselected row should render plainly")

        selection = setOf(listOf(1))
        awaitIdle()
        assertEquals(
            "veg*",
            tree.stampRow(2).firstLabelText(),
            "a selected row should observe isSelected through the TreeNodeScope",
        )
    }

    @Test
    fun theNodeScopeReflectsExpansion() = runComposeSwingTest {
        var expansion by mutableStateOf(setOf(emptyList<Int>()))
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                expandedPaths = expansion,
            ) { value ->
                Label(if (isExpanded) "${value.name} open" else value.name)
            }
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals("root open", tree.stampRow(0).firstLabelText(), "the expanded root should render as open")
        assertEquals("fruit", tree.stampRow(1).firstLabelText(), "a collapsed row should render as closed")

        expansion = setOf(emptyList(), listOf(0))
        awaitIdle()
        assertEquals(
            "fruit open",
            tree.stampRow(1).firstLabelText(),
            "expanding a node should restamp it as open through the TreeNodeScope",
        )
    }

    @Test
    fun composableNodesWorkInsideAScrollPane() = runComposeSwingTest {
        // The composable node's node composition joins the enclosing composition, and its own nodes belong
        // to the renderer rather than to the pane the tree is installed in: they render the row, they do
        // not install themselves as the viewport's view.
        setContent {
            ScrollPane {
                Tree(
                    root = sample,
                    children = { it.children },
                    modifier = SwingModifier.viewport(),
                    label = { it.name },
                    selectedPaths = setOf(emptyList()),
                ) { value ->
                    FlowPanel { Label(value.name) }
                }
            }
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(
            "root",
            tree.stampRow(0).firstLabelText(),
            "a composable node inside a ScrollPane should realize its row content, not leak the viewport slot",
        )
    }

    @Test
    fun nodeContentTakenAwayRestoresTheTreesOwnRenderer() = runComposeSwingTest {
        var composableNodes by mutableStateOf(true)
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                nodeContent =
                    if (composableNodes) {
                        { value -> FlowPanel { Label(value.name) } }
                    } else {
                        null
                    },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        val composedNode = tree.stampRow(0)
        assertFalse(composedNode is JLabel, "a composable node stamps what it composed, not the default JLabel")
        assertEquals("root", composedNode.firstLabelText(), "the composable node should render row 0")

        composableNodes = false
        awaitIdle()
        assertNull(
            tree.cellRenderer as? ComposingTreeCellRenderer<*>,
            "taking nodeContent away must leave the tree's own renderer, not a composing one",
        )
        val ownNode = tree.stampRow(0)
        assertTrue(ownNode is JLabel, "the restored renderer stamps a JLabel")
        assertEquals("root", (ownNode as JLabel).text, "the restored renderer renders the node's label")

        composableNodes = true
        awaitIdle()
        assertEquals(
            "root",
            tree.stampRow(0).firstLabelText(),
            "declaring nodeContent again should stamp the composable node",
        )
    }

    @Test
    fun omittingNodeContentKeepsTheTreesOwnRenderer() = runComposeSwingTest {
        setContent {
            Tree(root = sample, children = { it.children }, label = { it.name })
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertNull(
            tree.cellRenderer as? ComposingTreeCellRenderer<*>,
            "omitting nodeContent must leave the tree's own renderer, not install a composing one",
        )
        val node = tree.stampRow(0)
        assertTrue(node is JLabel, "the tree's own renderer stamps a JLabel")
        assertEquals("root", (node as JLabel).text, "the tree's own renderer renders the node's label")
    }
}

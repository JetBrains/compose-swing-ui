// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultTreeCellRenderer
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
 * Behavioral tests for [Tree]'s composable `nodeContent`. They prove the render mechanism end to
 * end: rendering a row through the installed [javax.swing.tree.TreeCellRenderer] realizes the composable
 * node into a real Swing subtree, that node sees the row it is being rendered for through its
 * [TreeNodeScope], and a `null` `nodeContent` renders rows through the renderer the tree itself carries
 * - whether it is `null` from the start or becomes `null` on a later composition.
 *
 * The node's component lives outside the composition root - it is what the renderer hands the tree - so
 * these drive the renderer directly (as `JTree` does when it paints a row) and inspect what it returns.
 */
class TreeComposableNodeTest {
    /** Renders the node at [row] through the renderer this tree carries, with the inputs the tree reports for it. */
    private fun JTree.renderRow(row: Int): Component {
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
    fun aValueMovingUnderAnUnchangedLabelRepaintsItsRow() = runComposeSwingTest {
        var apple by mutableStateOf(Branch("apple", badge = "ripe"))
        val changed = mutableListOf<TreePath>()
        setContent {
            Tree(
                root = Branch("root", listOf(apple)),
                children = { it.children },
                label = { it.name },
            ) { value ->
                Label("${value.name} ${value.badge}")
            }
        }

        val tree = onNodeOfType<JTree>().fetch()
        onNodeOfType<JTree>().assertProperty(
            "apple ripe",
            "the node renders the value it stands for",
        ) { renderRow(1).firstLabelText() }
        tree.model.addTreeModelListener(nodeChangeRecorder(changed))

        apple = Branch("apple", badge = "eaten")
        awaitIdle()

        assertContains(
            changed,
            tree.pathTo(0),
            "the row renders from the value, so a node that took over another one has to be painted again",
        )
        onNodeOfType<JTree>().assertProperty(
            "apple eaten",
            "and renders the value that stands",
        ) { renderRow(1).firstLabelText() }
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

        onNodeOfType<JTree>().assertProperty(
            "ROOT",
            "a node rendered by a composable still carries its label as the node's text",
        ) { getPathForRow(0).lastPathComponent.toString() }
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
            ) { value ->
                Label(if (isSelected) "${value.name}*" else value.name)
            }
        }

        onNodeOfType<JTree>().assertProperty(
            "veg",
            "an unselected row should render plainly",
        ) { renderRow(2).firstLabelText() }

        selection = setOf(listOf(1))
        awaitIdle()
        onNodeOfType<JTree>().assertProperty(
            "veg*",
            "a selected row should observe isSelected through the TreeNodeScope",
        ) { renderRow(2).firstLabelText() }
    }

    @Test
    fun theNodeScopeReflectsExpansion() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                state = state,
                label = { it.name },
            ) { value ->
                Label(if (isExpanded) "${value.name} open" else value.name)
            }
        }

        onNodeOfType<JTree>().assertProperty(
            "root open",
            "the expanded root should render as open",
        ) { renderRow(0).firstLabelText() }
        onNodeOfType<JTree>().assertProperty(
            "fruit",
            "a collapsed row should render as closed",
        ) { renderRow(1).firstLabelText() }

        state.expandedPaths = setOf(emptyList(), listOf(0))
        awaitIdle()
        onNodeOfType<JTree>().assertProperty(
            "fruit open",
            "expanding a node should rerender it as open through the TreeNodeScope",
        ) { renderRow(1).firstLabelText() }
    }

    @Test
    fun composableNodesWorkInsideAScrollPane() = runComposeSwingTest {
        // The composable node's node composition joins the enclosing composition, and its own nodes belong
        // to the renderer rather than to the pane the tree is installed in: they render the row, they do
        // not install themselves as the viewport's view.
        setContent {
            ScrollPane {
                Viewport {
                    Tree(
                        root = sample,
                        children = { it.children },
                        label = { it.name },
                        selectedPaths = setOf(emptyList()),
                    ) { value ->
                        Panel { Label(value.name) }
                    }
                }
            }
        }

        onNodeOfType<JTree>().assertProperty(
            "root",
            "a composable node inside a ScrollPane should realize its row content, not leak the viewport slot",
        ) { renderRow(0).firstLabelText() }
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
                        { value -> Panel { Label("<${value.name}>") } }
                    } else {
                        null
                    },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        val composedNode = tree.renderRow(0)
        assertFalse(composedNode is JLabel, "a composable node renders what it composed, not the default JLabel")
        assertEquals("<root>", composedNode.firstLabelText(), "the composable node should render row 0")

        composableNodes = false
        awaitIdle()
        assertNull(
            tree.cellRenderer as? ComposingTreeCellRenderer<*>,
            "taking nodeContent away must leave the tree's own renderer, not a composing one",
        )
        val ownNode = tree.renderRow(0)
        assertTrue(ownNode is JLabel, "the restored renderer renders a JLabel")
        assertEquals("root", (ownNode as JLabel).text, "the restored renderer renders the node's label")

        composableNodes = true
        awaitIdle()
        assertEquals(
            "<root>",
            tree.renderRow(0).firstLabelText(),
            "declaring nodeContent again should render the composable node",
        )
    }

    @Test
    fun nodeContentTakenAwayGivesAFactoryTreesRendererBack() = runComposeSwingTest {
        val factorysRenderer = DefaultTreeCellRenderer()
        var composableNodes by mutableStateOf(true)
        setContent {
            Tree(
                factory = { JTree().apply { cellRenderer = factorysRenderer } },
                root = sample,
                children = { it.children },
                state = remember { TreeState() },
                nodeContent = if (composableNodes) ({ value -> Label(value.name) }) else null,
            )
        }

        onNodeOfType<JTree>().assertProperty(true, "a composable node should replace the factory's renderer") {
            cellRenderer is ComposingTreeCellRenderer<*>
        }

        composableNodes = false
        awaitIdle()
        onNodeOfType<JTree>().assertProperty(
            factorysRenderer,
            "taking nodeContent away should give the factory's renderer back",
        ) { cellRenderer }
    }
}

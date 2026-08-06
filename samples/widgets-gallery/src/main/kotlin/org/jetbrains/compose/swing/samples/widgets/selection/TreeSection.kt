package org.jetbrains.compose.swing.samples.widgets.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.rememberTreeState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.UIManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

private class Node(
    val name: String,
    val children: List<Node> = emptyList(),
)

private val sampleTree =
    Node(
        "Project",
        listOf(
            Node(
                "src",
                listOf(
                    Node("main", listOf(Node("App.kt"), Node("Tree.kt"))),
                    Node("test", listOf(Node("TreeTest.kt"))),
                ),
            ),
            Node(
                "docs",
                listOf(Node("README.md"), Node("ARCHITECTURE.md")),
            ),
            Node("build.gradle.kts"),
        ),
    )

internal const val PRIMARY_TREE_TAG = "tree-primary"
internal const val EXPANSION_TREE_TAG = "tree-expansion"

// Tree: a nested-data demo with the selection and the tree's own display knobs (mode, root visibility,
// handles, click-to-toggle, row height) driven live, a demo of expansion held in a TreeState together
// with a will-expand veto and the state's reveal gesture, a demo of in-place editing and a composable
// node body, and a demo of the model-backed overload rendering a caller-owned TreeModel as-is.
@Composable
internal fun TreeSection() {
    SectionColumn {
        SectionHeading("Tree")
        SelectableTreeCard()
        ExpansionTreeCard()
        EditableTreeCard()
        ModelBackedTreeCard()
    }
}

@Composable
private fun ColumnScope.SelectableTreeCard() {
    ExampleCard("Tree from nested data with selection bound to state") {
        var selection by remember { mutableStateOf<Set<List<Int>>>(emptySet()) }

        val selectionModes =
            listOf(
                "Single" to TreeSelectionModel.SINGLE_TREE_SELECTION,
                "Contiguous" to TreeSelectionModel.CONTIGUOUS_TREE_SELECTION,
                "Discontiguous" to TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
            )
        var selectionModeIndex by remember { mutableIntStateOf(2) }
        var rootVisible by remember { mutableStateOf(true) }
        var showsRootHandles by remember { mutableStateOf(true) }
        var toggleClicks by remember { mutableIntStateOf(2) }
        var rowHeight by remember { mutableIntStateOf(18) }

        FlowPanel {
            Label("Selection mode:")
            RadioGroup(
                selectedIndex = selectionModeIndex,
                onSelectionChange = { selectionModeIndex = it },
                axis = BoxLayout.X_AXIS,
            ) {
                selectionModes.forEach { (label, _) -> option(label) }
            }
        }
        FlowPanel {
            CheckBox(text = "Root visible", checked = rootVisible, onCheckedChange = { rootVisible = it })
            CheckBox(
                text = "Show root handles",
                checked = showsRootHandles,
                onCheckedChange = { showsRootHandles = it },
            )
            Label("Toggle click count:")
            Spinner(toggleClicks, onValueChange = { toggleClicks = it.toInt() }, min = 0, max = 4, step = 1)
            Label("Row height:")
            Spinner(rowHeight, onValueChange = { rowHeight = it.toInt() }, min = 0, max = 40, step = 2)
        }

        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 220))) {
            content {
                Tree(
                    root = sampleTree,
                    children = { it.children },
                    label = { it.name },
                    selectedPaths = selection,
                    onSelectionChange = { selection = it },
                    selectionMode = selectionModes[selectionModeIndex].second,
                    rootVisible = rootVisible,
                    showsRootHandles = showsRootHandles,
                    toggleClickCount = toggleClicks,
                    rowHeight = rowHeight,
                    modifier = SwingModifier.testTag(PRIMARY_TREE_TAG),
                )
            }
        }
        Label(text = "Selected path: ${describeSelection(selection)}")
    }
}

@Composable
private fun ColumnScope.ExpansionTreeCard() {
    ExampleCard("Tree expansion, veto & reveal") {
        val state = rememberTreeState(initialExpandedPaths = setOf(emptyList()))
        var lockDocs by remember { mutableStateOf(false) }

        FlowPanel {
            Button("Expand all", onClick = { state.expandedPaths = allPaths(sampleTree) })
            Button("Collapse all", onClick = { state.expandedPaths = emptySet() })
            Button("Reveal TreeTest.kt", onClick = { state.revealPath(listOf(0, 0, 1)) })
        }
        CheckBox(
            text = "Veto expanding \"docs\" (onWillExpand)",
            checked = lockDocs,
            onCheckedChange = { lockDocs = it },
        )

        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 140))) {
            content {
                Tree(
                    root = sampleTree,
                    children = { it.children },
                    label = { it.name },
                    state = state,
                    onWillExpand = if (lockDocs) ({ value, _ -> value.name != "docs" }) else null,
                    modifier = SwingModifier.testTag(EXPANSION_TREE_TAG),
                )
            }
        }
        Label("Expanded nodes: ${state.expandedPaths.size}")
        WrappedCaption(
            "Expand all and Collapse all drive the state's expandedPaths directly. The veto refuses the " +
                "\"docs\" node whether the user opens it or a declared expansion does. Reveal scrolls to a " +
                "node, opening the ancestors that hide it.",
        )
    }
}

@Composable
private fun ColumnScope.EditableTreeCard() {
    ExampleCard("Tree editing & composable nodes") {
        var renamed by remember { mutableStateOf<Map<Node, String>>(emptyMap()) }

        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 180))) {
            content {
                Tree(
                    root = sampleTree,
                    children = { it.children },
                    label = { renamed[it] ?: it.name },
                    isEditable = true,
                    onNodeEdit = { value, _, newValue -> renamed = renamed + (value to newValue.toString()) },
                ) { value ->
                    FlowPanel(modifier = SwingModifier.opaque(false), alignment = FlowLayout.LEADING, vgap = 0) {
                        // The look-and-feel's own file icons, so a node reads the same on every platform.
                        val icon = UIManager.getIcon(if (isLeaf) "FileView.fileIcon" else "FileView.directoryIcon")
                        Label("", modifier = SwingModifier.icon(icon))
                        Label(renamed[value] ?: value.name)
                    }
                }
            }
        }
        Label("Double-click a node's text to rename it.")
    }
}

@Composable
private fun ColumnScope.ModelBackedTreeCard() {
    ExampleCard("Tree driven by a Swing TreeModel") {
        val model =
            remember {
                val root = DefaultMutableTreeNode("root")
                val fruit = DefaultMutableTreeNode("fruit")
                fruit.add(DefaultMutableTreeNode("apple"))
                fruit.add(DefaultMutableTreeNode("pear"))
                root.add(fruit)
                root.add(DefaultMutableTreeNode("veg"))
                DefaultTreeModel(root)
            }
        var selection by remember { mutableStateOf<Set<List<Int>>>(emptySet()) }

        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(280, 140))) {
            content {
                Tree(
                    model = model,
                    selectedPaths = selection,
                    onSelectionChange = { selection = it },
                )
            }
        }
        Label("Selected: ${selection.firstOrNull()?.joinToString(",") ?: "none"}")
        WrappedCaption("The tree renders this DefaultTreeModel as-is; the library never mutates it.")
    }
}

private fun describeSelection(selection: Set<List<Int>>): String {
    val path = selection.firstOrNull() ?: return "(none)"
    val names = ArrayList<String>(path.size + 1)
    var node = sampleTree
    names.add(node.name)
    for (index in path) {
        node = node.children[index]
        names.add(node.name)
    }
    return names.joinToString(" / ")
}

/** The index path of [node] together with the index path of every node below it: what "Expand all"
 * declares as the tree's `expandedPaths`. */
private fun allPaths(
    node: Node,
    prefix: List<Int> = emptyList(),
): Set<List<Int>> = setOf(prefix) + node.children.flatMapIndexed { index, child -> allPaths(child, prefix + index) }

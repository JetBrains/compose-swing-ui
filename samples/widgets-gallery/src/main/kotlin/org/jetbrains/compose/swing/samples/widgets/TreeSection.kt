package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Dimension

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

// Tree: a nested data structure rendered through the data-driven API (root + children + label), with
// the selection driven by state and echoed into a Label.
@Composable
internal fun TreeSection() {
    SectionColumn {
        SectionHeading("Tree")
        ExampleCard("Tree from nested data with selection bound to state") {
            var selection by remember { mutableStateOf<Set<List<Int>>>(emptySet()) }

            ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 220))) {
                content {
                    Tree(
                        root = sampleTree,
                        children = { it.children },
                        label = { it.name },
                        selectedPaths = selection,
                        onSelectionChange = { selection = it },
                    )
                }
            }
            Label(text = "Selected path: ${describeSelection(selection)}")
        }
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

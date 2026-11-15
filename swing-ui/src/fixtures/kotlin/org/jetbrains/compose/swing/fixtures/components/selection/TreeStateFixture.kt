// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.rememberTreeState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/** The parameters [TreeStateFixture] varies: the expansion the state holds. */
public data class TreeStateParameters(
    val expandedPaths: Set<List<Int>>,
)

/** A value-driven [Tree] driven by a tree state against a raw [JTree] with the same expansion. */
public object TreeStateFixture : ComposableFixture<TreeStateParameters, JTree>(
    initial = TreeStateParameters(expandedPaths = emptySet()),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TreeStateParameters> = listOf(initial.copy(expandedPaths = setOf(emptyList())))

    @Composable
    override fun Content(parameters: TreeStateParameters): Unit = Content(parameters.expandedPaths)

    /** Composes a [Tree] over the fixed root and children, with a state holding the given expanded paths. */
    @Composable
    public fun Content(expandedPaths: Set<List<Int>>) {
        val state = rememberTreeState(initialSelectedPaths = emptySet(), initialExpandedPaths = expandedPaths)
        SideEffect {
            state.selectedPaths = emptySet()
            state.expandedPaths = expandedPaths
        }
        Tree(
            root = "Root before",
            children = { if (it.startsWith("Root")) listOf("Alpha", "Beta") else emptyList() },
            state = state,
        )
    }

    /** Builds a [JTree] over the root and children the fixture composes, with nothing selected. */
    override fun createRawSwing(): JTree =
        JTree(
            valuesModel(
                "Root before",
                { if (it.startsWith("Root")) listOf("Alpha", "Beta") else emptyList() },
                { it },
                null,
            ),
        )

    /** Applies the expansion of [parameters] to [component]. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeStateParameters,
    ) {
        applyExpansion(component, parameters.expandedPaths)
    }
}

/**
 * A tree node that remembers the value it was built from.
 *
 * @property value the source value the node was built from, which stays when its label changes.
 * @param label the text the node displays, which may differ from [value].
 * @param allowsChildren whether the node may have children, whether or not it has any now.
 */
internal class ValueNode(
    val value: String,
    label: String,
    allowsChildren: Boolean,
) : DefaultMutableTreeNode(label, allowsChildren)

/**
 * Builds the model a hand-written program would build for the values [root] and [children] reach.
 *
 * @param root the value the root node is built from.
 * @param children the values a node's children are built from, in order, for the value of that node.
 * @param label the text a node displays for its value.
 * @param hasChildren whether the node of a value allows children even when [children] returns none, so that it
 *   shows as a branch that is empty now; `null` for none. A node with children always allows them.
 */
internal fun valuesModel(
    root: String,
    children: (String) -> List<String>,
    label: (String) -> String,
    hasChildren: ((String) -> Boolean)?,
): DefaultTreeModel {
    fun node(value: String): ValueNode {
        val childValues = children(value)
        val result = ValueNode(value, label(value), childValues.isNotEmpty() || hasChildren?.invoke(value) == true)
        childValues.forEach { result.add(node(it)) }
        return result
    }
    return DefaultTreeModel(node(root), true)
}

/** Builds a model with the value [root] over the two leaves [first] and [second]. */
internal fun sampleModel(
    root: String,
    first: String,
    second: String,
): TreeModel =
    DefaultTreeModel(
        DefaultMutableTreeNode(root).apply {
            add(DefaultMutableTreeNode(first))
            add(DefaultMutableTreeNode(second))
        },
    )

/** Selects the nodes [paths] names in [tree], each a chain of child indices from the root. */
internal fun applySelection(
    tree: JTree,
    paths: Set<List<Int>>,
) {
    tree.selectionPaths = paths.mapNotNull { pathOf(tree.model, it) }.toTypedArray()
}

/** Collapses every node of [tree], then opens the nodes [paths] names. */
internal fun applyExpansion(
    tree: JTree,
    paths: Set<List<Int>>,
) {
    for (row in tree.rowCount - 1 downTo 0) tree.collapseRow(row)
    paths.forEach { path -> pathOf(tree.model, path)?.let(tree::expandPath) }
}

/** The path in [model] the chain of child indices [indices] names, or `null` when it leaves the tree. */
private fun pathOf(
    model: TreeModel,
    indices: List<Int>,
): TreePath? {
    var current = model.root
    var path = TreePath(current)
    for (index in indices) {
        if (index !in 0 until model.getChildCount(current)) return null
        current = model.getChild(current, index)
        path = path.pathByAddingChild(current)
    }
    return path
}

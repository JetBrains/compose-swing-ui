// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/** The parameters [TreeModelFixture] varies: the root and the two leaves the model is built from. */
public data class TreeModelParameters(
    val root: String,
    val first: String,
    val second: String,
)

/**
 * A model-driven [Tree] against a raw [JTree] over a fresh model of the same values and the display values
 * [TreeFixture] fixes.
 */
public object TreeModelFixture : ComposableFixture<TreeModelParameters, JTree>(
    initial = TreeModelParameters(root = "Root before", first = "Alpha", second = "Beta"),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TreeModelParameters> =
        listOf(
            initial.copy(root = "Root after", first = "Delta", second = "Epsilon"),
        )

    @Composable
    override fun Content(parameters: TreeModelParameters): Unit =
        Content(
            parameters.root,
            parameters.first,
            parameters.second,
        )

    /** Composes a [Tree] displaying a model remembered for [root], [first] and [second]. */
    @Composable
    public fun Content(
        root: String,
        first: String,
        second: String,
    ) {
        Tree(
            model = remember(root, first, second) { TreeFixture.sampleModel(root, first, second) },
            selectedPaths = null,
            onSelectionChange = {},
            onExpansionChange = {},
            selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
            rootVisible = true,
            showsRootHandles = false,
            rowHeight = 20,
            visibleRowCount = 20,
            toggleClickCount = 2,
        )
    }

    /** Builds a [JTree] with no model, carrying the fixed display values and no-op listeners. */
    override fun createRawSwing(): JTree {
        val tree = JTree(DefaultTreeModel(null))
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        tree.isRootVisible = true
        tree.showsRootHandles = false
        tree.rowHeight = 20
        tree.visibleRowCount = 20
        tree.toggleClickCount = 2
        tree.addTreeSelectionListener(TreeFixture.SelectionReporter(tree) {})
        tree.addTreeExpansionListener(TreeFixture.ExpansionReporter(tree) {})
        return tree
    }

    /** Installs a fresh model of the values of [parameters] on [component]. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeModelParameters,
    ) {
        component.model = TreeFixture.sampleModel(parameters.root, parameters.first, parameters.second)
    }
}

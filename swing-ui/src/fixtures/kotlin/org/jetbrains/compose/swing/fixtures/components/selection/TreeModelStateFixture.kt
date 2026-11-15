// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.rememberTreeState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTree

/** The parameters [TreeModelStateFixture] varies: the expansion the state holds. */
public data class TreeModelStateParameters(
    val expandedPaths: Set<List<Int>>,
)

/** A model-driven [Tree] driven by a tree state against a raw [JTree] with the same expansion. */
public object TreeModelStateFixture : ComposableFixture<TreeModelStateParameters, JTree>(
    initial =
        TreeModelStateParameters(
            expandedPaths = emptySet(),
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TreeModelStateParameters> =
        listOf(
            initial.copy(expandedPaths = setOf(emptyList())),
        )

    @Composable
    override fun Content(parameters: TreeModelStateParameters): Unit = Content(parameters.expandedPaths)

    /** Composes a [Tree] displaying the fixed model, with a state holding the given expansion. */
    @Composable
    public fun Content(expandedPaths: Set<List<Int>>) {
        val state = rememberTreeState(initialSelectedPaths = emptySet(), initialExpandedPaths = expandedPaths)
        SideEffect {
            state.selectedPaths = emptySet()
            state.expandedPaths = expandedPaths
        }
        val model = remember { TreeFixture.sampleModel("Root before", "Alpha", "Beta") }
        Tree(model = model, state = state)
    }

    /** Builds a [JTree] over the fixed model with the fixed selection, before the expansion is applied. */
    override fun createRawSwing(): JTree {
        val tree = JTree(TreeFixture.sampleModel("Root before", "Alpha", "Beta"))
        TreeFixture.applySelection(tree, emptySet())
        return tree
    }

    /** Applies the expansion of [parameters] to [component]. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeModelStateParameters,
    ) {
        TreeFixture.applyExpansion(component, parameters.expandedPaths)
    }
}

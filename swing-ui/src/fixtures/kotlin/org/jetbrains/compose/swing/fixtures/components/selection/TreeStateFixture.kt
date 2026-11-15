// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.rememberTreeState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTree

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
            TreeFixture.valuesModel(
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
        TreeFixture.applyExpansion(component, parameters.expandedPaths)
    }
}

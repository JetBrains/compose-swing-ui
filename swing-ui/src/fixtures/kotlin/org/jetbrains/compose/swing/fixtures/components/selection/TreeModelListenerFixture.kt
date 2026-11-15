// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener

/** The parameters [TreeModelListenerFixture] varies: the raw listeners. */
public data class TreeModelListenerParameters(
    val treeSelectionListener: TreeSelectionListener,
    val treeExpansionListener: TreeExpansionListener?,
)

/** A model-driven [Tree] driven by raw listeners against a raw [JTree] with the same model and listeners. */
public object TreeModelListenerFixture : ComposableFixture<TreeModelListenerParameters, JTree>(
    initial =
        TreeModelListenerParameters(
            treeSelectionListener = {},
            treeExpansionListener = null,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TreeModelListenerParameters> =
        listOf(
            initial.copy(treeSelectionListener = {}),
            initial.copy(
                treeExpansionListener =
                    object : TreeExpansionListener {
                        override fun treeExpanded(event: TreeExpansionEvent) = Unit

                        override fun treeCollapsed(event: TreeExpansionEvent) = Unit
                    },
            ),
        )

    @Composable
    override fun Content(parameters: TreeModelListenerParameters): Unit =
        Content(parameters.treeSelectionListener, parameters.treeExpansionListener)

    /** Composes a [Tree] displaying the fixed model and notifying the given listeners. */
    @Composable
    public fun Content(
        treeSelectionListener: TreeSelectionListener,
        treeExpansionListener: TreeExpansionListener?,
    ) {
        val model = remember { TreeFixture.sampleModel("Root before", "Alpha", "Beta") }
        Tree(
            model = model,
            treeSelectionListener = treeSelectionListener,
            treeExpansionListener = treeExpansionListener,
        )
    }

    /** Builds a [JTree] over the fixed model before any of the fixture's values are applied. */
    override fun createRawSwing(): JTree = JTree(TreeFixture.sampleModel("Root before", "Alpha", "Beta"))

    /**
     * Replaces the listeners this fixture added on [component] with those of [parameters], keeping each adapter
     * whose delegate is unchanged.
     */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeModelListenerParameters,
    ) {
        val selectionListener = parameters.treeSelectionListener
        var selectionKept = false
        for (listener in component.treeSelectionListeners) {
            if (listener is TreeListenerFixture.SelectionForwarder) {
                if (listener.delegate === selectionListener) {
                    selectionKept = true
                } else {
                    component.removeTreeSelectionListener(listener)
                }
            }
        }
        if (!selectionKept) {
            component.addTreeSelectionListener(TreeListenerFixture.SelectionForwarder(selectionListener))
        }
        val expansionListener = parameters.treeExpansionListener
        var expansionKept = false
        for (listener in component.treeExpansionListeners) {
            if (listener is TreeListenerFixture.ExpansionForwarder) {
                if (listener.delegate === expansionListener) {
                    expansionKept = true
                } else {
                    component.removeTreeExpansionListener(listener)
                }
            }
        }
        if (expansionListener != null && !expansionKept) {
            component.addTreeExpansionListener(TreeListenerFixture.ExpansionForwarder(expansionListener))
        }
    }
}

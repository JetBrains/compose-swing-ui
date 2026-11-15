// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener

/** The parameters [TreeListenerFixture] varies: the raw listeners. */
public data class TreeListenerParameters(
    val treeSelectionListener: TreeSelectionListener,
    val treeExpansionListener: TreeExpansionListener?,
    val treeWillExpandListener: TreeWillExpandListener?,
)

/** A value-driven [Tree] driven by raw listeners against a raw [JTree] with the same listeners attached. */
public object TreeListenerFixture : ComposableFixture<TreeListenerParameters, JTree>(
    initial =
        TreeListenerParameters(
            treeSelectionListener = {},
            treeExpansionListener = null,
            treeWillExpandListener = null,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TreeListenerParameters> =
        listOf(
            initial.copy(treeSelectionListener = {}),
            initial.copy(
                treeExpansionListener =
                    object : TreeExpansionListener {
                        override fun treeExpanded(event: TreeExpansionEvent) = Unit

                        override fun treeCollapsed(event: TreeExpansionEvent) = Unit
                    },
            ),
            initial.copy(
                treeWillExpandListener =
                    object : TreeWillExpandListener {
                        override fun treeWillExpand(event: TreeExpansionEvent) = Unit

                        override fun treeWillCollapse(event: TreeExpansionEvent) = Unit
                    },
            ),
        )

    @Composable
    override fun Content(parameters: TreeListenerParameters): Unit =
        Content(
            parameters.treeSelectionListener,
            parameters.treeExpansionListener,
            parameters.treeWillExpandListener,
        )

    /** Composes a [Tree] over the fixed model, notifying the given listeners. */
    @Composable
    public fun Content(
        treeSelectionListener: TreeSelectionListener,
        treeExpansionListener: TreeExpansionListener?,
        treeWillExpandListener: TreeWillExpandListener?,
    ) {
        Tree(
            root = "Root before",
            children = { if (it.startsWith("Root")) listOf("Alpha", "Beta") else emptyList() },
            treeSelectionListener = treeSelectionListener,
            treeExpansionListener = treeExpansionListener,
            treeWillExpandListener = treeWillExpandListener,
        )
    }

    /** Builds a [JTree] over the fixed model the composed [Tree] shows, before any listener is applied. */
    override fun createRawSwing(): JTree =
        JTree(
            TreeFixture.valuesModel(
                "Root before",
                { if (it.startsWith("Root")) listOf("Alpha", "Beta") else emptyList() },
                { it },
                null,
            ),
        )

    /** Replaces the listeners this fixture added with the ones [parameters] names. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeListenerParameters,
    ) {
        val selectionListener = parameters.treeSelectionListener
        var selectionKept = false
        for (listener in component.treeSelectionListeners) {
            if (listener is SelectionForwarder) {
                if (listener.delegate === selectionListener) {
                    selectionKept = true
                } else {
                    component.removeTreeSelectionListener(listener)
                }
            }
        }
        if (!selectionKept) {
            component.addTreeSelectionListener(SelectionForwarder(selectionListener))
        }
        val expansionListener = parameters.treeExpansionListener
        var expansionKept = false
        for (listener in component.treeExpansionListeners) {
            if (listener is ExpansionForwarder) {
                if (listener.delegate === expansionListener) {
                    expansionKept = true
                } else {
                    component.removeTreeExpansionListener(listener)
                }
            }
        }
        if (expansionListener != null && !expansionKept) {
            component.addTreeExpansionListener(ExpansionForwarder(expansionListener))
        }
        val willExpandListener = parameters.treeWillExpandListener
        if (willExpandListener == null || willExpandListener !in component.treeWillExpandListeners) {
            initial.treeWillExpandListener?.let(component::removeTreeWillExpandListener)
            alternatives.forEach { it.treeWillExpandListener?.let(component::removeTreeWillExpandListener) }
            willExpandListener?.let(component::addTreeWillExpandListener)
        }
    }

    /**
     * Hands every selection event to the [delegate] it wraps; an update keeps it while the parameters name the
     * same instance.
     */
    public class SelectionForwarder(
        internal val delegate: TreeSelectionListener,
    ) : TreeSelectionListener by delegate

    /**
     * Hands every expansion event to the [delegate] it wraps; an update keeps it while the parameters name the
     * same instance.
     */
    public class ExpansionForwarder(
        internal val delegate: TreeExpansionListener,
    ) : TreeExpansionListener by delegate
}

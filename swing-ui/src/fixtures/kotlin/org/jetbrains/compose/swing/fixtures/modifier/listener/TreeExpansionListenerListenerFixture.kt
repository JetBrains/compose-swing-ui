// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.treeExpansionListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener

/** The parameters [TreeExpansionListenerListenerFixture] varies: the listener attached to the tree. */
public data class TreeExpansionListenerListenerParameters(
    val listener: TreeExpansionListener,
)

/**
 * A [SwingModifier.treeExpansionListener] given a [TreeExpansionListener] instance, against a raw [JTree] with
 * the same listener attached by hand.
 */
public object TreeExpansionListenerListenerFixture :
    ComposableFixture<TreeExpansionListenerListenerParameters, JTree>(
        initial =
            TreeExpansionListenerListenerParameters(
                listener =
                    object : TreeExpansionListener {
                        override fun treeExpanded(event: TreeExpansionEvent) = Unit

                        override fun treeCollapsed(event: TreeExpansionEvent) = Unit
                    },
            ),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TreeExpansionListenerListenerParameters> =
        listOf(
            initial.copy(
                listener =
                    object : TreeExpansionListener {
                        override fun treeExpanded(event: TreeExpansionEvent) = Unit

                        override fun treeCollapsed(event: TreeExpansionEvent) = Unit
                    },
            ),
        )

    @Composable
    override fun Content(parameters: TreeExpansionListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [JTree] host with [listener] attached to it. */
    @Composable
    public fun Content(listener: TreeExpansionListener) {
        SwingNode<JTree>(
            factory = { JTree() },
            modifier = SwingModifier.treeExpansionListener(listener = listener),
        )
    }

    /** Builds a bare [JTree] before any of the fixture's values are applied. */
    override fun createRawSwing(): JTree = JTree()

    /** Replaces the fixture's earlier expansion listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeExpansionListenerListenerParameters,
    ) {
        component.removeTreeExpansionListener(initial.listener)
        alternatives.forEach { component.removeTreeExpansionListener(it.listener) }
        component.addTreeExpansionListener(parameters.listener)
    }
}

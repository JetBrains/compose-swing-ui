// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.modifier.listener.treeWillExpandListener
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.ExpandVetoException

/** The parameters [TreeWillExpandListenerListenerFixture] varies. */
public data class TreeWillExpandListenerListenerParameters(
    val listener: TreeWillExpandListener,
)

/**
 * A `treeWillExpandListener` given a [TreeWillExpandListener] against a raw `JTree` with the same
 * listener added by hand.
 */
public object TreeWillExpandListenerListenerFixture :
    ComposableFixture<TreeWillExpandListenerListenerParameters, JTree>(
        initial =
            TreeWillExpandListenerListenerParameters(
                listener =
                    object : TreeWillExpandListener {
                        override fun treeWillExpand(event: TreeExpansionEvent) = Unit

                        override fun treeWillCollapse(event: TreeExpansionEvent) = Unit
                    },
            ),
    ) {
    private val vetoingListener =
        object : TreeWillExpandListener {
            override fun treeWillExpand(event: TreeExpansionEvent): Unit = throw ExpandVetoException(event)

            override fun treeWillCollapse(event: TreeExpansionEvent): Unit = throw ExpandVetoException(event)
        }

    /** The initial listener replaced by a fresh one that refuses every change. */
    override val alternatives: List<TreeWillExpandListenerListenerParameters> =
        listOf(
            initial.copy(listener = vetoingListener),
        )

    @Composable
    override fun Content(parameters: TreeWillExpandListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a `JTree` whose expand and collapse requests notify [listener]. */
    @Composable
    public fun Content(listener: TreeWillExpandListener) {
        SwingNode(
            factory = { JTree() },
            modifier = SwingModifier.treeWillExpandListener(listener = listener),
        )
    }

    /** Builds a bare `JTree` with no veto listener attached. */
    override fun createRawSwing(): JTree = JTree()

    /** Makes [component] notify [parameters]'s listener of expand and collapse requests, replacing any earlier one. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeWillExpandListenerListenerParameters,
    ) {
        component.removeTreeWillExpandListener(initial.listener)
        component.removeTreeWillExpandListener(vetoingListener)
        component.addTreeWillExpandListener(parameters.listener)
    }
}

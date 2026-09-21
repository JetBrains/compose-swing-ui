// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.modifier.listener.treeSelectionListener
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTree
import javax.swing.event.TreeSelectionListener

/** The parameters [TreeSelectionListenerListenerFixture] varies. */
public data class TreeSelectionListenerListenerParameters(
    val listener: TreeSelectionListener,
)

/**
 * A `treeSelectionListener` given a [TreeSelectionListener] against a raw `JTree` with the same
 * listener added by hand.
 */
public object TreeSelectionListenerListenerFixture :
    ComposableFixture<TreeSelectionListenerListenerParameters, JTree>(
        initial = TreeSelectionListenerListenerParameters(listener = {}),
    ) {
    private val alternateListener = TreeSelectionListener {}

    /** The initial listener replaced by a fresh instance, which the tree then notifies instead. */
    override val alternatives: List<TreeSelectionListenerListenerParameters> =
        listOf(
            initial.copy(listener = alternateListener),
        )

    @Composable
    override fun Content(parameters: TreeSelectionListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a `JTree` whose selection changes notify [listener]. */
    @Composable
    public fun Content(listener: TreeSelectionListener) {
        SwingNode(
            factory = { JTree().apply { setSelectionRow(0) } },
            modifier = SwingModifier.treeSelectionListener(listener = listener),
        )
    }

    /** Builds a bare `JTree` with row 0 selected and no selection listener attached. */
    override fun createRawSwing(): JTree = JTree().apply { setSelectionRow(0) }

    /** Makes [component] notify [parameters]'s listener of selection changes, replacing any earlier one. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeSelectionListenerListenerParameters,
    ) {
        component.removeTreeSelectionListener(initial.listener)
        component.removeTreeSelectionListener(alternateListener)
        component.addTreeSelectionListener(parameters.listener)
    }
}

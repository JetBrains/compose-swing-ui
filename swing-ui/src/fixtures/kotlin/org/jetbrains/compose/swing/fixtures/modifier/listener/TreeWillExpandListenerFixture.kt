// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.treeWillExpandListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.ExpandVetoException

/** The parameters [TreeWillExpandListenerFixture] varies. */
public data class TreeWillExpandListenerParameters(
    val onWillChange: (TreeExpansionEvent) -> Boolean,
)

/**
 * A `treeWillExpandListener` veto callback on a `JTree` against a raw `JTree` with the same
 * veto listener added by hand.
 */
public object TreeWillExpandListenerFixture : ComposableFixture<TreeWillExpandListenerParameters, JTree>(
    initial = TreeWillExpandListenerParameters(onWillChange = { true }),
) {
    /** The initial callback replaced by one that refuses every change. */
    override val alternatives: List<TreeWillExpandListenerParameters> =
        listOf(
            initial.copy(onWillChange = { false }),
        )

    @Composable
    override fun Content(parameters: TreeWillExpandListenerParameters): Unit = Content(parameters.onWillChange)

    /** Composes a `JTree` whose expand and collapse requests run [onWillChange]. */
    @Composable
    public fun Content(onWillChange: (TreeExpansionEvent) -> Boolean) {
        SwingNode(
            factory = { JTree() },
            modifier = SwingModifier.treeWillExpandListener(onWillChange = onWillChange),
        )
    }

    /** Builds a bare `JTree` with no veto listener attached. */
    override fun createRawSwing(): JTree = JTree()

    /** Adds the veto listener on the first call; later calls only replace its callback with the one of [parameters]. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeWillExpandListenerParameters,
    ) {
        val existing = component.treeWillExpandListeners.firstNotNullOfOrNull { it as? VetoListener }
        if (existing == null) {
            component.addTreeWillExpandListener(VetoListener(parameters.onWillChange))
        } else {
            existing.onWillChange = parameters.onWillChange
        }
    }

    /** The raw listener that vetoes what the callback refuses, told apart from listeners the fixture did not add. */
    private class VetoListener(
        var onWillChange: (TreeExpansionEvent) -> Boolean,
    ) : TreeWillExpandListener {
        override fun treeWillExpand(event: TreeExpansionEvent) {
            if (!onWillChange(event)) throw ExpandVetoException(event)
        }

        override fun treeWillCollapse(event: TreeExpansionEvent) {
            if (!onWillChange(event)) throw ExpandVetoException(event)
        }
    }
}

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

/** The parameters [TreeExpansionListenerCallbackFixture] varies: the one callback run on expansion and on collapse. */
public data class TreeExpansionListenerCallbackParameters(
    val onExpansionChange: (TreeExpansionEvent) -> Unit,
)

/**
 * A [SwingModifier.treeExpansionListener] given one callback for both directions, against a raw [JTree] with the
 * same callback attached by hand.
 */
public object TreeExpansionListenerCallbackFixture :
    ComposableFixture<TreeExpansionListenerCallbackParameters, JTree>(
        initial = TreeExpansionListenerCallbackParameters(onExpansionChange = {}),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TreeExpansionListenerCallbackParameters> =
        listOf(
            initial.copy(onExpansionChange = {}),
        )

    @Composable
    override fun Content(parameters: TreeExpansionListenerCallbackParameters): Unit =
        Content(parameters.onExpansionChange)

    /** Composes a [JTree] host that runs [onExpansionChange] whenever a node expands or collapses. */
    @Composable
    public fun Content(onExpansionChange: (TreeExpansionEvent) -> Unit) {
        SwingNode<JTree>(
            factory = { JTree() },
            modifier = SwingModifier.treeExpansionListener(onExpansionChange = onExpansionChange),
        )
    }

    /** Builds a bare [JTree] before any of the fixture's values are applied. */
    override fun createRawSwing(): JTree = JTree()

    /** Adds the listener on the first call; later calls only replace its callback with the one of [parameters]. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeExpansionListenerCallbackParameters,
    ) {
        val existing =
            component.treeExpansionListeners.firstNotNullOfOrNull { it as? ForwardingTreeExpansionListener }
        if (existing == null) {
            component.addTreeExpansionListener(ForwardingTreeExpansionListener(parameters.onExpansionChange))
        } else {
            existing.onExpansionChange = parameters.onExpansionChange
        }
    }
}

/** The listener [TreeExpansionListenerCallbackFixture] installs on its raw tree, so it finds only its own. */
private class ForwardingTreeExpansionListener(
    var onExpansionChange: (TreeExpansionEvent) -> Unit,
) : TreeExpansionListener {
    override fun treeExpanded(event: TreeExpansionEvent) {
        onExpansionChange(event)
    }

    override fun treeCollapsed(event: TreeExpansionEvent) {
        onExpansionChange(event)
    }
}

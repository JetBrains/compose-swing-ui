// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.modifier.listener.treeExpansionListener
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener

/** The parameters [TreeExpansionListenerFixture] varies: the callbacks run on expansion and on collapse. */
public data class TreeExpansionListenerParameters(
    val onTreeExpanded: (TreeExpansionEvent) -> Unit,
    val onTreeCollapsed: (TreeExpansionEvent) -> Unit,
)

/**
 * A [SwingModifier.treeExpansionListener] with expand and collapse callbacks, against a raw [JTree] with the
 * same callbacks attached by hand.
 */
public object TreeExpansionListenerFixture : ComposableFixture<TreeExpansionListenerParameters, JTree>(
    initial = TreeExpansionListenerParameters(onTreeExpanded = {}, onTreeCollapsed = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TreeExpansionListenerParameters> =
        listOf(
            initial.copy(onTreeExpanded = {}),
            initial.copy(onTreeCollapsed = {}),
        )

    @Composable
    override fun Content(parameters: TreeExpansionListenerParameters): Unit =
        Content(parameters.onTreeExpanded, parameters.onTreeCollapsed)

    /** Composes a [JTree] host that runs [onTreeExpanded] and [onTreeCollapsed] on the matching events. */
    @Composable
    public fun Content(
        onTreeExpanded: (TreeExpansionEvent) -> Unit,
        onTreeCollapsed: (TreeExpansionEvent) -> Unit,
    ) {
        SwingNode<JTree>(
            factory = { JTree() },
            modifier =
                SwingModifier.treeExpansionListener(
                    onTreeExpanded = onTreeExpanded,
                    onTreeCollapsed = onTreeCollapsed,
                ),
        )
    }

    /** Builds a bare [JTree] before any of the fixture's values are applied. */
    override fun createRawSwing(): JTree = JTree()

    /** Adds the listener on the first call; later calls only replace its callbacks with those of [parameters]. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeExpansionListenerParameters,
    ) {
        val existing = component.treeExpansionListeners.firstNotNullOfOrNull { it as? TreeExpansionRelay }
        if (existing == null) {
            component.addTreeExpansionListener(
                TreeExpansionRelay(parameters.onTreeExpanded, parameters.onTreeCollapsed),
            )
        } else {
            existing.onTreeExpanded = parameters.onTreeExpanded
            existing.onTreeCollapsed = parameters.onTreeCollapsed
        }
    }
}

/** The expansion listener [TreeExpansionListenerFixture] installs on its raw tree, so that it finds only its own. */
private class TreeExpansionRelay(
    var onTreeExpanded: (TreeExpansionEvent) -> Unit,
    var onTreeCollapsed: (TreeExpansionEvent) -> Unit,
) : TreeExpansionListener {
    override fun treeExpanded(event: TreeExpansionEvent) {
        onTreeExpanded(event)
    }

    override fun treeCollapsed(event: TreeExpansionEvent) {
        onTreeCollapsed(event)
    }
}

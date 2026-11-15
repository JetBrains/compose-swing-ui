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

/** The parameters [TreeWillExpandListenerDirectionalFixture] varies: the callbacks for an expand and a collapse. */
public data class TreeWillExpandListenerDirectionalParameters(
    val onWillExpand: (TreeExpansionEvent) -> Boolean,
    val onWillCollapse: (TreeExpansionEvent) -> Boolean,
)

/**
 * A `treeWillExpandListener` with split expand and collapse callbacks on a `JTree` against a raw `JTree`
 * with the same split callbacks added by hand.
 */
public object TreeWillExpandListenerDirectionalFixture :
    ComposableFixture<TreeWillExpandListenerDirectionalParameters, JTree>(
        initial = TreeWillExpandListenerDirectionalParameters(onWillExpand = { true }, onWillCollapse = { true }),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TreeWillExpandListenerDirectionalParameters> =
        listOf(
            initial.copy(onWillExpand = { false }),
            initial.copy(onWillCollapse = { false }),
        )

    @Composable
    override fun Content(parameters: TreeWillExpandListenerDirectionalParameters): Unit =
        Content(parameters.onWillExpand, parameters.onWillCollapse)

    /** Composes a `JTree` whose expand requests run [onWillExpand] and whose collapse requests run [onWillCollapse]. */
    @Composable
    public fun Content(
        onWillExpand: (TreeExpansionEvent) -> Boolean,
        onWillCollapse: (TreeExpansionEvent) -> Boolean,
    ) {
        SwingNode(
            factory = { JTree() },
            modifier =
                SwingModifier.treeWillExpandListener(
                    onWillExpand = onWillExpand,
                    onWillCollapse = onWillCollapse,
                ),
        )
    }

    /** Builds a bare `JTree` with no veto listener attached. */
    override fun createRawSwing(): JTree = JTree()

    /** Adds the veto listener on the first call; later calls only replace its callbacks with those of [parameters]. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeWillExpandListenerDirectionalParameters,
    ) {
        val existing = component.treeWillExpandListeners.firstNotNullOfOrNull { it as? VetoListener }
        if (existing == null) {
            component.addTreeWillExpandListener(VetoListener(parameters.onWillExpand, parameters.onWillCollapse))
        } else {
            existing.onWillExpand = parameters.onWillExpand
            existing.onWillCollapse = parameters.onWillCollapse
        }
    }

    /** The raw listener that vetoes what the callbacks refuse, told apart from listeners the fixture did not add. */
    private class VetoListener(
        var onWillExpand: (TreeExpansionEvent) -> Boolean,
        var onWillCollapse: (TreeExpansionEvent) -> Boolean,
    ) : TreeWillExpandListener {
        override fun treeWillExpand(event: TreeExpansionEvent) {
            if (!onWillExpand(event)) throw ExpandVetoException(event)
        }

        override fun treeWillCollapse(event: TreeExpansionEvent) {
            if (!onWillCollapse(event)) throw ExpandVetoException(event)
        }
    }
}

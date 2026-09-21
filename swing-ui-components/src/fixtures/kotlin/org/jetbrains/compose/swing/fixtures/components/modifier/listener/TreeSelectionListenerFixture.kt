// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.modifier.listener.treeSelectionListener
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener

/** The parameters [TreeSelectionListenerFixture] varies. */
public data class TreeSelectionListenerParameters(
    val onSelectionChange: (TreeSelectionEvent) -> Unit,
)

/**
 * A `treeSelectionListener` callback on a `JTree` against a raw `JTree` with the same
 * selection listener added by hand.
 */
public object TreeSelectionListenerFixture : ComposableFixture<TreeSelectionListenerParameters, JTree>(
    initial = TreeSelectionListenerParameters(onSelectionChange = {}),
) {
    /** The initial callback replaced by a fresh one, which the selection listener then runs instead. */
    override val alternatives: List<TreeSelectionListenerParameters> =
        listOf(
            initial.copy(onSelectionChange = {}),
        )

    @Composable
    override fun Content(parameters: TreeSelectionListenerParameters): Unit = Content(parameters.onSelectionChange)

    /** Composes a `JTree` whose selection changes run [onSelectionChange]. */
    @Composable
    public fun Content(onSelectionChange: (TreeSelectionEvent) -> Unit) {
        SwingNode(
            factory = { JTree().apply { setSelectionRow(0) } },
            modifier = SwingModifier.treeSelectionListener(onSelectionChange = onSelectionChange),
        )
    }

    /** Builds a bare `JTree` with row 0 selected and no selection listener attached. */
    override fun createRawSwing(): JTree = JTree().apply { setSelectionRow(0) }

    /** Adds the listener on the first call; later calls only replace its callback with the one in [parameters]. */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeSelectionListenerParameters,
    ) {
        val existing = component.treeSelectionListeners.firstNotNullOfOrNull { it as? CallbackSelectionListener }
        if (existing == null) {
            component.addTreeSelectionListener(CallbackSelectionListener(parameters.onSelectionChange))
        } else {
            existing.onSelectionChange = parameters.onSelectionChange
        }
    }

    /** The raw listener that runs the callback, told apart from listeners the fixture did not add. */
    private class CallbackSelectionListener(
        var onSelectionChange: (TreeSelectionEvent) -> Unit,
    ) : TreeSelectionListener {
        override fun valueChanged(event: TreeSelectionEvent) = onSelectionChange(event)
    }
}

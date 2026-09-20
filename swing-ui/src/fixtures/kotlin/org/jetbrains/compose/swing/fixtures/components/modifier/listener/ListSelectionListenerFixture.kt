// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JList
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/** The parameters [ListSelectionListenerFixture] varies: the callback run when the list's selection changes. */
public data class ListSelectionListenerParameters(
    val onSelectionChange: (ListSelectionEvent) -> Unit,
)

/** A [SwingModifier.listSelectionListener] callback on a list against a raw [JList] with the same callback. */
public object ListSelectionListenerFixture : ComposableFixture<ListSelectionListenerParameters, JList<String>>(
    initial = ListSelectionListenerParameters(onSelectionChange = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ListSelectionListenerParameters> =
        listOf(
            initial.copy(onSelectionChange = {}),
        )

    @Composable
    override fun Content(parameters: ListSelectionListenerParameters): Unit = Content(parameters.onSelectionChange)

    /** Composes a list that runs [onSelectionChange] on each selection change, through the modifier. */
    @Composable
    public fun Content(onSelectionChange: (ListSelectionEvent) -> Unit) {
        SwingNode<JList<String>>(
            factory = { JList(arrayOf("Before", "After")) },
            modifier = SwingModifier.listSelectionListener(onSelectionChange = onSelectionChange),
        )
    }

    /** Builds a bare [JList] with the same items and no selection callback attached yet. */
    override fun createRawSwing(): JList<String> = JList(arrayOf("Before", "After"))

    /** Adds the listener on the first call; later calls only replace its callback with the one of [parameters]. */
    override fun updateRawSwing(
        component: JList<String>,
        parameters: ListSelectionListenerParameters,
    ) {
        val existing =
            component.listSelectionListeners.firstNotNullOfOrNull { it as? ForwardingListSelectionListener }
        if (existing == null) {
            component.addListSelectionListener(ForwardingListSelectionListener(parameters.onSelectionChange))
        } else {
            existing.onSelectionChange = parameters.onSelectionChange
        }
    }
}

/** The selection listener [ListSelectionListenerFixture] adds to its raw component, so it finds only its own. */
private class ForwardingListSelectionListener(
    var onSelectionChange: (ListSelectionEvent) -> Unit,
) : ListSelectionListener {
    override fun valueChanged(event: ListSelectionEvent) {
        onSelectionChange(event)
    }
}

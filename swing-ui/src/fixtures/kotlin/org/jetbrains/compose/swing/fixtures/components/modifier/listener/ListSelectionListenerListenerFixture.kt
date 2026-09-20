// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JList
import javax.swing.event.ListSelectionListener

/** The parameters [ListSelectionListenerListenerFixture] varies: the listener attached to the list. */
public data class ListSelectionListenerListenerParameters(
    val listener: ListSelectionListener,
)

/** A [SwingModifier.listSelectionListener] listener on a list against a raw [JList] with the same listener. */
public object ListSelectionListenerListenerFixture :
    ComposableFixture<ListSelectionListenerListenerParameters, JList<String>>(
        initial = ListSelectionListenerListenerParameters(listener = {}),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ListSelectionListenerListenerParameters> =
        listOf(
            initial.copy(listener = {}),
        )

    @Composable
    override fun Content(parameters: ListSelectionListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a list that attaches [listener] through the modifier. */
    @Composable
    public fun Content(listener: ListSelectionListener) {
        SwingNode<JList<String>>(
            factory = { JList(arrayOf("Before", "After")) },
            modifier = SwingModifier.listSelectionListener(listener = listener),
        )
    }

    /** Builds a bare [JList] with the same items and no listener attached yet. */
    override fun createRawSwing(): JList<String> = JList(arrayOf("Before", "After"))

    /** Replaces the fixture's earlier selection listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JList<String>,
        parameters: ListSelectionListenerListenerParameters,
    ) {
        component.removeListSelectionListener(initial.listener)
        alternatives.forEach { component.removeListSelectionListener(it.listener) }
        component.addListSelectionListener(parameters.listener)
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ActionListener
import javax.swing.JButton

/** The parameters [ActionListenerListenerFixture] varies: the action listener instance. */
public data class ActionListenerListenerParameters(
    val listener: ActionListener,
)

/**
 * A button with an action listener declared through `actionListener(listener)`, against a raw [JButton] with the same
 * listener attached.
 */
public object ActionListenerListenerFixture : ComposableFixture<ActionListenerListenerParameters, JButton>(
    initial = ActionListenerListenerParameters(listener = {}),
) {
    private val alternateListener = ActionListener {}

    /** The single-change copies of [initial]: one with a different listener instance. */
    override val alternatives: List<ActionListenerListenerParameters> =
        listOf(
            initial.copy(listener = alternateListener),
        )

    @Composable
    override fun Content(parameters: ActionListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a button that attaches [listener] through [SwingModifier.actionListener]. */
    @Composable
    public fun Content(listener: ActionListener) {
        SwingNode<JButton>(
            factory = { JButton("Action") },
            modifier = SwingModifier.actionListener(listener = listener),
        )
    }

    /** Builds a bare [JButton] before any of the fixture's listeners are applied. */
    override fun createRawSwing(): JButton = JButton("Action")

    /** Installs the listener in [parameters] on [component] in place of the fixture's earlier ones. */
    override fun updateRawSwing(
        component: JButton,
        parameters: ActionListenerListenerParameters,
    ) {
        component.removeActionListener(initial.listener)
        component.removeActionListener(alternateListener)
        component.addActionListener(parameters.listener)
    }
}

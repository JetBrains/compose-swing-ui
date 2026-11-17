// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton

/** The parameters [ActionListenerFixture] varies: the callback run on each action event. */
public data class ActionListenerParameters(
    val onAction: (ActionEvent) -> Unit,
)

/**
 * A button with an action callback declared through `actionListener`, against a raw [JButton] with the same callback.
 */
public object ActionListenerFixture : ComposableFixture<ActionListenerParameters, JButton>(
    initial = ActionListenerParameters(onAction = {}),
) {
    /** The single-change copies of [initial]: one with a different callback. */
    override val alternatives: List<ActionListenerParameters> =
        listOf(
            initial.copy(onAction = {}),
        )

    @Composable
    override fun Content(parameters: ActionListenerParameters): Unit = Content(parameters.onAction)

    /** Composes a button that runs [onAction] on each action event, through the [Button] action listener overload. */
    @Composable
    public fun Content(onAction: (ActionEvent) -> Unit) {
        Button("Action", actionListener = ActionListener(onAction))
    }

    /** Builds a bare [JButton] before any of the fixture's callbacks are applied. */
    override fun createRawSwing(): JButton = JButton("Action")

    /** Adds the action listener on the first call; later calls only replace its callback with that of [parameters]. */
    override fun updateRawSwing(
        component: JButton,
        parameters: ActionListenerParameters,
    ) {
        val existing =
            component.actionListeners.firstNotNullOfOrNull { it as? ActionListenerModifierForwardingListener }
        if (existing == null) {
            component.addActionListener(ActionListenerModifierForwardingListener(parameters.onAction))
        } else {
            existing.onAction = parameters.onAction
        }
    }
}

/** The action listener [ActionListenerFixture] installs on its raw component, so that it finds only its own. */
private class ActionListenerModifierForwardingListener(
    var onAction: (ActionEvent) -> Unit,
) : ActionListener {
    override fun actionPerformed(event: ActionEvent) {
        onAction(event)
    }
}

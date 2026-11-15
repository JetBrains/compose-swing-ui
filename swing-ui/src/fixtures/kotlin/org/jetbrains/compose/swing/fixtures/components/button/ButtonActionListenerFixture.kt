// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import java.awt.event.ActionListener
import javax.swing.JButton

/** The parameter [ButtonActionListenerFixture] varies: the listener notified when the button is activated. */
public data class ButtonActionListenerParameters(
    val actionListener: ActionListener,
)

/** A [Button] driven by an [ActionListener] against a raw [JButton] with the same listener attached. */
public object ButtonActionListenerFixture : ComposableFixture<ButtonActionListenerParameters, JButton>(
    initial = ButtonActionListenerParameters(actionListener = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ButtonActionListenerParameters> =
        listOf(
            initial.copy(actionListener = {}),
        )

    @Composable
    override fun Content(parameters: ButtonActionListenerParameters): Unit = Content(parameters.actionListener)

    /** Composes a [Button] with no text or style that notifies [actionListener] when activated. */
    @Composable
    public fun Content(actionListener: ActionListener) {
        Button(text = "", actionListener = actionListener)
    }

    /** Builds a bare [JButton] before any of the fixture's values are applied. */
    override fun createRawSwing(): JButton = JButton()

    override fun updateRawSwing(
        component: JButton,
        parameters: ButtonActionListenerParameters,
    ) {
        if (parameters.actionListener !in component.actionListeners) {
            component.removeActionListener(initial.actionListener)
            alternatives.forEach { component.removeActionListener(it.actionListener) }
            component.addActionListener(parameters.actionListener)
        }
    }
}

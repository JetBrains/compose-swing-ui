// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.event.ActionListener
import javax.swing.JToggleButton

/** The parameters [ToggleButtonActionListenerFixture] varies: the action listener only. */
public data class ToggleButtonActionListenerParameters(
    val actionListener: ActionListener,
)

/**
 * A [ToggleButton] taking an [ActionListener] instead of an `onSelectedChange` callback, against a raw
 * [JToggleButton] in the same state as [ToggleButtonFixture.initial] notifying the same listener.
 */
public object ToggleButtonActionListenerFixture :
    ComposableFixture<ToggleButtonActionListenerParameters, JToggleButton>(
        initial = ToggleButtonActionListenerParameters(actionListener = {}),
    ) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<ToggleButtonActionListenerParameters> =
        listOf(
            initial.copy(actionListener = {}),
        )

    @Composable
    override fun Content(parameters: ToggleButtonActionListenerParameters): Unit = Content(parameters.actionListener)

    /** Composes a [ToggleButton] notifying [actionListener], with the shared values of [ToggleButtonFixture]. */
    @Composable
    public fun Content(actionListener: ActionListener) {
        val shared = ToggleButtonFixture.initial
        ToggleButton(
            text = shared.text,
            selected = shared.selected,
            actionListener = actionListener,
            modifier = SwingModifier.name(shared.name),
            style = { foreground(shared.color) },
        )
    }

    /** Builds a [JToggleButton] in the shared initial state of [ToggleButtonFixture]. */
    override fun createRawSwing(): JToggleButton =
        ToggleButtonFixture.createRawSwing().apply {
            val shared = ToggleButtonFixture.initial
            text = shared.text
            isSelected = shared.selected
            name = shared.name
            foreground = shared.color
        }

    /** Replaces the listener this fixture installed on [component] with the one of [parameters]. */
    override fun updateRawSwing(
        component: JToggleButton,
        parameters: ToggleButtonActionListenerParameters,
    ) {
        if (parameters.actionListener !in component.actionListeners) {
            component.removeActionListener(initial.actionListener)
            alternatives.forEach { component.removeActionListener(it.actionListener) }
            component.addActionListener(parameters.actionListener)
        }
    }
}

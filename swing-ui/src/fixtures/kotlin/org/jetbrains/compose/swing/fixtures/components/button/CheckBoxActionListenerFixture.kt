// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.event.ActionListener
import javax.swing.JCheckBox

/** The parameters [CheckBoxActionListenerFixture] varies: the action listener only. */
public data class CheckBoxActionListenerParameters(
    val actionListener: ActionListener,
)

/**
 * A [CheckBox] taking an [ActionListener] instead of an `onCheckedChange` callback, against a raw [JCheckBox]
 * in the same state as [CheckBoxFixture.initial] notifying the same listener.
 */
public object CheckBoxActionListenerFixture :
    ComposableFixture<CheckBoxActionListenerParameters, JCheckBox>(
        initial = CheckBoxActionListenerParameters(actionListener = {}),
    ) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<CheckBoxActionListenerParameters> =
        listOf(
            initial.copy(actionListener = {}),
        )

    @Composable
    override fun Content(parameters: CheckBoxActionListenerParameters): Unit = Content(parameters.actionListener)

    /** Composes a [CheckBox] notifying [actionListener], with the shared values of [CheckBoxFixture]. */
    @Composable
    public fun Content(actionListener: ActionListener) {
        val shared = CheckBoxFixture.initial
        CheckBox(
            text = shared.text,
            checked = shared.checked,
            actionListener = actionListener,
            modifier = SwingModifier.name(shared.name),
            style = { foreground(shared.color) },
        )
    }

    /** Builds a [JCheckBox] in the shared initial state of [CheckBoxFixture]. */
    override fun createRawSwing(): JCheckBox =
        CheckBoxFixture.createRawSwing().apply {
            val shared = CheckBoxFixture.initial
            text = shared.text
            isSelected = shared.checked
            name = shared.name
            foreground = shared.color
        }

    /** Replaces the listener this fixture installed on [component] with the one of [parameters]. */
    override fun updateRawSwing(
        component: JCheckBox,
        parameters: CheckBoxActionListenerParameters,
    ) {
        if (parameters.actionListener !in component.actionListeners) {
            component.removeActionListener(initial.actionListener)
            alternatives.forEach { component.removeActionListener(it.actionListener) }
            component.addActionListener(parameters.actionListener)
        }
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JCheckBox

/**
 * The parameters [CheckBoxFixture] varies: the text, the checked state, the change callback, the modifier name
 * and the foreground color.
 */
public data class CheckBoxParameters(
    val text: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val name: String,
    val color: Color,
)

/**
 * A [CheckBox] with an `onCheckedChange` callback against a raw [JCheckBox] with the same text, checked state,
 * name and foreground color, reporting its selection to the same callback.
 */
public object CheckBoxFixture : ComposableFixture<CheckBoxParameters, JCheckBox>(
    initial =
        CheckBoxParameters(
            text = "Before",
            checked = false,
            onCheckedChange = {},
            name = "Before",
            color = Color.BLUE,
        ),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<CheckBoxParameters> =
        listOf(
            initial.copy(text = "After"),
            initial.copy(checked = true),
            initial.copy(onCheckedChange = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
        )

    @Composable
    override fun Content(parameters: CheckBoxParameters): Unit =
        Content(parameters.text, parameters.checked, parameters.onCheckedChange, parameters.name, parameters.color)

    /**
     * Composes a [CheckBox] showing [text] in [checked] state, reporting changes to [onCheckedChange], named
     * [name] and drawn in [color].
     */
    @Composable
    public fun Content(
        text: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        name: String,
        color: Color,
    ) {
        CheckBox(
            text = text,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
        )
    }

    /** Builds a bare [JCheckBox] before any of the fixture's values are applied. */
    override fun createRawSwing(): JCheckBox = JCheckBox()

    /** Applies the text, checked state, name, foreground color and change callback of [parameters] to [component]. */
    override fun updateRawSwing(
        component: JCheckBox,
        parameters: CheckBoxParameters,
    ) {
        component.text = parameters.text
        component.isSelected = parameters.checked
        component.name = parameters.name
        component.foreground = parameters.color
        val listener = component.actionListeners.firstNotNullOfOrNull { it as? SelectionListener }
        if (listener == null) {
            component.addActionListener(SelectionListener(component, parameters.onCheckedChange))
        } else {
            listener.onCheckedChange = parameters.onCheckedChange
        }
    }

    /** Reports the selection of [checkBox] to [onCheckedChange] whenever the check box is toggled. */
    private class SelectionListener(
        private val checkBox: JCheckBox,
        var onCheckedChange: (Boolean) -> Unit,
    ) : ActionListener {
        override fun actionPerformed(event: ActionEvent) {
            onCheckedChange(checkBox.isSelected)
        }
    }
}

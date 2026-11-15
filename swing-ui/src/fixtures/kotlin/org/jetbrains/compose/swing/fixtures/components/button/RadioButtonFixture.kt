// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JRadioButton

/**
 * The parameters [RadioButtonFixture] varies: text, selection, selection callback, modifier name and foreground color.
 */
public data class RadioButtonParameters(
    val text: String,
    val selected: Boolean,
    val onSelectedChange: (Boolean) -> Unit,
    val name: String,
    val color: Color,
)

/** A [RadioButton] with an `onSelectedChange` callback against a raw [JRadioButton] with the same state. */
public object RadioButtonFixture : ComposableFixture<RadioButtonParameters, JRadioButton>(
    initial =
        RadioButtonParameters(text = "Before", selected = false, onSelectedChange = {
        }, name = "Before", color = Color.BLUE),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<RadioButtonParameters> =
        listOf(
            initial.copy(text = "After"),
            initial.copy(selected = true),
            initial.copy(onSelectedChange = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
        )

    @Composable
    override fun Content(parameters: RadioButtonParameters): Unit =
        Content(parameters.text, parameters.selected, parameters.onSelectedChange, parameters.name, parameters.color)

    /**
     * Composes a [RadioButton] labeled [text], selected as [selected], reporting to [onSelectedChange], named [name]
     * and drawn in [color].
     */
    @Composable
    public fun Content(
        text: String,
        selected: Boolean,
        onSelectedChange: (Boolean) -> Unit,
        name: String,
        color: Color,
    ) {
        RadioButton(
            text = text,
            selected = selected,
            onSelectedChange = onSelectedChange,
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
        )
    }

    /** Builds a bare [JRadioButton] before any of the fixture's values are applied. */
    override fun createRawSwing(): JRadioButton = JRadioButton()

    /** Applies the text, selection, name, foreground color and selection callback of [parameters] to [component]. */
    override fun updateRawSwing(
        component: JRadioButton,
        parameters: RadioButtonParameters,
    ) {
        component.text = parameters.text
        component.isSelected = parameters.selected
        component.name = parameters.name
        component.foreground = parameters.color
        val listener = component.actionListeners.firstNotNullOfOrNull { it as? SelectionListener }
        if (listener == null) {
            component.addActionListener(SelectionListener(component, parameters.onSelectedChange))
        } else {
            listener.onSelectedChange = parameters.onSelectedChange
        }
    }

    /** Reports the selection of its radio button to a callback. */
    private class SelectionListener(
        private val component: JRadioButton,
        var onSelectedChange: (Boolean) -> Unit,
    ) : ActionListener {
        override fun actionPerformed(event: ActionEvent) {
            onSelectedChange(component.isSelected)
        }
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JToggleButton

/**
 * The parameters [ToggleButtonFixture] varies: the text, the selected state, the selection callback, the
 * modifier name and the foreground color.
 */
public data class ToggleButtonParameters(
    val text: String,
    val selected: Boolean,
    val onSelectedChange: (Boolean) -> Unit,
    val name: String,
    val color: Color,
)

/**
 * A [ToggleButton] taking an `onSelectedChange` callback against a raw [JToggleButton] with the same text,
 * selected state, name and foreground color, reporting its selection to the same callback.
 */
public object ToggleButtonFixture : ComposableFixture<ToggleButtonParameters, JToggleButton>(
    initial =
        ToggleButtonParameters(
            text = "Before",
            selected = false,
            onSelectedChange = {},
            name = "Before",
            color = Color.BLUE,
        ),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<ToggleButtonParameters> =
        listOf(
            initial.copy(text = "After"),
            initial.copy(selected = true),
            initial.copy(onSelectedChange = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
        )

    @Composable
    override fun Content(parameters: ToggleButtonParameters): Unit =
        Content(
            parameters.text,
            parameters.selected,
            parameters.onSelectedChange,
            parameters.name,
            parameters.color,
        )

    /**
     * Composes a [ToggleButton] showing [text] in [selected] state, reporting toggles to [onSelectedChange],
     * named [name] and drawn in [color].
     */
    @Composable
    public fun Content(
        text: String,
        selected: Boolean,
        onSelectedChange: (Boolean) -> Unit,
        name: String,
        color: Color,
    ) {
        ToggleButton(
            text = text,
            selected = selected,
            onSelectedChange = onSelectedChange,
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
        )
    }

    /** Builds a bare [JToggleButton] before any of the fixture's values are applied. */
    override fun createRawSwing(): JToggleButton = JToggleButton()

    /** Applies the text, selected state, name, foreground color and callback of [parameters] to [component]. */
    override fun updateRawSwing(
        component: JToggleButton,
        parameters: ToggleButtonParameters,
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

    /** Reports the selection of [component] to [onSelectedChange] whenever the button is toggled. */
    private class SelectionListener(
        private val component: JToggleButton,
        var onSelectedChange: (Boolean) -> Unit,
    ) : ActionListener {
        override fun actionPerformed(event: ActionEvent) {
            onSelectedChange(component.isSelected)
        }
    }
}

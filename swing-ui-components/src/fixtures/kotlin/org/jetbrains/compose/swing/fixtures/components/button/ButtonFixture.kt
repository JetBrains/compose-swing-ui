// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton

/** The parameters [ButtonFixture] varies: text, click callback, modifier name and foreground color. */
public data class ButtonParameters(
    val text: String,
    val onClick: () -> Unit,
    val name: String,
    val color: Color,
)

/** A [Button] with an `onClick` callback against a raw [JButton] with the same text, name and color. */
public object ButtonFixture : ComposableFixture<ButtonParameters, JButton>(
    initial = ButtonParameters(text = "Before", onClick = {}, name = "Before", color = Color.BLUE),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ButtonParameters> =
        listOf(
            initial.copy(text = "After"),
            initial.copy(onClick = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
        )

    @Composable
    override fun Content(parameters: ButtonParameters): Unit =
        Content(parameters.text, parameters.onClick, parameters.name, parameters.color)

    /** Composes a [Button] labeled [text] that calls [onClick], named [name] and drawn in [color]. */
    @Composable
    public fun Content(
        text: String,
        onClick: () -> Unit,
        name: String,
        color: Color,
    ) {
        Button(
            text = text,
            onClick = onClick,
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
        )
    }

    /** Builds a bare [JButton] before any of the fixture's values are applied. */
    override fun createRawSwing(): JButton = JButton()

    /** Applies the text, name, foreground color and click callback of [parameters] to [component]. */
    override fun updateRawSwing(
        component: JButton,
        parameters: ButtonParameters,
    ) {
        component.text = parameters.text
        component.name = parameters.name
        component.foreground = parameters.color
        val listener = component.actionListeners.firstNotNullOfOrNull { it as? ButtonFixtureClickListener }
        if (listener == null) {
            component.addActionListener(ButtonFixtureClickListener(parameters.onClick))
        } else {
            listener.onClick = parameters.onClick
        }
    }
}

/** The action listener [ButtonFixture] installs once on its raw component, calling the latest click callback. */
private class ButtonFixtureClickListener(
    var onClick: () -> Unit,
) : ActionListener {
    override fun actionPerformed(event: ActionEvent) {
        onClick()
    }
}

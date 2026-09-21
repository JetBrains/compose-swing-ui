// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.keyboard

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke

/** The parameters [OnKeyStrokeKeyStrokeFixture] varies: the key-stroke given as a [KeyStroke] object. */
public data class OnKeyStrokeKeyStrokeParameters(
    val keyStroke: KeyStroke,
)

/**
 * A [Button] with an [onKeyStroke] binding declared from a [KeyStroke] object, against a raw [JButton] carrying the
 * same binding made by hand.
 */
public object OnKeyStrokeKeyStrokeFixture : ComposableFixture<OnKeyStrokeKeyStrokeParameters, JButton>(
    initial =
        OnKeyStrokeKeyStrokeParameters(keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)),
) {
    /** The text both sides show on the button. */
    private const val TEXT = "Event"

    /** The click handler both sides run for the button's own action. */
    private val onClick: () -> Unit = {}

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<OnKeyStrokeKeyStrokeParameters> =
        listOf(
            initial.copy(keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)),
        )

    @Composable
    override fun Content(parameters: OnKeyStrokeKeyStrokeParameters): Unit = Content(parameters.keyStroke)

    /** Composes a [Button] whose [onKeyStroke] binding is [keyStroke], with no action of its own. */
    @Composable
    public fun Content(keyStroke: KeyStroke) {
        Button(
            text = TEXT,
            onClick = onClick,
            modifier = SwingModifier.onKeyStroke(keyStroke = keyStroke, onAction = {}),
        )
    }

    /** Builds a [JButton] showing the same text, with the click listener [Button] installs for its `onClick`. */
    override fun createRawSwing(): JButton = JButton(TEXT).apply { addActionListener { onClick() } }

    /** Binds the key-stroke of [parameters] in the focused scope to a no-op action, replacing any earlier binding. */
    override fun updateRawSwing(
        component: JButton,
        parameters: OnKeyStrokeKeyStrokeParameters,
    ) {
        val actionKey = "onKeyStroke"
        removeBindings(component, actionKey)
        component.getInputMap(JComponent.WHEN_FOCUSED).put(parameters.keyStroke, actionKey)
        component.actionMap.put(
            actionKey,
            object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) = Unit
            },
        )
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.keyboard

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke

/** The parameters [OnKeyStrokeFixture] varies: the key-stroke descriptor, its focus scope and the action it runs. */
public data class OnKeyStrokeParameters(
    val keyStroke: String,
    val condition: Int,
    val onAction: () -> Unit,
)

/**
 * A [Button] with an [onKeyStroke] binding declared from a key-stroke descriptor, against a raw [JButton] carrying
 * the same binding made by hand.
 */
public object OnKeyStrokeFixture : ComposableFixture<OnKeyStrokeParameters, JButton>(
    initial = OnKeyStrokeParameters(keyStroke = "ctrl A", condition = JComponent.WHEN_FOCUSED, onAction = {}),
) {
    /** The text both sides show on the button. */
    private const val TEXT = "Event"

    /** The click handler both sides run for the button's own action. */
    private val onClick: () -> Unit = {}

    /** The action that [OnKeyStrokeParameters.onAction] is swapped to in [alternatives]. */
    private val alternateOnAction: () -> Unit = {}

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<OnKeyStrokeParameters> =
        listOf(
            initial.copy(keyStroke = "ctrl B"),
            initial.copy(condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT),
            initial.copy(onAction = alternateOnAction),
        )

    @Composable
    override fun Content(parameters: OnKeyStrokeParameters): Unit =
        Content(parameters.keyStroke, parameters.condition, parameters.onAction)

    /** Composes a [Button] whose [onKeyStroke] binding for [keyStroke] in [condition] runs [onAction]. */
    @Composable
    public fun Content(
        keyStroke: String,
        condition: Int,
        onAction: () -> Unit,
    ) {
        Button(
            text = TEXT,
            onClick = onClick,
            modifier = SwingModifier.onKeyStroke(keyStroke = keyStroke, condition = condition, onAction = onAction),
        )
    }

    /** Builds a [JButton] showing the same text, with the click listener [Button] installs for its `onClick`. */
    override fun createRawSwing(): JButton = JButton(TEXT).apply { addActionListener { onClick() } }

    /**
     * Binds the key-stroke of [parameters] in its focus scope to an action running `onAction`. A change of only the
     * action keeps the button's bound action and replaces the callback it runs; a changed key-stroke or scope rebinds.
     */
    override fun updateRawSwing(
        component: JButton,
        parameters: OnKeyStrokeParameters,
    ) {
        val actionKey = "onKeyStroke"
        val stroke = requireNotNull(KeyStroke.getKeyStroke(parameters.keyStroke))
        val inputMap = component.getInputMap(parameters.condition)
        val bound = component.actionMap.get(actionKey)
        if (inputMap.get(stroke) == actionKey && bound is CallbackAction) {
            bound.onAction = parameters.onAction
            return
        }
        removeBindings(component, actionKey)
        inputMap.put(stroke, actionKey)
        component.actionMap.put(actionKey, CallbackAction(parameters.onAction))
    }
}

/** The action one button retains for its binding; [onAction] is the callback it runs, replaced without rebinding. */
private class CallbackAction(
    var onAction: () -> Unit,
) : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
        onAction()
    }
}

/** Removes from every focus scope of [component] the bindings that an earlier update installed under [actionKey]. */
internal fun removeBindings(
    component: JComponent,
    actionKey: String,
) {
    for (condition in listOf(
        JComponent.WHEN_FOCUSED,
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
        JComponent.WHEN_IN_FOCUSED_WINDOW,
    )) {
        val inputMap = component.getInputMap(condition)
        inputMap.allKeys()?.filter { inputMap.get(it) == actionKey }?.forEach { inputMap.remove(it) }
    }
}

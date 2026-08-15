@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.liveCallbackListener
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import javax.swing.JButton

/**
 * A composable wrapper for JButton.
 *
 * @param text the text to display on the button
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onClick callback to be invoked when the button is clicked
 * @see javax.swing.JButton
 */
@Composable
public fun Button(
    text: @Nls String,
    modifier: SwingModifier = SwingModifier,
    onClick: () -> Unit = {},
) {
    ButtonNode(text = text, modifier = modifier.onClick(onClick))
}

/**
 * A composable wrapper for JButton driven by a raw [ActionListener] instead of an `onClick` lambda.
 *
 * The [actionListener] is attached as-is and removed on the same instance; pass a stable instance
 * (e.g. `remember {}`) to avoid a detach/re-attach on every recomposition.
 *
 * @param text the text to display on the button
 * @param actionListener the listener notified when the button is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JButton
 */
@Composable
public fun Button(
    text: @Nls String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
) {
    ButtonNode(text = text, modifier = modifier.actionListener(actionListener))
}

/** The `JButton` node both [Button] overloads render; [modifier] already carries the button's wiring. */
@Composable
private fun ButtonNode(
    text: @Nls String,
    modifier: SwingModifier,
) {
    SwingNode(
        factory = { JButton() },
        update = {
            set(text) { this.text = it }
            applyModifier(modifier)
        },
    )
}

/** Installs one [ActionListener] calling the [onClick] the current composition declares. */
private fun SwingModifier.onClick(onClick: () -> Unit): SwingModifier =
    liveCallbackListener<AbstractButton, () -> Unit, ActionListener>(
        callback = onClick,
        adapter = { current -> ActionListener { current().invoke() } },
        attach = { button, listener -> button.addActionListener(listener) },
        detach = { button, listener -> button.removeActionListener(listener) },
    )

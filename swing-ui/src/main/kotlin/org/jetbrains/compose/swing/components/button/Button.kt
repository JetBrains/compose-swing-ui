@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ActionListener
import javax.swing.JButton

/**
 * A `JButton` push button labeled [text], which calls [onClick] each time the button is activated.
 * Activation is an event and nothing more: the button carries no value for the caller to hold.
 *
 * @param text the text to display on the button
 * @param onClick callback to be invoked when the button is clicked
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JButton
 */
@Composable
public fun Button(
    text: @Nls String,
    onClick: () -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    ButtonNode(text = text, modifier = modifier.actionListener { onClick() })
}

/**
 * A [Button] driven by a raw [ActionListener] instead of an `onClick` lambda.
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

/**
 * The `JButton` node both [Button] overloads render; [modifier] already carries the button's wiring.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun ButtonNode(
    text: @Nls String,
    modifier: SwingModifier,
) {
    SwingNode(
        factory = { JButton() },
        modifier = modifier,
        update = {
            set(text) { this.text = it }
        },
    )
}

@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import java.awt.event.ActionListener
import javax.swing.JButton

/**
 * A composable wrapper for JButton.
 *
 * @param text the text to display on the button
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onClick callback to be invoked when the button is clicked
 */
@Composable
public fun Button(
    text: String,
    modifier: SwingModifier = SwingModifier,
    onClick: () -> Unit = {},
) {
    val callback = rememberUpdatedState(onClick)
    val listener = remember { ActionListener { callback.value() } }
    Button(text = text, actionListener = listener, modifier = modifier)
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
 */
@Composable
public fun Button(
    text: String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { JButton() },
        update = {
            set(text) { this.text = it }
            applyModifier(modifier.actionListener(actionListener))
        },
    )
}

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
import javax.swing.JRadioButton

/**
 * A composable wrapper for JRadioButton.
 *
 * @param text the text to display next to the radio button
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selected whether the radio button is selected
 * @param onSelect callback invoked when the radio button is selected
 */
@Composable
public fun RadioButton(
    text: String,
    modifier: SwingModifier = SwingModifier,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
) {
    val callback = rememberUpdatedState(onSelect)
    val listener =
        remember { ActionListener { event -> if ((event.source as JRadioButton).isSelected) callback.value() } }
    RadioButton(text = text, actionListener = listener, modifier = modifier, selected = selected)
}

/**
 * A composable wrapper for JRadioButton driven by a raw [ActionListener] instead of an `onSelect`
 * lambda. The [actionListener] is attached as-is and removed on the same instance; pass a stable
 * instance (e.g. `remember {}`) to avoid churn.
 *
 * @param text the text to display next to the radio button
 * @param actionListener the listener notified when the radio button is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selected whether the radio button is selected
 */
@Composable
public fun RadioButton(
    text: String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    selected: Boolean = false,
) {
    SwingNode(
        factory = { JRadioButton() },
        update = {
            set(text) { this.text = it }
            set(selected) { this.isSelected = it }
            applyModifier(modifier.actionListener(actionListener))
        },
    )
}

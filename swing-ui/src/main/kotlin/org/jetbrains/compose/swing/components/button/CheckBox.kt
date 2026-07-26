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
import javax.swing.JCheckBox

/**
 * A composable wrapper for JCheckBox.
 *
 * @param text the text to display next to the checkbox
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param checked whether the checkbox is checked
 * @param onCheckedChange callback invoked when the checked state changes
 */
@Composable
public fun CheckBox(
    text: String,
    modifier: SwingModifier = SwingModifier,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    val callback = rememberUpdatedState(onCheckedChange)
    val listener = remember { ActionListener { event -> callback.value((event.source as JCheckBox).isSelected) } }
    CheckBox(text = text, actionListener = listener, modifier = modifier, checked = checked)
}

/**
 * A composable wrapper for JCheckBox driven by a raw [ActionListener] instead of an `onCheckedChange`
 * lambda. The [actionListener] is attached as-is and removed on the same instance; pass a stable
 * instance (e.g. `remember {}`) to avoid churn.
 *
 * @param text the text to display next to the checkbox
 * @param actionListener the listener notified when the checkbox is toggled
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param checked whether the checkbox is checked
 */
@Composable
public fun CheckBox(
    text: String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    checked: Boolean = false,
) {
    SwingNode(
        factory = { JCheckBox() },
        update = {
            set(text) { this.text = it }
            set(checked) { this.isSelected = it }
            applyModifier(modifier.actionListener(actionListener))
        },
    )
}

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
import javax.swing.JToggleButton

/**
 * A composable wrapper for `JToggleButton`, a two-state button that stays pressed until clicked again.
 *
 * The pressed state is controlled via [pressed] + [onPressedChange]: the button shows whatever
 * [pressed] holds, and a click toggles it, reporting the new state through [onPressedChange]. A state
 * the caller pushes in is reflected without echoing back through the callback.
 *
 * ```
 * ToggleButton(text = "Bold", pressed = bold, onPressedChange = { bold = it })
 * ```
 *
 * @param text the text to display on the button
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param pressed whether the button is in its pressed (selected) state
 * @param onPressedChange callback invoked with the new pressed state when the button is toggled
 */
@Composable
public fun ToggleButton(
    text: String,
    modifier: SwingModifier = SwingModifier,
    pressed: Boolean = false,
    onPressedChange: (Boolean) -> Unit = {},
) {
    val callback = rememberUpdatedState(onPressedChange)
    val listener = remember { ActionListener { event -> callback.value((event.source as JToggleButton).isSelected) } }
    ToggleButton(text = text, actionListener = listener, modifier = modifier, pressed = pressed)
}

/**
 * A [ToggleButton] driven by a raw [ActionListener] instead of an `onPressedChange` lambda. The
 * listener is attached as-is and removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param text the text to display on the button
 * @param actionListener the listener notified when the button is toggled
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param pressed whether the button is in its pressed (selected) state
 */
@Composable
public fun ToggleButton(
    text: String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    pressed: Boolean = false,
) {
    SwingNode(
        factory = { JToggleButton() },
        update = {
            set(text) { this.text = it }
            set(pressed) { if (this.isSelected != it) this.isSelected = it }
            applyModifier(modifier.actionListener(actionListener))
        },
    )
}

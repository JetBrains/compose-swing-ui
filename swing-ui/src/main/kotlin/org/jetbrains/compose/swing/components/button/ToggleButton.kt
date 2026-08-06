@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.awt.event.ActionListener
import java.awt.event.ItemListener
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
    val applied = rememberAppliedValue(pressed)
    // The button publishes its new state for every toggle, its own and the user's alike. The binding
    // answers which is which by value: a toggle that lands on the declaration is the declaration arriving.
    val listener =
        remember(applied) {
            ActionListener { event ->
                val isPressed = (event.source as JToggleButton).isSelected
                if (applied.observed(isPressed)) callback.value(isPressed)
            }
        }
    ToggleButtonNode(
        text = text,
        modifier = modifier.actionListener(listener),
        pressed = pressed,
        applied = applied,
    )
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
    val applied = rememberAppliedValue(pressed)
    // The caller's listener is attached as-is, and is the only action listener on the button. What is
    // applied watches the button's own value channel instead, so a toggle the caller does not adopt is
    // still put back without the wrapper taking a slot on the channel the caller was handed.
    val observing =
        remember(applied) {
            ItemListener { event -> applied.observed((event.source as JToggleButton).isSelected) }
        }
    ToggleButtonNode(
        text = text,
        modifier =
            modifier
                .actionListener(actionListener)
                .listener<JToggleButton, ItemListener>(
                    observing,
                    { c, l -> c.addItemListener(l) },
                    { c, l -> c.removeItemListener(l) },
                ),
        pressed = pressed,
        applied = applied,
    )
}

/**
 * The `JToggleButton` node both [ToggleButton] overloads render. [pressed] is settled against the button
 * through [applied] rather than applied on change: the user can toggle the button out from under the
 * declaration, and a declaration equal to the last one still has to stand.
 */
@Composable
private fun ToggleButtonNode(
    text: String,
    modifier: SwingModifier,
    pressed: Boolean,
    applied: AppliedValue<Boolean>,
) {
    SwingNode(
        factory = { JToggleButton() },
        update = {
            set(text) { this.text = it }
            declare(pressed, applied, JToggleButton::isSelected, JToggleButton::setSelected)
            applyModifier(modifier)
        },
    )
}

@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.AppliedValue
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.declare
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.rememberAppliedValue
import java.awt.event.ActionListener
import java.awt.event.ItemListener
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
    val applied = rememberAppliedValue(selected)
    // The button publishes its new state for every activation, its own and the user's alike. The binding
    // answers which is which by value: a move that lands on the declaration is the declaration arriving.
    val listener =
        remember(applied) {
            ActionListener { event ->
                val isSelected = (event.source as JRadioButton).isSelected
                if (applied.observed(isSelected) && isSelected) callback.value()
            }
        }
    RadioButtonNode(
        text = text,
        modifier = modifier.actionListener(listener),
        selected = selected,
        applied = applied,
    )
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
    val applied = rememberAppliedValue(selected)
    // The caller's listener is attached as-is, and is the only action listener on the button. What is
    // applied watches the button's own value channel instead, so a move the caller does not adopt is
    // still put back without the wrapper taking a slot on the channel the caller was handed.
    val observing =
        remember(applied) {
            ItemListener { event -> applied.observed((event.source as JRadioButton).isSelected) }
        }
    RadioButtonNode(
        text = text,
        modifier =
            modifier
                .actionListener(actionListener)
                .listener<JRadioButton, ItemListener>(
                    observing,
                    { c, l -> c.addItemListener(l) },
                    { c, l -> c.removeItemListener(l) },
                ),
        selected = selected,
        applied = applied,
    )
}

/**
 * The `JRadioButton` node both [RadioButton] overloads render. [selected] is settled against the button
 * through [applied] rather than applied on change: the user can move the button out from under the
 * declaration, and a declaration equal to the last one still has to stand.
 */
@Composable
private fun RadioButtonNode(
    text: String,
    modifier: SwingModifier,
    selected: Boolean,
    applied: AppliedValue<Boolean>,
) {
    SwingNode(
        factory = { JRadioButton() },
        update = {
            set(text) { this.text = it }
            declare(selected, applied, JRadioButton::isSelected, JRadioButton::setSelected)
            applyModifier(modifier)
        },
    )
}

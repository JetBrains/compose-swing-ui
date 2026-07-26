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
import javax.swing.JCheckBox

/**
 * A composable wrapper for JCheckBox.
 *
 * @param text the text to display next to the checkbox
 * @param checked whether the checkbox is checked
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onCheckedChange callback invoked when the checked state changes
 */
@Composable
public fun CheckBox(
    text: String,
    checked: Boolean,
    modifier: SwingModifier = SwingModifier,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    val callback = rememberUpdatedState(onCheckedChange)
    val applied = rememberAppliedValue(checked)
    // The box publishes its new value for every toggle, its own and the user's alike. The binding answers
    // which is which by value: a toggle that lands on the declaration is the declaration arriving.
    val listener =
        remember(applied) {
            ActionListener { event ->
                val selected = (event.source as JCheckBox).isSelected
                if (applied.observed(selected)) callback.value(selected)
            }
        }
    CheckBoxNode(
        text = text,
        modifier = modifier.actionListener(listener),
        checked = checked,
        applied = applied,
    )
}

/**
 * A composable wrapper for JCheckBox driven by a raw [ActionListener] instead of an `onCheckedChange`
 * lambda. The [actionListener] is attached as-is and removed on the same instance; pass a stable
 * instance (e.g. `remember {}`) to avoid churn.
 *
 * @param text the text to display next to the checkbox
 * @param actionListener the listener notified when the checkbox is toggled
 * @param checked whether the checkbox is checked
 * @param modifier the [SwingModifier] applied to the underlying component
 */
@Composable
public fun CheckBox(
    text: String,
    actionListener: ActionListener,
    checked: Boolean,
    modifier: SwingModifier = SwingModifier,
) {
    val applied = rememberAppliedValue(checked)
    // The caller's listener is attached as-is, and is the only action listener on the box. What is applied
    // watches the box's own value channel instead, so a toggle the caller does not adopt is still put back
    // without the wrapper taking a slot on the channel the caller was handed.
    val observing =
        remember(applied) {
            ItemListener { event -> applied.observed((event.source as JCheckBox).isSelected) }
        }
    CheckBoxNode(
        text = text,
        modifier =
            modifier
                .actionListener(actionListener)
                .listener<JCheckBox, ItemListener>(
                    observing,
                    { c, l -> c.addItemListener(l) },
                    { c, l -> c.removeItemListener(l) },
                ),
        checked = checked,
        applied = applied,
    )
}

/**
 * The `JCheckBox` node both [CheckBox] overloads render. [checked] is settled against the box through
 * [applied] rather than applied on change: the user can toggle the box out from under the declaration, and
 * a declaration equal to the last one still has to stand.
 */
@Composable
private fun CheckBoxNode(
    text: String,
    modifier: SwingModifier,
    checked: Boolean,
    applied: AppliedValue<Boolean>,
) {
    SwingNode(
        factory = { JCheckBox() },
        update = {
            set(text) { this.text = it }
            declare(checked, applied, JCheckBox::isSelected, JCheckBox::setSelected)
            applyModifier(modifier)
        },
    )
}

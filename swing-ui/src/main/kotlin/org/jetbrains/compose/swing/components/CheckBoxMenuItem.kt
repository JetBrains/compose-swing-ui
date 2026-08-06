@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.awt.event.ActionListener
import java.awt.event.ItemListener
import javax.swing.JCheckBoxMenuItem
import javax.swing.KeyStroke

/**
 * A composable wrapper for JCheckBoxMenuItem.
 *
 * @param text the text of the menu item
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param checked whether the menu item is checked
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @param onCheckedChange callback invoked when the checked state changes
 * @see javax.swing.JCheckBoxMenuItem
 */
@Composable
public fun CheckBoxMenuItem(
    text: String,
    modifier: SwingModifier = SwingModifier,
    checked: Boolean = false,
    accelerator: KeyStroke? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    val callback = rememberUpdatedState(onCheckedChange)
    val applied = rememberAppliedValue(checked)
    // The item publishes its new value for every toggle, its own and the user's alike. The binding answers
    // which is which by value: a toggle that lands on the declaration is the declaration arriving.
    val listener =
        remember(applied) {
            ActionListener { event ->
                val selected = (event.source as JCheckBoxMenuItem).isSelected
                if (applied.observed(selected)) callback.value(selected)
            }
        }
    CheckBoxMenuItemNode(
        text = text,
        modifier = modifier.actionListener(listener),
        checked = checked,
        accelerator = accelerator,
        applied = applied,
    )
}

/**
 * A JCheckBoxMenuItem driven by a raw [ActionListener] instead of an `onCheckedChange` lambda. The
 * listener is attached as-is and removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param text the text of the menu item
 * @param actionListener the listener notified when the item is toggled
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param checked whether the menu item is checked
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JCheckBoxMenuItem
 */
@Composable
public fun CheckBoxMenuItem(
    text: String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    checked: Boolean = false,
    accelerator: KeyStroke? = null,
) {
    val applied = rememberAppliedValue(checked)
    // The caller's listener is attached as-is, and is the only action listener on the item. What is applied
    // watches the item's own value channel instead, so a toggle the caller does not adopt is still put back
    // without the wrapper taking a slot on the channel the caller was handed.
    val observing =
        remember(applied) {
            ItemListener { event -> applied.observed((event.source as JCheckBoxMenuItem).isSelected) }
        }
    CheckBoxMenuItemNode(
        text = text,
        modifier =
            modifier
                .actionListener(actionListener)
                .itemListener(observing),
        checked = checked,
        accelerator = accelerator,
        applied = applied,
    )
}

/**
 * The `JCheckBoxMenuItem` node both [CheckBoxMenuItem] overloads render. [checked] is settled against the
 * item through [applied] rather than applied on change: the user can toggle the item out from under the
 * declaration, and a declaration equal to the last one still has to stand.
 */
@Composable
private fun CheckBoxMenuItemNode(
    text: String,
    modifier: SwingModifier,
    checked: Boolean,
    accelerator: KeyStroke?,
    applied: AppliedValue<Boolean>,
) {
    MenuNode(
        factory = { JCheckBoxMenuItem() },
        update = {
            set(text) { this.text = it }
            declare(checked, applied, JCheckBoxMenuItem::isSelected, JCheckBoxMenuItem::setSelected)
            set(accelerator) { this.accelerator = it }
            applyModifier(modifier)
        },
    )
}

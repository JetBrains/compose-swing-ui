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
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke

/**
 * A composable wrapper for JRadioButtonMenuItem.
 *
 * @param text the text of the menu item
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selected whether the menu item is selected
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @param onSelectedChange callback invoked with the new selected state when the item is activated
 * @see javax.swing.JRadioButtonMenuItem
 */
@Composable
public fun RadioButtonMenuItem(
    text: String,
    modifier: SwingModifier = SwingModifier,
    selected: Boolean = false,
    accelerator: KeyStroke? = null,
    onSelectedChange: (Boolean) -> Unit = {},
) {
    val callback = rememberUpdatedState(onSelectedChange)
    val applied = rememberAppliedValue(selected)
    // The item publishes its new state for every activation, its own and the user's alike. The binding
    // answers which is which by value: a move that lands on the declaration is the declaration arriving.
    val listener =
        remember(applied) {
            ActionListener { event ->
                val isSelected = (event.source as JRadioButtonMenuItem).isSelected
                if (applied.observed(isSelected)) callback.value(isSelected)
            }
        }
    RadioButtonMenuItemNode(
        text = text,
        modifier = modifier.actionListener(listener),
        selected = selected,
        accelerator = accelerator,
        applied = applied,
    )
}

/**
 * A JRadioButtonMenuItem driven by a raw [ActionListener] instead of an `onSelectedChange` lambda. The
 * listener is attached as-is and removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param text the text of the menu item
 * @param actionListener the listener notified when the item is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selected whether the menu item is selected
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JRadioButtonMenuItem
 */
@Composable
public fun RadioButtonMenuItem(
    text: String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    selected: Boolean = false,
    accelerator: KeyStroke? = null,
) {
    val applied = rememberAppliedValue(selected)
    // The caller's listener is attached as-is, and is the only action listener on the item. What is applied
    // watches the item's own value channel instead, so a move the caller does not adopt is still put back
    // without the wrapper taking a slot on the channel the caller was handed.
    val observing =
        remember(applied) {
            ItemListener { event -> applied.observed((event.source as JRadioButtonMenuItem).isSelected) }
        }
    RadioButtonMenuItemNode(
        text = text,
        modifier =
            modifier
                .actionListener(actionListener)
                .itemListener(observing),
        selected = selected,
        accelerator = accelerator,
        applied = applied,
    )
}

/**
 * The `JRadioButtonMenuItem` node both [RadioButtonMenuItem] overloads render. [selected] is settled
 * against the item through [applied] rather than applied on change: the user can move the item out from
 * under the declaration, and a declaration equal to the last one still has to stand.
 */
@Composable
private fun RadioButtonMenuItemNode(
    text: String,
    modifier: SwingModifier,
    selected: Boolean,
    accelerator: KeyStroke?,
    applied: AppliedValue<Boolean>,
) {
    MenuNode(
        factory = { JRadioButtonMenuItem() },
        update = {
            set(text) { this.text = it }
            declare(selected, applied, JRadioButtonMenuItem::isSelected, JRadioButtonMenuItem::setSelected)
            set(accelerator) { this.accelerator = it }
            applyModifier(modifier)
        },
    )
}

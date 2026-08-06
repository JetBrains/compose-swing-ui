@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.declareSelected
import org.jetbrains.compose.swing.components.button.rememberToggleMirroring
import org.jetbrains.compose.swing.components.button.rememberToggleReporting
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.MenuNode
import java.awt.event.ActionListener
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
    val (reporting, applied) = rememberToggleReporting(selected, onSelectedChange)
    RadioButtonMenuItemNode(
        text = text,
        modifier = modifier.then(reporting),
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
    val (mirroring, applied) = rememberToggleMirroring(selected, actionListener)
    RadioButtonMenuItemNode(
        text = text,
        modifier = modifier.then(mirroring),
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
            declareSelected(selected, applied)
            set(accelerator) { this.accelerator = it }
            applyModifier(modifier)
        },
    )
}

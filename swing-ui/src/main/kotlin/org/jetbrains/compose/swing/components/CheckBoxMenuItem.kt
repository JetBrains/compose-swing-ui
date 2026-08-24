@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.button.declareSelected
import org.jetbrains.compose.swing.components.button.rememberToggleMirroring
import org.jetbrains.compose.swing.components.button.rememberToggleReporting
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.MenuNode
import java.awt.event.ActionListener
import javax.swing.JCheckBoxMenuItem
import javax.swing.KeyStroke

/**
 * A composable wrapper for JCheckBoxMenuItem.
 *
 * @param text the text of the menu item
 * @param checked whether the menu item is checked
 * @param onCheckedChange callback invoked when the checked state changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JCheckBoxMenuItem
 */
@Composable
public fun CheckBoxMenuItem(
    text: @Nls String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
    accelerator: KeyStroke? = null,
) {
    val (reporting, applied) = rememberToggleReporting(checked, onCheckedChange)
    CheckBoxMenuItemNode(
        text = text,
        modifier = modifier.then(reporting),
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
 * @param checked whether the menu item is checked
 * @param actionListener the listener notified when the item is toggled
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JCheckBoxMenuItem
 */
@Composable
public fun CheckBoxMenuItem(
    text: @Nls String,
    checked: Boolean,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    accelerator: KeyStroke? = null,
) {
    val (mirroring, applied) = rememberToggleMirroring(checked, actionListener)
    CheckBoxMenuItemNode(
        text = text,
        modifier = modifier.then(mirroring),
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
    text: @Nls String,
    modifier: SwingModifier,
    checked: Boolean,
    accelerator: KeyStroke?,
    applied: AppliedValue<Boolean>,
) {
    MenuNode(
        factory = { JCheckBoxMenuItem() },
        update = {
            set(text) { this.text = it }
            declareSelected(checked, applied)
            set(accelerator) { this.accelerator = it }
            applyModifier(modifier)
        },
    )
}

@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import java.awt.event.ActionListener
import javax.swing.JCheckBoxMenuItem
import javax.swing.KeyStroke

/**
 * A menu item carrying a checkmark: a `JCheckBoxMenuItem` that shows [checked] and reports the user's
 * toggle through [onCheckedChange].
 *
 * A toggle the caller does not answer with a matching [checked] is settled back onto the declared value,
 * so the mark on screen is the one the composition holds.
 *
 * @param text the text of the menu item
 * @param checked whether the item shows its checkmark
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
    val mirror = rememberMirrorState(checked)
    CheckBoxMenuItemNode(
        text = text,
        modifier =
            modifier.actionListener<JCheckBoxMenuItem> {
                mirror.report(isSelected, onCheckedChange)
            },
        checked = checked,
        accelerator = accelerator,
        mirror = mirror,
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
    val mirror = rememberMirrorState(checked)
    CheckBoxMenuItemNode(
        text = text,
        modifier =
            modifier
                .actionListener(actionListener)
                .itemListener<JCheckBoxMenuItem> { mirror.observed(isSelected) },
        checked = checked,
        accelerator = accelerator,
        mirror = mirror,
    )
}

/**
 * The `JCheckBoxMenuItem` node both [CheckBoxMenuItem] overloads render. [checked] is settled against the
 * item through [mirror] rather than applied on change: the user can toggle the item out from under the
 * declaration, and a declaration equal to the last one still has to stand.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun CheckBoxMenuItemNode(
    text: @Nls String,
    modifier: SwingModifier,
    checked: Boolean,
    accelerator: KeyStroke?,
    mirror: MirrorState<Boolean>,
) {
    MenuNode(
        factory = { JCheckBoxMenuItem() },
        modifier = modifier,
        update = {
            set(text) { this.text = it }
            declare(checked, mirror, JCheckBoxMenuItem::isSelected, JCheckBoxMenuItem::setSelected)
            set(accelerator) { this.accelerator = it }
        },
    )
}

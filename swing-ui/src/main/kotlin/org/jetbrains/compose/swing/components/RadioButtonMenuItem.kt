@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import java.awt.event.ActionListener
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke

/**
 * A composable wrapper for JRadioButtonMenuItem.
 *
 * @param text the text of the menu item
 * @param selected whether the menu item is selected
 * @param onSelectedChange callback invoked with the new selected state when the item is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JRadioButtonMenuItem
 */
@Composable
public fun RadioButtonMenuItem(
    text: @Nls String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
    accelerator: KeyStroke? = null,
) {
    val mirror = rememberMirrorState(selected)
    RadioButtonMenuItemNode(
        text = text,
        modifier =
            modifier.actionListener<JRadioButtonMenuItem> {
                mirror.report(isSelected, onSelectedChange)
            },
        selected = selected,
        accelerator = accelerator,
        mirror = mirror,
    )
}

/**
 * A JRadioButtonMenuItem driven by a raw [ActionListener] instead of an `onSelectedChange` lambda. The
 * listener is attached as-is and removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param text the text of the menu item
 * @param selected whether the menu item is selected
 * @param actionListener the listener notified when the item is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JRadioButtonMenuItem
 */
@Composable
public fun RadioButtonMenuItem(
    text: @Nls String,
    selected: Boolean,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    accelerator: KeyStroke? = null,
) {
    val mirror = rememberMirrorState(selected)
    RadioButtonMenuItemNode(
        text = text,
        modifier =
            modifier
                .actionListener(actionListener)
                .itemListener<JRadioButtonMenuItem> { mirror.observed(isSelected) },
        selected = selected,
        accelerator = accelerator,
        mirror = mirror,
    )
}

/**
 * The `JRadioButtonMenuItem` node both [RadioButtonMenuItem] overloads render. [selected] is settled
 * against the item through [mirror] rather than applied on change: the user can take the item out from
 * under the declaration, and a declaration equal to the last one still has to stand.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun RadioButtonMenuItemNode(
    text: @Nls String,
    modifier: SwingModifier,
    selected: Boolean,
    accelerator: KeyStroke?,
    mirror: MirrorState<Boolean>,
) {
    MenuNode(
        factory = { JRadioButtonMenuItem() },
        update = {
            set(text) { this.text = it }
            declare(selected, mirror, JRadioButtonMenuItem::isSelected, JRadioButtonMenuItem::setSelected)
            set(accelerator) { this.accelerator = it }
            applyModifier(modifier)
        },
    )
}

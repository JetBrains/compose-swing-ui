@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.MenuNode
import java.awt.event.ActionListener
import javax.swing.JMenuItem
import javax.swing.KeyStroke

/**
 * A composable wrapper for JMenuItem.
 *
 * @param text the text of the menu item
 * @param onClick callback to be invoked when the menu item is clicked
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JMenuItem
 */
@Composable
public fun MenuItem(
    text: @Nls String,
    onClick: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    accelerator: KeyStroke? = null,
) {
    MenuItemNode(text = text, accelerator = accelerator, modifier = modifier.actionListener { onClick() })
}

/**
 * A JMenuItem driven by a raw [ActionListener] instead of an `onClick` lambda. The listener is
 * attached as-is and removed on the same instance; pass a stable instance (e.g. `remember {}`) to
 * avoid churn.
 *
 * @param text the text of the menu item
 * @param actionListener the listener notified when the item is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JMenuItem
 */
@Composable
public fun MenuItem(
    text: @Nls String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    accelerator: KeyStroke? = null,
) {
    MenuItemNode(
        text = text,
        accelerator = accelerator,
        modifier =
            modifier.listener(actionListener, MENU_ITEM_ACTION),
    )
}

/**
 * The `JMenuItem` node both [MenuItem] overloads render. [modifier] already carries the item's
 * activation channel, whichever of the two the overload driving it uses.
 */
@Composable
private fun MenuItemNode(
    text: @Nls String,
    accelerator: KeyStroke?,
    modifier: SwingModifier,
) {
    MenuNode(
        factory = { JMenuItem() },
        update = {
            set(text) { this.text = it }
            set(accelerator) { this.accelerator = it }
            applyModifier(modifier)
        },
    )
}

private val MENU_ITEM_ACTION =
    ListenerRegistration<JMenuItem, ActionListener>(
        { component, listener -> component.addActionListener(listener) },
        { component, listener -> component.removeActionListener(listener) },
    )

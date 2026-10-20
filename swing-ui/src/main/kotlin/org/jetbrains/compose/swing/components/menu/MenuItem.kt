@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.components.style.MenuItems
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import java.awt.event.ActionListener
import javax.swing.JMenuItem
import javax.swing.KeyStroke

/**
 * An item that performs an action when the user picks it: a `JMenuItem` reporting the pick through
 * [onClick].
 *
 * @param text the text of the menu item
 * @param onClick callback to be invoked when the menu item is clicked
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JMenuItem
 */
@Composable
public fun MenuItem(
    text: @Nls String,
    onClick: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ButtonStyleScope> = SwingStyle,
    accelerator: KeyStroke? = null,
) {
    MenuItemNode(
        text = text,
        accelerator = accelerator,
        modifier = modifier.actionListener { onClick() },
        style = style,
    )
}

/**
 * A JMenuItem driven by a raw [ActionListener] instead of an `onClick` lambda. The listener is
 * attached as-is and removed on the same instance; pass a stable instance (e.g. `remember {}`) to
 * avoid churn.
 *
 * @param text the text of the menu item
 * @param actionListener the listener notified when the item is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JMenuItem
 */
@Composable
public fun MenuItem(
    text: @Nls String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ButtonStyleScope> = SwingStyle,
    accelerator: KeyStroke? = null,
) {
    MenuItemNode(
        text = text,
        accelerator = accelerator,
        modifier =
            modifier.listener(actionListener, MENU_ITEM_ACTION),
        style = style,
    )
}

private val MenuItemKind =
    nestingStyleKind<ButtonStyleScope, Unit>({ base, _ -> object : ButtonStyleScope, ComponentStyleScope by base {} }) {
        applyNestedStyle(AllComponents)
        applyNestedStyle(MenuItems)
    }

/**
 * The `JMenuItem` node both [MenuItem] overloads render. [modifier] already carries the item's
 * activation channel, whichever of the two the overload driving it uses.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun MenuItemNode(
    text: @Nls String,
    accelerator: KeyStroke?,
    modifier: SwingModifier,
    style: SwingStyle<ButtonStyleScope>,
) {
    MenuNode(
        factory = { JMenuItem() },
        modifier = modifier.styleable(style, MenuItemKind),
        update = {
            set(text) { this.text = it }
            set(accelerator) { this.accelerator = it }
        },
    )
}

private val MENU_ITEM_ACTION =
    ListenerRegistration<JMenuItem, ActionListener>(
        name = "actionListener",
        { component, listener -> component.addActionListener(listener) },
        { component, listener -> component.removeActionListener(listener) },
    )

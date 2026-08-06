@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.listener
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
 * @param onSelect callback invoked when the menu item is selected
 */
@Composable
public fun RadioButtonMenuItem(
    text: String,
    modifier: SwingModifier = SwingModifier,
    selected: Boolean = false,
    accelerator: KeyStroke? = null,
    onSelect: () -> Unit = {},
) {
    val callback = rememberUpdatedState(onSelect)
    val listener =
        remember { ActionListener { event -> if ((event.source as JRadioButtonMenuItem).isSelected) callback.value() } }
    RadioButtonMenuItem(
        text = text,
        actionListener = listener,
        modifier = modifier,
        selected = selected,
        accelerator = accelerator,
    )
}

/**
 * A JRadioButtonMenuItem driven by a raw [ActionListener] instead of an `onSelect` lambda. The listener
 * is attached as-is and removed on the same instance; pass a stable instance (e.g. `remember {}`) to
 * avoid churn.
 *
 * @param text the text of the menu item
 * @param actionListener the listener notified when the item is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selected whether the menu item is selected
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 */
@Composable
public fun RadioButtonMenuItem(
    text: String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    selected: Boolean = false,
    accelerator: KeyStroke? = null,
) {
    MenuNode(
        factory = { JRadioButtonMenuItem() },
        update = {
            set(text) { this.text = it }
            set(selected) { this.isSelected = it }
            set(accelerator) { this.accelerator = it }
            applyModifier(
                modifier.listener<JRadioButtonMenuItem, ActionListener>(
                    actionListener,
                    { c, l -> c.addActionListener(l) },
                    { c, l -> c.removeActionListener(l) },
                ),
            )
        },
    )
}

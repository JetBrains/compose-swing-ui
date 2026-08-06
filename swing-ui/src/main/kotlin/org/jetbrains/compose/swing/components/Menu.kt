@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.MenuNode
import javax.swing.JMenu

/**
 * A composable wrapper for JMenu.
 *
 * @param text the text of the menu
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param content the composable content of the menu (menu items)
 * @see javax.swing.JMenu
 */
@Composable
public fun Menu(
    text: String,
    modifier: SwingModifier = SwingModifier,
    content:
        @Composable @SwingMenuComposable
        () -> Unit = {},
) {
    MenuNode(
        factory = { JMenu() },
        update = {
            set(text) { this.text = it }
            applyModifier(modifier)
        },
        content = content,
    )
}

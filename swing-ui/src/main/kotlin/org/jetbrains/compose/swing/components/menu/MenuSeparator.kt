@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.MenuNode
import javax.swing.JPopupMenu

/**
 * The rule a menu draws between groups of items: a `JPopupMenu.Separator`, which shows no text and is
 * not one of the items the menu's navigation moves through.
 *
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JPopupMenu.Separator
 */
@Composable
public fun MenuSeparator(modifier: SwingModifier = SwingModifier) {
    MenuNode(
        factory = { JPopupMenu.Separator() },
        modifier = modifier,
    )
}

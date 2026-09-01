@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.MenuNode
import javax.swing.JPopupMenu

/**
 * A composable wrapper for JPopupMenu.Separator, the separator a menu draws between its items.
 *
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JPopupMenu.Separator
 */
@Composable
public fun MenuSeparator(modifier: SwingModifier = SwingModifier) {
    MenuNode(
        factory = { JPopupMenu.Separator() },
        update = {
            applyModifier(modifier)
        },
    )
}

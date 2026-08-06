@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.MenuNode
import javax.swing.JSeparator

/**
 * A composable wrapper for JSeparator in menus.
 *
 * @param modifier the [SwingModifier] applied to the underlying component
 */
@Composable
public fun MenuSeparator(modifier: SwingModifier = SwingModifier) {
    MenuNode(
        factory = { JSeparator() },
        update = {
            applyModifier(modifier)
        },
    )
}

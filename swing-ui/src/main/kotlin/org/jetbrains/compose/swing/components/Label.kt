@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JLabel

/**
 * A composable wrapper for JLabel.
 *
 * @param text the text to display
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JLabel
 */
@Composable
public fun Label(
    text: String,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { JLabel() },
        update = {
            set(text) { this.text = it }
            applyModifier(modifier)
        },
    )
}

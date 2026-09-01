@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JLabel

/**
 * A `JLabel` showing [text]: non-interactive text, which the Tab focus cycle skips, though a focus
 * request still lands on it.
 *
 * @param text the text to display
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JLabel
 */
@Composable
public fun Label(
    text: @Nls String,
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

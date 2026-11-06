@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JLabel

/**
 * A `JLabel` showing [text]: non-interactive text, which the Tab focus cycle skips, though a focus
 * request still lands on it.
 *
 * @param text the text to display
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @see javax.swing.JLabel
 */
@Composable
public fun Label(
    text: @Nls String,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<LabelStyleScope> = SwingStyle,
) {
    SwingNode(
        factory = { JLabel() },
        modifier = modifier.styleable(style, LabelKind),
        update = {
            set(text) { this.text = it }
        },
    )
}

private val LabelKind =
    nestingStyleKind<LabelStyleScope, Unit>({ base, _ -> object : LabelStyleScope, ComponentStyleScope by base {} }) {
        applyNestedStyle(AllComponents)
        applyNestedStyle(Labels)
    }

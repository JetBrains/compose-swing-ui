@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.Orientation
import org.jetbrains.compose.swing.components.style.Separators
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JSeparator
import javax.swing.SwingConstants

/**
 * A `JSeparator`: the divider line that breaks the items of any container into groups. A tool bar takes
 * its own divider - see [org.jetbrains.compose.swing.components.layout.ToolBarSeparator].
 *
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param orientation the orientation of the separator (an [Orientation] `SwingConstants` value);
 *   `HORIZONTAL` by default, which draws the line across the width it is given, dividing items stacked
 *   one above the other
 * @see javax.swing.JSeparator
 */
@Composable
public fun Separator(
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
) {
    SwingNode(
        factory = { JSeparator(orientation) },
        modifier = modifier.styleable(style, SeparatorKind),
        update = {
            set(orientation) { this.orientation = it }
        },
    )
}

private val SeparatorKind =
    nestingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    ) {
        applyNestedStyle(AllComponents)
        applyNestedStyle(Separators)
    }

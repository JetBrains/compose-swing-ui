@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JSeparator
import javax.swing.SwingConstants

/**
 * A `JSeparator`: the divider line that breaks the items of any container into groups. A tool bar takes
 * its own divider - see [org.jetbrains.compose.swing.components.layout.ToolBarSeparator].
 *
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param orientation the orientation of the separator (an [Orientation] `SwingConstants` value);
 *   `HORIZONTAL` by default, which draws the line across the width it is given, dividing items stacked
 *   one above the other
 * @see javax.swing.JSeparator
 */
@Composable
public fun Separator(
    modifier: SwingModifier = SwingModifier,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
) {
    SwingNode(
        factory = { JSeparator(orientation) },
        update = {
            set(orientation) { this.orientation = it }
            applyModifier(modifier)
        },
    )
}

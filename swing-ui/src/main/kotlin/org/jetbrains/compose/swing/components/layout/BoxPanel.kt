@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.constants.BoxAxis
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * A composable wrapper for JPanel with BoxLayout.
 *
 * A `BoxLayout` puts no space between children on its own. Declare gaps as content: [RigidArea] and
 * [Strut] for a fixed gap, [Glue] for empty space that takes the largest share of what is left over.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param axis the axis along which children are arranged (a [BoxAxis] `BoxLayout` value)
 * @param content the composable content of the panel
 * @see javax.swing.BoxLayout
 */
@Composable
public fun BoxPanel(
    modifier: SwingModifier = SwingModifier,
    @BoxAxis axis: Int = BoxLayout.Y_AXIS,
    content: @Composable () -> Unit = {},
) {
    SwingNode(
        factory = { JPanel().apply { layout = BoxLayout(this, axis) } },
        update = {
            // A BoxLayout fixes its axis at construction and serves only the container it was built
            // for, so a new axis means a new instance for this panel. It holds no per-child data, so
            // recreating it loses nothing.
            update(axis) {
                layout = BoxLayout(this, it)
                revalidate()
            }
            applyModifier(modifier)
        },
        content = content,
    )
}

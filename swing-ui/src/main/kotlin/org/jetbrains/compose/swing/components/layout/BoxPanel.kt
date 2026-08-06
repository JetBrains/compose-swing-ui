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
 * A `BoxLayout` places children edge to edge, so gaps between them are declared as content:
 * [RigidArea] and [Strut] for a fixed gap, [Glue] to push what follows to the far end.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param axis the axis along which children are arranged (a [BoxAxis] `BoxLayout` value)
 * @param content the composable content of the panel
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
            // A BoxLayout takes its axis at construction and serves only the container it was built
            // for, so a new axis means a new instance for this panel. It carries no per-child data:
            // its size requirements are recomputed from the container's current children.
            update(axis) {
                layout = BoxLayout(this, it)
                revalidate()
            }
            applyModifier(modifier)
        },
        content = content,
    )
}

/**
 * A composable that arranges its [content] vertically, top to bottom.
 *
 * Convenience over [BoxPanel] with a vertical axis.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param content the composable content of the column
 */
@Composable
public fun Column(
    modifier: SwingModifier = SwingModifier,
    content: @Composable () -> Unit = {},
) {
    BoxPanel(modifier = modifier, axis = BoxLayout.Y_AXIS, content = content)
}

/**
 * A composable that arranges its [content] horizontally, left to right.
 *
 * Convenience over [BoxPanel] with a horizontal axis.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param content the composable content of the row
 */
@Composable
public fun Row(
    modifier: SwingModifier = SwingModifier,
    content: @Composable () -> Unit = {},
) {
    BoxPanel(modifier = modifier, axis = BoxLayout.X_AXIS, content = content)
}

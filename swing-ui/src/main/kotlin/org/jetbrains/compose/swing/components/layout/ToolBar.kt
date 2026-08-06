@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JToolBar
import javax.swing.SwingConstants
import javax.swing.UIManager

/**
 * A composable wrapper for `JToolBar`, hosting a row or column of items.
 *
 * The items declared in [content] become the tool bar's children in declaration order:
 * ```
 * ToolBar {
 *     Button(text = "New", onClick = { ... })
 *     ToolBarSeparator()
 *     Button(text = "Open", onClick = { ... })
 * }
 * ```
 *
 * A [Glue] among the items pushes the ones after it to the trailing end, which is how a tool bar gets
 * a trailing group.
 *
 * @param modifier the [SwingModifier] applied to the underlying `JToolBar`
 * @param orientation the axis along which items are laid out (an [Orientation] `SwingConstants` value)
 * @param floatable whether the user can drag the tool bar out into a floating window
 * @param rollover whether the look and feel draws an item's border only while the pointer is over it,
 *   or `null` to leave that choice to the look and feel; a choice withdrawn after being declared
 *   settles at its answer for good
 * @param content the items hosted by the tool bar
 */
@Composable
public fun ToolBar(
    modifier: SwingModifier = SwingModifier,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
    floatable: Boolean = true,
    rollover: Boolean? = null,
    content: @Composable () -> Unit = {},
) {
    SwingNode(
        factory = { JToolBar(orientation).also { bar -> rollover?.let { bar.isRollover = it } } },
        update = {
            set(orientation) { this.orientation = it }
            set(floatable) { this.isFloatable = it }
            update(rollover) { declared ->
                isRollover = declared ?: UIManager.getBoolean(ROLLOVER_DEFAULT)
            }
            applyModifier(modifier)
        },
        content = content,
    )
}

/** The look-and-feel default a tool bar's UI reads while the bar records no rollover choice of its own. */
private const val ROLLOVER_DEFAULT: String = "ToolBar.isRollover"

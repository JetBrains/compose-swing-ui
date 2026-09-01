@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Dimension
import javax.swing.JToolBar

/**
 * The divider that groups a [ToolBar]'s items - `JToolBar.Separator`. It takes its orientation from the
 * bar holding it, so it lies across the bar's own axis and turns with it. To divide anything else, use
 * [org.jetbrains.compose.swing.components.Separator].
 *
 * The separator takes its place among the tool bar's items in declaration order:
 * ```
 * ToolBar {
 *     Button(text = "New", onClick = { ... })
 *     ToolBarSeparator()
 *     Button(text = "Delete", onClick = { ... })
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param size the size of the separator; `null` by default, which leaves the size to the look and feel
 * @see javax.swing.JToolBar.Separator
 */
@Composable
public fun ToolBarSeparator(
    modifier: SwingModifier = SwingModifier,
    size: Dimension? = null,
) {
    // UIManager alone can't name this size: a look and feel swaps ToolBar.separatorSize between width
    // and height by the separator's own orientation, and only while the separator carries no size of
    // its own. Read it off a real separator's construction instead, before a declared size overrides it.
    var lookAndFeelSize by remember { mutableStateOf<Dimension?>(null) }
    SwingNode(
        factory = {
            JToolBar.Separator().also { separator ->
                lookAndFeelSize = separator.separatorSize
                size?.let { separator.separatorSize = it }
            }
        },
        update = {
            update(size) { declared ->
                if (declared != null) {
                    separatorSize = declared
                } else {
                    val lookAndFeelAnswer = lookAndFeelSize
                    if (lookAndFeelAnswer != null && separatorSize != lookAndFeelAnswer) {
                        separatorSize = lookAndFeelAnswer
                    }
                }
                // Sizing only invalidates the separator; ask for the layout pass that applies it.
                revalidate()
            }
            applyModifier(modifier)
        },
    )
}

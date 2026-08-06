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
 * A composable wrapper for JToolBar.Separator, the divider that belongs inside a [ToolBar]. To divide
 * anything else, use [org.jetbrains.compose.swing.components.Separator].
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
 * A tool bar turns the separators it hosts to match its own orientation, so a vertical tool bar draws
 * its separators across itself and a horizontal one draws them down itself.
 *
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param size the size of the separator; `null` by default, which leaves the size to the look and feel
 */
@Composable
public fun ToolBarSeparator(
    modifier: SwingModifier = SwingModifier,
    size: Dimension? = null,
) {
    // No UIManager default alone names this size - a look and feel swaps its ToolBar.separatorSize
    // default between width and height depending on the separator's own orientation, and only applies
    // it while the separator carries no size of its own - so the answer is read straight off the
    // separator's own construction, before a declared size overrides it, rather than off a widget built
    // solely to ask.
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
                // Sizing a separator only invalidates it, so ask for the layout pass that lets the size
                // take effect.
                revalidate()
            }
            applyModifier(modifier)
        },
    )
}

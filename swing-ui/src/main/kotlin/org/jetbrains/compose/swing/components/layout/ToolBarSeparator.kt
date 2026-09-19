@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
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
 * @param size the size of the separator; `null` by default, which leaves the size to the look and feel.
 *   Withdrawing a declared size hands back the size the look and feel gave the separator, where it gave
 *   one; a separator its look and feel named no size for keeps the last size declared
 * @see javax.swing.JToolBar.Separator
 */
@Composable
public fun ToolBarSeparator(
    modifier: SwingModifier = SwingModifier,
    size: Dimension? = null,
) {
    SwingNode(
        factory = { JToolBar.Separator() },
        modifier = modifier.declaredSeparatorSize(size),
    )
}

/**
 * The element restores nothing: the only way to ask a separator for no size is
 * `setSeparatorSize(null)`, and that rebuilds the separator's UI rather than clearing the size.
 * Rebuilding a UI part-way through a change pass is not this wrapper's to do.
 *
 * Sizing only invalidates the separator, so the write asks for the layout pass that applies it. The
 * restore writes through this same lambda and needs that pass too.
 */
private val SeparatorSizeProperty =
    ComponentPropertyDescriptor<JToolBar.Separator, Dimension?>(
        name = "separatorSize",
        read = { it.separatorSize },
        write = { separator, value ->
            if (value != null) {
                separator.separatorSize = value
                separator.revalidate()
            }
        },
    )

private fun SwingModifier.declaredSeparatorSize(size: Dimension?): SwingModifier =
    if (size == null) {
        this
    } else {
        property(SeparatorSizeProperty, size, restores = RestorePolicy.None)
    }

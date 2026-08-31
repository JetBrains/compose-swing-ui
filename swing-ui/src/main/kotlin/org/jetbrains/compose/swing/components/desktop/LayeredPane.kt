@file:JvmMultifileClass
@file:JvmName("DesktopComponentsKt")

package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JLayeredPane

/**
 * A container that stacks its [content] on integer depth layers rather than laying it out, realized as
 * a `JLayeredPane`.
 *
 * A child declares the depth it sits on with `layer`, through [LayeredPaneScope]; a child declaring none
 * stands on `JLayeredPane.DEFAULT_LAYER`. Within one layer, the children stack in the order the
 * composition declares them, the first of them on top. A layered pane lays no child out, so each child
 * positions itself with `SwingModifier.bounds(...)`:
 *
 * ```
 * LayeredPane {
 *     Background(modifier = SwingModifier.bounds(0, 0, 400, 300))
 *     FloatingToolbar(modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER).bounds(8, 8, 120, 32))
 * }
 * ```
 *
 * Adding or removing a child in the composition adds or removes it on the pane, and a child moves to
 * the layer it declares on recomposition.
 *
 * @param modifier the [SwingModifier] applied to the underlying `JLayeredPane`
 * @param content the composable content of the pane; see [LayeredPaneScope]
 * @see javax.swing.JLayeredPane
 */
@Composable
public fun LayeredPane(
    modifier: SwingModifier = SwingModifier,
    content: @Composable LayeredPaneScope.() -> Unit,
) {
    SwingNode(
        factory = { JLayeredPane() },
        modifier = modifier,
        content = { LayeredPaneScopeImpl.content() },
    )
}

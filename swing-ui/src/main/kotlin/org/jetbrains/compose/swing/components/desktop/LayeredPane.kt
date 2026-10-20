@file:JvmMultifileClass
@file:JvmName("DesktopComponentsKt")

package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.style.LayeredPanes
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.NestedStyleProviderScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JLayeredPane

/**
 * A container that stacks its [content] on integer depth layers rather than laying it out, realized as
 * a `JLayeredPane`.
 *
 * A child declares the depth it sits on with `layer`, through [LayeredPaneScope]; a child declaring none
 * stands on `JLayeredPane.DEFAULT_LAYER`. Within one layer, the children stack in the order the
 * composition declares them, the first of them on top. A layered pane lays no child out, so each child
 * positions itself with `bounds(...)` in its style:
 *
 * ```
 * LayeredPane {
 *     Background(style = { bounds(0, 0, 400, 300) })
 *     FloatingToolbar(modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER), style = { bounds(8, 8, 120, 32) })
 * }
 * ```
 *
 * Adding or removing a child in the composition adds or removes it on the pane, and a child moves to
 * the layer it declares on recomposition.
 *
 * @param modifier the [SwingModifier] applied to the underlying `JLayeredPane`
 * @param style the [SwingStyle] applied to the underlying `JLayeredPane`
 * @param content the composable content of the pane; see [LayeredPaneScope]
 * @see javax.swing.JLayeredPane
 */
@Composable
public fun LayeredPane(
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ContainerStyleScope> = SwingStyle,
    content: @Composable LayeredPaneScope.() -> Unit,
) {
    SwingNode(
        factory = { JLayeredPane() },
        modifier = modifier.styleable(LayeredPaneKind, style),
        content = { LayeredPaneScopeImpl.content() },
    )
}

/** [ContainerStyleScope] for LayeredPane styles. No component state is supplied. */
private val LayeredPaneKind =
    nestingStyleKind<ContainerStyleScope, Unit>(
        { base, _ -> object : ContainerStyleScope, NestedStyleProviderScope by base {} },
    ) {
        applyNestedStyle(LayeredPanes)
    }

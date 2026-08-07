package org.jetbrains.compose.swing.components.desktop

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import javax.swing.JLayeredPane

/**
 * The receiver of a [LayeredPane]'s content, through which a child declares the depth layer it sits on.
 *
 * Children are written plainly, and the depth a child declares through [layer] rides along on its
 * `modifier`. A child declaring no depth stands on `JLayeredPane.DEFAULT_LAYER`, so a pane whose children
 * share one depth is written without naming it:
 *
 * ```
 * LayeredPane {
 *     Background()
 *     FloatingToolbar(modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER))
 * }
 * ```
 *
 * A layer holds as many children as the composition puts on it, so two children naming one depth both
 * stand on the pane.
 *
 * @see javax.swing.JLayeredPane
 */
public sealed interface LayeredPaneScope {
    /**
     * Places the child on the depth [layer]. Higher values paint above lower ones: name a well-known
     * `JLayeredPane` layer (`JLayeredPane.DEFAULT_LAYER`, `PALETTE_LAYER`, `MODAL_LAYER`, `POPUP_LAYER`,
     * `DRAG_LAYER`) or any other integer depth.
     *
     * A depth is the layout constraint the pane places the child under, so the last placement a chain
     * declares wins, and a chain declaring none leaves the child on `JLayeredPane.DEFAULT_LAYER`. Within
     * one layer the children stack in the order the composition declares them, the first of them on top.
     * Declaring a different depth moves the child to the top of the layer it names, keeping what it
     * remembers and the component it is realized as.
     *
     * The child positions itself within the pane through `SwingModifier.bounds(...)`, since a layered
     * pane lays no child out.
     *
     * @param layer the depth the child is placed at
     * @see javax.swing.JLayeredPane.setLayer
     */
    public fun SwingModifier.layer(layer: Int): SwingModifier
}

/**
 * The [LayeredPaneScope] every [LayeredPane] hands its content. A depth reaches the pane on the child's
 * own chain, so the scope carries nothing of its own and one instance serves every pane.
 */
internal object LayeredPaneScopeImpl : LayeredPaneScope {
    override fun SwingModifier.layer(layer: Int): SwingModifier =
        this then layerDepth(layer) then layoutConstraint(layer)
}

/**
 * The depth a child already on a pane sits at, written through that pane - the one call that both records
 * the layer and restacks the component among the pane's children, putting it at the top of the layer it
 * moves to.
 *
 * A `JLayeredPane` has no layout manager, so the constraint a child declares reaches the pane as the pane
 * takes the child in and no later: this is what carries every change of depth after that, and with it the
 * restore that returns a child to the default layer once its chain stops declaring one. It writes nothing
 * while the component has no pane to be moved within, which is where the constraint itself answers.
 */
private fun layerDepth(layer: Int): SwingModifier =
    propertyElement<Component, Int>(
        layer,
        read = { component ->
            (component.parent as? JLayeredPane)?.getLayer(component) ?: JLayeredPane.DEFAULT_LAYER
        },
        write = { component, value ->
            (component.parent as? JLayeredPane)?.setLayer(component, value, 0)
        },
    )

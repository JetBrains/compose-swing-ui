package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.Composable

/**
 * Declarative children of a [LayeredPane], each placed on an integer depth layer.
 *
 * Each [layer] call appends one child on the given depth, in call order. Multiple children may share a
 * layer; higher layers paint above lower ones, and within one layer later-declared children paint above
 * earlier ones.
 *
 * @see javax.swing.JLayeredPane
 */
public sealed interface LayeredPaneScope {
    /**
     * Declares one child on the depth [layer]. Higher values paint above lower ones. Use a well-known
     * `JLayeredPane` layer (`JLayeredPane.DEFAULT_LAYER`, `PALETTE_LAYER`, `MODAL_LAYER`, `POPUP_LAYER`,
     * `DRAG_LAYER`) or any other integer depth.
     *
     * @param layer the depth at which the child is placed
     * @param content the composable hosted on that layer
     * @see javax.swing.JLayeredPane.setLayer
     */
    public fun layer(
        layer: Int,
        content: @Composable () -> Unit,
    )
}

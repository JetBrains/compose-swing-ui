package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * A component that paints through the decoration its modifier declares: what every decoration modifier and every
 * [DecorationModifierNode] target.
 *
 * The library writes [decoration]; the component stores it and applies it, as it applies its `Border`:
 * - `paint` answers `decoration.paint(this, g) { super.paint(it) }`;
 * - `paintBorder` paints the border at [Decoration.localLayoutBounds];
 * - both `getInsets` overloads answer through [Decoration.insets];
 * - `contains` answers [Decoration.contains];
 * - `isOpaque` answers `super.isOpaque() && decoration.isOpaque(this)`, and the component paints its background
 *   inside [Decoration.localLayoutBounds] where `super.isOpaque()`;
 * - a component with children also answers `isPaintingOrigin` with [Decoration.isDecorated], and grows the area
 *   `paintImmediately` is given by [Decoration.paintOutsets] on each side, as `JLayer` does.
 *
 * Its size getters answer as for any Swing component: a size worked out from its content includes `getInsets()`, and
 * a set size answers as set. Only a Foundation container gives it paint outsets. Under any other parent it has none,
 * and its decoration is clipped at its bounds.
 */
public interface Decoratable : DeclaredNodesListener {
    /**
     * The decoration this component paints through; [Decoration.None] until the library writes one.
     *
     * Only the library writes it, on the event dispatch thread, and only with a value that differs from the one held.
     * An implementation stores the value and does nothing else: after writing, the library repaints the component,
     * and where the paint outsets changed, its Foundation parent fits its bounds around its layout bounds.
     */
    public var decoration: Decoration

    /**
     * Gathers the decoration the nodes declare and writes it to [decoration]. Not to be overridden.
     *
     * @throws IllegalStateException if this `Decoratable` is not a `java.awt.Component`.
     */
    override fun onDeclaredNodesChanged(nodes: List<SwingModifier.Node>) {
        publishDecoration(this, nodes)
    }

    /**
     * Takes a written [DecorationModifierNode] other than a [DrawModifierNode], since its element may have changed
     * the node's `outsets` or `isOpaque`, and declines every other node. A draw node's `outsets` and `isOpaque`
     * never change, and it repaints its own change. Not to be overridden.
     */
    override fun needsNodesAfterWrite(node: SwingModifier.Node): Boolean =
        node is DecorationModifierNode<*> && node !is DrawModifierNode<*>
}

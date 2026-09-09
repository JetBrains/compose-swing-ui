package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * A component that paints through the decoration its modifier declares: what every decoration modifier and every
 * [DecorationModifierNode] target.
 *
 * The library writes [decoration]; the component stores it and applies it, as it applies its `Border`:
 * - `paint` answers `decoration.paint(this, g) { super.paint(it) }`;
 * - `contains` answers [Decoration.contains];
 * - `isOpaque` answers `super.isOpaque() && decoration.isOpaque(this)`;
 * - a component with children also answers `isPaintingOrigin` with [Decoration.isDecorated].
 *
 * Its size getters answer as for any Swing component: a size worked out from its content includes `getInsets()`, and
 * a set size answers as set. Its decoration is clipped at its bounds. See "Making a component decoratable" in
 * `docs/FOUNDATION.md`.
 */
public interface Decoratable : DeclaredNodesListener {
    /**
     * The decoration this component paints through; [Decoration.None] until the library writes one.
     *
     * Only the library writes it, on the event dispatch thread, and only with a value that differs from the one held.
     * An implementation stores the value and does nothing else: after writing, the library repaints the component.
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
     * the node's `isOpaque`, and declines every other node. A draw node's `isOpaque` never changes, and it repaints
     * its own change. Not to be overridden.
     */
    override fun needsNodesAfterWrite(node: SwingModifier.Node): Boolean =
        node is DecorationModifierNode<*> && node !is DrawModifierNode<*>
}

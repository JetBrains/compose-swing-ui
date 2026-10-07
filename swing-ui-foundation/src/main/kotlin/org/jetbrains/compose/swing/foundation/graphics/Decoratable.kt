package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingComponentNode

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
 * - `repaint(long, int, int, int, int)` answers `decoration.repaint(this, tm, x, y, width, height)`;
 * - `paintImmediately(int, int, int, int)` answers
 *   `decoration.paintImmediately(this, x, y, w, h) { x, y, w, h -> super.paintImmediately(x, y, w, h) }`;
 * - a component with children also answers `isPaintingOrigin` with [Decoration.isDecorated], as `JLayer` does, so a
 *   repaint a child records is painted through its decoration.
 *
 * Its size getters answer as for any Swing component: a size worked out from its content includes `getInsets()`, and
 * a set size answers as set. A Foundation container gives it all its paint outsets. Any other parent gives it only
 * the part of its decoration its [PaintOutsets][org.jetbrains.compose.swing.foundation.layout.PaintOutsets] value
 * leaves in layout, none by default, and clips the rest at its bounds.
 */
public interface Decoratable : DeclaredNodesListener {
    /**
     * The decoration this component paints through; [Decoration.None] until the library writes one.
     *
     * Only the library writes it, on the event dispatch thread, and only with a value that differs from the one held.
     * An implementation stores the value and does nothing else: after writing, the library repaints the component,
     * and where the paint outsets changed, its Foundation parent fits its bounds around its layout bounds, and under
     * any other parent the library revalidates it. A superclass constructor that repaints, as `JPanel`'s does, runs
     * before the implementation's own fields are set; such an implementation answers [Decoration.None] from a getter
     * while no value is stored.
     */
    public var decoration: Decoration

    /**
     * Gathers the decoration the nodes declare and writes it to [decoration]. Not to be overridden.
     *
     * @throws IllegalStateException if this `Decoratable` is not a `java.awt.Component`.
     */
    override fun onDeclaredNodesChanged(
        componentNode: SwingComponentNode<*>,
        nodes: List<SwingModifier.Node>,
    ) {
        publishDecoration(this, nodes, requesterComponent = componentNode)
    }
}

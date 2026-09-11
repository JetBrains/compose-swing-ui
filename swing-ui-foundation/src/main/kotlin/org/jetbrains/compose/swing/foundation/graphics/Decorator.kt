package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Graphics2D
import java.awt.Insets

/**
 * One operation wrapping the painting of what it decorates, declared on a component with
 * [decoration]. It is handed the graphics, the size, and the
 * continuation that paints everything inside it, and it decides what graphics that continuation paints
 * into: a decorator painting *around* its content hands on the graphics it was given, one cutting the content
 * hands on a clipped one, and one that post-processes the content's pixels, such as a blur or a tint, points it
 * at an offscreen raster.
 *
 * Each decorator is handed a graphics of its own, created around the call and disposed after it, so a
 * clip, composite, stroke or transform it leaves behind reaches no other painting.
 *
 * Write one as a `data class` so it compares structurally: a modifier chain is rebuilt on every
 * recomposition of the enclosing scope, and a decorator that cannot be compared repaints every pass.
 *
 * A decorator that keeps state across paints, such as an offscreen [ImageLayer], is a [DecorationModifierNode].
 */
public fun interface Decorator {
    /**
     * Paints this decoration and, at the point of its own choosing, the [content] inside it.
     *
     * @param graphics this decorator's own graphics, clipped to the area to paint; disposed when this returns.
     * @param width the width of the area this decorator paints in.
     * @param height the height of the area this decorator paints in.
     * @param content paints everything inside this decorator into the graphics it is handed, which must be
     *   clipped. Call it once, more than once, or not at all. The size it is given reaches the decorators
     *   inside this one; the component itself paints at its own bounds whatever size it is given.
     */
    public fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    )

    /**
     * How far this decorator paints past the content's box; nothing by default, and never negative. What is
     * painted past the component's bounds is clipped.
     *
     * Callers read the value and never modify it, so it may be shared.
     */
    public val outsets: Insets get() = NoPaintOutsets

    /**
     * Whether painting through this decorator still covers every pixel of the area it is given; `true`
     * by default.
     *
     * A decorator that leaves part of its area unpainted or paints it translucently answers `false`. A
     * decorated component reports that from `isOpaque`, so Swing repaints what is
     * behind the component instead of letting the decorator blend over a reused back buffer.
     */
    public val isOpaque: Boolean get() = true
}

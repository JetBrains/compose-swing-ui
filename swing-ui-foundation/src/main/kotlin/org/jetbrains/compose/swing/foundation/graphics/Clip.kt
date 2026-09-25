@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.Shape as AwtShape

/**
 * Clips everything inside it to [shape]'s outline - the component's content, its border and, on a
 * container, its children.
 *
 * The cut is hard-edged by default: `Graphics2D.clip` produces no partially covered pixels, so a curve
 * or a diagonal shows stair-stepping. [antialias] trades that for a soft edge, at the cost of painting
 * the content into an offscreen raster and masking it, which a hard clip does not need. A rectangle has
 * no edge to soften and is better left hard.
 *
 * Inside its layout bounds, a clip changes how a component looks, not what it does: a circle-clipped
 * container still takes a click in its corners. A child placed past the decorated box is cut away there,
 * and a press there does not reach it.
 *
 * @param shape the outline to cut to, resolved against the [decorated box][decoration].
 * @param antialias whether a pixel the outline covers in part is kept in part; `false` by default.
 * @return this chain with the clip declared on it.
 * @see java.awt.Graphics2D.clip
 */
public fun SwingModifier.clip(
    shape: Shape,
    antialias: Boolean = false,
): SwingModifier = decoration(ClipElement(shape, antialias))

/** The additive element behind [SwingModifier.clip]. */
private data class ClipElement(
    private val shape: Shape,
    private val antialias: Boolean,
) : SwingModifier.NodeElement<Component, ClipNode>() {
    override val name: String get() = "clip"

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override val declaredValues: Map<String, Any?> get() = mapOf("shape" to shape, "antialias" to antialias)

    override fun create(): ClipNode = ClipNode(shape, antialias)

    override fun update(node: ClipNode) {
        node.shape = shape
        node.antialias = antialias
        node.component.repaint()
    }
}

/**
 * Restricts the graphics clip to the requested shape, optionally with antialiasing.
 *
 * @see clip
 */
private class ClipNode(
    shape: Shape,
    antialias: Boolean,
) : DecorationModifierNode<Component>() {
    /** What an antialiased clip records its content into, created by the first paint that needs it. */
    private var layer: ImageLayer? = null

    var shape: Shape = shape

    var antialias: Boolean = antialias
        set(value) {
            field = value
            if (!value) {
                layer?.release()
                layer = null
            }
        }

    override val isOpaque: Boolean get() = false

    /**
     * Cuts [content] to [shape]'s own outline, resolved against the layout bounds of [width] by [height], rather than
     * to the box: a shape wider than the box still reserves what it paints past it, and one narrower still reserves
     * no more than it covers.
     */
    override fun paintBounds(
        content: AwtShape,
        width: Int,
        height: Int,
    ): AwtShape = Area(content).apply { intersect(Area(shape.outline(width, height))) }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        val outlineShape = shape.outline(width, height)
        if (!antialias) {
            graphics.clip(outlineShape)
            content(graphics, width, height)
            return
        }
        val outline = outlineShape.bounds
        if (width <= 0 || height <= 0 || outline.isEmpty) return
        val layer = layer ?: ImageLayer().also { layer = it }
        // Recorded over the outline, not the box, so the antialiased edge paints everywhere the outline reserves.
        graphics.translate(outline.x, outline.y)
        try {
            layer.record(graphics, outline.width, outline.height) { recording ->
                recording.translate(-outline.x, -outline.y)
                content(recording, width, height)
                // Taking the outside back out of a recording is what a clip cannot do: a pixel the outline
                // covers in part is cleared in part, which is the soft edge. Filling the outline itself would
                // leave everything it never touches - the whole outside - standing.
                // The outside is taken in device pixels, across every pixel the outline's bounds touch: the
                // recording holds a pixel their edge crosses whole, and the bounds themselves would clear only
                // the part of it they cover.
                val toDevice = recording.transform
                val outside = Area(toDevice.createTransformedShape(outline).bounds)
                outside.subtract(Area(toDevice.createTransformedShape(outlineShape)))
                recording.transform = AffineTransform()
                recording.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                recording.composite = AlphaComposite.DstOut
                recording.color = Color.WHITE
                recording.fill(outside)
            }
        } finally {
            graphics.translate(-outline.x, -outline.y)
        }
        layer.draw(graphics)
    }

    override fun onRemovedFromDecoration() {
        layer?.release()
        layer = null
    }
}

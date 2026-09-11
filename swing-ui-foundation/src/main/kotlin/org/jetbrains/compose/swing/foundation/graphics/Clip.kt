@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Area

/**
 * Clips everything inside it to [shape]'s outline - the component's content, its border and, on a
 * container, its children.
 *
 * The cut is hard-edged by default: `Graphics2D.clip` produces no partially covered pixels, so a curve
 * or a diagonal shows stair-stepping. [antialias] trades that for a soft edge, at the cost of painting
 * the content into an offscreen raster and masking it, which a hard clip does not need. A rectangle has
 * no edge to soften and is better left hard.
 *
 * A clip changes how a component looks, not what it does: input is unaffected, so a circle-clipped
 * container still takes a click in its corners.
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

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        if (!antialias) {
            graphics.clip(shape.outline(width, height))
            content(graphics, width, height)
            return
        }
        if (width <= 0 || height <= 0) return
        val layer = layer ?: ImageLayer().also { layer = it }
        // Aligned to the graphics' device pixels, so the content keeps the resolution it would paint at directly.
        layer.record(graphics, width, height) { recording ->
            content(recording, width, height)
            // Taking the outside back out of a recording is what a clip cannot do: a pixel the outline
            // covers in part is cleared in part, which is the soft edge. Filling the outline itself would
            // leave everything it never touches - the whole outside - standing.
            // The outside is taken in device pixels, across every pixel the component touches: the recording
            // holds a pixel the component's edge crosses whole, and the component's own bounds would clear only
            // the part of it they cover.
            val toDevice = recording.transform
            val outside = Area(toDevice.createTransformedShape(Rectangle(0, 0, width, height)).bounds)
            outside.subtract(Area(toDevice.createTransformedShape(shape.outline(width, height))))
            recording.transform = AffineTransform()
            recording.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            recording.composite = AlphaComposite.DstOut
            recording.color = Color.WHITE
            recording.fill(outside)
        }
        layer.draw(graphics)
    }

    override fun onRemovedFromDecoration() {
        layer?.release()
        layer = null
    }
}

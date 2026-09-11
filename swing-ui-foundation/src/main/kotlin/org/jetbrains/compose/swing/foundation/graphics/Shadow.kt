@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Casts a blurred shadow of everything inside it.
 *
 * The shadow is the content's own silhouette, not a declared outline: everything declared after it, tinted,
 * blurred and laid down behind it. Rounded corners, a cut-out, a line of text - whatever the content's edge is,
 * the shadow has it.
 *
 * The outsets the blur needs are this step's [outsets][Decorator.outsets]. What the shadow casts past the
 * component's bounds is clipped.
 *
 * @param radius how far the silhouette is spread, in the component's own units; `0` or less casts a hard edge.
 * @param color the shadow's fill, usually translucent.
 * @param offsetX how far the shadow moves right of what casts it.
 * @param offsetY how far the shadow moves below what casts it.
 * @return this chain with the shadow declared on it.
 */
public fun SwingModifier.shadow(
    radius: Int,
    color: Color,
    offsetX: Int = 0,
    offsetY: Int = 0,
): SwingModifier = decoration(ShadowElement(radius, color, offsetX, offsetY))

/** The additive element behind [SwingModifier.shadow]. */
private data class ShadowElement(
    private val radius: Int,
    private val color: Color,
    private val offsetX: Int,
    private val offsetY: Int,
) : SwingModifier.NodeElement<Component, ShadowNode>() {
    override val name: String get() = "shadow"

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override val declaredValues: Map<String, Any?>
        get() = mapOf("radius" to radius, "color" to color, "offsetX" to offsetX, "offsetY" to offsetY)

    override fun create(): ShadowNode = ShadowNode(radius, color, offsetX, offsetY)

    override fun update(node: ShadowNode) {
        node.radius = radius
        node.color = color
        node.offsetX = offsetX
        node.offsetY = offsetY
        node.component.repaint()
    }
}

/**
 * Captures the content silhouette, blurs and tints it, then paints the content.
 *
 * @see shadow
 */
private class ShadowNode(
    radius: Int,
    var color: Color,
    var offsetX: Int,
    var offsetY: Int,
) : DecorationModifierNode<Component>() {
    var radius: Int = radius
        set(value) {
            if (field == value) return
            field = value
            effect = BlurEffect(value.toFloat(), edgeTreatment = TileMode.Decal)
            drawEffect = BlurEffect(value.toFloat())
        }

    // The silhouette fades into transparency around it rather than being clamped against its edge: a shadow's
    // whole point is the falloff past the edge of what casts it.
    private var effect: BlurEffect = BlurEffect(radius.toFloat(), edgeTreatment = TileMode.Decal)

    /**
     * What [cast] draws through for a whole-pixel cast, whose recordingBounds are sized over the area alone; see
     * [BlurEffect.recordingBounds]. The other cast, sized over the whole content box, keeps [effect]'s Decal growth
     * because its clip reaches into that margin.
     */
    private var drawEffect: BlurEffect = BlurEffect(radius.toFloat())

    /** What the blur reaches around the offset silhouette, as [BlurNode] reserves it around its content. */
    override val outsets: Insets
        get() {
            val reach = effect.outsets(1.0)
            return Insets(
                (reach.top - offsetY).coerceAtLeast(0),
                (reach.left - offsetX).coerceAtLeast(0),
                (reach.bottom + offsetY).coerceAtLeast(0),
                (reach.right + offsetX).coerceAtLeast(0),
            )
        }

    /** The content, created by the first paint. */
    private var captured: ImageLayer? = null

    /** The shadow the content casts, created by the first paint. */
    private var blurred: ImageLayer? = null

    override val isOpaque: Boolean get() = false

    /** [captured] and [blurred], created by the first paint that needs them. */
    private fun layers(): Pair<ImageLayer, ImageLayer> {
        val captured = captured ?: ImageLayer().also { captured = it }
        val blurred = blurred ?: ImageLayer().also { blurred = it }
        return captured to blurred
    }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        if (width <= 0 || height <= 0) return
        val full = outsets.around(width, height)
        val area = graphics.clipArea()
        if (area.isEmpty) return
        val wholePixels = graphics.transform.castsInWholePixels
        val shadow = shadowBounds(area, width, height, wholePixels)
        // The shadow over the area is cast by the content within the outsets under the whole shadow area,
        // offset back; the capture also holds the area itself, because it is drawn as the content.
        val source = area.union(Rectangle(shadow).apply { translate(-offsetX, -offsetY) }.intersection(full))
        val (captured, blurred) = layers()
        val capture = alignedTo(graphics, source)
        val cast = alignedTo(graphics, shadow)
        try {
            // Aligned to the device pixels the content paints on, so the capture is drawn back pixel for pixel.
            captured.record(capture, source.width, source.height) { recording ->
                recording.translate(-source.x, -source.y)
                content(recording, width, height)
            }
            cast(captured, blurred, cast, shadow, wholePixels)
        } finally {
            capture.dispose()
            cast.dispose()
        }
        blurred.draw(graphics)
        captured.draw(graphics)
    }

    /**
     * The bounds the shadow is cast over, snapped to the blur's grid counted from where the silhouette lies, so any
     * offset blurs it alike. The silhouette lies inside the content box, so only that much - not the outsets too -
     * needs casting.
     */
    private fun shadowBounds(
        area: Rectangle,
        width: Int,
        height: Int,
        wholePixels: Boolean,
    ): Rectangle =
        effect
            .recordingBounds(
                (if (wholePixels) Rectangle(area) else Rectangle(0, 0, width, height))
                    .apply { translate(-offsetX, -offsetY) },
            ).apply { translate(offsetX, offsetY) }

    /**
     * [captured] tinted flat across [shadow], recorded into [blurred] to be drawn blurred: [AlphaComposite.SrcIn]
     * keeps the coverage the capture has and replaces its color, which is what makes the shadow the content's
     * silhouette. It is recorded aligned to the device pixels of [destination]'s transform, whose clip is [shadow],
     * and the capture lands the offset away, rounded to whole device pixels. [wholePixels] chooses [drawEffect] over
     * [effect] to blur it.
     */
    private fun cast(
        captured: ImageLayer,
        blurred: ImageLayer,
        destination: Graphics2D,
        shadow: Rectangle,
        wholePixels: Boolean,
    ) {
        val transform = destination.transform
        val pixels = transform.devicePixels(Rectangle(0, 0, shadow.width, shadow.height))
        val shiftX = (transform.scaleX * offsetX + transform.shearX * offsetY).roundToInt()
        val shiftY = (transform.shearY * offsetX + transform.scaleY * offsetY).roundToInt()
        blurred.record(destination, shadow.width, shadow.height) { recording ->
            // An aligned capture draws at its own device pixels, counted here from the destination's.
            captured.draw(recording, shiftX - pixels.x, shiftY - pixels.y)
            recording.transform = AffineTransform()
            recording.composite = AlphaComposite.SrcIn
            recording.paint = color
            recording.fillRect(0, 0, pixels.width, pixels.height)
        }
        blurred.renderEffect = if (wholePixels) drawEffect else effect
    }

    override fun onRemovedFromDecoration() {
        captured?.release()
        captured = null
        blurred?.release()
        blurred = null
    }
}

/** A copy of [graphics] with its origin at [area]'s corner and its clip [area], which the caller disposes. */
private fun alignedTo(
    graphics: Graphics2D,
    area: Rectangle,
): Graphics2D =
    (graphics.create() as Graphics2D).apply {
        translate(area.x, area.y)
        clip = Rectangle(0, 0, area.width, area.height)
    }

/**
 * A shadow blurred at the same whole number of device pixels per unit on both axes, mirrored or not, blurs the same
 * over part of itself as over all of it, and so is cast over the area alone; at any other scale, it is cast over the
 * whole of itself.
 */
private val AffineTransform.castsInWholePixels: Boolean
    get() = shearX == 0.0 && shearY == 0.0 && abs(scaleX) == abs(scaleY) && scaleX == floor(scaleX)

/** A [width] x [height] content grown by these outsets, in the content's coordinates. */
private fun Insets.around(
    width: Int,
    height: Int,
): Rectangle = Rectangle(-left, -top, width + left + right, height + top + bottom)

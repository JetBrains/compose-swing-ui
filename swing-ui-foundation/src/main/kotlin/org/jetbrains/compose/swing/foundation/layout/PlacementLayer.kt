package org.jetbrains.compose.swing.foundation.layout

import androidx.annotation.FloatRange
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.compose.swing.foundation.graphics.Decorator
import org.jetbrains.compose.swing.foundation.graphics.ImageLayer
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.graphics.clipArea
import org.jetbrains.compose.swing.foundation.graphics.setToScaleAndRotation
import org.jetbrains.compose.swing.node.observeReads
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.geom.NoninvertibleTransformException
import java.awt.geom.Point2D

/**
 * The properties of the layer [PlacementScope.placeWithLayer] places a child with, which its `layerBlock` sets, as
 * androidx's `GraphicsLayerScope` does for a graphics layer.
 *
 * The layer covers the placed box: the size the child measured to where a [MeasurePolicy] places it, or the size
 * the content reports where a layout modifier places it. What it paints past the component's bounds is clipped at
 * them. A state read in the block repaints the component without measuring it again. Every run starts from the
 * defaults, so a property the block leaves unset paints at its default.
 *
 * Tooltips and popups the content opens stand where they would stand without the layer.
 */
public sealed interface PlacementLayerScope {
    /** The horizontal scale around [transformOrigin]; `1f` by default, and `0f` paints nothing. */
    public var scaleX: Float

    /** The vertical scale around [transformOrigin]; `1f` by default, and `0f` paints nothing. */
    public var scaleY: Float

    /**
     * The opacity the content paints at, as one image: overlapping content inside it does not show through.
     * `1f` by default. A value above `1f` paints at `1f`; `0f`, a value below it and `NaN` paint nothing.
     */
    @setparam:FloatRange(from = 0.0, to = 1.0)
    public var alpha: Float

    /** Rotation in degrees around [transformOrigin], after scaling; positive is clockwise on screen. Default `0f`. */
    public var rotationZ: Float

    /** Shared pivot for scale and [rotationZ], within the placed box; [TransformOrigin.Center] by default. */
    public var transformOrigin: TransformOrigin

    /**
     * Whether the content's painting is cut to the layer's box, inside every enclosing clipping layer;
     * `false` by default.
     */
    public var clip: Boolean
}

/** A layer's properties, reset to their defaults before each run of the block. */
internal class PlacementLayerProperties : PlacementLayerScope {
    override var scaleX: Float = 1f
    override var scaleY: Float = 1f
    override var alpha: Float = 1f
    override var rotationZ: Float = 0f
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
    override var clip: Boolean = false

    fun reset() {
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        rotationZ = 0f
        transformOrigin = TransformOrigin.Center
        clip = false
    }
}

/**
 * The layer a [LayoutModifierNode]'s placement paints, as a step of its component's decoration at the node's
 * position in the modifier, or the layer a [MeasurePolicy] places a component with, as the outermost step.
 *
 * The box is where the placed content lands, in the component's own coordinates: what [PlacementLayerScope.clip]
 * cuts to and what [PlacementLayerScope.transformOrigin] is a fraction of. Nested layers nest as decoration
 * steps, so a clip intersects every enclosing one and a scale applies inside every enclosing scale.
 *
 * A fade records the content into an [ImageLayer] aligned to the graphics it paints on and draws that back at the
 * alpha, so it fades as one image.
 */
internal class PlacementLayer(
    /** The layout node placing its content with this layer, or null where a [MeasurePolicy] places with it. */
    private val node: LayoutModifierNode?,
    /** The record of the component a [MeasurePolicy] places with this layer, or null where [node] places with it. */
    private val record: ChildMeasurable?,
) : Decorator {
    /** The block the content was last placed with, or [NoLayer] while it was placed without one. */
    private var layerBlock: PlacementLayerScope.() -> Unit = NoLayer

    /** The rotation and scale the content paints through, in the component's coordinates; null for none. */
    var transform: AffineTransform? = null
        private set

    /**
     * The matrix the next rotation or scale is written to. It swaps with [matrix] on each write, so the transform
     * a write replaces keeps its values for [placedWithLayer] to compare the new one against.
     */
    private var nextMatrix = AffineTransform()

    private var matrix = AffineTransform()

    /** Whether the content's painting is cut to the layer's box. */
    val clips: Boolean get() = properties.clip

    /** The layout node whose box the content lands in, or null where the content is the component itself. */
    var inner: LayoutModifierNode? = null

    /** The box, in the component's layout coordinates: the [inner] node's, or the component's layout box. */
    val boxX: Int get() = inner?.box?.x ?: 0

    val boxY: Int get() = inner?.box?.y ?: 0

    val boxWidth: Int get() = inner?.box?.width ?: placement?.layoutWidth ?: 0

    val boxHeight: Int get() = inner?.box?.height ?: placement?.layoutHeight ?: 0

    /** The record of the component this layer paints on, once its container has received it. */
    private val placement: ChildMeasurable? get() = record ?: node?.child

    /** Whether a [MeasurePolicy] places the component with this layer, outside every step of its modifier. */
    val isContainerLayer: Boolean get() = record != null

    /** Reset and reused by every run of the block, which runs only on the EDT. */
    private val properties = PlacementLayerProperties()

    // Stored once, so observing the block allocates nothing per run.
    private val runLayerBlock: () -> Unit = {
        properties.reset()
        layerBlock(properties)
    }

    /** Created by the first fade and released once the content is placed without the layer. */
    private var fadeLayer: ImageLayer? = null

    /** Whether the fade's recorded buffer is still held. */
    @get:VisibleForTesting
    internal val hasFadeContent: Boolean get() = fadeLayer?.hasContent == true

    override val isOpaque: Boolean get() = false

    /**
     * Records that the content was placed with [block], in the box, which [moved] where it differs from the one the
     * layer stood at, and runs the block, which a placement reaches after anything it reads may have changed, such
     * as a static composition local. A [MeasurePolicy]'s block runs inside its container's placement, whose reads
     * place the children again.
     *
     * @return whether the block instance or the box changed, or what a [MeasurePolicy]'s layer paints, which the
     *   component is repainted for.
     */
    fun placedWithLayer(
        block: PlacementLayerScope.() -> Unit,
        moved: Boolean,
    ): Boolean {
        val changed = block !== layerBlock || moved
        layerBlock = block
        if (node != null) {
            updateProperties()
            return changed
        }
        val alpha = properties.alpha
        val clip = properties.clip
        val transform = transform
        updateProperties()
        return changed || alpha.compareTo(properties.alpha) != 0 || clip != properties.clip ||
            transform != this.transform
    }

    /** Whether the box is at ([x], [y]) with the size [width] by [height]. */
    fun isBox(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Boolean = x == boxX && y == boxY && width == boxWidth && height == boxHeight

    /**
     * Records that the content was placed with no layer, which paints it plainly, and releases the fade's buffers.
     *
     * @return whether it was placed with one before, which the component is repainted for.
     */
    fun placedWithoutLayer(): Boolean {
        if (layerBlock === NoLayer) return false
        layerBlock = NoLayer
        transform = null
        fadeLayer?.release()
        fadeLayer = null
        return true
    }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        paint(graphics.transform, graphics, width, height, content)
    }

    /**
     * The part of [graphics]' clip that the component's bounds cover, mapped back through [start] and every transform
     * since; null where the component is not placed or the transform flattens the plane, which has no inverse.
     */
    private fun recordingArea(
        graphics: Graphics2D,
        start: AffineTransform,
    ): Rectangle? {
        val component = placement?.component ?: return null
        return try {
            val mapBack = graphics.transform.createInverse().apply { concatenate(start) }
            val footprint = mapBack.createTransformedShape(Rectangle(0, 0, component.width, component.height))
            graphics.clipArea().intersection(footprint.bounds)
        } catch (_: NoninvertibleTransformException) {
            null
        }
    }

    /**
     * Paints as [Decorator.paint] does, mapping a fade's recording area back through [start]: the transform this
     * component's own decoration started painting under, before it moved to the layout origin and before any of its
     * layers ran, so a layer nested inside another one on the same component maps through both, not only its own.
     * Paints only [content] once its layout node has detached.
     */
    fun paint(
        start: AffineTransform,
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        if (layerBlock === NoLayer || node?.isAttached == false) return content(graphics, width, height)
        val layer = properties
        // NaN compares false either way, so it is refused along with 0 rather than reaching AlphaComposite.
        val alpha = layer.alpha.coerceAtMost(1f)
        if (!(alpha > 0f) || layer.scaleX == 0f || layer.scaleY == 0f) return
        transform?.let(graphics::transform)
        if (layer.clip) graphics.clipRect(boxX, boxY, boxWidth, boxHeight)
        if (alpha == 1f) {
            content(graphics, width, height)
        } else {
            // What the steps inside this layer paint may reach anywhere in the component's bounds, as a shadow or a
            // scale does; where nothing paints, the recording stays transparent. The bounds are in the coordinates
            // the component's own decoration started painting in, ahead of every layer on it, and the clip is in
            // those after every layer up to and including this one, so the bounds are mapped through all of them to
            // meet it; a flattened plane, which has no inverse, never paints either.
            val fadeLayer = fadeLayer ?: ImageLayer().also { fadeLayer = it }
            fadeLayer.alpha = alpha
            val area = recordingArea(graphics, start)
            if (area != null && !area.isEmpty) {
                val shifted = graphics.create() as Graphics2D
                try {
                    shifted.translate(area.x, area.y)
                    fadeLayer.record(shifted, area.width, area.height) { recording ->
                        recording.translate(-area.x, -area.y)
                        content(recording, width, height)
                    }
                } finally {
                    shifted.dispose()
                }
                fadeLayer.draw(graphics)
            }
        }
    }

    /**
     * Maps a point where this layer paints back to the content point painted there.
     *
     * @param point a point in the component's coordinates, replaced by the content point.
     * @param clipped whether a point outside the box of a clipping layer maps to nothing.
     * @return false where no content paints at [point]: the transform cannot be inverted, as at a zero scale, or
     *   [clipped] cuts it away.
     */
    fun toContent(
        point: Point2D.Double,
        clipped: Boolean,
    ): Boolean {
        if (layerBlock === NoLayer) return true
        val inverted =
            try {
                transform?.inverseTransform(point, point)
                true
            } catch (_: NoninvertibleTransformException) {
                false
            }
        return inverted &&
            (
                !clipped ||
                    !properties.clip ||
                    (point.x >= boxX && point.y >= boxY && point.x < boxX + boxWidth && point.y < boxY + boxHeight)
            )
    }

    private fun updateProperties() {
        if (node == null) runLayerBlock() else node.observeReads(LayerReads, runLayerBlock)
        val layer = properties
        transform =
            if (layer.scaleX == 1f && layer.scaleY == 1f && layer.rotationZ == 0f) {
                null
            } else {
                nextMatrix.also {
                    it.setToScaleAndRotation(
                        boxX + layer.transformOrigin.pivotFractionX.toDouble() * boxWidth,
                        boxY + layer.transformOrigin.pivotFractionY.toDouble() * boxHeight,
                        layer.scaleX,
                        layer.scaleY,
                        layer.rotationZ,
                    )
                    nextMatrix = matrix
                    matrix = it
                }
            }
    }

    companion object {
        /** Reads made while the layer block runs; a change repaints the component the layer paints on. */
        val LayerReads: (LayoutModifierNode) -> Unit = {
            val layer = it.layer
            layer.updateProperties()
            it.child?.let { placement ->
                placement.component.repaint()
            }
        }
    }
}

/** The block of a layer the content is not placed with; never run. */
private val NoLayer: PlacementLayerScope.() -> Unit = {}

/**
 * Runs [place], which places the content in the box of [inner], and records that it was placed with [block]; see
 * [PlacementLayer.placedWithLayer].
 *
 * @return whether the component is repainted for it.
 */
internal inline fun PlacementLayer.placeContent(
    inner: LayoutModifierNode?,
    noinline block: PlacementLayerScope.() -> Unit,
    place: () -> Unit,
): Boolean {
    val standingX = boxX
    val standingY = boxY
    val standingWidth = boxWidth
    val standingHeight = boxHeight
    place()
    this.inner = inner
    return placedWithLayer(block, !isBox(standingX, standingY, standingWidth, standingHeight))
}

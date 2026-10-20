package org.jetbrains.compose.swing.foundation.layout

import androidx.annotation.FloatRange
import org.jetbrains.compose.swing.foundation.graphics.DecorationSteps
import org.jetbrains.compose.swing.foundation.graphics.Decorator
import org.jetbrains.compose.swing.foundation.graphics.DrawCache
import org.jetbrains.compose.swing.foundation.graphics.DrawCacheHost
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.graphics.clipArea
import org.jetbrains.compose.swing.foundation.graphics.publishSteps
import org.jetbrains.compose.swing.foundation.graphics.setToScaleAndRotation
import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.invalidateLayout
import org.jetbrains.compose.swing.node.observeReads
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.NoninvertibleTransformException
import java.awt.geom.Point2D

/**
 * The properties of the layer [PlacementScope.placeWithLayer] places a child with, which its `layerBlock` sets, as
 * androidx's `GraphicsLayerScope` does for a graphics layer.
 *
 * The layer covers the placed box: the size the child measured to where a [MeasurePolicy] places it, or the size
 * the content reports where a layout modifier places it. What it paints past the layout bounds grows the
 * component's paint outsets. A state read in the block repaints the component without measuring it again; where the
 * paint outsets it takes resize a Foundation container, that container places its children again. Each run of the
 * block starts from the defaults, so a property the block leaves unset paints at its default.
 *
 * Under a Foundation container, mouse input reaches what a rotation or a scale paints under the pointer, at the
 * point in the target's own coordinates. Tooltips and popups the content opens stand where they would stand
 * without the layer.
 */
public sealed interface PlacementLayerScope {
    /** The horizontal scale around [transformOrigin]; `1f` by default, and `0f` paints nothing. */
    public var scaleX: Float

    /** The vertical scale around [transformOrigin]; `1f` by default, and `0f` paints nothing. */
    public var scaleY: Float

    /**
     * The opacity the content paints at, as one image: overlapping content inside it does not show through.
     * `1f` by default. A value above `1f` paints at `1f`; `0f`, a value below it and `NaN` paint nothing.
     *
     * A change of the alpha or of [clip] shows one event after the change, together with the repaints requested before
     * it. A repaint of a component inside the layer shows when that repaint paints; a frame painted before it shows the
     * content as last drawn.
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
 * A fade records the content into an image layer, aligned to the graphics it paints on, and draws that back at
 * the alpha, so it fades as one image.
 */
internal sealed class PlacementLayer : Decorator {
    /** The layer [node] places its content with, a step of its component's decoration at the node's position. */
    class NodeLayer(
        private val node: LayoutModifierNode,
    ) : PlacementLayer() {
        override val placement: ChildMeasurable? get() = node.child

        override val isDetached: Boolean get() = !node.isAttached

        // Painting with it again regathers the decoration, so a placement skips the layer it already paints.
        override val isPublished: Boolean get() = node.decorationStep === this

        override fun publish(painted: Boolean) = node.paintWith(if (painted) this else null)

        override fun observeLayerBlock() = node.observeReads(LayerReads, runLayerBlock)
    }

    /** The layer a [MeasurePolicy] places [placement] with, outside every step of the component's modifier. */
    class ContainerLayer(
        override val placement: ChildMeasurable,
    ) : PlacementLayer() {
        override val isPublished: Boolean get() = placement.requireDecoratable.decoration.steps.containerLayer === this

        override fun publish(painted: Boolean) {
            val decoratable = placement.requireDecoratable
            publishSteps(
                decoratable,
                decoratable.decoration.steps.inContainerLayer(if (painted) this else null),
                requester = placement.owner.owner.node,
                revalidate = SwingComponentNode<*>::invalidateLayout,
            )
        }

        override fun observeLayerBlock() = placement.owner.owner.observe(ContainerLayerReads, runLayerBlock)

        /** Also reports a change to what the layer paints, since its block records its reads apart from placement. */
        override fun placedWithLayer(
            block: PlacementLayerScope.() -> Unit,
            moved: Boolean,
        ): Boolean {
            var changed = false
            val paintChanged = changesPaint { changed = super.placedWithLayer(block, moved) }
            return changed || paintChanged
        }
    }

    /** The record of the component this layer paints on, once its container has received it. */
    abstract val placement: ChildMeasurable?

    /** Whether the layout node placing with this layer has detached, which leaves only the content painting. */
    protected open val isDetached: Boolean get() = false

    /** Whether the component's decoration paints this layer. */
    abstract val isPublished: Boolean

    /** Makes this layer a step of the component's decoration where [painted], and removes it from there otherwise. */
    abstract fun publish(painted: Boolean)

    /** Runs the layer block, recording its reads where a change to one of them updates this layer. */
    protected abstract fun observeLayerBlock()

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

    /** Reset and reused by every run of the block, which runs only on the EDT. */
    protected val properties = PlacementLayerProperties()

    // Stored once, so observing the block allocates nothing per run.
    protected val runLayerBlock: () -> Unit = {
        properties.reset()
        layerBlock(properties)
    }

    private inner class FadeCacheHost : DrawCacheHost {
        override val component: Component? get() = placement?.component

        override val steps: DecorationSteps? get() = placement?.decoratable?.decoration?.steps
    }

    /** Holds the fade's recording; released once the content is placed without the layer. */
    internal val drawCache = DrawCache(FadeCacheHost())

    /** Whether a paint faded the content since the cache last dropped its recording. */
    private var holdsFade = false

    /**
     * Drops the fade's recording once, on leaving the fade, so that a resting layer does not invalidate the caches
     * outside it on every paint.
     */
    private fun dropFade() {
        if (!holdsFade) return
        holdsFade = false
        drawCache.invalidate()
    }

    /** The bounds of the graphics' clip before and after a clipped fade's box clip. */
    private val unclippedBounds = Rectangle()
    private val clippedBounds = Rectangle()

    override val isOpaque: Boolean get() = false

    override val repaintsWithinOutsets: Boolean get() = transform == null

    /** Only a rotation or a scale moves the content off its box; a fade or a clip alone takes no paint outsets. */
    override fun needsPaintBounds(
        width: Int,
        height: Int,
    ): Boolean = transform != null

    internal val hasPlacedLayer: Boolean get() = layerBlock !== NoLayer

    /**
     * Records that the content was placed with [block], in the box, which [moved] where it differs from the one the
     * layer stood at, and runs the block, which a placement reaches after anything it reads may have changed, such
     * as a static composition local. A [MeasurePolicy]'s block records its reads apart from its container's
     * placement, so a change to one of them measures nothing again; where the paint outsets the layer takes resize a
     * Foundation container, that container places its children again.
     *
     * @return whether the block instance or the box changed, which the component is repainted for.
     */
    open fun placedWithLayer(
        block: PlacementLayerScope.() -> Unit,
        moved: Boolean,
    ): Boolean {
        val changed = block !== layerBlock || moved
        layerBlock = block
        updateProperties()
        return changed
    }

    /**
     * Records that the content was placed with no layer, which paints it plainly, and releases the fade's buffers.
     *
     * @return whether it was placed with one before, which the component is repainted for.
     */
    fun placedWithoutLayer(): Boolean {
        if (layerBlock === NoLayer) return false
        layerBlock = NoLayer
        transform = null
        drawCache.release()
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
        if (layerBlock === NoLayer || isDetached) {
            dropFade()
            return content(graphics, width, height)
        }
        val layer = properties
        // NaN compares false either way, so it is refused along with 0 rather than reaching AlphaComposite.
        val alpha = layer.alpha.coerceAtMost(1f)
        if (!(alpha > 0f) || layer.scaleX == 0f || layer.scaleY == 0f) {
            dropFade()
            return
        }
        transform?.let(graphics::transform)
        if (alpha == 1f) {
            dropFade()
            if (layer.clip) graphics.clipRect(boxX, boxY, boxWidth, boxHeight)
            content(graphics, width, height)
        } else {
            holdsFade = true
            paintFaded(start, graphics, width, height, content)
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

    override fun paintBounds(
        content: Shape,
        width: Int,
        height: Int,
    ): Shape {
        if (layerBlock === NoLayer || (!properties.clip && transform == null)) return content
        val area = Area(content)
        if (properties.clip) area.intersect(Area(Rectangle(boxX, boxY, boxWidth, boxHeight)))
        return transform?.createTransformedShape(area) ?: area
    }

    private fun updateProperties() {
        observeLayerBlock()
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
        private fun PlacementLayer.paintFaded(
            start: AffineTransform,
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) {
            // What the steps inside this layer paint may reach anywhere in the component's bounds, as a shadow or a
            // scale does; where nothing paints, the recording stays transparent. The bounds are in the coordinates
            // the component's own decoration started painting in, ahead of every layer on it, and the clip is in
            // those after every layer up to and including this one, so the bounds are mapped through all of them to
            // meet it; a flattened plane, which has no inverse, never paints either. The area is taken before the
            // box clip, which is applied as the recording is drawn, so a clip that changes needs no new recording.
            val cache = drawCache
            cache.layer.alpha = properties.alpha
            val area = placement?.component?.recordingArea(graphics, start)
            if (area != null && !area.isEmpty) paintFadedOver(graphics, area, width, height, content)
        }

        private fun PlacementLayer.paintFadedOver(
            graphics: Graphics2D,
            area: Rectangle,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) {
            val cache = drawCache
            val clip = properties.clip
            if (clip) {
                graphics.getClipBounds(unclippedBounds)
                graphics.clipRect(boxX, boxY, boxWidth, boxHeight)
                graphics.getClipBounds(clippedBounds)
            }
            val visible = !clip || clippedBounds.intersects(area)
            if (visible && !(cache.isReusable(area, width, height) && cache.drawCached(graphics))) {
                cache.record(graphics, area, width, height) { layer ->
                    val shifted = graphics.create() as Graphics2D
                    try {
                        if (clip) shifted.clip = unclippedBounds
                        shifted.translate(area.x, area.y)
                        layer.record(shifted, area.width, area.height) { recording ->
                            recording.translate(-area.x, -area.y)
                            content(recording, width, height)
                        }
                    } finally {
                        shifted.dispose()
                    }
                }
                cache.layer.draw(graphics)
            }
        }

        /**
         * Has [placement]'s container queue the frame where [ChildMeasurables.queueLayerRedraw] takes it; otherwise
         * redraws the component. Either way the caches before this layer record again.
         */
        private fun PlacementLayer.redrawLayer(placement: ChildMeasurable) {
            if (placement.owner.queueLayerRedraw(placement)) return drawCache.invalidateBefore()
            drawCache.redraw()
        }

        /**
         * Reads made while the layer block runs; a change runs the block again and repaints the component where the
         * layer paints differently.
         */
        val LayerReads: (LayoutModifierNode) -> Unit = { node ->
            val placement = node.layer.placement
            if (node.layer.update() && placement != null) node.layer.redrawLayer(placement)
        }

        /**
         * Reads made while the blocks of the layers a [MeasurePolicy] places its container's children with run. A
         * layer moves neither a child nor an alignment line, so a change measures nothing again: it runs every such
         * block of the container again, since they all record under the container's one node, and repaints each child
         * whose layer paints differently. Where the paint outsets a layer takes resize a Foundation container, that
         * container places its children again.
         */
        private val ContainerLayerReads: (SwingComponentNode<ConstrainedPanel>) -> Unit = { node ->
            node.component.policyLayout.measurables.layoutPass.fastForEach { child ->
                val layer =
                    child.decoratable
                        ?.decoration
                        ?.steps
                        ?.containerLayer
                if (!child.isLeftUnplaced && layer != null && layer.update()) layer.redrawLayer(child)
            }
        }

        /** Runs [update], and returns whether it changed the alpha, the clip or the transform the layer paints with. */
        private inline fun PlacementLayer.changesPaint(update: () -> Unit): Boolean {
            val alpha = properties.alpha
            val clip = properties.clip
            val transform = transform
            update()
            return alpha.compareTo(properties.alpha) != 0 || !hasSameClipAndTransformAs(clip, transform)
        }

        private fun PlacementLayer.hasSameClipAndTransformAs(
            clip: Boolean,
            transform: AffineTransform?,
        ): Boolean = clip == properties.clip && transform == this.transform

        /**
         * Runs the layer's block again for a changed read and fits paint outsets where its clip or transform changed.
         * A changed transform repaints the component. Returns whether only the alpha or the clip changed, which its
         * caller redraws.
         */
        private fun PlacementLayer.update(): Boolean {
            val clip = properties.clip
            val transform = transform
            val changed = changesPaint { updateProperties() }
            val placement = placement
            if (!changed || placement == null || hasSameClipAndTransformAs(clip, transform)) return changed
            val startedOrStoppedTransforming = (transform != null) != (this.transform != null)
            placement.owner.fitPaintOutsets(placement, transformChanged = startedOrStoppedTransforming)
            val retransformed = transform != this.transform
            if (retransformed) placement.component.repaint()
            return !retransformed
        }
    }
}

/**
 * The part of [graphics]' clip that this component's bounds cover, mapped back through [start] and every transform
 * since; null where the transform flattens the plane, which has no inverse.
 */
private fun Component.recordingArea(
    graphics: Graphics2D,
    start: AffineTransform,
): Rectangle? =
    try {
        val mapBack = graphics.transform.createInverse().apply { concatenate(start) }
        val footprint = mapBack.createTransformedShape(Rectangle(0, 0, width, height))
        graphics.clipArea().intersection(footprint.bounds)
    } catch (_: NoninvertibleTransformException) {
        null
    }

/** The block of a layer the content is not placed with; never run. */
private val NoLayer: PlacementLayerScope.() -> Unit = {}

/**
 * Runs [place], which places the content in the box of [inner], with this layer running [block]; see
 * [PlacementLayer.placedWithLayer]. Makes the layer a step of the component's decoration where it is not one yet, and
 * repaints the component where the placement changed what the layer paints.
 */
internal inline fun PlacementLayer.placeWith(
    inner: LayoutModifierNode?,
    noinline block: PlacementLayerScope.() -> Unit,
    place: () -> Unit,
) {
    val standingX = boxX
    val standingY = boxY
    val standingWidth = boxWidth
    val standingHeight = boxHeight
    place()
    this.inner = inner
    val moved = standingX != boxX || standingY != boxY || standingWidth != boxWidth || standingHeight != boxHeight
    val changed = placedWithLayer(block, moved)
    if (!isPublished) publish(true)
    if (changed) placement?.component?.repaint()
}

/**
 * Records that the content, placed again, was placed with no layer; see [PlacementLayer.placedWithoutLayer]. Where it
 * was placed with this layer before, removes the layer from the component's decoration and repaints the component.
 */
internal fun PlacementLayer.placedPlainly() {
    if (!placedWithoutLayer()) return
    // A layout node attached again starts without the layer, and may hold a decorator of its own by now.
    if (isPublished) publish(false)
    placement?.component?.repaint()
}

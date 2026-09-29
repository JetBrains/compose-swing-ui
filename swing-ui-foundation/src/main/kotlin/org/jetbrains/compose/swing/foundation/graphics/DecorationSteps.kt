package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNode
import org.jetbrains.compose.swing.foundation.layout.PlacementLayer
import org.jetbrains.compose.swing.foundation.util.fastAll
import org.jetbrains.compose.swing.foundation.util.fastAny
import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D

/**
 * The steps a component's modifier declares, outermost first, and what they report together. Two are equal when they
 * hold the same steps reporting the same outsets.
 */
internal class DecorationSteps private constructor(
    private val decorators: List<Decorator>,
) {
    /** The [Decorator.outsets] of the steps, added together. */
    val outsets: Insets =
        run {
            var top = 0
            var left = 0
            var bottom = 0
            var right = 0
            decorators.fastForEach {
                val outsets = it.outsets
                top += outsets.top
                left += outsets.left
                bottom += outsets.bottom
                right += outsets.right
            }
            Insets(top, left, bottom, right)
        }

    /** Whether every step answers [Decorator.isOpaque] now; the library reads it once per gathering. */
    val isOpaque: Boolean get() = decorators.fastAll { it.isOpaque }

    val isEmpty: Boolean get() = decorators.isEmpty()

    /**
     * Whether a step paints past the content, or transforms or clips what is inside it, at layout bounds of [width]
     * by [height].
     */
    fun needsPaintBounds(
        width: Int,
        height: Int,
    ): Boolean =
        outsets != NoPaintOutsets ||
            decorators.fastAny { it.needsPaintBounds(width, height) }

    /** Whether every step that paints at a layout node's box paints at layout bounds of [width] by [height]. */
    fun paintsAtLayoutBox(
        width: Int,
        height: Int,
    ): Boolean = decorators.fastAll { it !is BoxedStep || it.isAtLayoutBox(width, height) }

    /** The layer the component's container places it with, the outermost step, or null for none. */
    val containerLayer: PlacementLayer? get() = decorators.firstOrNull() as? PlacementLayer.ContainerLayer

    /** These steps inside [layer], in place of their [containerLayer]; these steps themselves where it is that one. */
    fun inContainerLayer(layer: PlacementLayer?): DecorationSteps {
        val held = containerLayer
        if (layer === held) return this
        val decorators = ArrayList<Decorator>(decorators.size + 1)
        if (layer != null) {
            decorators += layer
        }
        val skipped = if (held == null) 0 else 1
        decorators.addAll(this.decorators.subList(skipped, this.decorators.size))
        return if (decorators.isEmpty()) None else DecorationSteps(decorators)
    }

    /** Whether a placement layer among the steps rotates or scales what is inside it. */
    val isTransformed: Boolean get() = decorators.fastAny { it is PlacementLayer && it.transform != null }

    /** Whether a placement layer among the steps has placed its content with a layer. */
    val hasPlacedLayer: Boolean get() = decorators.fastAny { it is PlacementLayer && it.hasPlacedLayer }

    /** Whether a placement layer among the steps rotates, scales or clips what is inside it. */
    val isClippedOrTransformed: Boolean get() =
        decorators.fastAny {
            it is PlacementLayer &&
                (it.transform != null || it.clips)
        }

    /**
     * Maps [point], in layout coordinates, back through the placement layers, outermost first, to the content point
     * painted there.
     *
     * @return false where no content paints at [point]; see [PlacementLayer.toContent].
     */
    fun toContent(
        point: Point2D.Double,
        clipped: Boolean,
    ): Boolean = decorators.fastAll { it !is PlacementLayer || it.toContent(point, clipped) }

    /**
     * Paints [content] inside the steps, outermost first, each in a graphics of its own so what one leaves on it
     * reaches nothing else. A step whose node is no longer attached, left over from a pass that threw before its
     * hand-over, paints only what is inside it. [start] is the transform the component's decoration started painting
     * under, before it moved to the layout origin. Each placement layer maps a fade's recording area back through it.
     */
    fun paint(
        start: AffineTransform,
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        fun step(
            stepGraphics: Graphics2D,
            stepWidth: Int,
            stepHeight: Int,
            index: Int,
        ) {
            if (index == decorators.size) return content(stepGraphics, stepWidth, stepHeight)
            val decorator = decorators[index]
            val next = { inner: Graphics2D, w: Int, h: Int -> step(inner, w, h, index + 1) }
            val decorated = stepGraphics.create() as Graphics2D
            try {
                when {
                    decorator is PlacementLayer -> decorator.paint(start, decorated, stepWidth, stepHeight, next)
                    decorator.isAttached -> decorator.paint(decorated, stepWidth, stepHeight, next)
                    else -> next(decorated, stepWidth, stepHeight)
                }
            } finally {
                decorated.dispose()
            }
        }
        step(graphics, width, height, 0)
    }

    /** Grows [bounds] to the paint bounds over them of every step, in place. */
    fun growToPaintBounds(
        bounds: Rectangle,
        width: Int,
        height: Int,
    ) {
        bounds.setBounds(paintBoundsOutward(Rectangle(bounds), decorators.lastIndex, width, height).bounds)
    }

    /** Maps [seed] through the steps from [start] outward to the first. */
    private fun paintBoundsOutward(
        seed: Shape,
        start: Int,
        width: Int,
        height: Int,
    ): Shape {
        var painted = seed
        for (index in start downTo 0) painted = decorators[index].paintBounds(painted, width, height)
        return painted
    }

    /**
     * The bounds of the innermost clipping placed layer's box, mapped through the steps outside it, in layout
     * coordinates at layout bounds of [width] by [height]; null where no placed layer clips.
     */
    fun placedClipBounds(
        width: Int,
        height: Int,
    ): Rectangle? {
        var clipIndex = -1
        for (index in decorators.lastIndex downTo 0) {
            val decorator = decorators[index]
            if (decorator is PlacementLayer && decorator.hasPlacedLayer && decorator.clips) {
                clipIndex = index
                break
            }
        }
        if (clipIndex < 0) return null
        val clip = decorators[clipIndex] as PlacementLayer
        val box = Rectangle(clip.boxX, clip.boxY, clip.boxWidth, clip.boxHeight)
        return paintBoundsOutward(box, clipIndex, width, height).bounds
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is DecorationSteps && decorators == other.decorators && outsets == other.outsets)

    override fun hashCode(): Int = decorators.hashCode()

    companion object {
        /** No steps. */
        val None: DecorationSteps = DecorationSteps(emptyList())

        /**
         * The [DecorationModifierNode]s, leaving out those that paint nothing, and the
         * [steps][LayoutModifierNode.decorationStep] of the [LayoutModifierNode]s among [nodes], in order, inside
         * [containerLayer] where one is given.
         *
         * A step declared before a layout node paints at the box of the first layout node declared after it; one
         * declared after every layout node paints at the component's layout bounds. A layout node's decorator paints
         * at the node's own box, and its layer in the component's coordinates.
         */
        fun of(
            nodes: List<SwingModifier.Node>,
            containerLayer: PlacementLayer?,
        ): DecorationSteps {
            val decorators = ArrayList<Decorator>()
            if (containerLayer != null) {
                decorators += containerLayer
            }
            // The steps declared since the last layout node, which paint at the next one's box.
            var unboxed = decorators.size
            nodes.fastForEach { node ->
                when (node) {
                    is LayoutModifierNode -> {
                        for (index in unboxed until decorators.size) {
                            decorators[index] = BoxedStep(decorators[index], node)
                        }
                        val step = node.decorationStep
                        val layer = node.layerOrNull
                        if (step != null && step === layer) {
                            decorators += layer
                        } else if (step != null) {
                            decorators += BoxedStep(step, node)
                        }
                        unboxed = decorators.size
                    }

                    is DecorationModifierNode<*> -> {
                        if (!node.paintsNothing) decorators += node
                    }
                }
            }
            if (decorators.isEmpty()) return None
            return DecorationSteps(decorators)
        }
    }
}

/** Whether the node declaring this step, where the step is a modifier node itself, is attached. */
private val Decorator.isAttached: Boolean get() = this !is SwingModifier.Node || isAttached

/**
 * A [decorator] painting at the [box][LayoutModifierNode.box] of [layoutNode], where a Foundation container places
 * the component through that node, and at the component's layout bounds otherwise. It hands on the component's own
 * coordinates and size. [decorator] is a [DecorationModifierNode] or a layout node's own
 * [LayoutModifierNode.decorator]; one that works out its own paint bounds works them out at the box.
 *
 * It may paint past the component's layout bounds, into its paint outsets.
 */
private class BoxedStep(
    val decorator: Decorator,
    val layoutNode: LayoutModifierNode,
) : Decorator {
    /** The layout box, reused by every paint and query; both run only on the event dispatch thread. */
    private val layoutBox = Rectangle()

    /** The box it paints at, in the component's layout coordinates, whose layout size is [width] by [height]. */
    private fun box(
        width: Int,
        height: Int,
    ): Rectangle {
        val placed = layoutNode.box
        return if (layoutNode.child == null || placed == null) {
            layoutBox.apply { setBounds(0, 0, width, height) }
        } else {
            placed
        }
    }

    /** Whether it paints at layout bounds of [width] by [height]. */
    fun isAtLayoutBox(
        width: Int,
        height: Int,
    ): Boolean {
        val box = box(width, height)
        return box.x == 0 && box.y == 0 && box.width == width && box.height == height
    }

    override fun needsPaintBounds(
        width: Int,
        height: Int,
    ): Boolean {
        val box = box(width, height)
        return box.x != 0 || box.y != 0 || box.width != width || box.height != height ||
            decorator.needsPaintBounds(box.width, box.height)
    }

    override val outsets: Insets get() = decorator.outsets

    override val isOpaque: Boolean get() = decorator.isOpaque

    override fun paintBounds(
        content: Shape,
        width: Int,
        height: Int,
    ): Shape {
        val box = box(width, height)
        val bounds = content.bounds
        bounds.translate(-box.x, -box.y)
        return decorator.paintBounds(bounds, box.width, box.height).bounds.apply { translate(box.x, box.y) }
    }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        if (!layoutNode.isAttached || !decorator.isAttached) return content(graphics, width, height)
        val box = box(width, height)
        val x = box.x
        val y = box.y
        graphics.translate(x, y)
        decorator.paint(graphics, box.width, box.height) { inner, _, _ ->
            inner.translate(-x, -y)
            try {
                content(inner, width, height)
            } finally {
                inner.translate(x, y)
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is BoxedStep && decorator == other.decorator && layoutNode === other.layoutNode)

    override fun hashCode(): Int = 31 * decorator.hashCode() + layoutNode.hashCode()
}

/** Insets of zero on every edge, shared and never modified. */
internal val NoPaintOutsets: Insets = Insets(0, 0, 0, 0)

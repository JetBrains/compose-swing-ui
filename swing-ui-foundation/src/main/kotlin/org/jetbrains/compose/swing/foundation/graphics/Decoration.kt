package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.layout.ChildMeasurables
import org.jetbrains.compose.swing.foundation.util.fastAny
import org.jetbrains.compose.swing.foundation.util.fastForEach
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.RepaintManager
import kotlin.math.floor

/**
 * What a [Decoratable] component paints through, and how far it paints past its layout bounds: the steps its modifier
 * declares and the paint outsets its parent gives it. The library creates each value for one component and writes it
 * to [Decoratable.decoration]; a value never changes, and a change is a new value.
 *
 * Each step is a [Decorator], the first outermost: it paints at the layout bounds, or, when a layout modifier is
 * declared after it, at the box of the first such modifier. The *layout bounds* are the box measurement, placement,
 * alignment, `onPlaced`, `onSizeChanged` and hit testing see. The component's *bounds*, Swing's rectangle, are the
 * layout bounds plus the *paint outsets*, how far the component paints past them on each side. A Foundation
 * container gives a component all its paint outsets: the steps' [outsets][Decorator.outsets], the box of a layout
 * modifier a step paints at, what a rotating or scaling placement layer adds and, for a Foundation container, what its
 * children paint past it. Any other parent gives only the part of the steps' outsets the component's
 * [PaintOutsets][org.jetbrains.compose.swing.foundation.layout.PaintOutsets] value leaves in layout.
 */
public class Decoration internal constructor(
    /** The steps the component's modifier declares. */
    internal val steps: DecorationSteps,
    /** The paint outsets: [NoPaintOutsets], the steps' own outsets, or insets of its own; never modified. */
    internal val heldPaintOutsets: Insets,
    /** The records of the Foundation container holding the component; null under any other parent. */
    internal val parentMeasurables: ChildMeasurables?,
    /** The records of the component's own children, where it is a Foundation container; null otherwise. */
    internal val childMeasurables: ChildMeasurables?,
    /**
     * Whether the steps move what the component paints: a placement layer rotates or scales it, or the steps paint
     * content the size of the layout bounds past them further than their outsets reach, as a step translating it
     * does. Also true where this holds for a descendant a Foundation container inside it placed.
     */
    internal val holdsTransform: Boolean,
    /** Whether every step answered [Decorator.isOpaque] with `true` when the library last gathered the steps. */
    internal val hasOpaqueSteps: Boolean,
) {
    /** Whether the component's modifier declares any decoration step. */
    public val isDecorated: Boolean get() = !steps.isEmpty

    /**
     * Whether [component], painted through this decoration, still covers every pixel of its bounds: `false` where it
     * has paint outsets, or where a step paints translucently, cuts the content ([Decorator.isOpaque]) or paints at a
     * box smaller than the layout bounds. The steps' own opacity is the one the library last gathered; the boxes are
     * read at each call.
     */
    public fun isOpaque(component: Component): Boolean =
        heldPaintOutsets == NoPaintOutsets && hasOpaqueSteps &&
            steps.paintsAtLayoutBox(component.width, component.height)

    /**
     * How far the component paints past its layout bounds on each side; a new [Insets] the caller owns. Under a parent
     * that is not a Foundation container, the part of its decoration its `paintOutsets` value leaves in layout, none
     * by default.
     */
    public fun paintOutsets(): Insets =
        Insets(heldPaintOutsets.top, heldPaintOutsets.left, heldPaintOutsets.bottom, heldPaintOutsets.right)

    /**
     * [component]'s layout bounds in its own coordinates: its bounds less the paint outsets; a new [Rectangle]. Named
     * as `SwingUtilities.getLocalBounds` is.
     */
    public fun localLayoutBounds(component: Component): Rectangle =
        Rectangle(heldPaintOutsets.left, heldPaintOutsets.top, layoutWidth(component), layoutHeight(component))

    /** [base] plus the paint outsets: [base] itself where there are none, otherwise a new [Insets]. */
    public fun insets(base: Insets): Insets =
        if (heldPaintOutsets == NoPaintOutsets) base else insets(base, Insets(0, 0, 0, 0))

    /**
     * Writes [base] plus the paint outsets into [into] and returns it: what `getInsets(Insets)` answers without
     * allocating. [base] may be [into]; a border's own [Insets] passed as [base] is left unchanged.
     */
    public fun insets(
        base: Insets,
        into: Insets,
    ): Insets {
        val outsets = heldPaintOutsets
        into.set(
            base.top + outsets.top,
            base.left + outsets.left,
            base.bottom + outsets.bottom,
            base.right + outsets.right,
        )
        return into
    }

    /**
     * Paints [content], the component itself in its own coordinates, through the decoration. The steps paint at the
     * layout bounds, or at the box of the layout modifier declared after them. Without a clip on [graphics], as in a
     * capture or a print, the whole component is painted.
     *
     * @throws IllegalArgumentException where a decorated component is handed a graphics that is not a Graphics2D.
     */
    public fun paint(
        component: Component,
        graphics: Graphics,
        content: (Graphics) -> Unit,
    ) {
        val steps = steps
        if (steps.isEmpty) return content(graphics)
        require(graphics is Graphics2D) {
            "A decoration paints through Graphics2D, which is what Swing hands a component's paint; this one was " +
                "handed a ${graphics.javaClass.name}"
        }
        val decorated = graphics.create() as Graphics2D
        try {
            if (decorated.clip == null) decorated.clipRect(0, 0, component.width, component.height)
            val start = decorated.transform
            val originX = heldPaintOutsets.left
            val originY = heldPaintOutsets.top
            decorated.translate(originX, originY)
            steps.paint(start, decorated, layoutWidth(component), layoutHeight(component)) { inner, _, _ ->
                val swing = inner.create()
                try {
                    swing.translate(-originX, -originY)
                    content(swing)
                } finally {
                    swing.dispose()
                }
            }
        } finally {
            decorated.dispose()
        }
    }

    /**
     * Whether ([x], [y]), in [component]'s coordinates, hits it: inside the layout bounds, as its placement layers
     * rotate, scale and clip them, or, inside its bounds, where a visible child's own `contains` takes the point, as
     * for a child placed or painting past the layout bounds. A step that cuts its children to its box, such as a clip
     * or a fade, cuts the point too. Other paint outsets, such as a shadow's, are not hit.
     */
    public fun contains(
        component: Component,
        x: Int,
        y: Int,
    ): Boolean {
        if (inLayoutBounds(component, x, y)) return true
        val children = childMeasurables?.layoutPass
        val inBounds = !children.isNullOrEmpty() && x in 0 until component.width && y in 0 until component.height
        val width = layoutWidth(component)
        val height = layoutHeight(component)
        val point = if (inBounds) contentPoint(x, y) { steps.toChildContent(it, width, height) } else null
        return point != null &&
            children?.fastAny {
                val child = it.component
                !it.isLeftUnplaced && child.isVisible && child.contains(point.x - child.x, point.y - child.y)
            } == true
    }

    /**
     * Records a repaint of the area at ([x], [y]) of [width] by [height], in [component]'s coordinates, grown to all a
     * change in it repaints: by how far the steps spread a change, such as a blur's or a shadow's reach, held inside
     * the bounds, or to the whole bounds while the steps move what [component] paints, as a rotating layer does. The
     * grown area is recorded on [component] with the `RepaintManager`, as `JComponent.repaint` records it, without
     * calling [component]'s `repaint` again. Where Swing paints [component] as the dirty component, its
     * `paintImmediately` grows the area again, which paints more pixels and changes none; where Swing merges the area
     * into the repaint of a dirty ancestor, the ancestor paints it as recorded. [tm] is ignored, as
     * `JComponent.repaint` ignores it. What `repaint(long, int, int, int, int)` of a `Decoratable` answers.
     */
    @Suppress("LongParameterList", "UnusedParameter") // JComponent.repaint's parameters, as forwarded; it skips tm too.
    public fun repaint(
        component: JComponent,
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val manager = RepaintManager.currentManager(component)
        if (stepsMoveContent(component)) {
            manager.addDirtyRegion(component, 0, 0, component.width, component.height)
            return
        }
        val reach = steps.outsets
        val left = grownStart(x, reach.left)
        val top = grownStart(y, reach.top)
        val right = grownEnd(x, width, reach.right, component.width)
        val bottom = grownEnd(y, height, reach.bottom, component.height)
        manager.addDirtyRegion(component, left, top, right - left, bottom - top)
    }

    // This expansion preserves coverage of every required pixel; direct and descendant requests still need it.

    /**
     * Hands [paint] the area at ([x], [y]) of [width] by [height], in [component]'s coordinates, grown as [repaint]
     * grows it; [paint] is `super.paintImmediately`. In a Foundation container, the area is grown first by the whole
     * of each visible component it touches whose steps spread or move what it paints, as found among the children and
     * inside the Foundation containers among them. Swing calls `paintImmediately` on a decorated component as the
     * painting origin of its descendants' repaints and to paint a repaint it recorded itself, and application code
     * may call it. What `paintImmediately(int, int, int, int)` of a `Decoratable` answers.
     */
    @Suppress("ForbiddenComment", "LongParameterList")
    public fun paintImmediately(
        component: JComponent,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        paint: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    ) {
        if (stepsMoveContent(component)) return paint(0, 0, component.width, component.height)
        val area = Rectangle(x, y, width, height)
        val children = childMeasurables
        if (children != null && children.hasChildRepaintingWhole) {
            children.addChildrenRepaintingWhole(Rectangle(area), area, 0, 0, Rectangle())
        }
        // TODO Grow an already-expanded repaint region only once,
        // while still expanding direct paintImmediately requests.
        val reach = steps.outsets
        val left = grownStart(area.x, reach.left)
        val top = grownStart(area.y, reach.top)
        val right = grownEnd(area.x, area.width, reach.right, component.width)
        val bottom = grownEnd(area.y, area.height, reach.bottom, component.height)
        paint(left, top, right - left, bottom - top)
    }

    /**
     * Whether a repaint that touches the component may have to grow: it [holdsTransform], its steps paint past what
     * they are given, or it is a Foundation container [holding a child][ChildMeasurables.hasChildRepaintingWhole]
     * this is true for.
     */
    internal val holdsRepaintingWhole: Boolean
        get() = holdsTransform || steps.outsets != NoPaintOutsets || childMeasurables?.hasChildRepaintingWhole == true

    /**
     * The point whose content the placement layers paint at ([x], [y]), both in the component's own coordinates: the
     * layers are read back from the pixel's center, outermost first. Null where [clipped] cuts it away or no content
     * paints there; see [DecorationSteps.toContent].
     */
    internal fun contentPoint(
        x: Int,
        y: Int,
        clipped: Boolean,
    ): Point? = contentPoint(x, y) { steps.toContent(it, clipped) }

    /** Holds [None]. */
    public companion object {
        /** No decoration and no paint outsets: what a component holds until the library writes one. */
        public val None: Decoration =
            Decoration(DecorationSteps.None, NoPaintOutsets, null, null, holdsTransform = false, hasOpaqueSteps = true)
    }
}

/**
 * What [component]'s innermost clipping placed layer lets paint, in the component's own coordinates, as the steps
 * outside that layer map it; null where no placed layer clips.
 */
internal fun Decoration.clippedPaintBounds(component: Component): Rectangle? =
    steps.placedClipBounds(layoutWidth(component), layoutHeight(component))?.apply {
        translate(heldPaintOutsets.left, heldPaintOutsets.top)
    }

/** The width of [component]'s layout bounds: its width less the paint outsets. */
internal fun Decoration.layoutWidth(component: Component): Int =
    (component.width - heldPaintOutsets.left - heldPaintOutsets.right).coerceAtLeast(0)

/** The height of [component]'s layout bounds: its height less the paint outsets. */
internal fun Decoration.layoutHeight(component: Component): Int =
    (component.height - heldPaintOutsets.top - heldPaintOutsets.bottom).coerceAtLeast(0)

/**
 * Whether ([x], [y]), in [component]'s coordinates, is inside the layout rectangle, rotated, scaled and clipped as
 * the placement layers paint it.
 */
private fun Decoration.inLayoutBounds(
    component: Component,
    x: Int,
    y: Int,
): Boolean {
    val point = if (steps.isClippedOrTransformed) contentPoint(x, y, clipped = true) ?: return false else null
    val boxX = (point?.x ?: x) - heldPaintOutsets.left
    val boxY = (point?.y ?: y) - heldPaintOutsets.top
    return boxX in 0 until layoutWidth(component) && boxY in 0 until layoutHeight(component)
}

/**
 * Whether [component]'s own steps move what it paints: a layer rotates or scales it, or the steps paint content the
 * size of its layout bounds past them further than their outsets reach. [Decoration.holdsTransform] answers for a
 * component that is not a Foundation container; a Foundation container holds it for its descendants too, so its steps
 * are asked again.
 */
private fun Decoration.stepsMoveContent(component: Component): Boolean =
    holdsTransform && !steps.isEmpty &&
        (
            steps.isTransformed || childMeasurables == null ||
                steps.movesContent(layoutWidth(component), layoutHeight(component))
        )

/**
 * Whether a Foundation container grows a repaint that touches [component] by the whole of it: its own
 * [steps move what it paints][stepsMoveContent], or they paint past what they are given, so they spread a change in
 * one part of it into another. A clip declared before a blur leaves the component no paint outsets, and the blur
 * still spreads a change inside the clip. Steps that neither move nor spread anything, such as a background, a border
 * or a clip, paint a part of the component from that part alone, whatever paint outsets it holds for its children, so
 * a Foundation container holding them is walked into, and each of its children answers for itself.
 */
private fun Decoration.repaintsWhole(component: Component): Boolean =
    steps.outsets != NoPaintOutsets || stepsMoveContent(component)

/** Where an area starting at [start] starts, grown by [reach] and held at 0. */
private fun grownStart(
    start: Int,
    reach: Int,
): Int = maxOf(start.toLong() - reach, 0L).toInt()

/**
 * Where an area starting at [start] of [length] ends, grown by [reach] and held at [limit]; in Long, so an area as
 * large as Int allows is not wrapped around by the growth.
 */
private fun grownEnd(
    start: Int,
    length: Int,
    reach: Int,
    limit: Int,
): Int = minOf(start.toLong() + length + reach, limit.toLong()).toInt()

/**
 * Grows [area] by the bounds of each visible child that [asked] touches and that
 * [repaints whole][Decoration.repaintsWhole]. The children of a Foundation container that does not are walked in its
 * place, while it [holds one a repaint may have to grow for][ChildMeasurables.hasChildRepaintingWhole].
 *
 * Each child is tested against [asked], the area the repaint names, and not against [area] as it grows: every
 * decoration step paints from its own component's content alone, so a child the repaint does not touch paints the
 * same pixels as before wherever the grown area reaches it. A decorated container above this one is asked for the
 * area this one grew and adds the children that area touches in turn, so neighbors whose paint bounds overlap repaint
 * one further per decorated container, which changes no pixel.
 *
 * Both rectangles are in the coordinates of the component being repainted, where this container stands at
 * ([x], [y]); [bounds] is scratch space.
 */
private fun ChildMeasurables.addChildrenRepaintingWhole(
    asked: Rectangle,
    area: Rectangle,
    x: Int,
    y: Int,
    bounds: Rectangle,
) {
    layoutPass.fastForEach {
        val child = it.component
        val decoration = it.decoratable?.decoration
        if (!child.isVisible || decoration?.holdsRepaintingWhole != true) return@fastForEach
        child.getBounds(bounds)
        bounds.translate(x, y)
        if (!asked.intersects(bounds)) return@fastForEach
        if (decoration.repaintsWhole(child)) {
            area.add(bounds)
        } else {
            decoration.childMeasurables?.addChildrenRepaintingWhole(asked, area, bounds.x, bounds.y, bounds)
        }
    }
}

/**
 * The point [toContent] maps ([x], [y]) to, both in the component's own coordinates, read from the pixel's center in
 * layout coordinates; null where [toContent] answers false.
 */
private inline fun Decoration.contentPoint(
    x: Int,
    y: Int,
    toContent: (Point2D.Double) -> Boolean,
): Point? {
    val outsets = heldPaintOutsets
    // 0.5: the offset from a pixel's corner, where its integer coordinates point, to its center.
    val point = Point2D.Double(x - outsets.left + 0.5, y - outsets.top + 0.5)
    if (!toContent(point)) return null
    return Point(floor(point.x).toInt() + outsets.left, floor(point.y).toInt() + outsets.top)
}

/** The bounds of this shape grown by [outsets] on each side; this shape itself where [outsets] is none. */
internal fun Shape.outsetBy(outsets: Insets): Shape {
    if (outsets == NoPaintOutsets) return this
    val bounds = bounds2D
    return Rectangle2D.Double(
        bounds.x - outsets.left,
        bounds.y - outsets.top,
        bounds.width + outsets.left + outsets.right,
        bounds.height + outsets.top + outsets.bottom,
    )
}

/**
 * This component's layout bounds, in its parent's layout coordinates; a new [Rectangle]. A component that is not
 * [Decoratable] takes its bounds as its layout bounds. A Foundation parent's paint outsets move its layout origin off
 * its bounds' origin; any other parent places its children against `getInsets()`, which carry its outsets already.
 */
internal val Component.layoutBounds: Rectangle
    get() {
        val outsets = (this as? Decoratable)?.decoration?.heldPaintOutsets ?: NoPaintOutsets
        val parentDecoration = (parent as? Decoratable)?.decoration
        val origin =
            if (parentDecoration?.childMeasurables == null) {
                NoPaintOutsets
            } else {
                parentDecoration.heldPaintOutsets
            }
        return Rectangle(
            x + outsets.left - origin.left,
            y + outsets.top - origin.top,
            (width - outsets.left - outsets.right).coerceAtLeast(0),
            (height - outsets.top - outsets.bottom).coerceAtLeast(0),
        )
    }

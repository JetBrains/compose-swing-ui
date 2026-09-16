package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decorator
import org.jetbrains.compose.swing.foundation.graphics.declaredNodes
import org.jetbrains.compose.swing.foundation.graphics.publishDecoration
import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import java.awt.Dimension
import java.awt.Rectangle
import java.util.function.BiConsumer

/**
 * A layout modifier: declares a [LayoutModifierNode] that measures one child between the constraints its parent
 * offers and the result it reports back, and keeps its state across passes, such as an animation mid-flight. A child
 * declares one through [ConstrainedScope.layout].
 *
 * Its [equals] decides whether a later declaration updates the node: an equal one leaves the node as it is, and an
 * unequal one runs [update], after which the child is measured again unless the node's
 * [shouldAutoInvalidate][LayoutModifierNode.shouldAutoInvalidate] is `false`.
 */
public abstract class LayoutModifierNodeElement<N : LayoutModifierNode> : ParentLayoutNodeElement<N>() {
    /** The capability a Foundation [Layout] must support to interpret this modifier. */
    final override val parentProtocol: ParentProtocol get() = LayoutModifierParentProtocol

    /** Layout modifiers wrap one another, so every declaration remains in order. */
    final override val additive: Boolean get() = true
}

/**
 * The node a [LayoutModifierNodeElement] creates once per slot and keeps for as long as an element of that type
 * fills it.
 *
 * Layout modifiers nest in declaration order: the first one is outermost and measures the next one as its
 * `measurable`. Use [MeasureScope.layout] to return this node's size and to place the placeable measured
 * from that child.
 *
 * A node launches an animation from its `measure` in [coroutineScope], which lives from [onAttach] until
 * [onDetach] cancels it.
 *
 * Its placement can place the content with a layer; see [PlacementScope.placeWithLayer].
 */
public abstract class LayoutModifierNode : ParentLayoutNode() {
    /** The capability of the element this node fills a slot for. */
    final override val parentProtocol: ParentProtocol get() = LayoutModifierParentProtocol

    /** Measures [measurable] under [constraints], and places it, with a layer or without one. */
    public abstract fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult

    /**
     * The least width this node reports when its child is [height] tall.
     *
     * The default runs this node's [measure] against a stand-in for the child, under a bounded cross axis and an
     * unbounded main axis, as androidx's `NodeMeasuringIntrinsics` does, so the answer carries this node's own
     * transform. A node whose `measure` must not run for a query, such as an animation node, overrides the four
     * intrinsic hooks and [intrinsicPlaceable] to ask [measurable] directly; a container's size query then runs its
     * `measure` nowhere.
     */
    public open fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measureIntrinsically(measurable, IntrinsicSize.Min, IntrinsicWidthHeight.Width, height)

    /** The greatest useful width this node reports when its child is [height] tall. */
    public open fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measureIntrinsically(measurable, IntrinsicSize.Max, IntrinsicWidthHeight.Width, height)

    /** The least height this node reports when its child is [width] wide. */
    public open fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measureIntrinsically(measurable, IntrinsicSize.Min, IntrinsicWidthHeight.Height, width)

    /** The greatest useful height this node reports when its child is [width] wide. */
    public open fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measureIntrinsically(measurable, IntrinsicSize.Max, IntrinsicWidthHeight.Height, width)

    /**
     * This node at [width] by [height] as a placeable a container reads alignment lines from while it answers an
     * intrinsic question, or null where it has none. A `Row` or `Column` asking for its own size reads a child's
     * baseline or `alignBy` line from it, so the line holds where this node moves it.
     *
     * The default runs this node's [measure] under exactly [width] by [height] against a stand-in for the child,
     * which [measurable]'s own [intrinsicPlaceable][IntrinsicMeasurable.intrinsicPlaceable] answers, and reads a line
     * this node does not name by running its placement without placing anything. The real child is never measured.
     * Override it with the four intrinsic hooks, returning `measurable.intrinsicPlaceable(width, height)`, where this
     * node's `measure` must not run for a size query; the line then stays where the child puts it.
     */
    public open fun IntrinsicMeasureScope.intrinsicPlaceable(
        measurable: IntrinsicMeasurable,
        width: Int,
        height: Int,
    ): Placeable? {
        val constraints = Constraints.fixed(width, height)
        val result = with(PolicyMeasureScope) { measure(IntrinsicLineMeasurable(measurable), constraints) }
        val leftToRight =
            child
                ?.owner
                ?.panel
                ?.componentOrientation
                ?.isLeftToRight ?: true
        return IntrinsicModifierPlaceable(result, constraints, leftToRight)
    }

    /**
     * Has the container place this node's component again at once, without measuring it: the placement of the
     * last measure result reruns before this returns, reading this node's state as it stands. Unlike androidx's
     * `LayoutModifierNode.invalidatePlacement`, which schedules the placement, a node updates its state first
     * and calls this last. A node whose [shouldAutoInvalidate] is `false` calls this from an update that changes
     * only where it places its content. A container awaiting a layout pass already measures and places the
     * component in that pass. One whose measure read an alignment line of the component, such as a `Row` aligning it
     * by its baseline, is measured again, since the line moves with the content, and so is a container above it that
     * read a line of a container holding the component in its measure; one that read it only in its placement places
     * its children again. Does nothing before the container has received the node.
     */
    public fun invalidatePlacement() {
        val child = child ?: return
        val panel = child.owner.panel
        if (panel.isValid && child.lineReadDuring != LayoutState.Measuring) {
            panel.placeChildrenAgain()
        } else {
            panel.revalidate()
        }
    }

    /**
     * Has the container measure this node's component again and lay it out by that measure, as androidx's
     * `LayoutModifierNode.invalidateMeasurement` does. Does nothing before the container has received the node.
     */
    public fun invalidateMeasurement() {
        child?.owner?.panel?.revalidate()
    }

    /** The layer this node's placement paints, made by its first placement with one. */
    internal var layerOrNull: PlacementLayer? = null
        private set

    /** The layer this node's placement paints, a step of its component's decoration once placed with. */
    internal val layer: PlacementLayer
        get() = layerOrNull ?: PlacementLayer(this, null).also { layerOrNull = it }

    /**
     * Where the last placement put this node, in its component's layout coordinates: the origin it places its content
     * from, and the size its [measure] reported. Null until a placement of a [Decoratable] component writes it.
     */
    internal var box: Rectangle? = null

    /**
     * The record of the child this node lays out, once its container's layout has received the node, and null again
     * once the node leaves it. A node leaving its container stops placing its content with a layer.
     */
    internal var child: ChildMeasurable? = null
        set(value) {
            val previous = field
            field = value
            if (value == null) {
                layerOrNull?.placedWithoutLayer()
                if (standingDecorator != null && standingDecorator === layerOrNull) {
                    standingDecorator = null
                    previous?.decoratable?.let { publishDecoration(it, declaredNodes()) }
                }
            }
            if (value != null && decorationStep != null) value.requireDecoratable
        }

    private var standingDecorator: Decorator? = null

    /**
     * The decorator this node paints its component with, at the node's position in the modifier, or null for
     * none. It paints at the node's own box: the size [measure] reports, where the node is placed.
     *
     * Setting a different instance replaces it and setting null removes it, both repainting the component. Setting the
     * same instance again gathers the decoration again, re-reading its outsets and isOpaque; set it again
     * after the decorator's outsets or isOpaque change. The node keeps it across a detach, and paints with it only
     * while attached.
     *
     * While the node places its content with a layer, the layer paints in the decorator's place: this reads null,
     * and setting null leaves the layer painting.
     *
     * @throws IllegalStateException if set while the node is not attached, if set to a decorator while the
     *   component is not a [Decoratable], or if set to a decorator while the node places its content with a
     *   layer. A decorator set before the component's container has received the node fails as it does.
     */
    public var decorator: Decorator?
        get() = decorationStep.takeIf { it !== layerOrNull }
        set(value) {
            val step = decorationStep
            if (step != null && step === layerOrNull) {
                check(value == null) {
                    "A layout node placing its content with a layer cannot hold a decorator, since the layer paints " +
                        "in its place. Declare the decorator on a node of its own."
                }
                return
            }
            paintWith(value)
        }

    /** What this node paints its component with: its [decorator], or its [layer] while it places with one. */
    internal val decorationStep: Decorator?
        get() = standingDecorator.takeIf { isAttached }

    /** Paints the component with [value] in this node's place; see [decorator]. */
    internal fun paintWith(value: Decorator?) {
        check(isAttached) { "A layout node sets its decorator only while it is attached" }
        val standing = decorationStep
        standingDecorator = value
        // Before the container has received the node, the modifier pass under way gathers the decoration.
        val child = child ?: return
        // A node that paints nothing, and painted nothing, leaves the decoration as it stands.
        if (value == null && standing == null) return
        publishDecoration(child.requireDecoratable, declaredNodes())
    }
}

/** Runs [LayoutModifierNode.measure] against an intrinsic-mode stand-in for the real child. */
private fun LayoutModifierNode.measureIntrinsically(
    measurable: IntrinsicMeasurable,
    intrinsicSize: IntrinsicSize,
    widthHeight: IntrinsicWidthHeight,
    crossAxisSize: Int,
): Int {
    val adapter = IntrinsicMeasurableAdapter(measurable, intrinsicSize, widthHeight)
    return intrinsicExtent(widthHeight, crossAxisSize) {
        with(PolicyMeasureScope) { measure(adapter, it) }
    }
}

/**
 * The child a layout modifier measures while its [LayoutModifierNode.intrinsicPlaceable] is asked for: measuring it
 * answers [source]'s own stand-in at the offered extent, or at the extent [source] prefers within a loose offer.
 */
private class IntrinsicLineMeasurable(
    private val source: IntrinsicMeasurable,
) : Measurable {
    override val parentData: Any? get() = source.parentData

    override fun measure(constraints: Constraints): Placeable {
        val width =
            if (constraints.hasFixedWidth) {
                constraints.minWidth
            } else {
                constraints.constrainWidth(source.maxIntrinsicWidth(constraints.maxHeight))
            }
        val height =
            if (constraints.hasFixedHeight) {
                constraints.minHeight
            } else {
                constraints.constrainHeight(source.maxIntrinsicHeight(width))
            }
        return source.intrinsicPlaceable(width, height) ?: IntrinsicPlaceable(width, height)
    }

    override fun minIntrinsicWidth(height: Int): Int = source.minIntrinsicWidth(height)

    override fun maxIntrinsicWidth(height: Int): Int = source.maxIntrinsicWidth(height)

    override fun minIntrinsicHeight(width: Int): Int = source.minIntrinsicHeight(width)

    override fun maxIntrinsicHeight(width: Int): Int = source.maxIntrinsicHeight(width)

    override fun intrinsicPlaceable(
        width: Int,
        height: Int,
    ): Placeable? = source.intrinsicPlaceable(width, height)

    override fun maximumSize(): Dimension? = source.maximumSize()
}

/**
 * A layout modifier's [result] under [constraints], standing in for its content in an intrinsic question, in
 * [leftToRight] reading order. It places nothing: a line the result does not name is read by replaying its
 * placement, as [MeasureResult.lineAt] does for the real one.
 */
private class IntrinsicModifierPlaceable(
    private val result: MeasureResult,
    constraints: Constraints,
    private val leftToRight: Boolean,
) : Placeable() {
    override val measuredWidth: Int get() = result.width
    override val measuredHeight: Int get() = result.height
    override val width: Int = constraints.constrainWidth(measuredWidth)
    override val height: Int = constraints.constrainHeight(measuredHeight)

    override fun get(alignmentLine: AlignmentLine): Int {
        val originX = centeredOverflowOffset(width, measuredWidth)
        val originY = centeredOverflowOffset(height, measuredHeight)
        return result.lineAt(alignmentLine, originX, originY, measuredWidth, leftToRight)
    }

    override fun alignmentLineAt(
        alignmentLine: AlignmentLine,
        x: Long,
        y: Long,
    ): Int {
        val line = get(alignmentLine)
        return if (line == AlignmentLine.UNSPECIFIED) {
            AlignmentLine.UNSPECIFIED
        } else {
            saturateLineCoordinate(line.toLong() + if (alignmentLine is HorizontalAlignmentLine) y else x)
        }
    }
}

/**
 * Where [alignmentLine] falls in [this] result: the line it names itself, offset by [originX] or [originY], or the
 * lines its placement puts the content it places at, merged by the line's merger, in [isLeftToRight] reading order.
 */
internal fun MeasureResult.lineAt(
    alignmentLine: AlignmentLine,
    originX: Long,
    originY: Long,
    parentWidth: Int,
    isLeftToRight: Boolean,
): Int {
    alignmentLines[alignmentLine]?.let { provided ->
        return saturateLineCoordinate(provided + if (alignmentLine is HorizontalAlignmentLine) originY else originX)
    }
    return AlignmentLinePlacementScope(alignmentLine, originX, originY, parentWidth, isLeftToRight)
        .also { scope -> with(this) { scope.placeChildren() } }
        .position
}

/**
 * Replays a placement without placing anything, merging where each content placed puts [alignmentLine] by the
 * line's merger, in [isLeftToRight] reading order.
 */
internal class AlignmentLinePlacementScope(
    private val alignmentLine: AlignmentLine,
    private val originX: Long,
    private val originY: Long,
    override val parentWidth: Int,
    override val isLeftToRight: Boolean,
) : PlacementScope() {
    var position: Int = AlignmentLine.UNSPECIFIED
        private set

    override fun Placeable.place(
        x: Int,
        y: Int,
        zIndex: Float,
    ) {
        record(this, originX + x.toLong(), originY + y.toLong())
    }

    override fun Placeable.placeRelative(
        x: Int,
        y: Int,
        zIndex: Float,
    ) {
        record(this, originX + relativeX(this, x), originY + y.toLong())
    }

    private fun record(
        placeable: Placeable,
        x: Long,
        y: Long,
    ) {
        val placed = placeable.alignmentLineAt(alignmentLine, x, y)
        if (placed == AlignmentLine.UNSPECIFIED) return
        position = if (position == AlignmentLine.UNSPECIFIED) placed else alignmentLine.merger(position, placed)
    }
}

/**
 * Replays placements without placing anything, merging each line a content placed puts by the line's merger. A line a
 * result names takes the place of that line of the content the result places. One container reuses it for every
 * [ChildMeasurables.alignmentLinesOf].
 */
internal class LineMergingPlacementScope :
    PlacementScope(),
    BiConsumer<AlignmentLine, Int> {
    /**
     * Moves each time the container, or a container inside it, places its children again without a measure: the lines
     * worked out before it moved are dirty.
     */
    var generation = 0

    /**
     * Whether a change to the reads the last replay recorded places the container's children again: true for a replay
     * of a result the container has not placed, until it places the result it measured last or replays the placed one.
     */
    var observesReplay = false

    /** The lines merged so far, a map made by the first line merged and handed over by [takeLines]. */
    private var merged: Map<AlignmentLine, Int> = emptyMap()

    /** What [replay] merges: the result, its origin and its reading order. */
    private lateinit var replayed: MeasureResult
    private var replayedX = 0L
    private var replayedY = 0L
    private var replayedLeftToRight = true

    // Stored once, reading inputs from fields, so observing a replay allocates nothing per call.
    val replay: () -> Unit = { merge(replayed, replayedX, replayedY, replayedLeftToRight) }

    private var originX = 0L
    private var originY = 0L

    override var parentWidth: Int = 0
        private set

    override var isLeftToRight: Boolean = true
        private set

    /** The lines each result being replayed names, outermost first. */
    private val named = ArrayList<Map<AlignmentLine, Int>>()

    /** Has [replay] merge the lines [result] puts with its origin at ([x], [y]), in [leftToRight] reading order. */
    fun replaying(
        result: MeasureResult,
        x: Long,
        y: Long,
        leftToRight: Boolean,
    ) {
        merged = emptyMap()
        replayed = result
        replayedX = x
        replayedY = y
        replayedLeftToRight = leftToRight
    }

    /** The lines merged since [replaying], or an empty map where there are none. */
    fun takeLines(): Map<AlignmentLine, Int> {
        val lines = merged
        merged = emptyMap()
        return lines
    }

    /** Merges the lines [result] names, and then those of the content its placement places, from ([x], [y]). */
    fun merge(
        result: MeasureResult,
        x: Long,
        y: Long,
        leftToRight: Boolean,
    ) {
        val standingX = originX
        val standingY = originY
        val standingWidth = parentWidth
        val standingOrder = isLeftToRight
        originX = x
        originY = y
        parentWidth = result.width
        isLeftToRight = leftToRight
        val own = result.alignmentLines
        own.forEach(this)
        val names = own.isNotEmpty()
        if (names) named += own
        try {
            with(result) { this@LineMergingPlacementScope.placeChildren() }
        } finally {
            if (names) named.removeAt(named.lastIndex)
            originX = standingX
            originY = standingY
            parentWidth = standingWidth
            isLeftToRight = standingOrder
        }
    }

    /** Merges [lines], each measured from ([x], [y]). */
    fun merge(
        lines: Map<AlignmentLine, Int>,
        x: Long,
        y: Long,
    ) {
        if (lines.isEmpty()) return
        val standingX = originX
        val standingY = originY
        originX = x
        originY = y
        lines.forEach(this)
        originX = standingX
        originY = standingY
    }

    /** Merges [line] at [coordinate], unless a result being replayed names it. */
    fun merge(
        line: AlignmentLine,
        coordinate: Long,
    ) {
        named.fastForEach { if (line in it) return }
        val position = saturateLineCoordinate(coordinate)
        val lines = merged as? HashMap ?: HashMap<AlignmentLine, Int>().also { merged = it }
        val standing = lines[line]
        lines[line] = if (standing == null) position else line.merger(standing, position)
    }

    /** Merges [line] at [position] from the origin. */
    override fun accept(
        line: AlignmentLine,
        position: Int,
    ) {
        merge(line, position + if (line is HorizontalAlignmentLine) originY else originX)
    }

    override fun Placeable.place(
        x: Int,
        y: Int,
        zIndex: Float,
    ) {
        mergeAlignmentLines(this@LineMergingPlacementScope, originX + x, originY + y)
    }

    override fun Placeable.placeRelative(
        x: Int,
        y: Int,
        zIndex: Float,
    ) {
        mergeAlignmentLines(this@LineMergingPlacementScope, originX + relativeX(this, x), originY + y)
    }
}

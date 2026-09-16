package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol

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
 */
public abstract class LayoutModifierNode : ParentLayoutNode() {
    /** The capability of the element this node fills a slot for. */
    final override val parentProtocol: ParentProtocol get() = LayoutModifierParentProtocol

    /** Measures [measurable] under [constraints], and places it. */
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
     * intrinsic hooks to ask [measurable] directly; a container's size query then runs its `measure` nowhere.
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
     * Has the container place this node's component again at once, without measuring it: the placement of the
     * last measure result reruns before this returns, reading this node's state as it stands. Unlike androidx's
     * `LayoutModifierNode.invalidatePlacement`, which schedules the placement, a node updates its state first
     * and calls this last. A node whose [shouldAutoInvalidate] is `false` calls this from an update that changes
     * only where it places its content. A container awaiting a layout pass already measures and places the
     * component in that pass, and one whose measure read the component's baseline, such as a `Row` aligning it
     * by its baseline, is measured again, since the baseline moves with the content. Does nothing before the
     * container has received the node.
     */
    public fun invalidatePlacement() {
        val child = child ?: return
        val panel = child.owner.panel
        if (panel.isValid && !child.lineReadByMeasure) {
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

    /**
     * The record of the child this node lays out, once its container's layout has received the node, and null again
     * once the node leaves it.
     */
    internal var child: ChildMeasurable? = null
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

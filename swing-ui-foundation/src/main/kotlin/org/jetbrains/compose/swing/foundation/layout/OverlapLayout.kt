package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component
import java.awt.Dimension

/**
 * The measure policy behind [Box]: it stacks every visible child in the same place, each at the extent it
 * prefers, capped at the container's, and sitting where the alignment it declares puts it, or where
 * [alignment] puts it when it declares none. Which of the stacked children is on top is [OverlapPanel]'s
 * to arrange; this manager only tells it that a child's constraint has been written again.
 *
 * A child that declared a match of its container's extent is offered the whole inner rectangle in place
 * of the extent it prefers, and is passed over while the container's own extent is worked out - so it
 * takes the size the other children settle rather than adding to it.
 *
 * [alignment] is the value the current composition declares; the container that owns this manager
 * writes it as it changes.
 *
 * One manager lays out the one container it belongs to: a box builds one alongside the panel it creates.
 */
internal class OverlapLayout(
    var alignment: Alignment,
) : MeasurePolicyLayout(),
    MeasurePolicy {
    override val policy: MeasurePolicy get() = this

    /** What the last [measure] settled on, which [OverlapResult.placeChildren] places. */
    private val result = OverlapResult()

    /**
     * Refuses a constraint of any kind but a box's own, the way `BorderLayout` and `GridBagLayout`
     * refuse one they cannot read, and stacks the children again.
     *
     * A child registered again is one whose modifier ran again, which is how a changed zIndex arrives:
     * the component keeps its parent, and only what it is registered under changes. The removal that
     * precedes such a write has given up the value to compare against, so the panel stacks its children
     * again either way; the reordering is what is held to whatever moved.
     */
    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        require(constraints == null || constraints is BoxConstraint) { foreignConstraint(component, constraints) }
        super.addLayoutComponent(component, constraints)
        (component.parent as? OverlapPanel)?.stackingOrder?.restack()
    }

    /**
     * Each child at the extent it prefers, capped at the container's and at an explicit `maximumSize`,
     * and each placed where its own alignment or the container's puts it.
     *
     * A child is never asked what it prefers along an axis it fills, or along either where it matches
     * the container's extent; it is offered the whole of the container there, and its alignment places
     * it in whatever an explicit `maximumSize` leaves free.
     *
     * A child that matches is measured after the others have settled the container's extent, and
     * against that extent, so it takes what they settled rather than adding to it. Under the exact
     * constraints a layout pass offers, what they settled is the container's own inner extent.
     */
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        var width = 0
        var height = 0
        var matching = false
        measurables.forEach { child ->
            if (boxConstraintOf(child)?.matchesParentSize == true) {
                matching = true
                return@forEach
            }
            val placeable = child.measure(offerTo(child, constraints))
            width = maxOf(width, placeable.width)
            height = maxOf(height, placeable.height)
        }
        width = constraints.constrainWidth(width)
        height = constraints.constrainHeight(height)
        if (matching) {
            val settled = Constraints(width, width, height, height)
            measurables.forEach { child ->
                if (boxConstraintOf(child)?.matchesParentSize == true) child.measure(offerTo(child, settled))
            }
        }
        return result.settled(children = measurables, width = width, height = height)
    }

    /**
     * The extent the container asks for: the largest extent along either axis among the children that do
     * not match the container's own. A container whose children all match it asks for nothing.
     */
    override fun MeasureScope.intrinsicSize(measurables: List<Measurable>): MeasureResult {
        var width = 0
        var height = 0
        measurables.forEach { child ->
            if (boxConstraintOf(child)?.matchesParentSize == true) return@forEach
            val placeable = child.measure(Constraints.Unbounded)
            width = maxOf(width, placeable.width)
            height = maxOf(height, placeable.height)
        }
        return intrinsicResult(width, height)
    }

    /** Where in the stack [child] declared it sits, and `0f` where it declared nothing. */
    internal fun zIndexOf(child: Component): Float = (measurables.declaredBy(child) as? BoxConstraint)?.zIndex ?: 0f

    /** What the last [measure] settled on, and where each child it measured goes. */
    private inner class OverlapResult : MeasureResult {
        override var width: Int = 0
            private set

        override var height: Int = 0
            private set

        private var children: List<Measurable> = emptyList()

        /** Records what [measure] settled on, which is the extent its children are aligned in. */
        fun settled(
            children: List<Measurable>,
            width: Int,
            height: Int,
        ): MeasureResult {
            this.children = children
            this.width = width
            this.height = height
            return this
        }

        override fun PlacementScope.placeChildren() {
            val inner = Dimension(width, height)
            children.forEach { child ->
                val placeable = child.measured
                val extent = Dimension(placeable.width, placeable.height)
                val place = (boxConstraintOf(child)?.alignment ?: alignment).align(extent, inner, orientation)
                placeable.place(place.x, place.y)
            }
        }
    }
}

/** What [child] declared to this box, or `null` where it declared nothing. */
private fun boxConstraintOf(child: Measurable): BoxConstraint? = child.layoutConstraint as? BoxConstraint

/**
 * The constraints [child] is measured under: the whole of what the container was offered on each axis
 * the child fills or matches, otherwise as much of it as the child prefers - in either case no more than
 * an explicit `maximumSize`.
 *
 * A fill has nothing to fill where the axis is unbounded, so there the child keeps what it prefers.
 */
private fun OverlapLayout.offerTo(
    child: Measurable,
    constraints: Constraints,
): Constraints {
    val constraint = boxConstraintOf(child)
    val matches = constraint?.matchesParentSize == true
    val component = child.component
    val maximum = if (component.isMaximumSizeSet) component.maximumSize else null
    val width = ceiling(constraints.maxWidth, maximum?.width)
    val height = ceiling(constraints.maxHeight, maximum?.height)
    val fillsWidth = (matches || constraint?.fillsWidth == true) && constraints.hasBoundedWidth
    val fillsHeight = (matches || constraint?.fillsHeight == true) && constraints.hasBoundedHeight
    return Constraints(
        minWidth = if (fillsWidth) width else 0,
        maxWidth = width,
        minHeight = if (fillsHeight) height else 0,
        maxHeight = height,
    )
}

/**
 * As much of [available] as an explicit `maximumSize` of [ceiling] leaves. A maximum below zero holds the
 * child to nothing on that axis.
 */
private fun ceiling(
    available: Int,
    ceiling: Int?,
): Int = if (ceiling == null) available else minOf(available, ceiling.coerceAtLeast(0))

/** The message refusing a constraint a box cannot read. */
private fun foreignConstraint(
    child: Component,
    constraint: Any?,
): String =
    "A Box places a child by the alignment it is declared with, and by align() / matchParentSize() / " +
        "fillWidth() / fillHeight() / " +
        "zIndex() on the child's own modifier, so '$child' can carry no layout constraint, but it was " +
        "added under '$constraint'."

/**
 * The panel behind a [Box]. `JComponent.isOptimizedDrawingEnabled` asks whether a component tiles its
 * children - whether it can promise that they never overlap - and this one cannot, so it answers false
 * the way `JLayeredPane` does. Painting one child then repaints whatever it overlaps, in place of the
 * shortcut the promise would allow.
 *
 * The order its children are declared in, and the component array derived from it, are [stackingOrder]'s.
 */
internal class OverlapPanel(
    private val overlap: OverlapLayout,
) : ConstrainedPanel(overlap) {
    /** Where each child stands among its siblings; a child declares that with a zIndex. */
    val stackingOrder: StackingOrder = StackingOrder(this) { overlap.zIndexOf(it) }

    override fun isOptimizedDrawingEnabled(): Boolean = false

    /**
     * Takes [index] as a place in declaration order, records the arriving child there, and stacks the
     * children again.
     *
     * Wherever the container puts the child in the component array, [StackingOrder.restack] gives it its
     * place there, because where in that array a child belongs is the whole stack's business rather than
     * this one child's.
     */
    override fun addImpl(
        comp: Component,
        constraints: Any?,
        index: Int,
    ) {
        try {
            super.addImpl(comp, constraints, index)
        } finally {
            stackingOrder.declared(comp, index)
        }
        stackingOrder.restack()
    }

    override fun remove(index: Int) {
        val dropped = getComponent(index)
        stackingOrder.dropped(dropped)
        super.remove(index)
    }

    override fun removeAll() {
        stackingOrder.cleared()
        super.removeAll()
    }
}

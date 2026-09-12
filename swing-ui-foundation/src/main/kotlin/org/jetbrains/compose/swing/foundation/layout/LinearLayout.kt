package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component
import java.awt.Dimension
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The measure policy behind [Row] and [Column]: it stacks the visible children along [axis] at their
 * preferred extent, and hands any leftover room to [arrangement] to place instead of the children absorbing it.
 *
 * Weighted children are the exception: they share the leftover extent along the axis in proportion to
 * their weights, and a child with an explicit `maximumSize` takes no more than that allows, leaving the rest empty.
 *
 * Across the axis a child keeps the extent it prefers, capped at the container's, and sits where its
 * declared alignment puts it, or [alignment] when it declared none. A child that declared a cross-axis
 * fill takes the container's whole extent instead, leaving no room for either alignment to place it.
 * Children that declared a baseline alignment are the other exception: they sit so that the baselines
 * their components report fall on one line, and the container holds the deepest baseline above that line
 * and the deepest remainder below it.
 *
 * [arrangement] and [alignment] are the values the current composition declares; the container that
 * owns this manager writes them as they change.
 *
 * One manager lays out the one container it belongs to: a row and a column each build one alongside the
 * panel they create.
 */
internal class LinearLayout(
    val axis: LayoutAxis,
    var arrangement: AxisArrangement,
    var alignment: AxisAlignment,
) : MeasurePolicyLayout(),
    MeasurePolicy {
    override val policy: MeasurePolicy get() = this

    /** The working room a pass takes, kept for the next one - see [PassRoom]. */
    internal val room: PassRoom = PassRoom()

    /** What the last [measure] settled on, which [LinearResult.placeChildren] places. */
    private val result = LinearResult()

    /**
     * Refuses a constraint of any kind but a row's or a column's own, the way `BorderLayout` and
     * `GridBagLayout` refuse one they cannot read.
     */
    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        require(constraints == null || constraints is LinearConstraint) {
            foreignConstraint(component, constraints)
        }
        super.addLayoutComponent(component, constraints)
    }

    /**
     * The extent each child occupies, in declaration order, and with it the extent the container takes.
     *
     * A child with no weight takes its preferred extent from whatever room is still unclaimed, reserving
     * the gap after it from that same room. What remains, minus the gaps between weighted children, is
     * what they share. A container narrower than its children's combined extent runs out of room partway
     * through: the child that empties it keeps whatever was left, and every child after it takes none at
     * all.
     *
     * An unbounded main axis has nothing to divide, so the weighted children are distributed against the
     * least extent the constraints ask for instead, which collapses them. That is the answer for a
     * caller who measures a row under [Constraints.Unbounded]; a container asking what a row prefers
     * asks [intrinsicSize], which divides nothing.
     */
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val mainMax = axis.mainMax(constraints)
        val crossMax = axis.crossMax(constraints)
        val hasBoundedMain = axis.hasBoundedMain(constraints)
        val hasBoundedCross = axis.hasBoundedCross(constraints)
        val sizes = room.sizes(measurables.size)
        val spacing = arrangement.spacing
        var claimed = 0
        var weightedCount = 0
        measurables.forEachIndexed { index, child ->
            if (weightOf(child) != null) {
                // distributeWeights writes every weighted index, out of what the rest leave unclaimed.
                weightedCount++
                return@forEachIndexed
            }
            val unclaimed =
                if (!hasBoundedMain) {
                    Int.MAX_VALUE
                } else {
                    saturatedInt(mainMax.toLong() - claimed).coerceAtLeast(0)
                }
            val placeable =
                child.measure(
                    crossOf(
                        child,
                        crossMax,
                        hasBoundedCross,
                        mainMin = 0,
                        mainMax = unclaimed,
                    ),
                )
            val size = axis.main(placeable)
            sizes[index] = size
            claimed =
                saturatedInt(
                    claimed.toLong() + size + minOf(spacing.toLong(), unclaimed.toLong() - size),
                )
        }
        if (weightedCount > 0) {
            val target = if (hasBoundedMain) mainMax else axis.mainMin(constraints)
            val share =
                (target.toLong() - claimed - spacing.toLong() * (weightedCount - 1))
                    .coerceIn(0, Int.MAX_VALUE.toLong())
                    .toInt()
            distributeWeights(measurables, sizes, share, crossMax, hasBoundedCross)
        }
        return result.settled(measurables, sizes, constraints)
    }

    /**
     * The extent the container asks for: enough room along the axis for every child to occupy what it
     * wants, plus the gap the arrangement holds between each adjacent pair, the widest child across it.
     *
     * A child claiming no share of the leftover space contributes the extent it asks for. A weighted
     * child is granted its weight's share of what the others leave, so the extent it asks for implies an
     * extent for the weighted children together: that share divided into it. The container holds the
     * largest of those implications, which is what it takes for none of them to be cut short.
     *
     * Across the axis, a child sitting on the shared baseline splits what it asks for in two, the part
     * above that line and the part below, and the container holds the deepest of each - so it is as tall
     * as it takes for every such child to fit on one line.
     */
    override fun MeasureScope.intrinsicSize(measurables: List<Measurable>): MeasureResult {
        var main = 0L
        var cross = 0
        var totalWeight = 0f
        var weightUnitSpace = 0
        var aboveBaseline = 0
        var belowBaseline = 0
        measurables.forEach { child ->
            val placeable = child.measure(Constraints.Unbounded)
            val mainExtent = axis.main(placeable)
            val crossExtent = axis.cross(placeable)
            val weight = weightOf(child)?.weight
            if (weight == null) {
                main += mainExtent
            } else {
                totalWeight += weight
                weightUnitSpace = maxOf(weightUnitSpace, (cappedMainSize(child, mainExtent) / weight).roundToInt())
            }
            val baseline = baselineOf(child, mainExtent, crossExtent)
            if (baseline >= 0) {
                aboveBaseline = maxOf(aboveBaseline, baseline)
                belowBaseline = maxOf(belowBaseline, crossExtent - baseline)
            }
            cross = maxOf(cross, crossExtent)
        }
        cross = maxOf(cross, aboveBaseline + belowBaseline)
        // AndroidX rounds the greatest size one weight unit must cover before multiplying it by the
        // total weight. The same sequence keeps the one-unit implication of a fractional share instead
        // of letting it disappear when the total weight happens to multiply back to an integer.
        main += (weightUnitSpace * totalWeight).roundToInt()
        if (measurables.isNotEmpty()) main += arrangement.spacing.toLong() * (measurables.size - 1)
        val size = axis.dimension(saturatedInt(main).coerceAtLeast(0), cross)
        return intrinsicResult(size.width, size.height)
    }

    /**
     * What the last [measure] settled on. One of these belongs to one policy, and so to the one container
     * it lays out: a pass runs to completion on the event dispatch thread before the next begins.
     */
    private inner class LinearResult : MeasureResult {
        override var width: Int = 0
            private set

        override var height: Int = 0
            private set

        /** The children the pass measured, and their extents along the axis. */
        private var children: List<Measurable> = emptyList()
        private var sizes: IntArray = IntArray(0)

        /**
         * Records what [measure] settled on, and the extent the container occupies under [constraints].
         *
         * Both extents are held to what was offered, which is also what the children are placed within.
         * A container reporting more than it was offered is placed at the offer all the same - its own
         * measurable holds it there - so its children would be arranged in an extent it does not occupy.
         */
        fun settled(
            children: List<Measurable>,
            sizes: IntArray,
            constraints: Constraints,
        ): MeasureResult {
            this.children = children
            this.sizes = sizes
            var main = 0L
            var cross = 0
            var aboveBaseline = 0
            var belowBaseline = 0
            children.forEachIndexed { index, child ->
                main += sizes[index]
                val crossExtent = axis.cross(child.measured)
                val baseline = baselineOf(child, sizes[index], crossExtent)
                if (baseline >= 0) {
                    aboveBaseline = maxOf(aboveBaseline, baseline)
                    belowBaseline = maxOf(belowBaseline, crossExtent - baseline)
                }
                cross = maxOf(cross, crossExtent)
            }
            // A child on the shared baseline is offset by what the deepest one above the line leaves, so
            // the pair takes more room across the axis than the tallest child alone asks for.
            cross = maxOf(cross, aboveBaseline + belowBaseline)
            if (children.isNotEmpty()) main += arrangement.spacing.toLong() * (children.size - 1)
            val size = axis.dimension(saturatedInt(main), cross)
            width = constraints.constrainWidth(size.width)
            height = constraints.constrainHeight(size.height)
            return this
        }

        override fun PlacementScope.placeChildren() {
            if (children.isEmpty()) return
            val extent = Dimension(width, height)
            val availableCross = axis.cross(extent)
            val positions = room.positions(children.size)
            arrangement.arrange(axis.main(extent), sizes, orientation, positions)
            val sharedBaseline = sharedBaseline(children)
            children.forEachIndexed { index, child ->
                val placeable = child.measured
                val crossSize = axis.cross(placeable)
                val baseline = baselineOf(child, sizes[index], crossSize)
                val crossAlignment = linearConstraintOf(child)?.alignment ?: alignment
                val cross =
                    if (baseline >= 0) {
                        sharedBaseline - baseline
                    } else {
                        crossAlignment.align(crossSize, availableCross, orientation)
                    }
                placeable.place(axis.x(positions[index], cross), axis.y(positions[index], cross))
            }
        }
    }
}

/** The message refusing a constraint a row or a column cannot read. */
private fun foreignConstraint(
    child: Component,
    constraint: Any?,
): String =
    "A Row or Column places a child by the arrangement and alignment it is declared with, and by " +
        "weight() / align() on the child's own modifier, so '$child' can carry no layout constraint, " +
        "but it was added under '$constraint'."

/** What [child] declared to this row or column, or `null` where it declared nothing. */
private fun linearConstraintOf(child: Measurable): LinearConstraint? = child.layoutConstraint as? LinearConstraint

/** What [child] claims of the leftover space, or `null` where it claims none. */
private fun weightOf(child: Measurable): WeightPlacement? = linearConstraintOf(child)?.weight

/**
 * The constraints [child] is measured under: [mainMin] to [mainMax] along the axis, and across it the
 * whole of [crossMax] where the child declared a cross-axis fill, otherwise as much of it as the child
 * prefers - in either case no more than an explicit `maximumSize`, the same ceiling a weighted child's
 * main-axis share is held to. A maximum below zero, which a component may carry as readily as any other,
 * holds the child to nothing on that axis.
 *
 * A fill has nothing to fill where the cross axis is unbounded, so there the child keeps what it prefers.
 */
private fun LinearLayout.crossOf(
    child: Measurable,
    crossMax: Int,
    hasBoundedCross: Boolean,
    mainMin: Int,
    mainMax: Int,
): Constraints {
    val component = child.component
    val ceiling =
        if (component.isMaximumSizeSet) {
            minOf(crossMax, axis.cross(component.maximumSize).coerceAtLeast(0))
        } else {
            crossMax
        }
    val fills = linearConstraintOf(child)?.fillsCrossAxis == true && hasBoundedCross
    return axis.constraints(mainMin, mainMax, if (fills) ceiling else 0, ceiling)
}

/**
 * Where the shared baseline falls below the container's leading edge across the axis: the deepest
 * baseline any child sitting on it reports, which is what it takes for none of them to be pushed past
 * that edge.
 */
private fun LinearLayout.sharedBaseline(children: List<Measurable>): Int {
    var deepest = 0
    children.forEach { child ->
        val placeable = child.measured
        deepest = maxOf(deepest, baselineOf(child, axis.main(placeable), axis.cross(placeable)))
    }
    return deepest
}

/**
 * Where [child] carries its baseline when it occupies [mainSize] along the axis and [crossSize] across
 * it, or `-1` where the child takes no part in its container's shared baseline: it declared no baseline
 * alignment, it takes the container's whole extent across the axis and so has nowhere left to sit, or
 * its component reports no baseline of its own.
 */
private fun LinearLayout.baselineOf(
    child: Measurable,
    mainSize: Int,
    crossSize: Int,
): Int {
    val constraint = linearConstraintOf(child)
    // An empty component is asked for nothing, the guard GroupLayout holds one to before it asks, and
    // Component.getBaseline refuses a negative extent outright.
    val onTheLine =
        constraint != null &&
            !constraint.fillsCrossAxis &&
            constraint.alignment == BaselineAxisAlignment &&
            mainSize > 0 &&
            crossSize > 0
    if (!onTheLine) return -1
    val size = axis.dimension(mainSize, crossSize)
    return child.baseline(size.width, size.height)
}

/**
 * Shares [share] pixels among the weighted children of [children], in proportion to their weights. An
 * unweighted child already holds the extent it claimed and is passed over. The total those shares are
 * taken against is summed from the same declarations that hand them out, so there is one account of what
 * was claimed, not two to keep in step.
 *
 * Rounding each share on its own would lose or gain pixels against the total, so the difference is
 * handed out a pixel at a time to the leading weighted children.
 */
private fun LinearLayout.distributeWeights(
    children: List<Measurable>,
    sizes: IntArray,
    share: Int,
    crossMax: Int,
    hasBoundedCross: Boolean,
) {
    var totalWeight = 0.0
    children.forEach { child ->
        totalWeight += (weightOf(child) ?: return@forEach).weight.toDouble()
    }
    val unit = share / totalWeight
    var remainder = share
    children.forEach { child ->
        val weighted = weightOf(child) ?: return@forEach
        remainder -= (unit * weighted.weight).roundToInt()
    }
    children.forEachIndexed { index, child ->
        val weighted = weightOf(child) ?: return@forEachIndexed
        val correction = remainder.sign
        remainder -= correction
        val granted = ((unit * weighted.weight).roundToInt() + correction).coerceAtLeast(0)
        val ceiling = cappedMainSize(child, granted)
        val placeable =
            child.measure(
                crossOf(child, crossMax, hasBoundedCross, if (weighted.fill) ceiling else 0, ceiling),
            )
        sizes[index] = axis.main(placeable)
    }
}

/**
 * The [wanted] extent along the axis, held to an explicit `maximumSize` on [child] - all such a child
 * would occupy of a larger extent, and so all its container has reason to hold for it.
 */
private fun LinearLayout.cappedMainSize(
    child: Measurable,
    wanted: Int,
): Int {
    val component = child.component
    if (!component.isMaximumSizeSet) return wanted
    return minOf(wanted, axis.main(component.maximumSize).coerceAtLeast(0))
}

/** [value] as an extent accumulator: it saturates rather than wrapping into the other side of zero. */
private fun saturatedInt(value: Long): Int = value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

/**
 * The working room one layout pass needs: the extents of the children along the axis, and the offsets it
 * places them at.
 *
 * Held between passes rather than allocated per pass. One of these belongs to one [LinearLayout], and so
 * to the one container that policy lays out; a pass runs to completion on the event dispatch thread
 * before the next begins - so no two passes hold this at once. An extent read mid-pass reaches a child's
 * own manager, which has room of its own.
 */
internal class PassRoom {
    private var sizes = IntArray(0)
    private var positions = IntArray(0)

    /**
     * Room for [count] extents, reused where the child count has not moved - which is every pass over a
     * container holding the children it already had. A pass writes every index while it measures, before
     * any of them is read, so no extent it hands back is one the pass before it wrote.
     */
    fun sizes(count: Int): IntArray {
        if (sizes.size != count) sizes = IntArray(count)
        return sizes
    }

    /**
     * Room for [count] offsets, cleared before it is handed over. An [Arrangement] writes the offsets it
     * means to place, and one that leaves an index alone leaves that child at zero rather than at
     * whatever the pass before it wrote.
     */
    fun positions(count: Int): IntArray {
        if (positions.size != count) {
            positions = IntArray(count)
        } else {
            positions.fill(0)
        }
        return positions
    }
}

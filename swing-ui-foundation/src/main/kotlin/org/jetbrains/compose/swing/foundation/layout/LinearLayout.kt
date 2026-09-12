package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import java.awt.Rectangle
import java.util.IdentityHashMap
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The layout manager behind [Row] and [Column]: it stacks the visible children along [axis] at their
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
) : LayoutManager2 {
    /** The extents measured for this container's children, see [PreferredSizeCache]. */
    private val measured = PreferredSizeCache()

    /**
     * What each child was registered under. A child added under nothing carries none, and is laid out
     * at the size it prefers, where the container's own alignment puts it.
     */
    internal val declared = IdentityHashMap<Component, LinearConstraint>()

    /** The working room a pass takes, kept for the next one - see [PassRoom]. */
    internal val room: PassRoom = PassRoom()

    /**
     * Takes what [component] was registered under, and refuses a constraint of any other kind the way
     * `BorderLayout` and `GridBagLayout` refuse one they cannot read.
     */
    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        when (constraints) {
            null -> declared.remove(component)
            is LinearConstraint -> declared[component] = constraints
            else -> throw IllegalArgumentException(foreignConstraint(component, constraints))
        }
    }

    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ): Unit = Unit

    /** Gives up what [component] was registered under, and the extent measured for it. */
    override fun removeLayoutComponent(component: Component) {
        measured.forget(component)
        declared.remove(component)
    }

    override fun invalidateLayout(target: Container): Unit = measured.invalidate()

    override fun preferredLayoutSize(parent: Container): Dimension = combinedSize(parent, ::preferredSizeOf)

    /**
     * Minimum extents are read fresh every time. [measured] holds preferred extents only, and a container
     * is asked for its minimum once per validate rather than once per pass.
     */
    override fun minimumLayoutSize(parent: Container): Dimension = combinedSize(parent) { it.minimumSize }

    /** The extent [child] prefers, measured once and reused until the next invalidation. */
    internal fun preferredSizeOf(child: Component): Dimension = measured.preferredSizeOf(child)

    /** A row or column takes any extent it is offered and places the surplus by its arrangement. */
    override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    /** What the row's first visible child reports; see [firstVisibleChildAlignment]. */
    override fun getLayoutAlignmentX(target: Container): Float = firstVisibleChildAlignment(target) { it.alignmentX }

    /** What the row's first visible child reports; see [firstVisibleChildAlignment]. */
    override fun getLayoutAlignmentY(target: Container): Float = firstVisibleChildAlignment(target) { it.alignmentY }

    override fun layoutContainer(parent: Container) {
        val children = room.visibleChildrenOf(parent)
        if (children.isEmpty()) return
        val insets = parent.insets
        val inner =
            Rectangle(
                insets.left,
                insets.top,
                parent.width - insets.left - insets.right,
                parent.height - insets.top - insets.bottom,
            )
        val availableMain = axis.main(inner).coerceAtLeast(0)
        val availableCross = axis.cross(inner).coerceAtLeast(0)
        val orientation = parent.componentOrientation
        val sizes = measureMainAxis(children, availableMain)
        val positions = room.positions(sizes.size)
        arrangement.arrange(availableMain, sizes, orientation, positions)
        val sharedBaseline = sharedBaseline(children, sizes, availableCross)
        children.forEachIndexed { index, child ->
            val crossSize = crossSize(child, availableCross)
            val baseline = baselineOf(child, sizes[index], crossSize)
            val crossAlignment = declared[child]?.alignment ?: alignment
            axis.place(
                component = child,
                main = axis.mainOrigin(inner) + positions[index],
                cross =
                    axis.crossOrigin(inner) +
                        if (baseline >= 0) {
                            sharedBaseline - baseline
                        } else {
                            crossAlignment.align(crossSize, availableCross, orientation)
                        },
                mainSize = sizes[index],
                crossSize = crossSize,
            )
        }
    }
}

/** The message refusing a constraint a row or a column cannot read. */
private fun foreignConstraint(
    child: Component,
    constraint: Any,
): String =
    "A Row or Column places a child by the arrangement and alignment it is declared with, and by " +
        "weight() / align() on the child's own modifier, so '$child' can carry no layout constraint, " +
        "but it was added under '$constraint'."

/**
 * The extent [child] occupies across the axis, of the [available] extent: the whole of it where the child
 * declared a cross-axis fill, otherwise the extent it prefers, and in either case no more than an explicit
 * `maximumSize` - the same ceiling a weighted child's main-axis share is held to.
 */
private fun LinearLayout.crossSize(
    child: Component,
    available: Int,
): Int {
    val requested =
        if (declared[child]?.fillsCrossAxis == true) {
            available
        } else {
            axis.cross(preferredSizeOf(child)).coerceAtMost(available)
        }
    val ceiling = if (child.isMaximumSizeSet) axis.cross(child.maximumSize) else requested
    return minOf(requested, ceiling)
}

/**
 * Where the shared baseline falls below the container's leading edge across the axis: the deepest
 * baseline any child sitting on it reports, which is what it takes for none of them to be pushed past
 * that edge.
 */
private fun LinearLayout.sharedBaseline(
    children: List<Component>,
    sizes: IntArray,
    availableCross: Int,
): Int {
    var deepest = 0
    children.forEachIndexed { index, child ->
        deepest = maxOf(deepest, baselineOf(child, sizes[index], crossSize(child, availableCross)))
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
    child: Component,
    mainSize: Int,
    crossSize: Int,
): Int {
    val constraint = declared[child]
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
    return child.getBaseline(size.width, size.height)
}

/**
 * The extent the container asks for: enough room along the axis for every child to occupy what it
 * wants, plus the gap the arrangement holds between each adjacent pair, the widest child across it,
 * and the container's insets.
 *
 * A child claiming no share of the leftover space contributes the extent it asks for. A weighted child
 * is granted its weight's share of what the others leave, so the extent it asks for implies an extent
 * for the weighted children together: that share divided into it. The container holds the largest of
 * those implications, which is what it takes for none of them to be cut short.
 *
 * Across the axis, a child sitting on the shared baseline splits what it asks for in two, the part above
 * that line and the part below, and the container holds the deepest of each - so it is as tall as it
 * takes for every such child to fit on one line.
 */
private fun LinearLayout.combinedSize(
    parent: Container,
    extentOf: (Component) -> Dimension,
): Dimension {
    var main = 0
    var cross = 0
    var visible = 0
    var totalWeight = 0.0
    var perWeight = 0.0
    var aboveBaseline = 0
    var belowBaseline = 0
    // Read straight off the container: this walk needs no index of its own, so it borrows none of the
    // room a layout pass keeps, and stays usable while one is in flight.
    for (index in 0 until parent.componentCount) {
        val child = parent.getComponent(index)
        if (!child.isVisible) continue
        val extent = extentOf(child)
        val weight = declared[child]?.weight?.weight
        if (weight == null) {
            main += axis.main(extent)
        } else {
            totalWeight += weight.toDouble()
            perWeight = maxOf(perWeight, cappedMainSize(child, axis.main(extent)) / weight.toDouble())
        }
        val crossExtent = axis.cross(extent)
        val baseline = baselineOf(child, axis.main(extent), crossExtent)
        if (baseline >= 0) {
            aboveBaseline = maxOf(aboveBaseline, baseline)
            belowBaseline = maxOf(belowBaseline, crossExtent - baseline)
        }
        cross = maxOf(cross, crossExtent)
        visible++
    }
    cross = maxOf(cross, aboveBaseline + belowBaseline)
    // Rounded once, here: what a child implies is a share of one extent rather than an extent of its
    // own. The weights are summed as doubles, so two children at the largest finite weight add up
    // instead of overflowing to an infinity multiplied by the vanishing share it leaves each of them.
    main += (perWeight * totalWeight).roundToInt()
    if (visible > 0) main += arrangement.spacing * (visible - 1)
    val insets = parent.insets
    val size = axis.dimension(main, cross)
    size.width += insets.left + insets.right
    size.height += insets.top + insets.bottom
    return size
}

/**
 * The extent each child occupies along the axis, in declaration order.
 *
 * A child with no weight takes its preferred extent from whatever room is still unclaimed, reserving the
 * gap after it from that same room. What remains, minus the gaps between weighted children, is what they
 * share. A container narrower than its children's combined extent runs out of room partway through: the
 * child that empties it keeps whatever was left, and every child after it takes none at all.
 */
private fun LinearLayout.measureMainAxis(
    children: List<Component>,
    available: Int,
): IntArray {
    val sizes = room.sizes(children.size)
    val spacing = arrangement.spacing
    var claimed = 0
    var weightedCount = 0
    children.forEachIndexed { index, child ->
        val weighted = declared[child]?.weight
        if (weighted == null) {
            val unclaimed = (available - claimed).coerceAtLeast(0)
            val size = axis.main(preferredSizeOf(child)).coerceAtMost(unclaimed)
            sizes[index] = size
            claimed += size + minOf(spacing, unclaimed - size)
        } else {
            // distributeWeights writes every weighted index, out of what the rest leave unclaimed.
            weightedCount++
        }
    }
    if (weightedCount > 0) {
        val share = available - claimed - spacing * (weightedCount - 1)
        distributeWeights(children, sizes, share.coerceAtLeast(0))
    }
    return sizes
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
    children: List<Component>,
    sizes: IntArray,
    share: Int,
) {
    var totalWeight = 0.0
    for (child in children) {
        totalWeight += (declared[child]?.weight ?: continue).weight.toDouble()
    }
    val unit = share / totalWeight
    var remainder = share
    for (child in children) {
        val weighted = declared[child]?.weight ?: continue
        remainder -= (unit * weighted.weight).roundToInt()
    }
    for (index in children.indices) {
        val child = children[index]
        val weighted = declared[child]?.weight ?: continue
        val correction = remainder.sign
        remainder -= correction
        val granted = ((unit * weighted.weight).roundToInt() + correction).coerceAtLeast(0)
        sizes[index] = weightedMainSize(child, weighted, granted)
    }
}

/**
 * How much of the [granted] extent a weighted child occupies: all of it when it fills, otherwise as much
 * of it as the child prefers, and in either case no more than an explicit `maximumSize`.
 */
private fun LinearLayout.weightedMainSize(
    child: Component,
    weighted: WeightPlacement,
    granted: Int,
): Int = cappedMainSize(child, if (weighted.fill) granted else minOf(axis.main(preferredSizeOf(child)), granted))

/**
 * The [wanted] extent along the axis, held to an explicit `maximumSize` on [child] - all such a child
 * would occupy of a larger extent, and so all its container has reason to hold for it.
 */
private fun LinearLayout.cappedMainSize(
    child: Component,
    wanted: Int,
): Int = if (child.isMaximumSizeSet) minOf(wanted, axis.main(child.maximumSize)) else wanted

/**
 * The working room one layout pass needs: the visible children it places, their extents along the axis,
 * and the offsets it places them at.
 *
 * Held between passes rather than allocated per pass. One of these belongs to one [LinearLayout], and so
 * to the one container that manager lays out; a pass runs to completion on the event dispatch thread
 * before the next begins - so no two passes hold this at once. An extent read mid-pass reaches a child's
 * own manager, which has room of its own.
 */
internal class PassRoom {
    private val children = ArrayList<Component>()
    private var sizes = IntArray(0)
    private var positions = IntArray(0)

    /** The visible children [parent] holds, in declaration order; an invisible child takes no space. */
    fun visibleChildrenOf(parent: Container): List<Component> {
        children.clear()
        for (index in 0 until parent.componentCount) {
            val child = parent.getComponent(index)
            if (child.isVisible) children.add(child)
        }
        return children
    }

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

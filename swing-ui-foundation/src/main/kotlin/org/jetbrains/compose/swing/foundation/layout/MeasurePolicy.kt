package org.jetbrains.compose.swing.foundation.layout

/**
 * How a container measures and places its children.
 *
 * A policy is handed one [Measurable] per child and the [Constraints] the container was given, measures
 * each child under constraints of its own working out, and answers with the extent it occupies and the
 * placement of what it measured.
 *
 * [Row], [Column] and [Box] are each written as one of these.
 */
public fun interface MeasurePolicy {
    /**
     * Measures [measurables] under [constraints] and answers the extent this container occupies, along
     * with where each measured child goes inside it.
     *
     * Under an unbounded main axis a policy dividing a finite extent among its children has nothing to
     * divide; see the four intrinsic functions, which answer Swing's argument-less size queries.
     */
    public fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult

    /** The smallest width that lets [measurables] paint correctly when they are [height] tall. */
    public fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = this@MeasurePolicy.intrinsicMeasure(measurables, IntrinsicMinMax.Min, IntrinsicWidthHeight.Width, height)

    /** The width beyond which growing [measurables] no longer reduces their height at [height]. */
    public fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = this@MeasurePolicy.intrinsicMeasure(measurables, IntrinsicMinMax.Max, IntrinsicWidthHeight.Width, height)

    /** The smallest height that lets [measurables] paint correctly when they are [width] wide. */
    public fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = this@MeasurePolicy.intrinsicMeasure(measurables, IntrinsicMinMax.Min, IntrinsicWidthHeight.Height, width)

    /** The height beyond which growing [measurables] no longer reduces their width at [width]. */
    public fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = this@MeasurePolicy.intrinsicMeasure(measurables, IntrinsicMinMax.Max, IntrinsicWidthHeight.Height, width)
}

/** CMP's default intrinsic-policy adapter, using Swing's integer constraints rather than packed ones. */
private fun MeasurePolicy.intrinsicMeasure(
    measurables: List<IntrinsicMeasurable>,
    minMax: IntrinsicMinMax,
    widthHeight: IntrinsicWidthHeight,
    crossAxisSize: Int,
): Int {
    require(crossAxisSize >= 0) { "An intrinsic cross-axis size must be zero or more, but was $crossAxisSize." }
    val constrained =
        measurables.map { IntrinsicMeasurableAdapter(it, minMax, widthHeight) }
    val constraints =
        if (widthHeight == IntrinsicWidthHeight.Width) {
            Constraints(maxHeight = crossAxisSize)
        } else {
            Constraints(maxWidth = crossAxisSize)
        }
    val result = with(PolicyMeasureScope) { measure(constrained, constraints) }
    return if (widthHeight == IntrinsicWidthHeight.Width) result.width else result.height
}

/** Identifies the minimum or maximum intrinsic question a policy is answering. */
internal enum class IntrinsicMinMax {
    Min,
    Max,
}

/** Identifies the axis an intrinsic question asks its policy to return. */
internal enum class IntrinsicWidthHeight {
    Width,
    Height,
}

/**
 * CMP's default intrinsic measurable adapter.
 *
 * A policy may run normal measurement while answering an intrinsic query. Swing constraints can
 * represent an unbounded axis, but a component policy may use that otherwise irrelevant axis in
 * arithmetic, so this follows CMP and substitutes a finite large value.
 */
internal class IntrinsicMeasurableAdapter(
    /** The Swing-backed measurable a policy may inspect for maximum-size adaptations. */
    internal val source: IntrinsicMeasurable,
    private val minMax: IntrinsicMinMax,
    private val widthHeight: IntrinsicWidthHeight,
) : Measurable {
    override val parentData: Any? get() = source.parentData

    override fun measure(constraints: Constraints): Placeable {
        val width =
            if (widthHeight == IntrinsicWidthHeight.Width) {
                source.intrinsicWidth(minMax, constraints.maxHeight)
            } else {
                if (constraints.hasBoundedWidth) constraints.maxWidth else INTRINSIC_LARGE_DIMENSION
            }
        val height =
            if (widthHeight == IntrinsicWidthHeight.Height) {
                source.intrinsicHeight(minMax, constraints.maxWidth)
            } else {
                if (constraints.hasBoundedHeight) constraints.maxHeight else INTRINSIC_LARGE_DIMENSION
            }
        return IntrinsicPlaceable(width, height, constraints)
    }

    override fun minIntrinsicWidth(height: Int): Int = source.minIntrinsicWidth(height)

    override fun maxIntrinsicWidth(height: Int): Int = source.maxIntrinsicWidth(height)

    override fun minIntrinsicHeight(width: Int): Int = source.minIntrinsicHeight(width)

    override fun maxIntrinsicHeight(width: Int): Int = source.maxIntrinsicHeight(width)

    override fun baseline(
        width: Int,
        height: Int,
    ): Int = -1
}

private fun IntrinsicMeasurable.intrinsicWidth(
    minMax: IntrinsicMinMax,
    height: Int,
): Int = if (minMax == IntrinsicMinMax.Min) minIntrinsicWidth(height) else maxIntrinsicWidth(height)

private fun IntrinsicMeasurable.intrinsicHeight(
    minMax: IntrinsicMinMax,
    width: Int,
): Int = if (minMax == IntrinsicMinMax.Min) minIntrinsicHeight(width) else maxIntrinsicHeight(width)

/** A fixed measurement used only while a policy's default intrinsic implementation runs. */
private class IntrinsicPlaceable(
    override val measuredWidth: Int,
    override val measuredHeight: Int,
    constraints: Constraints,
) : Placeable {
    override val width: Int = constraints.constrainWidth(measuredWidth)
    override val height: Int = constraints.constrainHeight(measuredHeight)
}

/** CMP's finite replacement for an intrinsic axis a policy was not asked to determine. */
private const val INTRINSIC_LARGE_DIMENSION: Int = (1 shl 15) - 1

/**
 * The receiver a [MeasurePolicy] measures in, and the one way it answers.
 *
 * @see layout
 */
public sealed interface MeasureScope : IntrinsicMeasureScope {
    /**
     * The extent this policy settled on, with [placementBlock] as the block that places what it
     * measured.
     *
     * @param width the width the container actually measured itself to. A parent sees this extent
     *   coerced into the constraints it offered, while Swing ultimately receives this raw extent.
     * @param height the height the container actually measured itself to. A parent sees this extent
     *   coerced into the constraints it offered, while Swing ultimately receives this raw extent.
     * @param alignmentLines lines this layout explicitly provides to its parent. Swing has no native
     *   alignment-line graph, so lines from ordinary child components are not inferred or inherited.
     * @param placementBlock places every child the policy measured, relative to the container's inner
     *   rectangle
     * @return what the policy settled on.
     * @throws IllegalArgumentException if either extent is negative
     */
    public fun layout(
        width: Int,
        height: Int,
        alignmentLines: Map<AlignmentLine, Int> = emptyMap(),
        placementBlock: PlacementScope.() -> Unit,
    ): MeasureResult
}

/**
 * An empty intrinsic result: it reports only an extent, because intrinsic sizing never places the
 * children it measured.
 *
 * It is deliberately a separate result from a layout pass. A parent may retain a measured result,
 * ask the same policy for its intrinsic extent, and then place the retained result.
 */
internal fun MeasureScope.intrinsicResult(
    width: Int,
    height: Int,
): MeasureResult = layout(width, height) {}

/** The [MeasureScope] every policy of this library is run in; it holds nothing of its own. */
internal object PolicyMeasureScope : MeasureScope {
    override fun layout(
        width: Int,
        height: Int,
        alignmentLines: Map<AlignmentLine, Int>,
        placementBlock: PlacementScope.() -> Unit,
    ): MeasureResult {
        require(width >= 0 && height >= 0) {
            "A container occupies an extent of zero or more, but $width by $height was named."
        }
        return SettledExtent(width, height, alignmentLines, placementBlock)
    }
}

/** What [MeasureScope.layout] hands back: the raw extent named there, and the block that was named with it. */
private class SettledExtent(
    override val width: Int,
    override val height: Int,
    override val alignmentLines: Map<AlignmentLine, Int>,
    private val placementBlock: PlacementScope.() -> Unit,
) : MeasureResult {
    override fun PlacementScope.placeChildren(): Unit = placementBlock()
}

/**
 * What a [MeasurePolicy] settled on: the raw extent the container measured itself to, and where its
 * children go in it.
 *
 * When this result becomes a child's [Placeable], the parent reads a constraint-coerced apparent
 * extent and Swing places the raw extent centered within that apparent space.
 */
public interface MeasureResult {
    /** The width the container actually measured itself to. */
    public val width: Int

    /** The height the container actually measured itself to. */
    public val height: Int

    /** The alignment lines this layout explicitly provides, excluding any child-derived lines. */
    public val alignmentLines: Map<AlignmentLine, Int> get() = emptyMap()

    /**
     * Places every child into a zero-origin, left-to-right rectangle as wide as [width].
     *
     * This is CMP's standalone placement surface. Swing's real layout, nested-modifier replay and
     * baseline probing each have a distinct origin and reading order, so they call the scoped
     * overload below instead. Keeping that overload is necessary to replay one retained measurement
     * without losing its pass-specific AWT placement context.
     */
    public fun placeChildren() {
        val scope = StandalonePlacementScope(width)
        this.run { scope.placeChildren() }
    }

    /** Places every child using the actual Swing pass's origin, width and component orientation. */
    public fun PlacementScope.placeChildren()
}

/** CMP's standalone placement scope, adapted to Swing's zero-origin, left-to-right default. */
private class StandalonePlacementScope(
    override val parentWidth: Int,
) : PlacementScope {
    override val isLeftToRight: Boolean = true

    override fun Placeable.place(
        x: Int,
        y: Int,
    ) {
        (this as PositionedPlaceable).placeAt(x.toLong(), y.toLong())
    }
}

/**
 * The receiver a [MeasureResult] places its children in.
 *
 * A placement is relative to the container's inner rectangle - inside its insets - so a policy works in
 * the same coordinates it measured in.
 */
public sealed interface PlacementScope {
    /** The inner width the policy divided, which is what [placeRelative] mirrors a placement across. */
    public val parentWidth: Int

    /** Whether the container reads left to right; see `java.awt.ComponentOrientation`. */
    public val isLeftToRight: Boolean

    /** Places the child at [x] from the left edge, whatever the orientation. */
    public fun Placeable.place(
        x: Int,
        y: Int,
    )

    /** Places the child at [x] from the leading edge, mirroring under a right-to-left parent. */
    public fun Placeable.placeRelative(
        x: Int,
        y: Int,
    ): Unit =
        place(
            if (isLeftToRight) x else saturateLayoutCoordinate(parentWidth.toLong() - width.toLong() - x.toLong()),
            y,
        )
}

/** A signed coordinate held to the range AWT can represent rather than wrapped across the opposite edge. */
internal fun saturateLayoutCoordinate(value: Long): Int =
    value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

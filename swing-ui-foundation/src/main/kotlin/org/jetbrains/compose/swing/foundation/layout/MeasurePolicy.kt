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
     * divide; see [intrinsicSize], which is what the container's own `preferredLayoutSize` asks.
     */
    public fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult

    /**
     * The extent this policy asks for when nothing constrains it. Only [MeasureResult.width] and
     * [MeasureResult.height] are read; [MeasureResult.placeChildren] is not called.
     *
     * The default measures under [Constraints.Unbounded], which is the answer for any policy that does
     * not divide a finite extent among its children. One that does must override this, because there is
     * no extent to divide.
     *
     * This is what the container answers `preferredLayoutSize` and `minimumLayoutSize` with. Which of
     * the two is being asked rides on the measurables rather than on this signature: it decides only
     * which extent a child that cannot be asked a constrained question answers with.
     */
    public fun MeasureScope.intrinsicSize(measurables: List<Measurable>): MeasureResult =
        measure(measurables, Constraints.Unbounded)
}

/**
 * The receiver a [MeasurePolicy] measures in, and the one way it answers.
 *
 * @see layout
 */
public sealed interface MeasureScope {
    /**
     * The extent this policy settled on, with [placementBlock] as the block that places what it
     * measured.
     *
     * @param width the width the container occupies
     * @param height the height the container occupies
     * @param placementBlock places every child the policy measured, relative to the container's inner
     *   rectangle
     * @return what the policy settled on.
     * @throws IllegalArgumentException if either extent is negative
     */
    public fun layout(
        width: Int,
        height: Int,
        placementBlock: PlacementScope.() -> Unit,
    ): MeasureResult
}

/**
 * An answer to [MeasurePolicy.intrinsicSize]: it reports only an extent, because intrinsic sizing
 * never places the children it measured.
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
        placementBlock: PlacementScope.() -> Unit,
    ): MeasureResult {
        require(width >= 0 && height >= 0) {
            "A container occupies an extent of zero or more, but $width by $height was named."
        }
        return SettledExtent(width, height, placementBlock)
    }
}

/** What [MeasureScope.layout] hands back: the extent named there, and the block that was named with it. */
private class SettledExtent(
    override val width: Int,
    override val height: Int,
    private val placementBlock: PlacementScope.() -> Unit,
) : MeasureResult {
    override fun PlacementScope.placeChildren(): Unit = placementBlock()
}

/** What a [MeasurePolicy] settled on: the extent the container occupies, and where its children go in it. */
public interface MeasureResult {
    /** The width the container occupies. */
    public val width: Int

    /** The height the container occupies. */
    public val height: Int

    /** Places every child the policy measured. Called only when the container is being laid out. */
    public fun PlacementScope.placeChildren()
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
    ): Unit = place(if (isLeftToRight) x else parentWidth - width - x, y)
}

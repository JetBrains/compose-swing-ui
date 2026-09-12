package org.jetbrains.compose.swing.foundation.layout

/** A child as a [MeasurePolicy] asks about its unconstrained, axis-specific dimensions. */
public sealed interface IntrinsicMeasurable {
    /** What the child declared to this container - a weight, an alignment, a scope's own value. */
    public val parentData: Any?

    /** The least width that lets this child paint correctly when it is [height] tall. */
    public fun minIntrinsicWidth(height: Int): Int

    /** The width beyond which growing this child no longer reduces its height at [height]. */
    public fun maxIntrinsicWidth(height: Int): Int

    /** The least height that lets this child paint correctly when it is [width] wide. */
    public fun minIntrinsicHeight(width: Int): Int

    /** The height beyond which growing this child no longer reduces its width at [width]. */
    public fun maxIntrinsicHeight(width: Int): Int
}

/**
 * One child of a container that can answer both intrinsic and constrained measurement questions.
 *
 * Swing widgets expose argument-less preferred and minimum sizes, rather than a cross-axis-sensitive
 * intrinsic protocol. Foundation therefore answers its intrinsic functions from those stock Swing
 * sizes, while policy containers forward the question through their own policy.
 */
public sealed interface Measurable : IntrinsicMeasurable {
    /**
     * The extent the child takes under [constraints].
     *
     * Each answer is an independent [Placeable]. Measuring the same child again does not rewrite an
     * earlier result retained by the policy.
     */
    public fun measure(constraints: Constraints): Placeable

    /**
     * Where the child carries its text baseline when it occupies [width] by [height], or `-1` where it
     * carries none. Foundation reconstructs the exact measurement so the baseline follows any raw-size
     * centering and layout modifiers before it reaches `java.awt.Component.getBaseline`.
     */
    public fun baseline(
        width: Int,
        height: Int,
    ): Int
}

/** The receiver of a [MeasurePolicy]'s four intrinsic measurement functions. */
public sealed interface IntrinsicMeasureScope

/**
 * An offset line a measured layout exposes to its parent for alignment.
 *
 * Foundation retains lines named explicitly in [MeasureResult.alignmentLines]. Swing has no native
 * alignment-line graph, so it does not infer or inherit lines from ordinary components.
 */
public sealed class AlignmentLine(
    internal val merger: (Int, Int) -> Int,
) {
    /** A line no layout supplied. */
    public companion object {
        /** Value indicating an unspecified alignment line position. */
        public const val UNSPECIFIED: Int = Int.MIN_VALUE
    }
}

/** A line whose position is measured from the left or right edge. */
public class VerticalAlignmentLine(
    merger: (Int, Int) -> Int,
) : AlignmentLine(merger)

/** A line whose position is measured from the top or bottom edge, such as a text baseline. */
public class HorizontalAlignmentLine(
    merger: (Int, Int) -> Int,
) : AlignmentLine(merger)

/**
 * A child measured, and the handle its container places it by.
 *
 * A placeable is one immutable measurement of a child. It retains the component extent and layout
 * modifier offsets computed for that measurement.
 */
public sealed interface Placeable {
    /**
     * The width the parent sees: [measuredWidth] coerced inside the constraints passed to
     * [Measurable.measure].
     *
     * A parent normally lays its children out from this apparent extent, so one that measures a
     * child outside its offer still has an extent it can safely account for.
     */
    public val width: Int

    /**
     * The height the parent sees: [measuredHeight] coerced inside the constraints passed to
     * [Measurable.measure].
     */
    public val height: Int

    /** The width the child actually measured itself to, before its parent coerced [width]. */
    public val measuredWidth: Int

    /** The height the child actually measured itself to, before its parent coerced [height]. */
    public val measuredHeight: Int
}

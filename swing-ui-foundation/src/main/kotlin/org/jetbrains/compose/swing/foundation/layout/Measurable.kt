package org.jetbrains.compose.swing.foundation.layout

/**
 * One child of a container, as the [MeasurePolicy] laying that container out asks about it.
 *
 * A policy measures a child under the constraints it computes for it and places what comes back. It
 * never reads the component: what the child declared to this container is [layoutConstraint], and what
 * the child takes under a given offer is [measure].
 *
 * A measurable belongs to the container holding the child, and lives as long as the child stays in it.
 */
public sealed interface Measurable {
    /** What the child declared to this container - a weight, an alignment, a scope's own value. */
    public val layoutConstraint: Any?

    /**
     * The extent the child takes under [constraints].
     *
     * The answer is a [Placeable] that is the same object every time. A later measure overwrites the
     * earlier result, so place only the final measurement and do not retain a [Placeable] across
     * another measure.
     */
    public fun measure(constraints: Constraints): Placeable

    /**
     * Where the child carries its text baseline when it occupies [width] by [height], or `-1` where it
     * carries none. It is `java.awt.Component.getBaseline`, which takes its own extents and needs no
     * measure before it.
     */
    public fun baseline(
        width: Int,
        height: Int,
    ): Int
}

/**
 * A child measured, and the handle its container places it by.
 *
 * One object is both the [Measurable] and the [Placeable] it returns, so measuring a child again
 * overwrites the placeable already handed out.
 */
public sealed interface Placeable {
    /** The width the measure settled on, after the constraints were applied to it. */
    public val width: Int

    /** The height the measure settled on, after the constraints were applied to it. */
    public val height: Int
}

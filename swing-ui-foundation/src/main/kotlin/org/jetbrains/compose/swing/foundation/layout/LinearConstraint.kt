package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.layout.ConstraintElement
import org.jetbrains.compose.swing.modifier.layout.twoScopesOfConstraint
import java.awt.ComponentOrientation

/**
 * What a child of a [Row] or a [Column] declares for itself: the share of the leftover space it claims,
 * the cross-axis placement it names in place of its container's, and whether it takes the container's
 * whole extent across the axis.
 *
 * It is the constraint the child is registered under, so [LinearLayout] is handed it by
 * `Container.add(Component, Object)` and gives it up again on `removeLayoutComponent`.
 *
 * @property weight what the child claims of the leftover space, or `null` when it takes the size it
 *   prefers.
 * @property alignment where the child sits across the axis, or `null` to leave that to its container.
 * @property fillsCrossAxis whether the child takes its container's whole extent across the axis.
 */
internal data class LinearConstraint(
    val weight: WeightPlacement? = null,
    val alignment: AxisAlignment? = null,
    val fillsCrossAxis: Boolean = false,
)

/**
 * A child's claim on the space its container has left over: [weight] shares of it, and whether the child
 * occupies all of what it is granted ([fill]) or only as much of it as it prefers.
 */
internal data class WeightPlacement(
    val weight: Float,
    val fill: Boolean,
)

/**
 * A child sitting on the shared text baseline of a [Row], as `alignByBaseline` declares it. It is an
 * [AxisAlignment] so that it and an `align` occupy the one place a child names its cross-axis placement
 * in, which is what makes the last of the two declared the one that places the child.
 *
 * A [LinearLayout] reads the baseline off the component rather than through [align]; [align] is the
 * answer for a child that reports none, and puts it against the container's leading edge across the
 * axis.
 */
internal data object BaselineAxisAlignment : AxisAlignment {
    override fun align(
        size: Int,
        space: Int,
        orientation: ComponentOrientation,
    ): Int = 0
}

/**
 * Builds the claim a scope's `weight` extension declares. An infinite weight is taken as the largest
 * finite one, so a child asking for everything gets it rather than an arithmetic answer.
 */
internal fun weightPlacement(
    weight: Float,
    fill: Boolean,
): WeightPlacement {
    require(weight > 0f) { "A weight must be greater than zero, but was $weight." }
    return WeightPlacement(weight.coerceAtMost(Float.MAX_VALUE), fill)
}

/**
 * What the modifier has declared to a row or a column so far, and an empty constraint where it has
 * declared nothing of the kind.
 *
 * A constraint built by another container's scope is refused: both state parts of one constraint, so the
 * walk over the modifier folds them together rather than refusing the pair itself. The fallback stands
 * for a first part folded onto nothing.
 */
private fun linearConstraintCarried(carried: Any?): LinearConstraint {
    require(carried == null || carried is LinearConstraint) { twoScopesOfConstraint() }
    return carried as? LinearConstraint ?: LinearConstraint()
}

/** The share of the leftover space a child claims, as a row's or a column's `weight` declares it. */
internal data class WeightElement(
    val placement: WeightPlacement,
) : ConstraintElement {
    override val name: String get() = "weight"

    override val declaredValues: Map<String, Any?> get() = mapOf("weight" to placement)

    override fun foldInto(carried: Any?): Any = linearConstraintCarried(carried).copy(weight = placement)
}

/** Where across the axis a child sits, as a row's or a column's `align` declares it. */
internal data class AlignElement(
    val alignment: AxisAlignment,
) : ConstraintElement {
    override val name: String get() = "align"

    override val declaredValues: Map<String, Any?> get() = mapOf("alignment" to alignment)

    override fun foldInto(carried: Any?): Any = linearConstraintCarried(carried).copy(alignment = alignment)
}

/** A child taking its container's whole extent across the axis. */
internal data object FillElement : ConstraintElement {
    override val name: String get() = "fill"

    override fun foldInto(carried: Any?): Any = linearConstraintCarried(carried).copy(fillsCrossAxis = true)
}

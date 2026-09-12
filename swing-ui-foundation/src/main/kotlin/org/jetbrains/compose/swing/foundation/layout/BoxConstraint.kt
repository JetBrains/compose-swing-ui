package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.layout.ConstraintElement
import org.jetbrains.compose.swing.modifier.layout.twoScopesOfConstraint

/**
 * What a child of a [Box] declares for itself: the placement it names in place of its container's, and
 * whether it takes the box's whole extent instead of the one it prefers.
 *
 * It is the constraint the child is registered under, so [OverlapLayout] is handed it by
 * `Container.add(Component, Object)` and gives it up again on `removeLayoutComponent`.
 *
 * @property alignment where the child sits in the box, or `null` to leave that to its container.
 * @property matchesParentSize whether the child takes the box's whole extent, and so sets none of it.
 * @property fillsWidth whether the child takes the box's whole width, while still setting its height.
 * @property fillsHeight whether the child takes the box's whole height, while still setting its width.
 * @property zIndex where in the stack the child sits, the largest on top.
 */
internal data class BoxConstraint(
    val alignment: Alignment? = null,
    val matchesParentSize: Boolean = false,
    override val fillsWidth: Boolean = false,
    override val fillsHeight: Boolean = false,
    val zIndex: Float = 0f,
) : ParentFill

/**
 * What the modifier has declared to a box so far, and an empty constraint where it has declared nothing
 * of the kind.
 *
 * A constraint built by another container's scope is refused: both state parts of one constraint, so the
 * walk over the modifier folds them together rather than refusing the pair itself. The fallback stands
 * for a first part folded onto nothing.
 */
private fun boxConstraintCarried(carried: Any?): BoxConstraint {
    require(carried == null || carried is BoxConstraint) { twoScopesOfConstraint() }
    return carried as? BoxConstraint ?: BoxConstraint()
}

/** Where in the box a child sits, as a box's `align` declares it. */
internal data class BoxAlignElement(
    val alignment: Alignment,
) : ConstraintElement {
    override val name: String get() = "align"

    override val declaredValues: Map<String, Any?> get() = mapOf("alignment" to alignment)

    override fun foldInto(carried: Any?): Any = boxConstraintCarried(carried).copy(alignment = alignment)
}

/** Where in the box's stack a child sits, as a box's `zIndex` declares it. */
internal data class BoxZIndexElement(
    val zIndex: Float,
) : ConstraintElement {
    override val name: String get() = "zIndex"

    override val declaredValues: Map<String, Any?> get() = mapOf("zIndex" to zIndex)

    override fun foldInto(carried: Any?): Any {
        val constraint = boxConstraintCarried(carried)
        return constraint.copy(zIndex = constraint.zIndex + zIndex)
    }
}

/** A child taking the box's whole extent, as a box's `matchParentSize` declares it. */
internal data object BoxMatchParentSizeElement : ConstraintElement {
    override val name: String get() = "matchParentSize"

    override fun foldInto(carried: Any?): Any = boxConstraintCarried(carried).copy(matchesParentSize = true)
}

/** A child taking the box's whole width, as a box's `fillWidth` declares it. */
internal data object BoxFillWidthElement : ConstraintElement {
    override val name: String get() = "fillWidth"

    override fun foldInto(carried: Any?): Any = boxConstraintCarried(carried).copy(fillsWidth = true)
}

/** A child taking the box's whole height, as a box's `fillHeight` declares it. */
internal data object BoxFillHeightElement : ConstraintElement {
    override val name: String get() = "fillHeight"

    override fun foldInto(carried: Any?): Any = boxConstraintCarried(carried).copy(fillsHeight = true)
}

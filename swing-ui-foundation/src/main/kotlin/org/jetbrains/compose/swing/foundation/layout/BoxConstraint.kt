package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentProtocol

/**
 * What a child of a [Box] declares for itself: the placement it names in place of its container's, and
 * whether it takes the box's whole extent instead of the one it prefers.
 *
 * It is the parent data the child is registered under, so [PolicyLayout] hands it to the [BoxMeasurePolicy]
 * through [Measurable.parentData].
 *
 * @property alignment where the child sits in the box, or `null` to leave that to its container.
 * @property matchesParentSize whether the child takes the box's whole extent, and so sets none of it.
 * @property zIndex where in the stack the child sits, the largest on top.
 */
internal data class BoxConstraint(
    val alignment: Alignment? = null,
    val matchesParentSize: Boolean = false,
    override val zIndex: Float = 0f,
) : StackingParentData

/**
 * What the modifier has declared to a box so far, and an empty constraint where it has declared nothing
 * of the kind.
 *
 * The core runtime validates this family against the actual receiving parent before it folds the data.
 */
private fun boxConstraintCarried(carried: Any?): BoxConstraint = carried as? BoxConstraint ?: BoxConstraint()

internal data class BoxAlignElement(
    val alignment: Alignment,
) : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = BoxParentDataProtocol

    override val key: Any get() = BoxAlignElement::class

    override val name: String get() = "align"

    override val declaredValues: Map<String, Any?> get() = mapOf("alignment" to alignment)

    override fun modifyParentData(parentData: Any?): Any = boxConstraintCarried(parentData).copy(alignment = alignment)
}

internal data class BoxZIndexElement(
    val zIndex: Float,
) : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = BoxParentDataProtocol

    override val additive: Boolean get() = true

    override val name: String get() = "zIndex"

    override val declaredValues: Map<String, Any?> get() = mapOf("zIndex" to zIndex)

    override fun modifyParentData(parentData: Any?): Any {
        val constraint = boxConstraintCarried(parentData)
        return constraint.copy(zIndex = constraint.zIndex + zIndex)
    }
}

internal data object BoxMatchParentSizeElement : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = BoxParentDataProtocol

    override val key: Any get() = BoxMatchParentSizeElement::class

    override val name: String get() = "matchParentSize"

    override fun modifyParentData(parentData: Any?): Any =
        boxConstraintCarried(parentData).copy(matchesParentSize = true)
}

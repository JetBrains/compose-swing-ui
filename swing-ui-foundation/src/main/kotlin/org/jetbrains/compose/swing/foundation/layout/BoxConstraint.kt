package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentProtocol

/**
 * What a child of a [Box] declares for itself: the placement it names in place of its container's, and
 * whether it takes the box's whole extent instead of the one it prefers.
 *
 * It is the parent data the child is registered under, so [MeasurePolicyLayout] hands it to the [BoxMeasurePolicy]
 * through [Measurable.parentData].
 *
 * @property alignment where the child sits in the box, or `null` to leave that to its container.
 * @property matchesParentSize whether the child takes the box's whole extent, and so sets none of it.
 */
internal data class BoxConstraint(
    val alignment: Alignment? = null,
    val matchesParentSize: Boolean = false,
)

internal data class BoxAlignElement(
    val alignment: Alignment,
) : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = BoxParentDataProtocol

    override val key: Any get() = BoxAlignElement::class

    override val name: String get() = "align"

    override val declaredValues: Map<String, Any?> get() = mapOf("alignment" to alignment)

    override fun modifyParentData(parentData: Any?): Any =
        (parentData as? BoxConstraint ?: BoxConstraint()).copy(alignment = alignment)
}

internal data object BoxMatchParentSizeElement : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = BoxParentDataProtocol

    override val key: Any get() = BoxMatchParentSizeElement::class

    override val name: String get() = "matchParentSize"

    override fun modifyParentData(parentData: Any?): Any =
        (parentData as? BoxConstraint ?: BoxConstraint()).copy(matchesParentSize = true)
}

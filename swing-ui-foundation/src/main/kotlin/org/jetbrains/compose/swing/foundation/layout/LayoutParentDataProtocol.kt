@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.ParentProtocol
import java.awt.Component
import java.awt.Container

/**
 * A stable token for parent data a [Layout] policy reads through [Measurable.parentData].
 *
 * Instances come from [layoutParentDataProtocol]. The final [accepts] implementation binds the token
 * to the policy layout that holds that same instance, so custom implementations cannot accidentally
 * broaden the parent family they declare under.
 */
public open class LayoutParentDataProtocol internal constructor(
    final override val description: String,
) : ParentProtocol {
    final override fun accepts(parent: Container): Boolean = parent.acceptsParentProtocol(this)

    /**
     * Refuses [parentData] where the policies of this family read one concrete representation and [component] was
     * registered under another, the way `BorderLayout` and `GridBagLayout` reject a constraint they cannot read.
     * Accepts everything by default.
     */
    internal open fun validateParentData(
        component: Component,
        parentData: Any?,
    ) = Unit
}

/**
 * Creates a stable token for parent data a [Layout] policy reads through [Measurable.parentData].
 *
 * Pass the token to [Layout] as [Layout.parentDataProtocol], and use that same instance from every
 * [org.jetbrains.compose.swing.layout.ParentDataModifier] the policy understands. A token accepts only
 * the policy layout that was built with that exact instance, so a declaration hoisted under another
 * custom layout is refused before Swing attaches the child.
 */
public fun layoutParentDataProtocol(description: String): LayoutParentDataProtocol =
    object : LayoutParentDataProtocol(description) {}

/** The capability every Foundation [LayoutModifierNodeElement] uses. */
public val LayoutModifierParentProtocol: ParentProtocol =
    object : ParentProtocol {
        override val description: String = "Foundation layout modifiers"

        override fun accepts(parent: Container): Boolean = parent.acceptsParentProtocol(this)
    }

/** The parent-data family understood by [BoxMeasurePolicy]: a [BoxConstraint] or nothing. */
@PublishedApi
internal val BoxParentDataProtocol: LayoutParentDataProtocol =
    object : LayoutParentDataProtocol("Box parent data") {
        override fun validateParentData(
            component: Component,
            parentData: Any?,
        ) = require(parentData == null || parentData is BoxConstraint) {
            "A Box places a child by the alignment it is declared with, and by align() / " +
                "matchParentSize() on the child's own modifier, so '$component' can carry no " +
                "layout constraint, but it was added under '$parentData'."
        }
    }

/**
 * The parent-data family shared by [RowMeasurePolicy] and [ColumnMeasurePolicy]: a [LinearConstraint] or nothing.
 */
@PublishedApi
internal val LinearParentDataProtocol: LayoutParentDataProtocol =
    object : LayoutParentDataProtocol("linear parent data") {
        override fun validateParentData(
            component: Component,
            parentData: Any?,
        ) = require(parentData == null || parentData is LinearConstraint) {
            "A Row or Column places a child by the arrangement and alignment it is declared with, and by " +
                "weight() / align() on the child's own modifier, so '$component' can carry no layout constraint, " +
                "but it was added under '$parentData'."
        }
    }

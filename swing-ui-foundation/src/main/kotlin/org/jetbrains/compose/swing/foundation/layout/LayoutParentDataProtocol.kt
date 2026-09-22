@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.ParentProtocol
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
    final override fun accepts(parent: Container): Boolean =
        (parent.layout as? PolicyLayout)?.acceptsParentProtocol(this) == true
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

/** The capability every Foundation [LayoutModifier] uses. */
public val LayoutModifierParentProtocol: ParentProtocol =
    object : ParentProtocol {
        override val description: String = "Foundation layout modifiers"

        override fun accepts(parent: Container): Boolean =
            (parent.layout as? PolicyLayout)?.acceptsParentProtocol(this) == true
    }

/** The parent-data family understood by [BoxMeasurePolicy]. */
@PublishedApi
internal val BoxParentDataProtocol: LayoutParentDataProtocol = layoutParentDataProtocol("Box parent data")

/** The parent-data family shared by [RowMeasurePolicy] and [ColumnMeasurePolicy]. */
@PublishedApi
internal val LinearParentDataProtocol: LayoutParentDataProtocol = layoutParentDataProtocol("linear parent data")

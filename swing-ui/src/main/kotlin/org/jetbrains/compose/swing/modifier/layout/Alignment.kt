@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.JComponent

/**
 * Sets the horizontal alignment along the x axis, where `0.0` aligns to the left, `0.5` centers, and
 * `1.0` aligns to the right. A parent that honors alignment - a vertical `BoxLayout` - lines its
 * children up by this value, so siblings given the same alignment stay in one column.
 *
 * @see javax.swing.JComponent.setAlignmentX
 */
public fun SwingModifier.alignmentX(value: Float): SwingModifier =
    this then
        propertyElement<JComponent, Float>(
            value,
            read = { it.alignmentX },
            write = { component, alignment ->
                component.alignmentX = alignment
                component.revalidate()
            },
        )

/**
 * Sets the vertical alignment along the y axis, where `0.0` aligns to the top, `0.5` centers, and
 * `1.0` aligns to the bottom. A parent that honors alignment - a horizontal `BoxLayout` - lines its
 * children up by this value, so siblings given the same alignment stay on one row.
 *
 * @see javax.swing.JComponent.setAlignmentY
 */
public fun SwingModifier.alignmentY(value: Float): SwingModifier =
    this then
        propertyElement<JComponent, Float>(
            value,
            read = { it.alignmentY },
            write = { component, alignment ->
                component.alignmentY = alignment
                component.revalidate()
            },
        )

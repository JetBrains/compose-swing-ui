@file:JvmMultifileClass
@file:JvmName("LayoutModifiersKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.JComponent

/**
 * Sets the horizontal alignment used to position the component along the x axis, where `0.0` aligns to
 * the left, `0.5` centers, and `1.0` aligns to the right. A parent that honors alignment - such as a
 * vertical `BoxLayout` - lines its children up by this value, so giving siblings the same alignment
 * keeps them in one column.
 */
public fun SwingModifier.alignmentX(value: Float): SwingModifier =
    this then
        propertyElement<JComponent, Float>(
            value,
            read = { it.alignmentX },
            write = { c, v ->
                c.alignmentX = v
                c.revalidate()
            },
        )

/**
 * Sets the vertical alignment used to position the component along the y axis, where `0.0` aligns to
 * the top, `0.5` centers, and `1.0` aligns to the bottom. A parent that honors alignment - such as a
 * horizontal `BoxLayout` - lines its children up by this value, so giving siblings the same alignment
 * keeps them on one row.
 */
public fun SwingModifier.alignmentY(value: Float): SwingModifier =
    this then
        propertyElement<JComponent, Float>(
            value,
            read = { it.alignmentY },
            write = { c, v ->
                c.alignmentY = v
                c.revalidate()
            },
        )

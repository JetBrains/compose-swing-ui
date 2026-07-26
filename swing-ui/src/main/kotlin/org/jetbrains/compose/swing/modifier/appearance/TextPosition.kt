@file:JvmMultifileClass
@file:JvmName("AppearanceModifiersKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.constants.HorizontalAlignment
import org.jetbrains.compose.swing.constants.VerticalAlignment
import org.jetbrains.compose.swing.modifier.MultiTargetProperty
import org.jetbrains.compose.swing.modifier.MultiTargetPropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyCase
import javax.swing.AbstractButton
import javax.swing.JLabel

/**
 * Sets which side of the icon the text is drawn on. Applies to labels and to everything built on a
 * button; a component with no icon lays out the same either way.
 *
 * @param position a [HorizontalAlignment] `SwingConstants` value.
 */
public fun SwingModifier.horizontalTextPosition(
    @HorizontalAlignment position: Int,
): SwingModifier = this then MultiTargetPropertyElement(HorizontalTextPositionProperty, position)

/**
 * Sets whether the text is drawn above, across or below the icon. Applies to labels and to everything
 * built on a button; a component with no icon lays out the same either way.
 *
 * @param position a [VerticalAlignment] `SwingConstants` value.
 */
public fun SwingModifier.verticalTextPosition(
    @VerticalAlignment position: Int,
): SwingModifier = this then MultiTargetPropertyElement(VerticalTextPositionProperty, position)

/**
 * Sets the space between a component's icon and its text. Applies to labels and to everything built on
 * a button.
 *
 * A button stops taking this from the look and feel once the property is set, and Swing offers no way to
 * hand it back. A label has no such latch.
 */
public fun SwingModifier.iconTextGap(gap: Int): SwingModifier =
    this then MultiTargetPropertyElement(IconTextGapProperty, gap)

private val HorizontalTextPositionProperty =
    MultiTargetProperty<Int>(
        "horizontalTextPosition",
        propertyCase<JLabel, Int>(
            read = { it.horizontalTextPosition },
            // A label re-lays out on every write of this one, unlike its neighbours, so skip an
            // unchanged value rather than asking for a layout that changes nothing.
            write = { c, v -> if (c.horizontalTextPosition != v) c.horizontalTextPosition = v },
        ),
        propertyCase<AbstractButton, Int>(
            read = { it.horizontalTextPosition },
            write = { c, v -> c.horizontalTextPosition = v },
        ),
    )

private val VerticalTextPositionProperty =
    MultiTargetProperty<Int>(
        "verticalTextPosition",
        propertyCase<JLabel, Int>(
            read = { it.verticalTextPosition },
            write = { c, v -> c.verticalTextPosition = v },
        ),
        propertyCase<AbstractButton, Int>(
            read = { it.verticalTextPosition },
            write = { c, v -> c.verticalTextPosition = v },
        ),
    )

private val IconTextGapProperty =
    MultiTargetProperty<Int>(
        "iconTextGap",
        propertyCase<JLabel, Int>(
            read = { it.iconTextGap },
            write = { c, v -> c.iconTextGap = v },
        ),
        propertyCase<AbstractButton, Int>(
            read = { it.iconTextGap },
            // Latched by the first write, as a button's painting flags are.
            write = { c, v -> if (c.iconTextGap != v) c.iconTextGap = v },
        ),
    )

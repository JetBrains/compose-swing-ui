@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

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
 * @param position a [HorizontalAlignment] `SwingConstants` value; `LEADING` and `TRAILING` resolve
 *   against the component's orientation. Any other value is refused with an `IllegalArgumentException`.
 * @return this chain with the horizontal text position declared on it.
 * @see javax.swing.JLabel.setHorizontalTextPosition
 * @see javax.swing.AbstractButton.setHorizontalTextPosition
 */
public fun SwingModifier.horizontalTextPosition(
    @HorizontalAlignment position: Int,
): SwingModifier = this then MultiTargetPropertyElement(HorizontalTextPositionProperty, position)

/**
 * Sets whether the text is drawn above, across or below the icon. Applies to labels and to everything
 * built on a button; a component with no icon lays out the same either way.
 *
 * @param position a [VerticalAlignment] `SwingConstants` value; any other value is refused with an
 *   `IllegalArgumentException`.
 * @return this chain with the vertical text position declared on it.
 * @see javax.swing.JLabel.setVerticalTextPosition
 * @see javax.swing.AbstractButton.setVerticalTextPosition
 */
public fun SwingModifier.verticalTextPosition(
    @VerticalAlignment position: Int,
): SwingModifier = this then MultiTargetPropertyElement(VerticalTextPositionProperty, position)

/**
 * Sets the space between a component's icon and its text. Applies to labels and to everything built on
 * a button.
 *
 * A button stops taking this from the look and feel once set, and Swing offers no way to hand it back.
 * A label has no such latch.
 *
 * @param gap the pixels between the icon and the text, on whichever side of the icon the text sits.
 * @return this chain with the icon-text gap declared on it.
 * @see javax.swing.JLabel.setIconTextGap
 * @see javax.swing.AbstractButton.setIconTextGap
 */
public fun SwingModifier.iconTextGap(gap: Int): SwingModifier =
    this then MultiTargetPropertyElement(IconTextGapProperty, gap)

private val HorizontalTextPositionProperty =
    MultiTargetProperty<Int>(
        "horizontalTextPosition",
        propertyCase<JLabel, Int>(
            read = { it.horizontalTextPosition },
            // A label re-lays out on every write of this one, unlike its neighbors, so skip an
            // unchanged value rather than asking for a layout that changes nothing.
            write = { component, value ->
                if (component.horizontalTextPosition != value) component.horizontalTextPosition = value
            },
        ),
        propertyCase<AbstractButton, Int>(
            read = { it.horizontalTextPosition },
            write = { component, value -> component.horizontalTextPosition = value },
        ),
    )

private val VerticalTextPositionProperty =
    MultiTargetProperty<Int>(
        "verticalTextPosition",
        propertyCase<JLabel, Int>(
            read = { it.verticalTextPosition },
            write = { component, value -> component.verticalTextPosition = value },
        ),
        propertyCase<AbstractButton, Int>(
            read = { it.verticalTextPosition },
            write = { component, value -> component.verticalTextPosition = value },
        ),
    )

private val IconTextGapProperty =
    MultiTargetProperty<Int>(
        "iconTextGap",
        propertyCase<JLabel, Int>(
            read = { it.iconTextGap },
            write = { component, value -> component.iconTextGap = value },
        ),
        propertyCase<AbstractButton, Int>(
            read = { it.iconTextGap },
            // Latched by the first write, as a button's painting flags are.
            write = { component, value -> if (component.iconTextGap != value) component.iconTextGap = value },
        ),
    )

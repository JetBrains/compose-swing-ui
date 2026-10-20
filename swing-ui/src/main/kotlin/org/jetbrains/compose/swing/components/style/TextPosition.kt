@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.annotations.HorizontalAlignment
import org.jetbrains.compose.swing.annotations.VerticalAlignment
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor.Companion.accessor
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.AbstractButton
import javax.swing.JLabel

/**
 * Sets which side of the icon a label draws its text on; a label with no icon lays out the same either way.
 *
 * @param position a [HorizontalAlignment] `SwingConstants` value; `LEADING` and `TRAILING` resolve against the
 *   component's orientation. Any other value is refused with an `IllegalArgumentException`.
 * @see javax.swing.JLabel.setHorizontalTextPosition
 */
public fun LabelStyleScope.horizontalTextPosition(
    @HorizontalAlignment position: Int,
): Unit = HorizontalTextPositionProperty.provide(position)

/**
 * Sets which side of the icon a button draws its text on; a button with no icon lays out the same either way.
 *
 * @param position a [HorizontalAlignment] `SwingConstants` value; `LEADING` and `TRAILING` resolve against the
 *   component's orientation. Any other value is refused with an `IllegalArgumentException`.
 * @see javax.swing.AbstractButton.setHorizontalTextPosition
 */
public fun ButtonStyleScope.horizontalTextPosition(
    @HorizontalAlignment position: Int,
): Unit = HorizontalTextPositionProperty.provide(position)

/**
 * Sets whether a label draws its text above, across or below the icon; a label with no icon lays out the same
 * either way.
 *
 * @param position a [VerticalAlignment] `SwingConstants` value; any other value is refused with an
 *   `IllegalArgumentException`.
 * @see javax.swing.JLabel.setVerticalTextPosition
 */
public fun LabelStyleScope.verticalTextPosition(
    @VerticalAlignment position: Int,
): Unit = VerticalTextPositionProperty.provide(position)

/**
 * Sets whether a button draws its text above, across or below the icon; a button with no icon lays out the same
 * either way.
 *
 * @param position a [VerticalAlignment] `SwingConstants` value; any other value is refused with an
 *   `IllegalArgumentException`.
 * @see javax.swing.AbstractButton.setVerticalTextPosition
 */
public fun ButtonStyleScope.verticalTextPosition(
    @VerticalAlignment position: Int,
): Unit = VerticalTextPositionProperty.provide(position)

/**
 * Sets the space between a label's icon and its text.
 *
 * @param gap the space between the icon and the text, on whichever side of the icon the text sits.
 * @see javax.swing.JLabel.setIconTextGap
 */
public fun LabelStyleScope.iconTextGap(gap: Int): Unit = IconTextGapProperty.provide(gap)

/**
 * Sets the space between a button's icon and its text.
 *
 * A button stops taking this from the look and feel once set, and Swing offers no way to hand it back.
 *
 * @param gap the space between the icon and the text, on whichever side of the icon the text sits.
 * @see javax.swing.AbstractButton.setIconTextGap
 */
public fun ButtonStyleScope.iconTextGap(gap: Int): Unit = IconTextGapProperty.provide(gap)

internal val HorizontalTextPositionDescriptor =
    ComponentPropertyDescriptor(
        "horizontalTextPosition",
        accessor<JLabel, Int>(
            read = { it.horizontalTextPosition },
            // A label re-lays out on every write of this one, unlike its neighbors, so skip an
            // unchanged value rather than asking for a layout that changes nothing.
            write = { component, value ->
                if (component.horizontalTextPosition != value) component.horizontalTextPosition = value
            },
        ),
        accessor<AbstractButton, Int>(
            read = { it.horizontalTextPosition },
            write = { component, value -> component.horizontalTextPosition = value },
        ),
    )

internal val VerticalTextPositionDescriptor =
    ComponentPropertyDescriptor(
        "verticalTextPosition",
        accessor<JLabel, Int>(
            read = { it.verticalTextPosition },
            write = { component, value -> component.verticalTextPosition = value },
        ),
        accessor<AbstractButton, Int>(
            read = { it.verticalTextPosition },
            write = { component, value -> component.verticalTextPosition = value },
        ),
    )

internal val IconTextGapDescriptor =
    ComponentPropertyDescriptor(
        "iconTextGap",
        accessor<JLabel, Int>(
            read = { it.iconTextGap },
            write = { component, value -> component.iconTextGap = value },
        ),
        accessor<AbstractButton, Int>(
            read = { it.iconTextGap },
            // Latched by the first write, as a button's painting flags are.
            write = { component, value -> if (component.iconTextGap != value) component.iconTextGap = value },
        ),
    )

private val HorizontalTextPositionProperty = stylePropertyOf(HorizontalTextPositionDescriptor)

private val VerticalTextPositionProperty = stylePropertyOf(VerticalTextPositionDescriptor)

private val IconTextGapProperty = stylePropertyOf(IconTextGapDescriptor)

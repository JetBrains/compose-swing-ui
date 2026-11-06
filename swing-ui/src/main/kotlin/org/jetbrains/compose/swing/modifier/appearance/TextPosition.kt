@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.annotations.HorizontalAlignment
import org.jetbrains.compose.swing.annotations.VerticalAlignment
import org.jetbrains.compose.swing.components.style.HorizontalTextPositionDescriptor
import org.jetbrains.compose.swing.components.style.IconTextGapDescriptor
import org.jetbrains.compose.swing.components.style.VerticalTextPositionDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property

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
@Deprecated("Set it in the component's style: style = { horizontalTextPosition(position) }")
public fun SwingModifier.horizontalTextPosition(
    @HorizontalAlignment position: Int,
): SwingModifier = property(HorizontalTextPositionDescriptor, position, inheritable = true)

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
@Deprecated("Set it in the component's style: style = { verticalTextPosition(position) }")
public fun SwingModifier.verticalTextPosition(
    @VerticalAlignment position: Int,
): SwingModifier = property(VerticalTextPositionDescriptor, position, inheritable = true)

/**
 * Sets the space between a component's icon and its text. Applies to labels and to everything built on
 * a button.
 *
 * A button stops taking this from the look and feel once set, and Swing offers no way to hand it back.
 * A label has no such latch.
 *
 * @param gap the space between the icon and the text, on whichever side of the icon the text sits.
 * @return this chain with the icon-text gap declared on it.
 * @see javax.swing.JLabel.setIconTextGap
 * @see javax.swing.AbstractButton.setIconTextGap
 */
@Deprecated("Set it in the component's style: style = { iconTextGap(gap) }")
public fun SwingModifier.iconTextGap(gap: Int): SwingModifier = property(IconTextGapDescriptor, gap, inheritable = true)

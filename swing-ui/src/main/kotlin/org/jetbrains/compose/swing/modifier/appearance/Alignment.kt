@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.annotations.HorizontalAlignment
import org.jetbrains.compose.swing.annotations.VerticalAlignment
import org.jetbrains.compose.swing.components.style.HorizontalAlignmentDescriptor
import org.jetbrains.compose.swing.components.style.VerticalAlignmentDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.inheritableProperty

/**
 * Sets where a component's content sits along its width, when it is given more width than it needs.
 *
 * Applies to labels, to everything built on a button, and to single-line text fields.
 *
 * @param alignment a [HorizontalAlignment] `SwingConstants` value; `LEADING` and `TRAILING` resolve
 *   against the component's orientation. Any other value is refused with an `IllegalArgumentException`.
 * @return this chain with the horizontal alignment declared on it.
 * @see javax.swing.JLabel.setHorizontalAlignment
 * @see javax.swing.AbstractButton.setHorizontalAlignment
 * @see javax.swing.JTextField.setHorizontalAlignment
 */
@Deprecated("Set it in the component's style: style = { horizontalAlignment(alignment) }")
public fun SwingModifier.horizontalAlignment(
    @HorizontalAlignment alignment: Int,
): SwingModifier = inheritableProperty(HorizontalAlignmentDescriptor, alignment)

/**
 * Sets where a component's content sits along its height, when it is given more height than it needs.
 *
 * Applies to labels and to everything built on a button. A text field draws its single line centered
 * and offers no vertical alignment of its own.
 *
 * @param alignment a [VerticalAlignment] `SwingConstants` value; any other value is refused with an
 *   `IllegalArgumentException`.
 * @return this chain with the vertical alignment declared on it.
 * @see javax.swing.JLabel.setVerticalAlignment
 * @see javax.swing.AbstractButton.setVerticalAlignment
 */
@Deprecated("Set it in the component's style: style = { verticalAlignment(alignment) }")
public fun SwingModifier.verticalAlignment(
    @VerticalAlignment alignment: Int,
): SwingModifier = inheritableProperty(VerticalAlignmentDescriptor, alignment)

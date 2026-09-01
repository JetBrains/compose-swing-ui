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
import javax.swing.JTextField

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
public fun SwingModifier.horizontalAlignment(
    @HorizontalAlignment alignment: Int,
): SwingModifier = this then MultiTargetPropertyElement(HorizontalAlignmentProperty, alignment)

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
public fun SwingModifier.verticalAlignment(
    @VerticalAlignment alignment: Int,
): SwingModifier = this then MultiTargetPropertyElement(VerticalAlignmentProperty, alignment)

private val HorizontalAlignmentProperty =
    MultiTargetProperty<Int>(
        "horizontalAlignment",
        propertyCase<JLabel, Int>(
            read = { it.horizontalAlignment },
            write = { component, value -> component.horizontalAlignment = value },
        ),
        propertyCase<AbstractButton, Int>(
            read = { it.horizontalAlignment },
            write = { component, value -> component.horizontalAlignment = value },
        ),
        propertyCase<JTextField, Int>(
            read = { it.horizontalAlignment },
            write = { component, value -> component.horizontalAlignment = value },
        ),
    )

private val VerticalAlignmentProperty =
    MultiTargetProperty<Int>(
        "verticalAlignment",
        propertyCase<JLabel, Int>(
            read = { it.verticalAlignment },
            write = { component, value -> component.verticalAlignment = value },
        ),
        propertyCase<AbstractButton, Int>(
            read = { it.verticalAlignment },
            write = { component, value -> component.verticalAlignment = value },
        ),
    )

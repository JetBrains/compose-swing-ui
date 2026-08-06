@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.AbstractButton

/**
 * Sets whether a button paints its border. Applies to everything built on a button, including menu
 * items.
 *
 * Switching this off, together with [contentAreaFilled] and [focusPainted], is what leaves a button
 * drawing only its own content - the shape a tool bar or a link-like button usually wants.
 */
public fun SwingModifier.borderPainted(painted: Boolean): SwingModifier =
    this then
        propertyElement<AbstractButton, Boolean>(
            painted,
            read = { it.isBorderPainted },
            // A button takes this from the look and feel only until something writes it, and nothing
            // clears that again, so writing the value the button already carries would detach it from
            // the look and feel for good while changing nothing.
            write = { c, v -> if (c.isBorderPainted != v) c.isBorderPainted = v },
        )

/**
 * Sets whether a button fills the area behind its content. Applies to everything built on a button.
 *
 * A button stops taking this from the look and feel once the property is set, and Swing offers no way to
 * hand it back.
 */
public fun SwingModifier.contentAreaFilled(filled: Boolean): SwingModifier =
    this then
        propertyElement<AbstractButton, Boolean>(
            filled,
            read = { it.isContentAreaFilled },
            // Latched by the first write, as a button's border painting is.
            write = { c, v -> if (c.isContentAreaFilled != v) c.isContentAreaFilled = v },
        )

/**
 * Sets whether a button paints the indicator showing it holds keyboard focus. Applies to everything
 * built on a button.
 *
 * Switching it off removes the indicator, not the focus: the button still takes focus and still
 * answers the keyboard.
 */
public fun SwingModifier.focusPainted(painted: Boolean): SwingModifier =
    this then
        propertyElement<AbstractButton, Boolean>(
            painted,
            read = { it.isFocusPainted },
            write = { c, v -> c.isFocusPainted = v },
        )

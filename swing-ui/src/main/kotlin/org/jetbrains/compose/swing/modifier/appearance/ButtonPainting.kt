@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.MultiTargetProperty
import org.jetbrains.compose.swing.modifier.MultiTargetPropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyCase
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.AbstractButton
import javax.swing.JMenuBar
import javax.swing.JPopupMenu
import javax.swing.JProgressBar
import javax.swing.JToolBar

/*
 * Button painting SwingModifiers - what a button paints around and behind its content. Each of these is
 * a request to the look and feel: one that does not implement the state ignores the declaration and
 * paints the button its own way.
 */

/**
 * Sets whether a component paints its border. Applies to everything built on a button, including menu
 * items, and to progress bars, tool bars, menu bars and popup menus.
 *
 * Switching this off, together with [contentAreaFilled] and [focusPainted], is what leaves a button
 * drawing only its own content - the shape a tool bar or a link-like button wants.
 *
 * @param painted `false` leaves the border area blank while the border keeps taking up its space, so the
 *   content stays where it is.
 * @return this chain with border painting declared on it.
 * @see javax.swing.AbstractButton.setBorderPainted
 * @see javax.swing.JProgressBar.setBorderPainted
 * @see javax.swing.JToolBar.setBorderPainted
 * @see javax.swing.JMenuBar.setBorderPainted
 * @see javax.swing.JPopupMenu.setBorderPainted
 */
public fun SwingModifier.borderPainted(painted: Boolean): SwingModifier =
    this then MultiTargetPropertyElement(BorderPaintedProperty, painted)

/**
 * Sets whether a button fills the area behind its content. Applies to everything built on a button.
 *
 * A button stops taking this from the look and feel once the property is set, and Swing offers no way to
 * hand it back.
 *
 * This, rather than [opaque]`(false)`, is how a button is made transparent: a look and feel's button
 * listener writes the button's opaque flag from this property as soon as it changes.
 *
 * @param filled `false` leaves the area behind the text and icon unpainted, so whatever is behind the
 *   button shows through it.
 * @return this chain with content-area painting declared on it.
 * @see javax.swing.AbstractButton.setContentAreaFilled
 */
public fun SwingModifier.contentAreaFilled(filled: Boolean): SwingModifier =
    this then
        propertyElement<AbstractButton, Boolean>(
            filled,
            read = { it.isContentAreaFilled },
            // Latched by the first write, as a button's border painting is.
            write = { component, value ->
                if (component.isContentAreaFilled != value) component.isContentAreaFilled = value
            },
        )

/**
 * Sets whether a button paints its rollover state - the look it takes while the pointer is over it.
 * Applies to everything built on a button.
 *
 * A button takes this from the look and feel until something sets it, as it takes [contentAreaFilled].
 * Declaring a [rolloverIcon] or a [rolloverSelectedIcon] switches it on by itself; declare it here to
 * paint a rollover a look and feel would not, or to suppress one it would.
 *
 * @param enabled `false` keeps the button looking the same under the pointer, and keeps a declared
 *   [rolloverIcon] from being drawn.
 * @return this chain with rollover painting declared on it.
 * @see javax.swing.AbstractButton.setRolloverEnabled
 */
public fun SwingModifier.rolloverEnabled(enabled: Boolean): SwingModifier =
    this then
        propertyElement<AbstractButton, Boolean>(
            enabled,
            read = { it.isRolloverEnabled },
            // Latched by the first write, as a button's border painting is.
            write = { component, value ->
                if (component.isRolloverEnabled != value) component.isRolloverEnabled = value
            },
        )

/**
 * Sets whether a button paints the indicator showing it holds keyboard focus. Applies to everything
 * built on a button.
 *
 * Switching it off removes the indicator, not the focus: the button still takes focus and still
 * answers the keyboard.
 *
 * @param painted `false` hides the outline a look and feel draws inside the button's border while it holds
 *   focus.
 * @return this chain with focus painting declared on it.
 * @see javax.swing.AbstractButton.setFocusPainted
 */
public fun SwingModifier.focusPainted(painted: Boolean): SwingModifier =
    this then
        propertyElement<AbstractButton, Boolean>(
            painted,
            read = { it.isFocusPainted },
            write = { component, value -> component.isFocusPainted = value },
        )

/**
 * Each of these types declares `borderPainted` for itself; the classes they share declare no such
 * property, so the accessors are named separately.
 *
 * Every case writes only a value differing from the one the component carries. A menu item takes this
 * property from the look and feel until something writes it, and nothing clears that again, so a
 * redundant write would detach it from the look and feel for good while changing nothing; the other
 * types answer one with a repaint of a component that looks no different.
 */
private val BorderPaintedProperty =
    MultiTargetProperty<Boolean>(
        "borderPainted",
        propertyCase<AbstractButton, Boolean>(
            read = { it.isBorderPainted },
            write = { component, value -> if (component.isBorderPainted != value) component.isBorderPainted = value },
        ),
        propertyCase<JProgressBar, Boolean>(
            read = { it.isBorderPainted },
            write = { component, value -> if (component.isBorderPainted != value) component.isBorderPainted = value },
        ),
        propertyCase<JToolBar, Boolean>(
            read = { it.isBorderPainted },
            write = { component, value -> if (component.isBorderPainted != value) component.isBorderPainted = value },
        ),
        propertyCase<JMenuBar, Boolean>(
            read = { it.isBorderPainted },
            write = { component, value -> if (component.isBorderPainted != value) component.isBorderPainted = value },
        ),
        propertyCase<JPopupMenu, Boolean>(
            read = { it.isBorderPainted },
            write = { component, value -> if (component.isBorderPainted != value) component.isBorderPainted = value },
        ),
    )

@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.components.style.BorderPaintedDescriptor
import org.jetbrains.compose.swing.components.style.ContentAreaFilledDescriptor
import org.jetbrains.compose.swing.components.style.FocusPaintedDescriptor
import org.jetbrains.compose.swing.components.style.RolloverEnabledDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.style.OpaqueDescriptor

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
 * @return this modifier with border painting declared on it.
 * @see javax.swing.AbstractButton.setBorderPainted
 * @see javax.swing.JProgressBar.setBorderPainted
 * @see javax.swing.JToolBar.setBorderPainted
 * @see javax.swing.JMenuBar.setBorderPainted
 * @see javax.swing.JPopupMenu.setBorderPainted
 */
@Deprecated("Set it in the component's style: style = { borderPainted(painted) }")
public fun SwingModifier.borderPainted(painted: Boolean): SwingModifier = property(BorderPaintedDescriptor, painted)

/**
 * Sets whether a button fills the area behind its content. Applies to everything built on a button.
 *
 * A button stops taking this from the look and feel once the property is set, and Swing offers no way to
 * hand it back.
 *
 * This, rather than [opaque]`(false)`, is how a button is made transparent: a look and feel paints the
 * fill from this property, and keeps the opaque flag in step with it - for every button but a menu item,
 * whose flag stands on its own.
 *
 * @param filled `false` leaves the area behind the text and icon unpainted, so whatever is behind the
 *   button shows through it.
 * @return this modifier with content-area painting declared on it.
 * @see javax.swing.AbstractButton.setContentAreaFilled
 */
@Deprecated("Set it in the component's style: style = { contentAreaFilled(filled) }")
public fun SwingModifier.contentAreaFilled(filled: Boolean): SwingModifier =
    property(ContentAreaFilledDescriptor, filled, alsoOverwrites = listOf(OpaqueDescriptor))

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
 * @return this modifier with rollover painting declared on it.
 * @see javax.swing.AbstractButton.setRolloverEnabled
 */
@Deprecated("Set it in the component's style: style = { rolloverEnabled(enabled) }")
public fun SwingModifier.rolloverEnabled(enabled: Boolean): SwingModifier =
    property(
        RolloverEnabledDescriptor,
        enabled,
        // Writing either rollover icon switches the state on after announcing the icon, so the
        // icon's own announcement comes too early to answer.
        rewriteOn = "rolloverEnabled",
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
 * @return this modifier with focus painting declared on it.
 * @see javax.swing.AbstractButton.setFocusPainted
 */
@Deprecated("Set it in the component's style: style = { focusPainted(painted) }")
public fun SwingModifier.focusPainted(painted: Boolean): SwingModifier = property(FocusPaintedDescriptor, painted)

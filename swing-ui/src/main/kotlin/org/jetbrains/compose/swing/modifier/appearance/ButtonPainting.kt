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

/**
 * Sets whether a component paints its border. Applies to everything built on a button, including menu
 * items, and to progress bars, tool bars, menu bars and popup menus.
 *
 * Switching this off, together with [contentAreaFilled] and [focusPainted], is what leaves a button
 * drawing only its own content - the shape a tool bar or a link-like button usually wants.
 *
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
 * @see javax.swing.AbstractButton.setContentAreaFilled
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
 * Sets whether a button paints its rollover state - the look it takes while the pointer is over it.
 * Applies to everything built on a button.
 *
 * A button takes this from the look and feel until something sets it, as it takes [contentAreaFilled].
 * Declaring a [rolloverIcon] or a [rolloverSelectedIcon] switches it on by itself; declare it here to
 * paint a rollover a look and feel would not, or to suppress one it would.
 *
 * @see javax.swing.AbstractButton.setRolloverEnabled
 */
public fun SwingModifier.rolloverEnabled(enabled: Boolean): SwingModifier =
    this then
        propertyElement<AbstractButton, Boolean>(
            enabled,
            read = { it.isRolloverEnabled },
            // Latched by the first write, as a button's border painting is.
            write = { c, v -> if (c.isRolloverEnabled != v) c.isRolloverEnabled = v },
        )

/**
 * Sets whether a button paints the indicator showing it holds keyboard focus. Applies to everything
 * built on a button.
 *
 * Switching it off removes the indicator, not the focus: the button still takes focus and still
 * answers the keyboard.
 *
 * @see javax.swing.AbstractButton.setFocusPainted
 */
public fun SwingModifier.focusPainted(painted: Boolean): SwingModifier =
    this then
        propertyElement<AbstractButton, Boolean>(
            painted,
            read = { it.isFocusPainted },
            write = { c, v -> c.isFocusPainted = v },
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
            write = { c, v -> if (c.isBorderPainted != v) c.isBorderPainted = v },
        ),
        propertyCase<JProgressBar, Boolean>(
            read = { it.isBorderPainted },
            write = { c, v -> if (c.isBorderPainted != v) c.isBorderPainted = v },
        ),
        propertyCase<JToolBar, Boolean>(
            read = { it.isBorderPainted },
            write = { c, v -> if (c.isBorderPainted != v) c.isBorderPainted = v },
        ),
        propertyCase<JMenuBar, Boolean>(
            read = { it.isBorderPainted },
            write = { c, v -> if (c.isBorderPainted != v) c.isBorderPainted = v },
        ),
        propertyCase<JPopupMenu, Boolean>(
            read = { it.isBorderPainted },
            write = { c, v -> if (c.isBorderPainted != v) c.isBorderPainted = v },
        ),
    )

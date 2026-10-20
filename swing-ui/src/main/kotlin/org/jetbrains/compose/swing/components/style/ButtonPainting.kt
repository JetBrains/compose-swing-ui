@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor.Companion.accessor
import org.jetbrains.compose.swing.style.OpaqueDescriptor
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.AbstractButton
import javax.swing.JProgressBar
import javax.swing.JToolBar

/*
 * Button painting - what a button paints around and behind its content. Each of these is a request to the look and
 * feel: one that does not implement the state ignores it and paints the button its own way.
 */

/**
 * Sets whether a button paints its border.
 *
 * Switching this off, together with [contentAreaFilled] and [focusPainted], leaves a button drawing only its own
 * content - the shape a tool bar or a link-like button wants.
 *
 * @param painted `false` leaves the border area blank while the border keeps taking up its space, so the content
 *   stays where it is.
 * @see javax.swing.AbstractButton.setBorderPainted
 */
public fun ButtonStyleScope.borderPainted(painted: Boolean): Unit = BorderPaintedProperty.provide(painted)

/**
 * Sets whether a progress bar paints its border.
 *
 * @param painted `false` leaves the border area blank while the border keeps taking up its space.
 * @see javax.swing.JProgressBar.setBorderPainted
 */
public fun ProgressBarStyleScope.borderPainted(painted: Boolean): Unit = BorderPaintedProperty.provide(painted)

/**
 * Sets whether a tool bar paints its border.
 *
 * @param painted `false` leaves the border area blank while the border keeps taking up its space.
 * @see javax.swing.JToolBar.setBorderPainted
 */
public fun ToolBarStyleScope.borderPainted(painted: Boolean): Unit = BorderPaintedProperty.provide(painted)

/**
 * Sets whether a button fills the area behind its content.
 *
 * A button stops taking this from the look and feel once the property is set, and Swing offers no way to hand it
 * back.
 *
 * This, rather than `opaque(false)`, is how a button is made transparent: a look and feel paints the fill from this
 * property, and keeps the opaque flag in step with it - for every button but a menu item, whose flag stands on its
 * own.
 *
 * @param filled `false` leaves the area behind the text and icon unpainted, so whatever is behind the button shows
 *   through it.
 * @see javax.swing.AbstractButton.setContentAreaFilled
 */
public fun ButtonStyleScope.contentAreaFilled(filled: Boolean): Unit = ContentAreaFilledProperty.provide(filled)

/**
 * Sets whether a button paints its rollover state - the look it takes while the pointer is over it.
 *
 * A button takes this from the look and feel until something sets it, as it takes [contentAreaFilled]. Setting a
 * [rolloverIcon] or a [rolloverSelectedIcon] switches it on by itself; set it here to paint a rollover a look and
 * feel would not, or to suppress one it would.
 *
 * @param enabled `false` keeps the button looking the same under the pointer, and keeps a [rolloverIcon] from being
 *   drawn.
 * @see javax.swing.AbstractButton.setRolloverEnabled
 */
public fun ButtonStyleScope.rolloverEnabled(enabled: Boolean): Unit = RolloverEnabledProperty.provide(enabled)

/**
 * Sets whether a button paints the indicator showing it holds keyboard focus.
 *
 * Switching it off removes the indicator, not the focus: the button still takes focus and still answers the
 * keyboard.
 *
 * @param painted `false` hides the outline a look and feel draws inside the button's border while it holds focus.
 * @see javax.swing.AbstractButton.setFocusPainted
 */
public fun ButtonStyleScope.focusPainted(painted: Boolean): Unit = FocusPaintedProperty.provide(painted)

/**
 * Each of these types declares `borderPainted` for itself; the classes they share declare no such property, so the
 * accessors are named separately.
 *
 * Every case writes only a value differing from the one the component carries. A menu item takes this property from
 * the look and feel until something writes it, and nothing clears that again, so a redundant write would detach it
 * from the look and feel for good while changing nothing; the other types answer one with a repaint of a component
 * that looks no different.
 */
internal val BorderPaintedDescriptor =
    ComponentPropertyDescriptor(
        "borderPainted",
        accessor<AbstractButton, Boolean>(
            read = { it.isBorderPainted },
            write = { component, value -> if (component.isBorderPainted != value) component.isBorderPainted = value },
        ),
        accessor<JProgressBar, Boolean>(
            read = { it.isBorderPainted },
            write = { component, value -> if (component.isBorderPainted != value) component.isBorderPainted = value },
        ),
        accessor<JToolBar, Boolean>(
            read = { it.isBorderPainted },
            write = { component, value -> if (component.isBorderPainted != value) component.isBorderPainted = value },
        ),
    )

/** The switch's own accessors. The write is latched by the first, as a button's border painting is. */
internal val RolloverEnabledDescriptor =
    ComponentPropertyDescriptor<AbstractButton, Boolean>(
        name = "rolloverEnabled",
        read = { it.isRolloverEnabled },
        write = { component, value ->
            if (component.isRolloverEnabled != value) component.isRolloverEnabled = value
        },
    )

internal val ContentAreaFilledDescriptor =
    ComponentPropertyDescriptor<AbstractButton, Boolean>(
        name = "contentAreaFilled",
        read = { it.isContentAreaFilled },
        // Latched by the first write, as a button's border painting is.
        write = { component, value ->
            if (component.isContentAreaFilled != value) component.isContentAreaFilled = value
        },
    )

internal val FocusPaintedDescriptor =
    ComponentPropertyDescriptor<AbstractButton, Boolean>(
        name = "focusPainted",
        read = { it.isFocusPainted },
        write = { component, value -> component.isFocusPainted = value },
    )

private val BorderPaintedProperty = stylePropertyOf(BorderPaintedDescriptor)

private val ContentAreaFilledProperty =
    stylePropertyOf(ContentAreaFilledDescriptor, alsoOverwrites = listOf(OpaqueDescriptor))

private val RolloverEnabledProperty =
    stylePropertyOf(
        RolloverEnabledDescriptor,
        // Writing either rollover icon switches the state on after announcing the icon, so the icon's own
        // announcement comes too early to answer.
        rewriteOn = "rolloverEnabled",
    )

private val FocusPaintedProperty = stylePropertyOf(FocusPaintedDescriptor)

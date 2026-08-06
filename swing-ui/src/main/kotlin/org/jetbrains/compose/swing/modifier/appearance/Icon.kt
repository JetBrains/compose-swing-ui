@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.MultiTargetProperty
import org.jetbrains.compose.swing.modifier.MultiTargetPropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyCase
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JLabel

/*
 * Icon SwingModifiers - the icon a component displays, and the icons a button swaps in for its states.
 *
 * "Every kind of button" below means everything built on `AbstractButton`: buttons, check boxes, radio
 * buttons, toggle buttons, and every kind of menu item.
 *
 * A button paints a state icon only when it has a base [icon] to begin with: the state icons decorate
 * that one for the states a button can be in rather than standing in for it.
 */

/**
 * Sets the icon a component displays beside its text; `null` displays none.
 *
 * Applies to labels and to every kind of button.
 *
 * @see javax.swing.JLabel.setIcon
 * @see javax.swing.AbstractButton.setIcon
 */
public fun SwingModifier.icon(icon: Icon?): SwingModifier = this then MultiTargetPropertyElement(IconProperty, icon)

/**
 * Sets the icon a button displays while it is held down; `null` falls back to the base icon. Applies
 * to every kind of button.
 *
 * @see javax.swing.AbstractButton.setPressedIcon
 */
public fun SwingModifier.pressedIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            read = { it.pressedIcon },
            write = { c, v -> c.pressedIcon = v },
        )

/**
 * Sets the icon a button displays while it is selected - a checked check box, an on toggle button;
 * `null` falls back to the base icon. Applies to every kind of button.
 *
 * @see javax.swing.AbstractButton.setSelectedIcon
 */
public fun SwingModifier.selectedIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            read = { it.selectedIcon },
            write = { c, v -> c.selectedIcon = v },
        )

/**
 * Sets the icon a button displays while it is disabled; `null` hands the state back to the look and
 * feel, which greys the base icon for it. Applies to every kind of button.
 *
 * @see javax.swing.AbstractButton.setDisabledIcon
 */
public fun SwingModifier.disabledIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            // Reading the property is what makes the look and feel derive its greyed icon, so what is
            // captured here may be that derived one. A derived icon is a UIResource, which the button
            // discards by itself the next time its base icon changes, so the fallback resumes.
            read = { it.disabledIcon },
            write = { c, v -> c.disabledIcon = v },
        )

/**
 * Sets the icon a button displays while it is both disabled and selected; `null` hands the state back
 * to the look and feel, which greys [selectedIcon] for it. Applies to every kind of button.
 *
 * @see javax.swing.AbstractButton.setDisabledSelectedIcon
 */
public fun SwingModifier.disabledSelectedIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            read = { it.disabledSelectedIcon },
            write = { c, v -> c.disabledSelectedIcon = v },
        )

/**
 * Sets the icon a button displays while the pointer is over it; `null` falls back to the base icon.
 * Declaring it switches [rolloverEnabled] on. Applies to every kind of button.
 *
 * @see javax.swing.AbstractButton.setRolloverIcon
 */
public fun SwingModifier.rolloverIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            read = { it.rolloverIcon },
            write = { c, v -> c.rolloverIcon = v },
        )

/**
 * Sets the icon a button displays while the pointer is over it and it is selected; `null` falls back to
 * [selectedIcon]. Declaring it switches [rolloverEnabled] on. Applies to every kind of button.
 *
 * @see javax.swing.AbstractButton.setRolloverSelectedIcon
 */
public fun SwingModifier.rolloverSelectedIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            read = { it.rolloverSelectedIcon },
            write = { c, v -> c.rolloverSelectedIcon = v },
        )

/**
 * `JLabel` and `AbstractButton` each declare `icon` for themselves; the class they share declares no
 * such property, so the two accessors are named separately. One case for `AbstractButton` covers every
 * button and every menu item, since all of them are built on it.
 */
private val IconProperty =
    MultiTargetProperty<Icon?>(
        "icon",
        propertyCase<JLabel, Icon?>(read = { it.icon }, write = { c, v -> c.icon = v }),
        propertyCase<AbstractButton, Icon?>(read = { it.icon }, write = { c, v -> c.icon = v }),
    )

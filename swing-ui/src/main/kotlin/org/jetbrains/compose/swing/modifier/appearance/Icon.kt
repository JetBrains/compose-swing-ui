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
 * @param icon the icon drawn beside the text; its size counts toward the component's preferred size, so
 *   swapping in a differently sized one re-lays the component out.
 * @return this chain with the icon declared on it.
 * @see javax.swing.JLabel.setIcon
 * @see javax.swing.AbstractButton.setIcon
 */
public fun SwingModifier.icon(icon: Icon?): SwingModifier = this then MultiTargetPropertyElement(IconProperty, icon)

/**
 * Sets the icon a button displays while it is held down; `null` falls back to the base icon. Applies
 * to every kind of button.
 *
 * @param icon the icon drawn while the button is both pressed and armed; dragging the pointer off the
 *   button with the mouse button still down disarms it and puts the base icon back until the pointer
 *   returns.
 * @return this chain with the pressed icon declared on it.
 * @see javax.swing.AbstractButton.setPressedIcon
 */
public fun SwingModifier.pressedIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            read = { it.pressedIcon },
            write = { component, value -> component.pressedIcon = value },
        )

/**
 * Sets the icon a button displays while it is selected - a checked check box, an on toggle button;
 * `null` falls back to the base icon. Applies to every kind of button.
 *
 * @param icon the icon drawn while the button reports itself selected, whether the user or the code
 *   selected it.
 * @return this chain with the selected icon declared on it.
 * @see javax.swing.AbstractButton.setSelectedIcon
 */
public fun SwingModifier.selectedIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            read = { it.selectedIcon },
            write = { component, value -> component.selectedIcon = value },
        )

/**
 * Sets the icon a button displays while it is disabled; `null` hands the state back to the look and
 * feel, which grays the base icon for it. Applies to every kind of button.
 *
 * @param icon the icon drawn while the button is disabled, in place of the graying the look and feel
 *   would derive.
 * @return this chain with the disabled icon declared on it.
 * @see javax.swing.AbstractButton.setDisabledIcon
 */
public fun SwingModifier.disabledIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            // Reading the property is what makes the look and feel derive its grayed icon, so what is
            // captured here may be that derived one. A derived icon is a UIResource, which the button
            // discards by itself the next time its base icon changes, so the fallback resumes.
            read = { it.disabledIcon },
            write = { component, value -> component.disabledIcon = value },
        )

/**
 * Sets the icon a button displays while it is both disabled and selected; `null` hands the state back
 * to the look and feel, which grays [selectedIcon] for it, or falls back to the disabled icon where the
 * button carries no selected icon. Applies to every kind of button.
 *
 * @param icon the icon drawn while both states hold at once - a checked check box on a disabled form.
 * @return this chain with the disabled selected icon declared on it.
 * @see javax.swing.AbstractButton.setDisabledSelectedIcon
 */
public fun SwingModifier.disabledSelectedIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            read = { it.disabledSelectedIcon },
            write = { component, value -> component.disabledSelectedIcon = value },
        )

/**
 * Sets the icon a button displays while the pointer is over it; `null` falls back to the base icon.
 * Declaring it switches [rolloverEnabled] on. Applies to every kind of button.
 *
 * @param icon the icon drawn while the button reports a rollover - the pointer entered it with the left
 *   mouse button up, and the button is neither disabled nor pressed.
 * @return this chain with the rollover icon declared on it.
 * @see javax.swing.AbstractButton.setRolloverIcon
 */
public fun SwingModifier.rolloverIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            read = { it.rolloverIcon },
            write = { component, value -> component.rolloverIcon = value },
        )

/**
 * Sets the icon a button displays while the pointer is over it and it is selected; `null` falls back to
 * [selectedIcon]. Declaring it switches [rolloverEnabled] on. Applies to every kind of button.
 *
 * @param icon the icon drawn while the pointer is over a button that is already selected.
 * @return this chain with the rollover selected icon declared on it.
 * @see javax.swing.AbstractButton.setRolloverSelectedIcon
 */
public fun SwingModifier.rolloverSelectedIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement<AbstractButton, Icon?>(
            icon,
            read = { it.rolloverSelectedIcon },
            write = { component, value -> component.rolloverSelectedIcon = value },
        )

/**
 * `JLabel` and `AbstractButton` each declare `icon` for themselves; the class they share declares no
 * such property, so the two accessors are named separately. One case for `AbstractButton` covers every
 * button and every menu item, since all of them are built on it.
 */
private val IconProperty =
    MultiTargetProperty<Icon?>(
        "icon",
        propertyCase<JLabel, Icon?>(read = { it.icon }, write = { component, value -> component.icon = value }),
        propertyCase<AbstractButton, Icon?>(read = { it.icon }, write = { component, value -> component.icon = value }),
    )

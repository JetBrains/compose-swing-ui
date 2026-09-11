@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.MultiTargetProperty
import org.jetbrains.compose.swing.modifier.MultiTargetPropertyElement
import org.jetbrains.compose.swing.modifier.PropertyAccessors
import org.jetbrains.compose.swing.modifier.PropertyInterference
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyCase
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.plaf.UIResource

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
 * A look and feel may derive other properties from the icon; removing the declaration restores the
 * icon, not what was derived from it.
 *
 * @param icon the icon drawn beside the text; its size counts toward the component's preferred size, so
 *   swapping in a differently sized one re-lays the component out.
 * @return this modifier with the icon declared on it.
 * @see javax.swing.JLabel.setIcon
 * @see javax.swing.AbstractButton.setIcon
 */
public fun SwingModifier.icon(icon: Icon?): SwingModifier = this then IconElement(icon)

/**
 * Sets the icon a button displays while it is held down; `null` falls back to the base icon. Applies
 * to every kind of button.
 *
 * @param icon the icon drawn while the button is both pressed and armed; dragging the pointer off the
 *   button with the mouse button still down disarms it and puts the base icon back until the pointer
 *   returns.
 * @return this modifier with the pressed icon declared on it.
 * @see javax.swing.AbstractButton.setPressedIcon
 */
public fun SwingModifier.pressedIcon(icon: Icon?): SwingModifier = this then propertyElement(PressedIconProperty, icon)

/**
 * Sets the icon a button displays while it is selected - a checked check box, an on toggle button;
 * `null` falls back to the base icon. Applies to every kind of button.
 *
 * @param icon the icon drawn while the button reports itself selected, whether the user or the code
 *   selected it.
 * @return this modifier with the selected icon declared on it.
 * @see javax.swing.AbstractButton.setSelectedIcon
 */
public fun SwingModifier.selectedIcon(icon: Icon?): SwingModifier =
    this then propertyElement(SelectedIconProperty, icon)

/**
 * Sets the icon a button displays while it is disabled; `null` hands the state back to the look and
 * feel, which grays the base icon for it. Applies to every kind of button.
 *
 * @param icon the icon drawn while the button is disabled, in place of the graying the look and feel
 *   would derive.
 * @return this modifier with the disabled icon declared on it.
 * @see javax.swing.AbstractButton.setDisabledIcon
 */
public fun SwingModifier.disabledIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement(
            DisabledIconProperty,
            icon,
            // Replacing the base icon drops a disabled icon that is a `UIResource`, a declared one
            // included, and announces the base icon rather than the drop. A look and feel change drops
            // such an icon too, after announcing the look and feel it took, and leaves it dropped.
            interference = PropertyInterference.OverwrittenOn("icon"),
        )

/**
 * Sets the icon a button displays while it is both disabled and selected; `null` hands the state back
 * to the look and feel, which grays [selectedIcon] for it, or falls back to the disabled icon where the
 * button carries no selected icon. Applies to every kind of button.
 *
 * @param icon the icon drawn while both states hold at once - a checked check box on a disabled form.
 * @return this modifier with the disabled selected icon declared on it.
 * @see javax.swing.AbstractButton.setDisabledSelectedIcon
 */
public fun SwingModifier.disabledSelectedIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement(
            DisabledSelectedIconProperty,
            icon,
            // Replacing the selected icon drops a disabled selected icon that is a `UIResource`, as
            // [disabledIcon] describes for the base icon.
            interference = PropertyInterference.OverwrittenOn("selectedIcon"),
        )

/**
 * Sets the icon a button displays while the pointer is over it; `null` falls back to the base icon.
 * Declaring it switches [rolloverEnabled] on. Applies to every kind of button.
 *
 * @param icon the icon drawn while the button reports a rollover - the pointer entered it with the left
 *   mouse button up, and the button is neither disabled nor pressed.
 * @return this modifier with the rollover icon declared on it.
 * @see javax.swing.AbstractButton.setRolloverIcon
 */
public fun SwingModifier.rolloverIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement(
            RolloverIconProperty,
            icon,
            // `setRolloverIcon` switches rollover painting on whatever it is handed, the `null` a
            // removal writes included.
            interference = PropertyInterference.AlsoOverwrites(RolloverEnabledProperty),
        )

/**
 * Sets the icon a button displays while the pointer is over it and it is selected; `null` falls back to
 * [selectedIcon]. Declaring it switches [rolloverEnabled] on. Applies to every kind of button.
 *
 * @param icon the icon drawn while the pointer is over a button that is already selected.
 * @return this modifier with the rollover selected icon declared on it.
 * @see javax.swing.AbstractButton.setRolloverSelectedIcon
 */
public fun SwingModifier.rolloverSelectedIcon(icon: Icon?): SwingModifier =
    this then
        propertyElement(
            RolloverSelectedIconProperty,
            icon,
            // Switches rollover painting on whatever it is handed, as [rolloverIcon] describes.
            interference = PropertyInterference.AlsoOverwrites(RolloverEnabledProperty),
        )

/**
 * This icon where the button holds it as its own, and `null` where the look and feel derived it - which
 * is what reading a disabled state's icon makes the look and feel do while nothing has set one.
 *
 * A derived icon is a `UIResource`, which is how the button itself tells the two apart: it discards a
 * derived one whenever the icon it was derived from, or the look and feel deriving it, is replaced. So
 * it is not a value to restore - a button that never carried the modifier holds none.
 */
private fun Icon?.ownIcon(): Icon? = takeUnless { it is UIResource }

/** A look and feel styles a component for whether it carries an icon at all. */
private class IconElement(
    icon: Icon?,
) : MultiTargetPropertyElement<Icon?>(IconProperty, icon) {
    override val restores: RestorePolicy get() = RestorePolicy.DeclaredPropertyOnly
}

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

private val PressedIconProperty =
    PropertyAccessors<AbstractButton, Icon?>(
        name = "pressedIcon",
        read = { it.pressedIcon },
        write = { component, value -> component.pressedIcon = value },
    )

private val SelectedIconProperty =
    PropertyAccessors<AbstractButton, Icon?>(
        name = "selectedIcon",
        read = { it.selectedIcon },
        write = { component, value -> component.selectedIcon = value },
    )

private val DisabledIconProperty =
    PropertyAccessors<AbstractButton, Icon?>(
        name = "disabledIcon",
        read = { it.disabledIcon.ownIcon() },
        write = { component, value -> component.disabledIcon = value },
    )

private val DisabledSelectedIconProperty =
    PropertyAccessors<AbstractButton, Icon?>(
        name = "disabledSelectedIcon",
        read = {
            val own = it.disabledSelectedIcon
            // A button carrying no selected icon answers this read with its disabled icon, which
            // belongs to that declaration; only an icon of its own is one to hand back here.
            if (it.selectedIcon == null && own === it.disabledIcon) null else own.ownIcon()
        },
        write = { component, value -> component.disabledSelectedIcon = value },
    )

private val RolloverIconProperty =
    PropertyAccessors<AbstractButton, Icon?>(
        name = "rolloverIcon",
        read = { it.rolloverIcon },
        write = { component, value -> component.rolloverIcon = value },
    )

private val RolloverSelectedIconProperty =
    PropertyAccessors<AbstractButton, Icon?>(
        name = "rolloverSelectedIcon",
        read = { it.rolloverSelectedIcon },
        write = { component, value -> component.rolloverSelectedIcon = value },
    )

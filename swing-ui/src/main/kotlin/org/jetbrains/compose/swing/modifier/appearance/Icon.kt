@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.components.style.DisabledIconDescriptor
import org.jetbrains.compose.swing.components.style.DisabledSelectedIconDescriptor
import org.jetbrains.compose.swing.components.style.IconDescriptor
import org.jetbrains.compose.swing.components.style.PressedIconDescriptor
import org.jetbrains.compose.swing.components.style.RolloverEnabledDescriptor
import org.jetbrains.compose.swing.components.style.RolloverIconDescriptor
import org.jetbrains.compose.swing.components.style.RolloverSelectedIconDescriptor
import org.jetbrains.compose.swing.components.style.SelectedIconDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import javax.swing.Icon

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
@Deprecated("Set it in the component's style: style = { icon(icon) }")
public fun SwingModifier.icon(icon: Icon?): SwingModifier =
    property(IconDescriptor, icon, restores = RestorePolicy.DeclaredPropertyOnly)

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
@Deprecated("Set it in the component's style: style = { pressedIcon(icon) }")
public fun SwingModifier.pressedIcon(icon: Icon?): SwingModifier = property(PressedIconDescriptor, icon)

/**
 * Sets the icon a button displays while it is selected - a checked check box, an on toggle button;
 * `null` falls back to the base icon. Applies to every kind of button.
 *
 * @param icon the icon drawn while the button reports itself selected, whether the user or the code
 *   selected it.
 * @return this modifier with the selected icon declared on it.
 * @see javax.swing.AbstractButton.setSelectedIcon
 */
@Deprecated("Set it in the component's style: style = { selectedIcon(icon) }")
public fun SwingModifier.selectedIcon(icon: Icon?): SwingModifier = property(SelectedIconDescriptor, icon)

/**
 * Sets the icon a button displays while it is disabled; `null` hands the state back to the look and
 * feel, which grays the base icon for it. Applies to every kind of button.
 *
 * @param icon the icon drawn while the button is disabled, in place of the graying the look and feel
 *   would derive.
 * @return this modifier with the disabled icon declared on it.
 * @see javax.swing.AbstractButton.setDisabledIcon
 */
@Deprecated("Set it in the component's style: style = { disabledIcon(icon) }")
public fun SwingModifier.disabledIcon(icon: Icon?): SwingModifier =
    property(
        DisabledIconDescriptor,
        icon,
        // Replacing the base icon drops a disabled icon that is a `UIResource`, a declared one
        // included, and announces the base icon rather than the drop. A look and feel change drops
        // such an icon too, after announcing the look and feel it took, and leaves it dropped.
        rewriteOn = "icon",
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
@Deprecated("Set it in the component's style: style = { disabledSelectedIcon(icon) }")
public fun SwingModifier.disabledSelectedIcon(icon: Icon?): SwingModifier =
    property(
        DisabledSelectedIconDescriptor,
        icon,
        // Replacing the selected icon drops a disabled selected icon that is a `UIResource`, as
        // [disabledIcon] describes for the base icon.
        rewriteOn = "selectedIcon",
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
@Deprecated("Set it in the component's style: style = { rolloverIcon(icon) }")
public fun SwingModifier.rolloverIcon(icon: Icon?): SwingModifier =
    property(
        RolloverIconDescriptor,
        icon,
        // `setRolloverIcon` switches rollover painting on whatever it is handed, the `null` a
        // removal writes included.
        alsoOverwrites = listOf(RolloverEnabledDescriptor),
    )

/**
 * Sets the icon a button displays while the pointer is over it and it is selected; `null` falls back to
 * [selectedIcon]. Declaring it switches [rolloverEnabled] on. Applies to every kind of button.
 *
 * @param icon the icon drawn while the pointer is over a button that is already selected.
 * @return this modifier with the rollover selected icon declared on it.
 * @see javax.swing.AbstractButton.setRolloverSelectedIcon
 */
@Deprecated("Set it in the component's style: style = { rolloverSelectedIcon(icon) }")
public fun SwingModifier.rolloverSelectedIcon(icon: Icon?): SwingModifier =
    property(
        RolloverSelectedIconDescriptor,
        icon,
        // Switches rollover painting on whatever it is handed, as [rolloverIcon] describes.
        alsoOverwrites = listOf(RolloverEnabledDescriptor),
    )

@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor.Companion.accessor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.plaf.UIResource

/*
 * Icons - the icon a component displays, and the icons a button swaps in for its states.
 *
 * A button paints a state icon only when it has a base icon to begin with: the state icons decorate that one for
 * the states a button can be in rather than standing in for it.
 */

/**
 * Sets the icon a label displays beside its text; `null` displays none.
 *
 * A look and feel may derive other properties from the icon; once the style stops setting it, the icon is restored,
 * not what was derived from it.
 *
 * @param icon the icon drawn beside the text; its size counts toward the label's preferred size, so swapping in a
 *   differently sized one re-lays the label out.
 * @see javax.swing.JLabel.setIcon
 */
public fun LabelStyleScope.icon(icon: Icon?): Unit = IconProperty.provide(icon)

/**
 * Sets the icon a button displays beside its text; `null` displays none.
 *
 * A look and feel may derive other properties from the icon; once the style stops setting it, the icon is restored,
 * not what was derived from it.
 *
 * @param icon the icon drawn beside the text; its size counts toward the button's preferred size, so swapping in a
 *   differently sized one re-lays the button out.
 * @see javax.swing.AbstractButton.setIcon
 */
public fun ButtonStyleScope.icon(icon: Icon?): Unit = IconProperty.provide(icon)

/**
 * Sets the icon a button displays while it is held down; `null` falls back to the base icon.
 *
 * @param icon the icon drawn while the button is both pressed and armed; dragging the pointer off the button with
 *   the mouse button still down disarms it and puts the base icon back until the pointer returns.
 * @see javax.swing.AbstractButton.setPressedIcon
 */
public fun ButtonStyleScope.pressedIcon(icon: Icon?): Unit = PressedIconProperty.provide(icon)

/**
 * Sets the icon a button displays while it is selected - a checked check box, an on toggle button; `null` falls
 * back to the base icon.
 *
 * @param icon the icon drawn while the button reports itself selected, whether the user or the code selected it.
 * @see javax.swing.AbstractButton.setSelectedIcon
 */
public fun ButtonStyleScope.selectedIcon(icon: Icon?): Unit = SelectedIconProperty.provide(icon)

/**
 * Sets the icon a button displays while it is disabled; `null` hands the state back to the look and feel, which
 * grays the base icon for it.
 *
 * @param icon the icon drawn while the button is disabled, in place of the graying the look and feel would derive.
 * @see javax.swing.AbstractButton.setDisabledIcon
 */
public fun ButtonStyleScope.disabledIcon(icon: Icon?): Unit = DisabledIconProperty.provide(icon)

/**
 * Sets the icon a button displays while it is both disabled and selected; `null` hands the state back to the look
 * and feel, which grays [selectedIcon] for it, or falls back to the disabled icon where the button carries no
 * selected icon.
 *
 * @param icon the icon drawn while both states hold at once - a checked check box on a disabled form.
 * @see javax.swing.AbstractButton.setDisabledSelectedIcon
 */
public fun ButtonStyleScope.disabledSelectedIcon(icon: Icon?): Unit = DisabledSelectedIconProperty.provide(icon)

/**
 * Sets the icon a button displays while the pointer is over it; `null` falls back to the base icon. Setting it
 * switches [rolloverEnabled] on.
 *
 * @param icon the icon drawn while the button reports a rollover - the pointer entered it with the left mouse button
 *   up, and the button is neither disabled nor pressed.
 * @see javax.swing.AbstractButton.setRolloverIcon
 */
public fun ButtonStyleScope.rolloverIcon(icon: Icon?): Unit = RolloverIconProperty.provide(icon)

/**
 * Sets the icon a button displays while the pointer is over it and it is selected; `null` falls back to
 * [selectedIcon]. Setting it switches [rolloverEnabled] on.
 *
 * @param icon the icon drawn while the pointer is over a button that is already selected.
 * @see javax.swing.AbstractButton.setRolloverSelectedIcon
 */
public fun ButtonStyleScope.rolloverSelectedIcon(icon: Icon?): Unit = RolloverSelectedIconProperty.provide(icon)

/**
 * This icon where the button holds it as its own, and `null` where the look and feel derived it - which is what
 * reading a disabled state's icon makes the look and feel do while nothing has set one.
 *
 * A derived icon is a `UIResource`, which is how the button itself tells the two apart: it discards a derived one
 * whenever the icon it was derived from, or the look and feel deriving it, is replaced. So it is not a value to
 * restore - a button that never carried the property holds none.
 */
private fun Icon?.ownIcon(): Icon? = takeUnless { it is UIResource }

/**
 * `JLabel` and `AbstractButton` each declare `icon` for themselves; the class they share declares no such property,
 * so the two accessors are named separately. One case for `AbstractButton` covers every button and every menu item,
 * since all of them are built on it.
 */
internal val IconDescriptor =
    ComponentPropertyDescriptor(
        "icon",
        accessor<JLabel, Icon?>(read = { it.icon }, write = { component, value -> component.icon = value }),
        accessor<AbstractButton, Icon?>(
            read = { it.icon },
            write = { component, value -> component.icon = value },
        ),
    )

internal val PressedIconDescriptor =
    ComponentPropertyDescriptor<AbstractButton, Icon?>(
        name = "pressedIcon",
        read = { it.pressedIcon },
        write = { component, value -> component.pressedIcon = value },
    )

internal val SelectedIconDescriptor =
    ComponentPropertyDescriptor<AbstractButton, Icon?>(
        name = "selectedIcon",
        read = { it.selectedIcon },
        write = { component, value -> component.selectedIcon = value },
    )

internal val DisabledIconDescriptor =
    ComponentPropertyDescriptor<AbstractButton, Icon?>(
        name = "disabledIcon",
        read = { it.disabledIcon.ownIcon() },
        write = { component, value -> component.disabledIcon = value },
    )

internal val DisabledSelectedIconDescriptor =
    ComponentPropertyDescriptor<AbstractButton, Icon?>(
        name = "disabledSelectedIcon",
        read = {
            val own = it.disabledSelectedIcon
            // A button carrying no selected icon answers this read with its disabled icon, which belongs to that
            // declaration; only an icon of its own is one to hand back here.
            if (it.selectedIcon == null && own === it.disabledIcon) null else own.ownIcon()
        },
        write = { component, value -> component.disabledSelectedIcon = value },
    )

internal val RolloverIconDescriptor =
    ComponentPropertyDescriptor<AbstractButton, Icon?>(
        name = "rolloverIcon",
        read = { it.rolloverIcon },
        write = { component, value -> component.rolloverIcon = value },
    )

internal val RolloverSelectedIconDescriptor =
    ComponentPropertyDescriptor<AbstractButton, Icon?>(
        name = "rolloverSelectedIcon",
        read = { it.rolloverSelectedIcon },
        write = { component, value -> component.rolloverSelectedIcon = value },
    )

private val IconProperty = stylePropertyOf(IconDescriptor, restores = RestorePolicy.DeclaredPropertyOnly)

private val PressedIconProperty = stylePropertyOf(PressedIconDescriptor)

private val SelectedIconProperty = stylePropertyOf(SelectedIconDescriptor)

private val DisabledIconProperty =
    stylePropertyOf(
        DisabledIconDescriptor,
        // Replacing the base icon drops a disabled icon that is a `UIResource`, a set one included, and announces
        // the base icon rather than the drop. A look and feel change drops such an icon too, after announcing the
        // look and feel it took, and leaves it dropped.
        rewriteOn = "icon",
    )

private val DisabledSelectedIconProperty =
    stylePropertyOf(
        DisabledSelectedIconDescriptor,
        // Replacing the selected icon drops a disabled selected icon that is a `UIResource`, as the disabled icon's
        // property describes for the base icon.
        rewriteOn = "selectedIcon",
    )

private val RolloverIconProperty =
    stylePropertyOf(
        RolloverIconDescriptor,
        // `setRolloverIcon` switches rollover painting on whatever it is handed, the `null` a removal writes
        // included.
        alsoOverwrites = listOf(RolloverEnabledDescriptor),
    )

private val RolloverSelectedIconProperty =
    stylePropertyOf(
        RolloverSelectedIconDescriptor,
        // Switches rollover painting on whatever it is handed, as the rollover icon's property describes.
        alsoOverwrites = listOf(RolloverEnabledDescriptor),
    )

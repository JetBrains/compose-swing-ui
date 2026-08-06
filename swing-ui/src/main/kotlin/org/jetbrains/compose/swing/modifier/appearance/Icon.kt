@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.MultiTargetProperty
import org.jetbrains.compose.swing.modifier.MultiTargetPropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyCase
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JLabel

/**
 * Sets the icon a component displays beside its text; `null` displays none.
 *
 * Applies to labels and to everything built on a button - buttons, check boxes, radio buttons, toggle
 * buttons, and every kind of menu item.
 */
public fun SwingModifier.icon(icon: Icon?): SwingModifier = this then MultiTargetPropertyElement(IconProperty, icon)

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

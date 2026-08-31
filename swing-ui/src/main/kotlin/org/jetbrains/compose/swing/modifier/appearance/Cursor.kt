@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.Cursor

/**
 * Sets `cursor`; `null` restores the inherited cursor.
 *
 * @param cursor the pointer shape shown over the component while it is visible, displayable and enabled,
 *   and over every child that has none of its own. A platform that cannot change the pointer shape shows
 *   nothing different.
 * @return this chain with the cursor declared on it.
 * @see java.awt.Component.setCursor
 */
public fun SwingModifier.cursor(cursor: Cursor?): SwingModifier =
    this then
        propertyElement<Component, Cursor?>(
            name = "cursor",
            value = cursor,
            read = { it.cursor },
            write = { component, value -> component.cursor = value },
        )

@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import java.awt.Component
import java.awt.Cursor

/**
 * Sets `cursor`; `null` restores the inherited cursor.
 *
 * @param cursor the pointer shape shown over the component while it is visible, displayable and enabled,
 *   and over every child that has none of its own. A platform that cannot change the pointer shape shows
 *   nothing different.
 * @return this modifier with the cursor declared on it.
 * @see java.awt.Component.setCursor
 */
public fun SwingModifier.cursor(cursor: Cursor?): SwingModifier = property(CursorProperty, cursor, inheritable = true)

private val CursorProperty =
    ComponentPropertyDescriptor<Component, Cursor?>(
        name = "cursor",
        read = { if (it.isCursorSet) it.cursor else null },
        write = { component, value -> component.cursor = value },
    )

@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import java.awt.Component
import java.awt.Cursor

/**
 * Sets `cursor`; `null` restores the inherited cursor.
 *
 * @param cursor the pointer shape shown over the component while it is visible, displayable and enabled,
 *   and over every child that has none of its own. A platform that cannot change the pointer shape shows
 *   nothing different.
 * @see java.awt.Component.setCursor
 */
public fun ComponentStyleScope.cursor(cursor: Cursor?): Unit = CursorProperty.provide(cursor)

internal val CursorDescriptor =
    ComponentPropertyDescriptor<Component, Cursor?>(
        name = "cursor",
        read = { if (it.isCursorSet) it.cursor else null },
        write = { component, value -> component.cursor = value },
    )

private val CursorProperty = stylePropertyOf(CursorDescriptor)

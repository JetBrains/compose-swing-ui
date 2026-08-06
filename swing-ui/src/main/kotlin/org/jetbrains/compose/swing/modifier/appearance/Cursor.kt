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
 * @see java.awt.Component.setCursor
 */
public fun SwingModifier.cursor(cursor: Cursor?): SwingModifier =
    this then propertyElement<Component, Cursor?>(cursor, read = { it.cursor }, write = { c, v -> c.cursor = v })

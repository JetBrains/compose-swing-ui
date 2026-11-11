// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.inheritableProperty
import org.jetbrains.compose.swing.style.CursorDescriptor
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
@Deprecated("Set it in the component's style: style = { cursor(cursor) }")
public fun SwingModifier.cursor(cursor: Cursor?): SwingModifier = inheritableProperty(CursorDescriptor, cursor)

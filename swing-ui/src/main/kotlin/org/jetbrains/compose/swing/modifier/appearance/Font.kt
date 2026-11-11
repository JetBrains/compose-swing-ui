// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.inheritableProperty
import org.jetbrains.compose.swing.style.FontDescriptor
import java.awt.Font

/**
 * Sets `font`; `null` takes the font from the parent container.
 *
 * @param font the font the component draws its text in, inherited by children that have none of their own;
 *   a change of size re-lays the component out.
 * @return this modifier with the font declared on it.
 * @see java.awt.Component.setFont
 */
@Deprecated("Set it in the component's style: style = { font(font) }")
public fun SwingModifier.font(font: Font?): SwingModifier = inheritableProperty(FontDescriptor, font)

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.inheritableProperty
import org.jetbrains.compose.swing.style.ForegroundDescriptor
import java.awt.Color

/**
 * Sets `foreground`; `null` takes the color from the parent container.
 *
 * @param color the color the component draws its text and content in, inherited by children that have none
 *   of their own.
 * @return this modifier with the foreground color declared on it.
 * @see java.awt.Component.setForeground
 */
@Deprecated("Set it in the component's style: style = { foreground(color) }")
public fun SwingModifier.foreground(color: Color?): SwingModifier = inheritableProperty(ForegroundDescriptor, color)

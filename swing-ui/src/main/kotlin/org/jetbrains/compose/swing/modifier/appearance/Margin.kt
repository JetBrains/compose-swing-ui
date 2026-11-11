// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.components.style.MarginDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import java.awt.Insets

/**
 * Sets the space a component keeps between its border and its content. Applies to everything built on
 * a button, and to text components.
 *
 * This is the space inside the border; to put space outside it, size the component or space it in its
 * container. Removing the modifier restores the space the look and feel had chosen.
 *
 * It is the component's default border that turns this into space, so a chain that also declares a
 * [border] leaves the margin to that border - one built by [lineBorder] or [emptyBorder] ignores it.
 *
 * @param margin how much each side keeps; all four at zero pulls the content flush with the border.
 * @return this chain with the margin declared on it.
 * @see javax.swing.AbstractButton.setMargin
 * @see javax.swing.text.JTextComponent.setMargin
 */
@Deprecated("Set it in the component's style: style = { margin(margin) }")
public fun SwingModifier.margin(margin: Insets): SwingModifier = property(MarginDescriptor, margin)

@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.style.ForegroundDescriptor
import java.awt.Color

/**
 * Sets `foreground`; `null` takes the color from the parent container.
 *
 * @param color the color the component draws its text and content in.
 * @return this modifier with the foreground color declared on it.
 * @see java.awt.Component.setForeground
 */
@Deprecated("Set it in the component's style: style = { foreground(color) }")
public fun SwingModifier.foreground(color: Color?): SwingModifier =
    property(ForegroundDescriptor, color, inheritable = true)

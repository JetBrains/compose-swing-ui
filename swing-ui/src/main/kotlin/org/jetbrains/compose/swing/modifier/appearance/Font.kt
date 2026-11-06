@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.style.FontDescriptor
import java.awt.Font

/**
 * Sets `font`; `null` takes the font from the parent container.
 *
 * @param font the font the component draws its text in;
 *   a change of size re-lays the component out.
 * @return this modifier with the font declared on it.
 * @see java.awt.Component.setFont
 */
@Deprecated("Set it in the component's style: style = { font(font) }")
public fun SwingModifier.font(font: Font?): SwingModifier = property(FontDescriptor, font, inheritable = true)

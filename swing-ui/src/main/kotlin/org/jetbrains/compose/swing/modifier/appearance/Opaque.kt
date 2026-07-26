@file:JvmMultifileClass
@file:JvmName("AppearanceModifiersKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.JComponent

/** Sets `isOpaque` - required for [background] to actually paint. Requires a `JComponent` target. */
public fun SwingModifier.opaque(opaque: Boolean): SwingModifier =
    this then propertyElement<JComponent, Boolean>(opaque, read = { it.isOpaque }, write = { c, v -> c.isOpaque = v })

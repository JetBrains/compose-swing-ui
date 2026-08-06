@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.JComponent
import javax.swing.border.Border

/** Sets `border`; `null` removes the border. Requires a `JComponent` target. */
public fun SwingModifier.border(border: Border?): SwingModifier =
    this then propertyElement<JComponent, Border?>(border, read = { it.border }, write = { c, v -> c.border = v })

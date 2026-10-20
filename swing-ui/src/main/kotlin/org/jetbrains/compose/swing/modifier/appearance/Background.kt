@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.style.BackgroundDescriptor
import java.awt.Color

/**
 * Sets `background`; on a non-opaque component also declare [opaque]`(true)` for it to paint.
 *
 * @param color the background color; `null` takes the color from the parent container. Which parts of a
 *   component the color reaches is up to that component and to the platform.
 * @return this modifier with the background color declared on it.
 * @see java.awt.Component.setBackground
 */
@Deprecated("Set it in the component's style: style = { background(color) }")
public fun SwingModifier.background(color: Color?): SwingModifier =
    property(BackgroundDescriptor, color, inheritable = true)

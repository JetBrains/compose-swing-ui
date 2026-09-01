@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.JComponent

/**
 * Sets `isOpaque` - required for [background] to actually paint. Requires a `JComponent` target.
 *
 * @param opaque `true` promises the component paints every pixel of its bounds, letting Swing skip what is
 *   behind it; `false` lets the parent show through.
 * @return this chain with the opaque flag declared on it.
 * @see javax.swing.JComponent.setOpaque
 */
public fun SwingModifier.opaque(opaque: Boolean): SwingModifier =
    this then
        propertyElement<JComponent, Boolean>(
            opaque,
            read = { it.isOpaque },
            write = { component, value -> component.isOpaque = value },
        )

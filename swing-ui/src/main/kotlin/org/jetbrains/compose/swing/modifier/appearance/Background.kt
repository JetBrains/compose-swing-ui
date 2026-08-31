@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent

/**
 * Sets `background`; on a non-opaque component also chain [opaque]`(true)` for it to paint.
 *
 * @param color the background color; `null` takes the color from the parent container. Which parts of a
 *   component the color reaches is up to that component and to the platform.
 * @return this chain with the background color declared on it.
 * @see java.awt.Component.setBackground
 */
public fun SwingModifier.background(color: Color?): SwingModifier =
    this then
        propertyElement<Component, Color?>(
            name = "background",
            value = color,
            read = { it.background },
            write = { component, value ->
                component.background = value
                // JComponent.setBackground already repaints. A plain AWT Component does not, so the
                // new color would not show until an unrelated repaint - request one here.
                if (component !is JComponent) {
                    component.revalidate()
                    component.repaint()
                }
            },
        )

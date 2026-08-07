@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent

/**
 * Sets `foreground`; `null` restores the inherited/look-and-feel default.
 *
 * @see java.awt.Component.setForeground
 */
public fun SwingModifier.foreground(color: Color?): SwingModifier =
    this then
        propertyElement<Component, Color?>(
            color,
            read = { it.foreground },
            write = { c, v ->
                c.foreground = v
                // JComponent.setForeground already repaints. A plain AWT Component does not, so the
                // new color would not show until an unrelated repaint - request one here.
                if (c !is JComponent) {
                    c.revalidate()
                    c.repaint()
                }
            },
        )

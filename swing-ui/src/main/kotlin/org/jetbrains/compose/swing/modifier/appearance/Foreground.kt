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
 * @param color the color the component draws its text and content in, inherited by children that have none
 *   of their own.
 * @return this chain with the foreground color declared on it.
 * @see java.awt.Component.setForeground
 */
public fun SwingModifier.foreground(color: Color?): SwingModifier =
    this then
        propertyElement<Component, Color?>(
            name = "foreground",
            value = color,
            read = { it.foreground },
            write = { component, value ->
                component.foreground = value
                // JComponent.setForeground already repaints. A plain AWT Component does not, so the
                // new color would not show until an unrelated repaint - request one here.
                if (component !is JComponent) {
                    component.revalidate()
                    component.repaint()
                }
            },
        )

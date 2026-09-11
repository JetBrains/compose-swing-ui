@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.PropertyAccessors
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent

/**
 * Sets `foreground`; `null` takes the color from the parent container.
 *
 * @param color the color the component draws its text and content in, inherited by children that have none
 *   of their own.
 * @return this modifier with the foreground color declared on it.
 * @see java.awt.Component.setForeground
 */
public fun SwingModifier.foreground(color: Color?): SwingModifier =
    this then propertyElement(ForegroundProperty, color, inheritable = true)

private val ForegroundProperty =
    PropertyAccessors<Component, Color?>(
        name = "foreground",
        read = { if (it.isForegroundSet) it.foreground else null },
        write = { component, value ->
            component.foreground = value
            // JComponent.setForeground already repaints. A plain AWT Component does not, so the
            // new color would not show until an unrelated repaint - request one here. A color is
            // read at paint only, so it asks for no layout.
            if (component !is JComponent) component.repaint()
        },
    )

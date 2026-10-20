@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent

/**
 * Sets `foreground`; `null` takes the color from the parent container.
 *
 * Every descendant component that takes a style and sets no foreground of its own takes this one, the nearest
 * ancestor's winning. `null` stops that at this component, and its descendants keep their own color.
 *
 * @param color the color the component draws its text and content in, inherited by children that have none
 *   of their own.
 * @see java.awt.Component.setForeground
 */
public fun ComponentStyleScope.foreground(color: Color?): Unit = ForegroundProperty.provide(color)

internal val ForegroundDescriptor =
    ComponentPropertyDescriptor<Component, Color?>(
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

private val ForegroundProperty = styleLocalOf(ForegroundDescriptor)

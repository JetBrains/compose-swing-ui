@file:JvmMultifileClass
@file:JvmName("AppearanceModifiersKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.Font
import javax.swing.JComponent

/** Sets `font`; `null` restores the default. */
public fun SwingModifier.font(font: Font?): SwingModifier =
    this then
        propertyElement<Component, Font?>(
            font,
            read = { it.font },
            write = { c, v ->
                c.font = v
                // JComponent.setFont already revalidates and repaints. A plain AWT Component only
                // invalidates, so a font change that resizes it stays invisible until an unrelated
                // relayout - request one here for the non-JComponent target.
                if (c !is JComponent) {
                    c.revalidate()
                    c.repaint()
                }
            },
        )

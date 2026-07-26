@file:JvmMultifileClass
@file:JvmName("AppearanceModifiersKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent

/** Sets `background`; on a non-opaque component also chain [opaque]`(true)` for it to paint. */
public fun SwingModifier.background(color: Color?): SwingModifier =
    this then
        propertyElement<Component, Color?>(
            color,
            read = { it.background },
            write = { c, v ->
                c.background = v
                // JComponent.setBackground already repaints. A plain AWT Component does not, so the
                // new colour would not show until an unrelated repaint - request one here.
                if (c !is JComponent) {
                    c.revalidate()
                    c.repaint()
                }
            },
        )

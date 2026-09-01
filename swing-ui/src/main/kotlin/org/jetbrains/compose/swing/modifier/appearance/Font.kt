@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.Font
import javax.swing.JComponent

/**
 * Sets `font`; `null` restores the default.
 *
 * @param font the font the component draws its text in, inherited by children that have none of their own;
 *   a change of size re-lays the component out.
 * @return this chain with the font declared on it.
 * @see java.awt.Component.setFont
 */
public fun SwingModifier.font(font: Font?): SwingModifier =
    this then
        propertyElement<Component, Font?>(
            font,
            read = { it.font },
            write = { component, value ->
                component.font = value
                // JComponent.setFont already revalidates and repaints. A plain AWT Component only
                // invalidates, so a font change that resizes it stays invisible until an unrelated
                // relayout - request one here for the non-JComponent target.
                if (component !is JComponent) {
                    component.revalidate()
                    component.repaint()
                }
            },
        )

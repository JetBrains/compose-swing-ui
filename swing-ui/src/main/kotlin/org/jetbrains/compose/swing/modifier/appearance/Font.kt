@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.PropertyAccessors
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.Font
import javax.swing.JComponent

/**
 * Sets `font`; `null` takes the font from the parent container.
 *
 * @param font the font the component draws its text in, inherited by children that have none of their own;
 *   a change of size re-lays the component out.
 * @return this modifier with the font declared on it.
 * @see java.awt.Component.setFont
 */
public fun SwingModifier.font(font: Font?): SwingModifier =
    this then propertyElement(FontProperty, font, inheritable = true)

private val FontProperty =
    PropertyAccessors<Component, Font?>(
        name = "font",
        read = { if (it.isFontSet) it.font else null },
        write = { component, value ->
            component.font = value
            // JComponent.setFont already revalidates and repaints. A plain AWT Component only
            // invalidates, so request both here: a font changes the size, which needs a relayout,
            // and the glyphs, which need a repaint even where the bounds stay the same.
            if (component !is JComponent) {
                component.revalidate()
                component.repaint()
            }
        },
    )

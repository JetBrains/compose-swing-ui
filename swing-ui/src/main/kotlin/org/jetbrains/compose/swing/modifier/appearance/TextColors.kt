@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Color
import javax.swing.text.JTextComponent

/**
 * Sets the color of the caret in a text component - fields, areas, editor and text panes.
 *
 * A text component takes these colors from the look and feel, and only while they have not been set;
 * once one is set here, it holds until the modifier leaves the chain, which puts back the color the
 * look and feel had supplied.
 *
 * @see javax.swing.text.JTextComponent.setCaretColor
 */
public fun SwingModifier.caretColor(color: Color): SwingModifier =
    this then
        propertyElement<JTextComponent, Color>(
            color,
            read = { it.caretColor },
            write = { c, v ->
                c.caretColor = v
                // A text component's color setters only fire a property change, and the color itself is
                // read at paint time, so each of these writes asks for the repaint that puts the new
                // color on the screen.
                c.repaint()
            },
        )

/**
 * Sets the background painted behind selected text in a text component.
 *
 * @see javax.swing.text.JTextComponent.setSelectionColor
 */
public fun SwingModifier.selectionColor(color: Color): SwingModifier =
    this then
        propertyElement<JTextComponent, Color>(
            color,
            read = { it.selectionColor },
            write = { c, v ->
                c.selectionColor = v
                c.repaint()
            },
        )

/**
 * Sets the color selected text is drawn in.
 *
 * @see javax.swing.text.JTextComponent.setSelectedTextColor
 */
public fun SwingModifier.selectedTextColor(color: Color): SwingModifier =
    this then
        propertyElement<JTextComponent, Color>(
            color,
            read = { it.selectedTextColor },
            write = { c, v ->
                c.selectedTextColor = v
                c.repaint()
            },
        )

/**
 * Sets the color text is drawn in while the component is disabled.
 *
 * @see javax.swing.text.JTextComponent.setDisabledTextColor
 */
public fun SwingModifier.disabledTextColor(color: Color): SwingModifier =
    this then
        propertyElement<JTextComponent, Color>(
            color,
            read = { it.disabledTextColor },
            write = { c, v ->
                c.disabledTextColor = v
                c.repaint()
            },
        )

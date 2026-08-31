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
 * @param color the color the blinking insertion caret is drawn in.
 * @return this chain with the caret color declared on it.
 * @see javax.swing.text.JTextComponent.setCaretColor
 */
public fun SwingModifier.caretColor(color: Color): SwingModifier =
    this then
        propertyElement<JTextComponent, Color>(
            name = "caretColor",
            value = color,
            read = { it.caretColor },
            write = { component, value ->
                component.caretColor = value
                // A text component's color setters only fire a property change, and the color itself is
                // read at paint time, so each of these writes asks for the repaint that puts the new
                // color on the screen.
                component.repaint()
            },
        )

/**
 * Sets the background painted behind selected text in a text component.
 *
 * @param color the color filled behind the selected span; the characters over it take [selectedTextColor].
 * @return this chain with the selection color declared on it.
 * @see javax.swing.text.JTextComponent.setSelectionColor
 */
public fun SwingModifier.selectionColor(color: Color): SwingModifier =
    this then
        propertyElement<JTextComponent, Color>(
            name = "selectionColor",
            value = color,
            read = { it.selectionColor },
            write = { component, value ->
                component.selectionColor = value
                component.repaint()
            },
        )

/**
 * Sets the color selected text is drawn in.
 *
 * @param color the color the characters inside the selection take, in place of the component's foreground.
 * @return this chain with the selected text color declared on it.
 * @see javax.swing.text.JTextComponent.setSelectedTextColor
 */
public fun SwingModifier.selectedTextColor(color: Color): SwingModifier =
    this then
        propertyElement<JTextComponent, Color>(
            name = "selectedTextColor",
            value = color,
            read = { it.selectedTextColor },
            write = { component, value ->
                component.selectedTextColor = value
                component.repaint()
            },
        )

/**
 * Sets the color text is drawn in while the component is disabled.
 *
 * @param color the color the whole text takes once the component is disabled; a component that is only
 *   non-editable keeps its foreground.
 * @return this chain with the disabled text color declared on it.
 * @see javax.swing.text.JTextComponent.setDisabledTextColor
 */
public fun SwingModifier.disabledTextColor(color: Color): SwingModifier =
    this then
        propertyElement<JTextComponent, Color>(
            name = "disabledTextColor",
            value = color,
            read = { it.disabledTextColor },
            write = { component, value ->
                component.disabledTextColor = value
                component.repaint()
            },
        )

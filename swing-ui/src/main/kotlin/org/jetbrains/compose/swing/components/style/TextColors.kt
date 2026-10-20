@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.style.stylePropertyOf
import java.awt.Color
import javax.swing.text.JTextComponent

/**
 * Sets the color of the caret in a text component.
 *
 * A text component takes these colors from the look and feel, and only while they have not been set;
 * once one is set here, it holds until the style stops setting it, which puts back the color the look
 * and feel had supplied.
 *
 * @param color the color the blinking insertion caret is drawn in.
 * @see javax.swing.text.JTextComponent.setCaretColor
 */
public fun TextStyleScope.caretColor(color: Color): Unit = CaretColorProperty.provide(color)

/**
 * Sets the background painted behind selected text in a text component.
 *
 * @param color the color filled behind the selected span; the characters over it take [selectedTextColor].
 * @see javax.swing.text.JTextComponent.setSelectionColor
 */
public fun TextStyleScope.selectionColor(color: Color): Unit = SelectionColorProperty.provide(color)

/**
 * Sets the color selected text is drawn in.
 *
 * @param color the color the characters inside the selection take, in place of the component's foreground.
 * @see javax.swing.text.JTextComponent.setSelectedTextColor
 */
public fun TextStyleScope.selectedTextColor(color: Color): Unit = SelectedTextColorProperty.provide(color)

/**
 * Sets the color text is drawn in while the component is disabled.
 *
 * @param color the color the whole text takes once the component is disabled; a component that is only
 *   non-editable keeps its foreground.
 * @see javax.swing.text.JTextComponent.setDisabledTextColor
 */
public fun TextStyleScope.disabledTextColor(color: Color): Unit = DisabledTextColorProperty.provide(color)

internal val CaretColorDescriptor =
    ComponentPropertyDescriptor<JTextComponent, Color>(
        name = "caretColor",
        read = { it.caretColor },
        write = { component, value ->
            component.caretColor = value
            // A text component's color setters only fire a property change, and the color itself is
            // read at paint time, so each of these writes asks for the repaint that puts the new
            // color on the screen.
            component.repaint()
        },
    )

internal val SelectionColorDescriptor =
    ComponentPropertyDescriptor<JTextComponent, Color>(
        name = "selectionColor",
        read = { it.selectionColor },
        write = { component, value ->
            component.selectionColor = value
            component.repaint()
        },
    )

internal val SelectedTextColorDescriptor =
    ComponentPropertyDescriptor<JTextComponent, Color>(
        name = "selectedTextColor",
        read = { it.selectedTextColor },
        write = { component, value ->
            component.selectedTextColor = value
            component.repaint()
        },
    )

internal val DisabledTextColorDescriptor =
    ComponentPropertyDescriptor<JTextComponent, Color>(
        name = "disabledTextColor",
        read = { it.disabledTextColor },
        write = { component, value ->
            component.disabledTextColor = value
            component.repaint()
        },
    )

private val CaretColorProperty = stylePropertyOf(CaretColorDescriptor)
private val SelectionColorProperty = stylePropertyOf(SelectionColorDescriptor)
private val SelectedTextColorProperty = stylePropertyOf(SelectedTextColorDescriptor)
private val DisabledTextColorProperty = stylePropertyOf(DisabledTextColorDescriptor)

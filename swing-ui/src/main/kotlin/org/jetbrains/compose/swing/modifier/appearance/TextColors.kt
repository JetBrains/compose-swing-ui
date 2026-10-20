@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.components.style.CaretColorDescriptor
import org.jetbrains.compose.swing.components.style.DisabledTextColorDescriptor
import org.jetbrains.compose.swing.components.style.SelectedTextColorDescriptor
import org.jetbrains.compose.swing.components.style.SelectionColorDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import java.awt.Color

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
@Deprecated("Set it in the component's style: style = { caretColor(color) }")
public fun SwingModifier.caretColor(color: Color): SwingModifier = property(CaretColorDescriptor, color)

/**
 * Sets the background painted behind selected text in a text component.
 *
 * @param color the color filled behind the selected span; the characters over it take [selectedTextColor].
 * @return this chain with the selection color declared on it.
 * @see javax.swing.text.JTextComponent.setSelectionColor
 */
@Deprecated("Set it in the component's style: style = { selectionColor(color) }")
public fun SwingModifier.selectionColor(color: Color): SwingModifier = property(SelectionColorDescriptor, color)

/**
 * Sets the color selected text is drawn in.
 *
 * @param color the color the characters inside the selection take, in place of the component's foreground.
 * @return this chain with the selected text color declared on it.
 * @see javax.swing.text.JTextComponent.setSelectedTextColor
 */
@Deprecated("Set it in the component's style: style = { selectedTextColor(color) }")
public fun SwingModifier.selectedTextColor(color: Color): SwingModifier = property(SelectedTextColorDescriptor, color)

/**
 * Sets the color text is drawn in while the component is disabled.
 *
 * @param color the color the whole text takes once the component is disabled; a component that is only
 *   non-editable keeps its foreground.
 * @return this chain with the disabled text color declared on it.
 * @see javax.swing.text.JTextComponent.setDisabledTextColor
 */
@Deprecated("Set it in the component's style: style = { disabledTextColor(color) }")
public fun SwingModifier.disabledTextColor(color: Color): SwingModifier = property(DisabledTextColorDescriptor, color)

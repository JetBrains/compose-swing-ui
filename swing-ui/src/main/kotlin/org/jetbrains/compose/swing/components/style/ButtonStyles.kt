@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.SwingComponentStyleScope

/** What a style can set on a component backed by a `javax.swing.AbstractButton`: any button or menu item. */
public interface ButtonStyleScope : SwingComponentStyleScope

/** What a style can set on a button that has a checked state. */
public interface CheckableStyleScope : ButtonStyleScope {
    /** The `checked` value the component was declared with, never the widget's own state. */
    public val isChecked: Boolean
}

/** What a style can set on a button that has a selected state. */
public interface SelectableStyleScope : ButtonStyleScope {
    /** The `selected` value the component was declared with, never the widget's own state. */
    public val isSelected: Boolean
}

/**
 * Runs [block] while the component is declared checked. A change of `checked` runs the style again.
 *
 * The look and feel honors the font, foreground, toolTip and per-state icons set in [block]. A property the look
 * and feel paints itself, such as the background, may not show.
 *
 * @param block the declarations that apply while the component is declared checked.
 */
public inline fun CheckableStyleScope.checked(block: () -> Unit) {
    if (isChecked) block()
}

/**
 * Runs [block] while the component is declared selected. A change of `selected` runs the style again.
 *
 * The look and feel honors the font, foreground, toolTip and per-state icons set in [block]. A property the look
 * and feel paints itself, such as the background, may not show.
 *
 * @param block the declarations that apply while the component is declared selected.
 */
public inline fun SelectableStyleScope.selected(block: () -> Unit) {
    if (isSelected) block()
}

/** Styles provided for every Button. */
public val Buttons: NestedStyleKey<ButtonStyleScope> = NestedStyleKey("Buttons")

/** Styles provided for every CheckBox. */
public val CheckBoxes: NestedStyleKey<CheckableStyleScope> = NestedStyleKey("CheckBoxes")

/** Styles provided for every RadioButton and every RadioGroup option. */
public val RadioButtons: NestedStyleKey<SelectableStyleScope> = NestedStyleKey("RadioButtons")

/** Styles provided for every ToggleButton. */
public val ToggleButtons: NestedStyleKey<SelectableStyleScope> = NestedStyleKey("ToggleButtons")

@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.SwingComponentStyleScope

/**
 * TextField, PasswordField, FormattedTextField, TextArea, EditorPane and TextPane: the properties of a
 * `JTextComponent`.
 */
public interface TextStyleScope : SwingComponentStyleScope

/** TextField, PasswordField and FormattedTextField: the properties of a `JTextField`. */
public interface TextFieldStyleScope : TextStyleScope

/** Styles provided for every TextField. */
public val TextFields: NestedStyleKey<TextFieldStyleScope> = NestedStyleKey("TextFields")

/** Styles provided for every PasswordField. */
public val PasswordFields: NestedStyleKey<TextFieldStyleScope> = NestedStyleKey("PasswordFields")

/** Styles provided for every FormattedTextField. */
public val FormattedTextFields: NestedStyleKey<TextFieldStyleScope> = NestedStyleKey("FormattedTextFields")

/** Styles provided for every TextArea. */
public val TextAreas: NestedStyleKey<TextStyleScope> = NestedStyleKey("TextAreas")

/** Styles provided for every EditorPane. */
public val EditorPanes: NestedStyleKey<TextStyleScope> = NestedStyleKey("EditorPanes")

/** Styles provided for every TextPane. */
public val TextPanes: NestedStyleKey<TextStyleScope> = NestedStyleKey("TextPanes")

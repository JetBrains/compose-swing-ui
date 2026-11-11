// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("AccessibilityModifierKt")

package org.jetbrains.compose.swing.modifier.accessibility

import org.jetbrains.compose.swing.components.style.DisplayedMnemonicIndexElement
import org.jetbrains.compose.swing.components.style.MnemonicDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import java.awt.event.KeyEvent

/*
 * Mnemonic SwingModifiers - the underlined letter that activates a component when pressed with Alt -
 * Ctrl+Alt on macOS - and which occurrence of that letter is underlined.
 *
 * All of them require an `AbstractButton` (Button, CheckBox, RadioButton, menu item, ...) or a `JLabel`
 * target. On a button the mnemonic activates the button; on a label it moves focus to the label's
 * [labelFor] target.
 */

/**
 * Sets the keyboard mnemonic to the key identified by [keyCode], a `KeyEvent.VK_*` value.
 * `KeyEvent.VK_UNDEFINED` declares no mnemonic.
 *
 * This is the form to use for a key that types no character - a function key, an arrow - and the form
 * a character mnemonic resolves to.
 *
 * @param keyCode the `KeyEvent.VK_*` code of the key that activates the component.
 * @return this modifier with the mnemonic declared on it.
 * @see javax.swing.AbstractButton.setMnemonic
 * @see javax.swing.JLabel.setDisplayedMnemonic
 */
@Deprecated("Set it in the component's style: style = { mnemonic(keyCode) }")
public fun SwingModifier.mnemonic(keyCode: Int): SwingModifier = property(MnemonicDescriptor, keyCode)

/**
 * Sets the keyboard mnemonic to the key that types [mnemonic], resolved with
 * `KeyEvent.getExtendedKeyCodeForChar` - so `'s'` and `'S'` both declare the S key. A character that
 * appears on no known keyboard layout resolves to `KeyEvent.VK_UNDEFINED`, declaring no mnemonic.
 *
 * @param mnemonic the character to use as the mnemonic.
 * @return this modifier with the mnemonic declared on it.
 * @see javax.swing.AbstractButton.setMnemonic
 * @see javax.swing.JLabel.setDisplayedMnemonic
 */
@Deprecated("Set it in the component's style: style = { mnemonic(mnemonic) }")
public fun SwingModifier.mnemonic(mnemonic: Char): SwingModifier =
    property(MnemonicDescriptor, KeyEvent.getExtendedKeyCodeForChar(mnemonic.code))

/**
 * Sets which occurrence of the mnemonic letter in the text is underlined, as a zero-based index into
 * the text; `-1` underlines none of them. Use it when the letter appears more than once and the first
 * one is not the one to decorate - `displayedMnemonicIndex(5)` underlines the `A` of `Save As`.
 *
 * An unchanged declaration does not rewrite the index. After the text or mnemonic changes, the declared
 * index is written back while the text reaches it, wherever the [mnemonic] appears in the modifier.
 * Removing the declaration leaves the index the widget derives from its current text and mnemonic.
 *
 * Throws when [index] is below `-1` or beyond the end of the text, as Swing does. Where a later text no
 * longer reaches it, the widget keeps the index it derives.
 *
 * @param index the zero-based index into the text of the character to underline, or `-1` to underline
 *   none of them.
 * @return this modifier with the underlined index declared on it.
 * @see javax.swing.AbstractButton.setDisplayedMnemonicIndex
 * @see javax.swing.JLabel.setDisplayedMnemonicIndex
 */
@Deprecated("Set it in the component's style: style = { displayedMnemonicIndex(index) }")
public fun SwingModifier.displayedMnemonicIndex(index: Int): SwingModifier =
    this then DisplayedMnemonicIndexElement(index)

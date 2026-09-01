@file:JvmMultifileClass
@file:JvmName("AccessibilityModifierKt")

package org.jetbrains.compose.swing.modifier.accessibility

import org.jetbrains.compose.swing.modifier.MultiTargetProperty
import org.jetbrains.compose.swing.modifier.MultiTargetPropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyCase
import java.awt.event.KeyEvent
import javax.swing.AbstractButton
import javax.swing.JLabel

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
 * @return this chain with the mnemonic declared on it.
 * @see javax.swing.AbstractButton.setMnemonic
 * @see javax.swing.JLabel.setDisplayedMnemonic
 */
public fun SwingModifier.mnemonic(keyCode: Int): SwingModifier =
    this then MultiTargetPropertyElement(MnemonicProperty, keyCode)

/**
 * Sets the keyboard mnemonic to the key that types [mnemonic], resolved with
 * `KeyEvent.getExtendedKeyCodeForChar` - so `'s'` and `'S'` both declare the S key. A character that
 * appears on no known keyboard layout resolves to `KeyEvent.VK_UNDEFINED`, declaring no mnemonic.
 *
 * @param mnemonic the character to use as the mnemonic.
 * @return this chain with the mnemonic declared on it.
 * @see javax.swing.AbstractButton.setMnemonic
 * @see javax.swing.JLabel.setDisplayedMnemonic
 */
public fun SwingModifier.mnemonic(mnemonic: Char): SwingModifier =
    this.mnemonic(KeyEvent.getExtendedKeyCodeForChar(mnemonic.code))

/**
 * Sets which occurrence of the mnemonic letter in the text is underlined, as a zero-based index into
 * the text; `-1` underlines none of them. Use it when the letter appears more than once and the first
 * one is not the one to decorate - `displayedMnemonicIndex(5)` underlines the `A` of `Save As`.
 *
 * Setting the mnemonic recomputes the index, and so does changing the text, so declare this after the
 * [mnemonic] in the chain.
 *
 * Throws when [index] is below `-1` or beyond the end of the text, as Swing does.
 *
 * @param index the zero-based index into the text of the character to underline, or `-1` to underline
 *   none of them.
 * @return this chain with the underlined index declared on it.
 * @see javax.swing.AbstractButton.setDisplayedMnemonicIndex
 * @see javax.swing.JLabel.setDisplayedMnemonicIndex
 */
public fun SwingModifier.displayedMnemonicIndex(index: Int): SwingModifier =
    this then MultiTargetPropertyElement(DisplayedMnemonicIndexProperty, index)

/**
 * `AbstractButton` and `JLabel` each declare the mnemonic for themselves and under different names, so
 * the accessors are named separately.
 */
private val MnemonicProperty =
    MultiTargetProperty<Int>(
        "mnemonic",
        propertyCase<AbstractButton, Int>(
            read = { it.mnemonic },
            write = { component, value -> component.mnemonic = value },
        ),
        propertyCase<JLabel, Int>(
            read = { it.displayedMnemonic },
            write = { component, value -> component.displayedMnemonic = value },
        ),
    )

/** The same pair of targets as [MnemonicProperty], here under a name both of them share. */
private val DisplayedMnemonicIndexProperty =
    MultiTargetProperty<Int>(
        "displayedMnemonicIndex",
        propertyCase<AbstractButton, Int>(
            read = { it.displayedMnemonicIndex },
            write = { component, value -> component.displayedMnemonicIndex = value },
        ),
        propertyCase<JLabel, Int>(
            read = { it.displayedMnemonicIndex },
            write = { component, value -> component.displayedMnemonicIndex = value },
        ),
    )

@file:JvmMultifileClass
@file:JvmName("AccessibilityModifierKt")

package org.jetbrains.compose.swing.modifier.accessibility

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor.Companion.accessor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import java.awt.Component
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
 * @return this modifier with the mnemonic declared on it.
 * @see javax.swing.AbstractButton.setMnemonic
 * @see javax.swing.JLabel.setDisplayedMnemonic
 */
public fun SwingModifier.mnemonic(keyCode: Int): SwingModifier = property(MnemonicProperty, keyCode)

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
public fun SwingModifier.mnemonic(mnemonic: Char): SwingModifier =
    this.mnemonic(KeyEvent.getExtendedKeyCodeForChar(mnemonic.code))

/**
 * Sets which occurrence of the mnemonic letter in the text is underlined, as a zero-based index into
 * the text; `-1` underlines none of them. Use it when the letter appears more than once and the first
 * one is not the one to decorate - `displayedMnemonicIndex(5)` underlines the `A` of `Save As`.
 *
 * A widget derives the index again from its text and its mnemonic whenever either is written, and every
 * pass writes this declaration back over what it derived. Declare it after the [mnemonic], since a later
 * element in the modifier chain is applied last. Removing it leaves the index the widget derives for the text
 * and mnemonic that stand.
 *
 * Throws when [index] is below `-1` or beyond the end of the text, as Swing does - including where a
 * shortened text is what leaves the declared index past its end.
 *
 * @param index the zero-based index into the text of the character to underline, or `-1` to underline
 *   none of them.
 * @return this modifier with the underlined index declared on it.
 * @see javax.swing.AbstractButton.setDisplayedMnemonicIndex
 * @see javax.swing.JLabel.setDisplayedMnemonicIndex
 */
public fun SwingModifier.displayedMnemonicIndex(index: Int): SwingModifier =
    this then DisplayedMnemonicIndexElement(index)

/**
 * `AbstractButton` and `JLabel` each declare the mnemonic for themselves and under different names, so
 * the accessors are named separately.
 */
private val MnemonicProperty =
    ComponentPropertyDescriptor(
        "mnemonic",
        accessor<AbstractButton, Int>(
            read = { it.mnemonic },
            write = { component, value -> component.mnemonic = value },
        ),
        accessor<JLabel, Int>(
            read = { it.displayedMnemonic },
            write = { component, value -> component.displayedMnemonic = value },
        ),
    )

private class DisplayedMnemonicIndexNode : SwingModifier.ComponentNode<Component>() {
    var index: Int = -1

    fun apply(newIndex: Int) {
        index = newIndex
        write(newIndex)
    }

    override fun onAttach() {
        write(index)
    }

    override fun onDetach() {
        write(null)
    }

    private fun write(value: Int?) {
        when (val c = component) {
            is AbstractButton -> if (value == null) c.text = c.text else c.displayedMnemonicIndex = value
            is JLabel -> if (value == null) c.text = c.text else c.displayedMnemonicIndex = value
            else -> error("displayedMnemonicIndex applies to AbstractButton or JLabel, got: ${c.javaClass.name}")
        }
    }
}

/**
 * The element [displayedMnemonicIndex] declares. An index unchanged since the last pass is still due to
 * be written back over what the widget derived, so the element is equal only to itself.
 *
 * Both widgets announce the derived index, but a listener answering that announcement would write from
 * inside `setText`, against text the pass has already installed that the declared index may no longer
 * reach - including on the pass that shortens the text and withdraws the declaration together. Writing
 * on the pass instead puts the index in after the text it indexes into.
 */
private class DisplayedMnemonicIndexElement(
    private val index: Int,
) : SwingModifier.NodeElement<Component, DisplayedMnemonicIndexNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override val name: String get() = "displayedMnemonicIndex"

    override val key: Any get() = "displayedMnemonicIndex"

    override val restores: RestorePolicy get() = RestorePolicy.None

    override val declaredValues: Map<String, Any?> get() = mapOf(name to index)

    override fun create(): DisplayedMnemonicIndexNode = DisplayedMnemonicIndexNode()

    override fun update(node: DisplayedMnemonicIndexNode) {
        node.apply(index)
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

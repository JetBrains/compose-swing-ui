// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor.Companion.accessor
import org.jetbrains.compose.swing.modifier.PropertyRewrites
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.runReportingUncaught
import org.jetbrains.compose.swing.style.stylePropertyOf
import java.awt.Component
import java.awt.event.KeyEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.AbstractButton
import javax.swing.JLabel

/*
 * Mnemonics - the underlined letter that activates a component when pressed with Alt - Ctrl+Alt on macOS - and
 * which occurrence of that letter is underlined. On a button the mnemonic activates the button; on a label it moves
 * focus to the label's `labelFor` target.
 */

/**
 * Sets the label's keyboard mnemonic to the key identified by [keyCode], a `KeyEvent.VK_*` value.
 * `KeyEvent.VK_UNDEFINED` sets no mnemonic.
 *
 * This is the form to use for a key that types no character - a function key, an arrow - and the form a character
 * mnemonic resolves to.
 *
 * @param keyCode the `KeyEvent.VK_*` code of the key that moves focus to the label's target.
 * @see javax.swing.JLabel.setDisplayedMnemonic
 */
public fun LabelStyleScope.mnemonic(keyCode: Int): Unit = MnemonicProperty.provide(keyCode)

/**
 * Sets the label's keyboard mnemonic to the key that types [mnemonic], resolved with
 * `KeyEvent.getExtendedKeyCodeForChar` - so `'s'` and `'S'` both set the S key. A character that appears on no
 * known keyboard layout resolves to `KeyEvent.VK_UNDEFINED`, setting no mnemonic.
 *
 * @param mnemonic the character to use as the mnemonic.
 * @see javax.swing.JLabel.setDisplayedMnemonic
 */
public fun LabelStyleScope.mnemonic(mnemonic: Char): Unit =
    MnemonicProperty.provide(KeyEvent.getExtendedKeyCodeForChar(mnemonic.code))

/**
 * Sets the button's keyboard mnemonic to the key identified by [keyCode], a `KeyEvent.VK_*` value.
 * `KeyEvent.VK_UNDEFINED` sets no mnemonic.
 *
 * This is the form to use for a key that types no character - a function key, an arrow - and the form a character
 * mnemonic resolves to.
 *
 * @param keyCode the `KeyEvent.VK_*` code of the key that activates the button.
 * @see javax.swing.AbstractButton.setMnemonic
 */
public fun ButtonStyleScope.mnemonic(keyCode: Int): Unit = MnemonicProperty.provide(keyCode)

/**
 * Sets the button's keyboard mnemonic to the key that types [mnemonic], resolved with
 * `KeyEvent.getExtendedKeyCodeForChar` - so `'s'` and `'S'` both set the S key. A character that appears on no
 * known keyboard layout resolves to `KeyEvent.VK_UNDEFINED`, setting no mnemonic.
 *
 * @param mnemonic the character to use as the mnemonic.
 * @see javax.swing.AbstractButton.setMnemonic
 */
public fun ButtonStyleScope.mnemonic(mnemonic: Char): Unit =
    MnemonicProperty.provide(KeyEvent.getExtendedKeyCodeForChar(mnemonic.code))

/**
 * Sets which occurrence of the mnemonic letter in the label's text is underlined, as a zero-based index into the
 * text; `-1` underlines none of them. Use it when the letter appears more than once and the first one is not the
 * one to decorate - `displayedMnemonicIndex(5)` underlines the `A` of `Save As`.
 *
 * The index is applied after the [mnemonic], wherever it appears in the style. Once the style stops setting it,
 * the label keeps the index it derives for the text and mnemonic that stand.
 *
 * Swing refuses an index below `-1` or past the end of the text: setting one throws, and where a later text no
 * longer reaches it, the label keeps the index it derives.
 *
 * @param index the zero-based index into the text of the character to underline, or `-1` to underline none of them.
 * @see javax.swing.JLabel.setDisplayedMnemonicIndex
 */
public fun LabelStyleScope.displayedMnemonicIndex(index: Int): Unit = DisplayedMnemonicIndexProperty.provide(index)

/**
 * Sets which occurrence of the mnemonic letter in the button's text is underlined, as a zero-based index into the
 * text; `-1` underlines none of them. Use it when the letter appears more than once and the first one is not the
 * one to decorate - `displayedMnemonicIndex(5)` underlines the `A` of `Save As`.
 *
 * The index is applied after the [mnemonic], wherever it appears in the style. Once the style stops setting it,
 * the button keeps the index it derives for the text and mnemonic that stand.
 *
 * Swing refuses an index below `-1` or past the end of the text: setting one throws, and where a later text no
 * longer reaches it, the button keeps the index it derives.
 *
 * @param index the zero-based index into the text of the character to underline, or `-1` to underline none of them.
 * @see javax.swing.AbstractButton.setDisplayedMnemonicIndex
 */
public fun ButtonStyleScope.displayedMnemonicIndex(index: Int): Unit = DisplayedMnemonicIndexProperty.provide(index)

/**
 * `AbstractButton` and `JLabel` each declare the mnemonic for themselves and under different names, so the
 * accessors are named separately.
 */
internal val MnemonicDescriptor =
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

private val MnemonicProperty = stylePropertyOf(MnemonicDescriptor)

private val DisplayedMnemonicIndexDescriptor =
    ComponentPropertyDescriptor(
        "displayedMnemonicIndex",
        accessor<AbstractButton, Int>(
            read = { it.displayedMnemonicIndex },
            write = { component, value -> component.displayedMnemonicIndex = value },
        ),
        accessor<JLabel, Int>(
            read = { it.displayedMnemonicIndex },
            write = { component, value -> component.displayedMnemonicIndex = value },
        ),
    )

private val MnemonicTextDescriptor =
    ComponentPropertyDescriptor(
        "text",
        accessor<AbstractButton, String?>(
            read = { it.text },
            write = { component, value -> component.text = value },
        ),
        accessor<JLabel, String?>(
            read = { it.text },
            write = { component, value -> component.text = value },
        ),
    )

internal val DisplayedMnemonicIndexProperty = stylePropertyOf { index: Int -> DisplayedMnemonicIndexElement(index) }

internal class DisplayedMnemonicIndexNode :
    SwingModifier.ComponentNode<Component>(),
    PropertyChangeListener {
    var index: Int = -1
        private set

    private lateinit var rewrites: PropertyRewrites

    private var sequence = 0L

    fun apply(newIndex: Int) {
        sequence = rewrites.next()
        val unchanged = newIndex == index
        index = newIndex
        if (!unchanged || reachedByText()) write(newIndex)
    }

    override fun onAttach() {
        val state = checkNotNull(holder?.modifierState) { "A mnemonic index node belongs to a modifier" }
        rewrites = state.rewrites()
        // Refuses a component without a mnemonic text at attach, before any write.
        MnemonicTextDescriptor.read(component)
        component.addPropertyChangeListener("displayedMnemonicIndex", this)
        component.addPropertyChangeListener("text", this)
    }

    override fun onDetach() {
        component.removePropertyChangeListener("text", this)
        component.removePropertyChangeListener("displayedMnemonicIndex", this)
        write(null)
    }

    /**
     * A text or mnemonic write announces its own change before the widget derives the index again, so the node
     * listens on the index's own announcement, which comes after the derived index is in. It writes back only an
     * index the text reaches: this runs inside `setText`, where a shorter text may no longer reach it - including
     * in the pass that shortens the text and withdraws the index - and Swing throws for such an index. A rewrite of
     * an unchanged index applies the same check.
     *
     * A derived index equal to the one standing announces nothing, so a text that reaches the declared index again
     * after a shorter one derived the same index is announced too: the index is written ahead of the derivation,
     * which then changes it and brings the index's own announcement.
     */
    override fun propertyChange(event: PropertyChangeEvent) {
        // An earlier declaration must not answer a later declaration's write in the same cascade.
        if (!rewrites.mayReapply(sequence)) return
        if (event.propertyName == "text") {
            if (reachedByText()) write(index)
        } else if (event.newValue != index && reachedByText()) {
            write(index)
        }
    }

    private fun reachedByText(): Boolean = index < (MnemonicTextDescriptor.read(component)?.length ?: 0)

    private fun write(value: Int?) {
        rewrites.enter(sequence)
        try {
            if (value == null) {
                // The read refuses a widget without text, and that refusal stays the caller's; only the write's
                // listeners are reported.
                val text = MnemonicTextDescriptor.read(component)
                runReportingUncaught { MnemonicTextDescriptor.write(component, text) }
            } else {
                // Swing refuses an index its text does not carry; that refusal belongs to the caller, while a
                // listener's throw from the write is reported.
                require(value in -1 until (MnemonicTextDescriptor.read(component)?.length ?: 0)) {
                    "displayedMnemonicIndex $value lies outside the text"
                }
                runReportingUncaught { DisplayedMnemonicIndexDescriptor.write(component, value) }
            }
        } finally {
            rewrites.exit()
        }
    }
}

/**
 * The element a displayed mnemonic index is written through. Its node writes the index back over each one the
 * widget derives. A pass that rewrites an unchanged index the text no longer reaches skips the write, which
 * would throw, and the widget keeps the index it derives.
 */
internal class DisplayedMnemonicIndexElement(
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

    override fun equals(other: Any?): Boolean = other is DisplayedMnemonicIndexElement && other.index == index

    override fun hashCode(): Int = index
}

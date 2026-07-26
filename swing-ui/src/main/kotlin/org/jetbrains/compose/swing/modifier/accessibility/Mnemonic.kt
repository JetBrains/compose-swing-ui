@file:JvmMultifileClass
@file:JvmName("AccessibilityModifiersKt")

package org.jetbrains.compose.swing.modifier.accessibility

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * Sets the keyboard mnemonic - the underlined letter that activates the component when pressed with the
 * platform menu modifier (typically Alt). On a button it activates the button; on a label it moves
 * focus to the label's [labelFor] target. Requires an `AbstractButton` (Button, CheckBox, RadioButton,
 * menu item, ...) or a `JLabel` target.
 *
 * @param mnemonic the character to use as the mnemonic.
 */
public fun SwingModifier.mnemonic(mnemonic: Char): SwingModifier = this then MnemonicElement(mnemonic)

private class MnemonicElement(
    private val mnemonic: Char,
) : SwingModifier.Element<JComponent, MnemonicElement.Node> {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.mnemonic = mnemonic
        node.apply()
    }

    class Node : SwingModifier.Node<JComponent>() {
        var mnemonic: Char = ' '
        private var original: Int = 0

        override fun onAttach() {
            original =
                when (val component = component) {
                    is AbstractButton -> component.mnemonic
                    is JLabel -> component.displayedMnemonic
                    else -> 0
                }
        }

        fun apply() {
            when (val component = component) {
                is AbstractButton -> {
                    component.setMnemonic(mnemonic)
                }

                is JLabel -> {
                    component.setDisplayedMnemonic(mnemonic)
                }

                else -> {
                    error(
                        "mnemonic requires an AbstractButton or JLabel target, " +
                            "but the component is a ${component.javaClass.name}",
                    )
                }
            }
        }

        override fun onDetach() {
            when (val component = component) {
                is AbstractButton -> component.mnemonic = original
                is JLabel -> component.displayedMnemonic = original
                else -> Unit
            }
        }
    }
}

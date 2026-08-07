@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.event.ActionListener
import javax.swing.JTextField

/**
 * Runs [onAccept] when this text field accepts its value - the field's own action event, the one its
 * Enter binding fires - so a search box, a command entry or a login form acts on the keyboard.
 *
 * Requires a `JTextField` target, which covers `TextField`, `PasswordField` and `FormattedTextField`
 * whichever overload of them is used. It rides the field's own Enter binding rather than replacing it,
 * so a formatted field commits its edit first and runs nothing at all when that edit does not parse;
 * binding the Enter key with `onKeyStroke` instead takes that behavior over.
 *
 * Multiple applications all fire, and [onAccept] is read live, so passing a fresh lambda each
 * recomposition is fine.
 *
 * @see javax.swing.JTextField.addActionListener
 */
public fun SwingModifier.onAccept(onAccept: () -> Unit): SwingModifier = this then AcceptElement(onAccept)

private class AcceptElement(
    private val onAccept: () -> Unit,
) : SwingModifier.NodeElement<JTextField, AcceptElement.Node>() {
    override val targetType: Class<JTextField> get() = JTextField::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onAccept = onAccept
    }

    override fun equals(other: Any?): Boolean = other is AcceptElement && onAccept === other.onAccept

    override fun hashCode(): Int = System.identityHashCode(onAccept)

    class Node : SwingModifier.Node<JTextField>() {
        var onAccept: () -> Unit = {}

        private val listener = ActionListener { onAccept() }

        override fun onAttach(): Unit = component.addActionListener(listener)

        override fun onDetach(): Unit = component.removeActionListener(listener)
    }
}

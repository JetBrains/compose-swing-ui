@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
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
 * @param onAccept takes no argument: the accepted value is the one the composition already declared.
 * @return this chain with the accept handler declared on it.
 * @see javax.swing.JTextField.addActionListener
 */
public fun SwingModifier.onAccept(onAccept: () -> Unit): SwingModifier = listener(onAccept, ACCEPTS)

private val ACCEPT =
    ListenerRegistration<JTextField, ActionListener>(
        { field, listener -> field.addActionListener(listener) },
        { field, listener -> field.removeActionListener(listener) },
    )

private val ACCEPTS =
    CallbackRegistration<JTextField, () -> Unit, ActionListener>(
        adapter = { current -> ActionListener { current()() } },
        registration = ACCEPT,
    )

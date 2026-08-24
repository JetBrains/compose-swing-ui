@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

/**
 * Runs [onKeyEvent] for every key event on the component - a press, a release and the character
 * they type alike, so one keystroke reports more than once. Declare the events one by one to tell
 * them apart.
 *
 * [onKeyEvent] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see java.awt.Component.addKeyListener
 */
public fun SwingModifier.keyListener(onKeyEvent: (KeyEvent) -> Unit): SwingModifier =
    keyListener(
        onKeyTyped = onKeyEvent,
        onKeyPressed = onKeyEvent,
        onKeyReleased = onKeyEvent,
    )

/**
 * Runs each lambda on the key event it is declared for. An event left undeclared reports
 * nowhere.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition
 * registers nothing again.
 *
 * Declaring none at all is refused.
 *
 * @see java.awt.Component.addKeyListener
 */
public fun SwingModifier.keyListener(
    onKeyTyped: (KeyEvent) -> Unit = UNDECLARED,
    onKeyPressed: (KeyEvent) -> Unit = UNDECLARED,
    onKeyReleased: (KeyEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("keyListener", declared(onKeyTyped) || declared(onKeyPressed) || declared(onKeyReleased))
    return listener<Component, KeyCallbacks, KeyListener>(
        callback = KeyCallbacks(onKeyTyped, onKeyPressed, onKeyReleased),
        adapter = { current ->
            object : KeyListener {
                override fun keyTyped(event: KeyEvent): Unit = current().onKeyTyped(event)

                override fun keyPressed(event: KeyEvent): Unit = current().onKeyPressed(event)

                override fun keyReleased(event: KeyEvent): Unit = current().onKeyReleased(event)
            }
        },
        attach = { component, listener -> component.addKeyListener(listener) },
        detach = { component, listener -> component.removeKeyListener(listener) },
    )
}

/**
 * Attaches a [KeyListener] (`addKeyListener`/`removeKeyListener`).
 *
 * @see java.awt.Component.addKeyListener
 */
public fun SwingModifier.keyListener(listener: KeyListener): SwingModifier =
    listener<Component, KeyListener>(
        listener,
        Component::addKeyListener,
        Component::removeKeyListener,
    )

/** The lambdas [keyListener] was declared with, as one value the built listener reads. */
private class KeyCallbacks(
    val onKeyTyped: (KeyEvent) -> Unit,
    val onKeyPressed: (KeyEvent) -> Unit,
    val onKeyReleased: (KeyEvent) -> Unit,
)

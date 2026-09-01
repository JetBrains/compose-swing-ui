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
 * @param onKeyEvent receives the event, which reports only while the component holds the keyboard
 *   focus.
 * @return this chain with the key callback declared on it.
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
 * @param onKeyTyped runs for the character a keystroke produces, carrying a `keyChar` and
 *   `VK_UNDEFINED` for its `keyCode`; a key that produces no Unicode character, such as an arrow,
 *   never reaches it.
 * @param onKeyPressed runs when the key goes down; whether holding it repeats, and in what order the
 *   events then arrive, the platform decides.
 * @param onKeyReleased runs when the key comes back up.
 * @return this chain with the key callbacks declared on it.
 * @see java.awt.Component.addKeyListener
 */
public fun SwingModifier.keyListener(
    onKeyTyped: (KeyEvent) -> Unit = UNDECLARED,
    onKeyPressed: (KeyEvent) -> Unit = UNDECLARED,
    onKeyReleased: (KeyEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("keyListener", declared(onKeyTyped) || declared(onKeyPressed) || declared(onKeyReleased))
    return listener(KeyCallbacks(onKeyTyped, onKeyPressed, onKeyReleased), KEY_CALLBACKS)
}

/**
 * Attaches a [KeyListener] (`addKeyListener`/`removeKeyListener`).
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the key listener declared on it.
 * @see java.awt.Component.addKeyListener
 */
public fun SwingModifier.keyListener(listener: KeyListener): SwingModifier = listener(listener, KEY)

private class KeyCallbacks(
    val onKeyTyped: (KeyEvent) -> Unit,
    val onKeyPressed: (KeyEvent) -> Unit,
    val onKeyReleased: (KeyEvent) -> Unit,
)

/** The registration a key listener sits on, shared by every builder over a component's keys. */
internal val KEY =
    ListenerRegistration<Component, KeyListener>(
        Component::addKeyListener,
        Component::removeKeyListener,
    )

private val KEY_CALLBACKS =
    CallbackRegistration<Component, KeyCallbacks, KeyListener>(
        adapter = { current ->
            object : KeyListener {
                override fun keyTyped(event: KeyEvent): Unit = current().onKeyTyped(event)

                override fun keyPressed(event: KeyEvent): Unit = current().onKeyPressed(event)

                override fun keyReleased(event: KeyEvent): Unit = current().onKeyReleased(event)
            }
        },
        registration = KEY,
    )

@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

/**
 * Runs [onFocusChange] whenever the component takes or loses the keyboard focus. Read
 * `isFocusOwner` to tell which, or declare the two directions separately.
 *
 * [onFocusChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @param onFocusChange receives the event, whose `oppositeComponent` is the other side of the
 *   transfer and whose `isTemporary` marks a loss the component gets back, such as its window being
 *   deactivated.
 * @return this chain with the focus callback declared on it.
 * @see java.awt.Component.addFocusListener
 */
public fun SwingModifier.focusListener(onFocusChange: (FocusEvent) -> Unit): SwingModifier =
    focusListener(
        onFocusGained = onFocusChange,
        onFocusLost = onFocusChange,
    )

/**
 * Runs [onFocusGained] when the component takes the keyboard focus and [onFocusLost] when it loses
 * it. A direction left undeclared reports nowhere.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition
 * registers nothing again.
 *
 * Declaring none at all is refused.
 *
 * @param onFocusGained runs once the component is the focus owner, so `isFocusOwner` already answers
 *   `true`.
 * @param onFocusLost runs once the focus has gone; a temporary loss, such as a window deactivating,
 *   reports here too.
 * @return this chain with the focus callbacks declared on it.
 * @see java.awt.Component.addFocusListener
 */
public fun SwingModifier.focusListener(
    onFocusGained: (FocusEvent) -> Unit = UNDECLARED,
    onFocusLost: (FocusEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("focusListener", declared(onFocusGained) || declared(onFocusLost))
    return listener(FocusCallbacks(onFocusGained, onFocusLost), FOCUS_CALLBACKS)
}

/**
 * Attaches a [FocusListener] (`addFocusListener`/`removeFocusListener`).
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the focus listener declared on it.
 * @see java.awt.Component.addFocusListener
 */
public fun SwingModifier.focusListener(listener: FocusListener): SwingModifier = listener(listener, FOCUS)

/** The lambdas [focusListener] was declared with, as one value the built listener reads. */
private class FocusCallbacks(
    val onFocusGained: (FocusEvent) -> Unit,
    val onFocusLost: (FocusEvent) -> Unit,
)

private val FOCUS =
    ListenerRegistration<Component, FocusListener>(
        Component::addFocusListener,
        Component::removeFocusListener,
    )

private val FOCUS_CALLBACKS =
    CallbackRegistration<Component, FocusCallbacks, FocusListener>(
        adapter = { current ->
            object : FocusListener {
                override fun focusGained(event: FocusEvent): Unit = current().onFocusGained(event)

                override fun focusLost(event: FocusEvent): Unit = current().onFocusLost(event)
            }
        },
        registration = FOCUS,
    )

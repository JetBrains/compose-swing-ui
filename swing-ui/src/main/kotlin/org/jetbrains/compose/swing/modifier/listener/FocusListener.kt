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
 * @see java.awt.Component.addFocusListener
 */
public fun SwingModifier.focusListener(
    onFocusGained: (FocusEvent) -> Unit = UNDECLARED,
    onFocusLost: (FocusEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("focusListener", declared(onFocusGained) || declared(onFocusLost))
    return liveCallbackListener<Component, FocusCallbacks, FocusListener>(
        callback = FocusCallbacks(onFocusGained, onFocusLost),
        adapter = { current ->
            object : FocusListener {
                override fun focusGained(event: FocusEvent): Unit = current().onFocusGained(event)

                override fun focusLost(event: FocusEvent): Unit = current().onFocusLost(event)
            }
        },
        attach = { component, listener -> component.addFocusListener(listener) },
        detach = { component, listener -> component.removeFocusListener(listener) },
    )
}

/**
 * Attaches a [FocusListener] (`addFocusListener`/`removeFocusListener`).
 *
 * @see java.awt.Component.addFocusListener
 */
public fun SwingModifier.focusListener(listener: FocusListener): SwingModifier =
    listener<Component, FocusListener>(
        listener,
        Component::addFocusListener,
        Component::removeFocusListener,
    )

/** The lambdas [focusListener] was declared with, as one value the built listener reads. */
private class FocusCallbacks(
    val onFocusGained: (FocusEvent) -> Unit,
    val onFocusLost: (FocusEvent) -> Unit,
)

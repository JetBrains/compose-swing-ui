@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener

/**
 * Runs [onMouseWheel] when the wheel turns over the component.
 *
 * [onMouseWheel] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see java.awt.Component.addMouseWheelListener
 */
public fun SwingModifier.mouseWheelListener(onMouseWheel: (MouseWheelEvent) -> Unit): SwingModifier =
    listener(onMouseWheel, MOUSE_WHEEL_CALLBACKS)

/**
 * Attaches a [MouseWheelListener] (`addMouseWheelListener`/`removeMouseWheelListener`).
 *
 * @see java.awt.Component.addMouseWheelListener
 */
public fun SwingModifier.mouseWheelListener(listener: MouseWheelListener): SwingModifier =
    listener(listener, MOUSE_WHEEL)

private val MOUSE_WHEEL =
    ListenerRegistration<Component, MouseWheelListener>(
        Component::addMouseWheelListener,
        Component::removeMouseWheelListener,
    )

private val MOUSE_WHEEL_CALLBACKS =
    CallbackRegistration<Component, (MouseWheelEvent) -> Unit, MouseWheelListener>(
        adapter = { current -> MouseWheelListener { event -> current()(event) } },
        registration = MOUSE_WHEEL,
    )

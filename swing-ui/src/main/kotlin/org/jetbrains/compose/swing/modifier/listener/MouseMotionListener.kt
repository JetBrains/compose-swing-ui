@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener

/**
 * Runs [onMouseMove] whenever the pointer moves over the component, whether or not a button is
 * held.
 *
 * [onMouseMove] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @param onMouseMove receives the event, whose `x` and `y` are in the component's own coordinates and
 *   can fall outside it while a drag that began here continues.
 * @return this chain with the mouse motion callback declared on it.
 * @see java.awt.Component.addMouseMotionListener
 */
public fun SwingModifier.mouseMotionListener(onMouseMove: (MouseEvent) -> Unit): SwingModifier =
    mouseMotionListener(
        onMouseDragged = onMouseMove,
        onMouseMoved = onMouseMove,
    )

/**
 * Runs [onMouseDragged] while the pointer moves with a button held, and [onMouseMoved] while it
 * moves with none. A movement left undeclared reports nowhere.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition
 * registers nothing again.
 *
 * Declaring none at all is refused.
 *
 * @param onMouseDragged keeps reporting after the pointer leaves the component, until the button is
 *   released.
 * @param onMouseMoved stops as soon as the pointer leaves.
 * @return this chain with the mouse motion callbacks declared on it.
 * @see java.awt.Component.addMouseMotionListener
 */
public fun SwingModifier.mouseMotionListener(
    onMouseDragged: (MouseEvent) -> Unit = UNDECLARED,
    onMouseMoved: (MouseEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("mouseMotionListener", declared(onMouseDragged) || declared(onMouseMoved))
    return listener(MouseMotionCallbacks(onMouseDragged, onMouseMoved), MOUSE_MOTION_CALLBACKS)
}

/**
 * Attaches a [MouseMotionListener] (`addMouseMotionListener`/`removeMouseMotionListener`).
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the mouse motion listener declared on it.
 * @see java.awt.Component.addMouseMotionListener
 */
public fun SwingModifier.mouseMotionListener(listener: MouseMotionListener): SwingModifier =
    listener(listener, MOUSE_MOTION)

private class MouseMotionCallbacks(
    val onMouseDragged: (MouseEvent) -> Unit,
    val onMouseMoved: (MouseEvent) -> Unit,
)

private val MOUSE_MOTION =
    ListenerRegistration<Component, MouseMotionListener>(
        Component::addMouseMotionListener,
        Component::removeMouseMotionListener,
    )

private val MOUSE_MOTION_CALLBACKS =
    CallbackRegistration<Component, MouseMotionCallbacks, MouseMotionListener>(
        adapter = { current ->
            object : MouseMotionListener {
                override fun mouseDragged(event: MouseEvent): Unit = current().onMouseDragged(event)

                override fun mouseMoved(event: MouseEvent): Unit = current().onMouseMoved(event)
            }
        },
        registration = MOUSE_MOTION,
    )

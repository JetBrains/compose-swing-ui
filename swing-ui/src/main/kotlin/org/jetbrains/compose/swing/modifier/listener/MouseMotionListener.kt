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
 * @see java.awt.Component.addMouseMotionListener
 */
public fun SwingModifier.mouseMotionListener(
    onMouseDragged: (MouseEvent) -> Unit = UNDECLARED,
    onMouseMoved: (MouseEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("mouseMotionListener", declared(onMouseDragged) || declared(onMouseMoved))
    return listener<Component, MouseMotionCallbacks, MouseMotionListener>(
        callback = MouseMotionCallbacks(onMouseDragged, onMouseMoved),
        adapter = { current ->
            object : MouseMotionListener {
                override fun mouseDragged(event: MouseEvent): Unit = current().onMouseDragged(event)

                override fun mouseMoved(event: MouseEvent): Unit = current().onMouseMoved(event)
            }
        },
        attach = { component, listener -> component.addMouseMotionListener(listener) },
        detach = { component, listener -> component.removeMouseMotionListener(listener) },
    )
}

/**
 * Attaches a [MouseMotionListener] (`addMouseMotionListener`/`removeMouseMotionListener`).
 *
 * @see java.awt.Component.addMouseMotionListener
 */
public fun SwingModifier.mouseMotionListener(listener: MouseMotionListener): SwingModifier =
    listener<Component, MouseMotionListener>(
        listener,
        Component::addMouseMotionListener,
        Component::removeMouseMotionListener,
    )

/** The lambdas [mouseMotionListener] was declared with, as one value the built listener reads. */
private class MouseMotionCallbacks(
    val onMouseDragged: (MouseEvent) -> Unit,
    val onMouseMoved: (MouseEvent) -> Unit,
)

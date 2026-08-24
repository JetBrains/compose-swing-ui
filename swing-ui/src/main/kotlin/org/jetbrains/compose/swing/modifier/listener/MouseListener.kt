@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.event.MouseListener

/**
 * Runs [onMouseEvent] for every mouse event on the component - a click, a press, a release, the
 * pointer entering and the pointer leaving alike, so one interaction reports more than once.
 * Declare the events one by one to tell them apart.
 *
 * [onMouseEvent] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see java.awt.Component.addMouseListener
 */
public fun SwingModifier.mouseListener(onMouseEvent: (MouseEvent) -> Unit): SwingModifier =
    mouseListener(
        onMouseClicked = onMouseEvent,
        onMousePressed = onMouseEvent,
        onMouseReleased = onMouseEvent,
        onMouseEntered = onMouseEvent,
        onMouseExited = onMouseEvent,
    )

/**
 * Runs each lambda on the mouse event it is declared for. An event left undeclared reports
 * nowhere.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition
 * registers nothing again.
 *
 * Declaring none at all is refused.
 *
 * @see java.awt.Component.addMouseListener
 */
public fun SwingModifier.mouseListener(
    onMouseClicked: (MouseEvent) -> Unit = UNDECLARED,
    onMousePressed: (MouseEvent) -> Unit = UNDECLARED,
    onMouseReleased: (MouseEvent) -> Unit = UNDECLARED,
    onMouseEntered: (MouseEvent) -> Unit = UNDECLARED,
    onMouseExited: (MouseEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared(
        "mouseListener",
        declared(onMouseClicked) || declared(onMousePressed) || declared(onMouseReleased) || declared(onMouseEntered) ||
            declared(onMouseExited),
    )
    return listener(
        MouseCallbacks(onMouseClicked, onMousePressed, onMouseReleased, onMouseEntered, onMouseExited),
        MOUSE_CALLBACKS,
    )
}

/**
 * Attaches a [MouseListener] (`addMouseListener`/`removeMouseListener`).
 *
 * @see java.awt.Component.addMouseListener
 */
public fun SwingModifier.mouseListener(listener: MouseListener): SwingModifier = listener(listener, MOUSE)

private class MouseCallbacks(
    val onMouseClicked: (MouseEvent) -> Unit,
    val onMousePressed: (MouseEvent) -> Unit,
    val onMouseReleased: (MouseEvent) -> Unit,
    val onMouseEntered: (MouseEvent) -> Unit,
    val onMouseExited: (MouseEvent) -> Unit,
)

private val MOUSE =
    ListenerRegistration<Component, MouseListener>(
        Component::addMouseListener,
        Component::removeMouseListener,
    )

private val MOUSE_CALLBACKS =
    CallbackRegistration<Component, MouseCallbacks, MouseListener>(
        adapter = { current ->
            object : MouseListener {
                override fun mouseClicked(event: MouseEvent): Unit = current().onMouseClicked(event)

                override fun mousePressed(event: MouseEvent): Unit = current().onMousePressed(event)

                override fun mouseReleased(event: MouseEvent): Unit = current().onMouseReleased(event)

                override fun mouseEntered(event: MouseEvent): Unit = current().onMouseEntered(event)

                override fun mouseExited(event: MouseEvent): Unit = current().onMouseExited(event)
            }
        },
        registration = MOUSE,
    )

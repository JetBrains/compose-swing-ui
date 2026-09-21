// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.UNDECLARED
import org.jetbrains.compose.swing.modifier.listener.UNDECLARED_ACTION
import org.jetbrains.compose.swing.modifier.listener.declared
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.requireAnyDeclared
import java.awt.Component
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener

/**
 * Installs mouse enter/exit handlers.
 *
 * A callback left undeclared reports nowhere, and declaring neither is refused.
 *
 * @param onEnter runs when the pointer arrives over the component, a return from a child of its own that
 *   listens for mouse events included.
 * @param onExit runs when the pointer leaves it, a move onto such a child included; a child listening for no
 *   mouse event of its own never takes the pointer, so passing over it reports neither.
 * @return this chain with the hover handlers declared on it.
 * @see java.awt.Component.addMouseListener
 */
public fun SwingModifier.onHover(
    onEnter: () -> Unit = UNDECLARED_ACTION,
    onExit: () -> Unit = UNDECLARED_ACTION,
): SwingModifier {
    requireAnyDeclared("onHover", declared(onEnter) || declared(onExit))
    return listener(HoverActions(onEnter, onExit), HOVER_ACTIONS)
}

/**
 * Installs focus gained/lost handlers.
 *
 * A callback left undeclared reports nowhere, and declaring neither is refused.
 *
 * @param onGained runs when the component becomes the focus owner, its window regaining activation included.
 * @param onLost runs when it stops being the focus owner, the temporary loss while another window is active
 *   included.
 * @return this chain with the focus handlers declared on it.
 * @see java.awt.Component.addFocusListener
 */
public fun SwingModifier.onFocus(
    onGained: () -> Unit = UNDECLARED_ACTION,
    onLost: () -> Unit = UNDECLARED_ACTION,
): SwingModifier {
    requireAnyDeclared("onFocus", declared(onGained) || declared(onLost))
    return listener(FocusActions(onGained, onLost), FOCUS_ACTIONS)
}

/**
 * Installs mouse press/release/click handlers. [onPress] fires on `MOUSE_PRESSED`, [onRelease] on
 * `MOUSE_RELEASED`, and [onClick] on a completed click; each receives the [MouseEvent] (button, click
 * count, point, modifiers). This is the low-level complement to a widget's domain `onClick`: use it
 * for arbitrary components (a Label, a Panel) or for right/middle-button handling.
 *
 * Multiple `onPointerEvent` applications all fire. Callbacks are read live, so passing fresh lambdas
 * each recomposition is fine.
 *
 * A callback left undeclared reports nowhere, and declaring none is refused.
 *
 * @param onPress the platform decides whether a popup gesture is reported on the press or on the release, so
 *   check `isPopupTrigger` in both.
 * @param onRelease still runs when the pointer has been dragged off the component, since the press holds the
 *   grab.
 * @param onClick skipped when the pointer is dragged between press and release - on Windows and macOS for
 *   any movement at all, on Linux only once the drag leaves a few pixels around the press point.
 * @return this chain with the pointer handlers declared on it.
 * @see java.awt.Component.addMouseListener
 */
public fun SwingModifier.onPointerEvent(
    onPress: ((MouseEvent) -> Unit)? = null,
    onRelease: ((MouseEvent) -> Unit)? = null,
    onClick: ((MouseEvent) -> Unit)? = null,
): SwingModifier {
    requireAnyDeclared("onPointerEvent", onPress != null || onRelease != null || onClick != null)
    return mouseListener(
        onMouseClicked = onClick ?: UNDECLARED,
        onMousePressed = onPress ?: UNDECLARED,
        onMouseReleased = onRelease ?: UNDECLARED,
    )
}

/** The actions [onHover] was declared with, as one value the built listener reads. */
private class HoverActions(
    val onEnter: () -> Unit,
    val onExit: () -> Unit,
)

/** The actions [onFocus] was declared with, as one value the built listener reads. */
private class FocusActions(
    val onGained: () -> Unit,
    val onLost: () -> Unit,
)

private val HOVER_ACTIONS =
    CallbackRegistration.byCallbackIdentity<Component, HoverActions, MouseListener>(
        adapter = { callback ->
            object : MouseAdapter() {
                override fun mouseEntered(event: MouseEvent): Unit = callback.current.onEnter()

                override fun mouseExited(event: MouseEvent): Unit = callback.current.onExit()
            }
        },
        registration =
            ListenerRegistration<Component, MouseListener>(
                name = "onHover",
                Component::addMouseListener,
                Component::removeMouseListener,
            ),
        HoverActions::onEnter,
        HoverActions::onExit,
    )

private val FOCUS_ACTIONS =
    CallbackRegistration.byCallbackIdentity<Component, FocusActions, FocusListener>(
        adapter = { callback ->
            object : FocusAdapter() {
                override fun focusGained(event: FocusEvent): Unit = callback.current.onGained()

                override fun focusLost(event: FocusEvent): Unit = callback.current.onLost()
            }
        },
        registration =
            ListenerRegistration<Component, FocusListener>(
                name = "onFocus",
                Component::addFocusListener,
                Component::removeFocusListener,
            ),
        FocusActions::onGained,
        FocusActions::onLost,
    )

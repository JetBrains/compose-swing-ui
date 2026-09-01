@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.UNDECLARED_ACTION
import org.jetbrains.compose.swing.modifier.listener.declared
import org.jetbrains.compose.swing.modifier.listener.focusListener
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.requireAnyDeclared
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.event.MouseEvent

/**
 * Sets `isFocusable`, declaring whether this component can receive keyboard focus.
 *
 * @param focusable `false` takes the component out of the Tab cycle as well as refusing it a click's focus.
 * @return this chain with the focusability declared on it.
 * @see java.awt.Component.setFocusable
 */
public fun SwingModifier.focusable(focusable: Boolean): SwingModifier =
    this then
        propertyElement<Component, Boolean>(
            focusable,
            read = { it.isFocusable },
            write = { component, value -> component.isFocusable = value },
        )

/**
 * Sets `isEnabled` on **this component only** - whether it responds to user input and paints in its
 * enabled state. Disabling a container does not disable the components inside it, so disable each child
 * you want disabled.
 *
 * @param enabled `false` keeps the component out of the Tab cycle and stops its key bindings firing. A
 *   lightweight component still receives mouse events while disabled, so the listeners declared on it go
 *   on reporting; it is the widget's own listeners that read the enabled state before acting on one.
 * @return this chain with the enabled state declared on it.
 * @see java.awt.Component.setEnabled
 */
public fun SwingModifier.enabled(enabled: Boolean): SwingModifier =
    this then
        propertyElement<Component, Boolean>(
            enabled,
            read = { it.isEnabled },
            write = { component, value -> component.isEnabled = value },
        )

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
    return mouseListener(onMouseEntered = { onEnter() }, onMouseExited = { onExit() })
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
    return focusListener(onFocusGained = { onGained() }, onFocusLost = { onLost() })
}

/**
 * Installs mouse press/release/click handlers. [onPress] fires on `MOUSE_PRESSED`, [onRelease] on
 * `MOUSE_RELEASED`, and [onClick] on a completed click; each receives the [MouseEvent] (button, click
 * count, point, modifiers). This is the low-level complement to a widget's domain `onClick`: use it
 * for arbitrary components (a Label, a FlowPanel) or for right/middle-button handling.
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
        onMouseClicked = { event -> onClick?.invoke(event) },
        onMousePressed = { event -> onPress?.invoke(event) },
        onMouseReleased = { event -> onRelease?.invoke(event) },
    )
}

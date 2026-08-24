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

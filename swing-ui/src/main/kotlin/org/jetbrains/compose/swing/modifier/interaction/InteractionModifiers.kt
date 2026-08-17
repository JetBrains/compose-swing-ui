@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/*
 * The listener elements (onHover/onFocus/onPointerEvent) install one Swing listener for the node's life;
 * its body reads node fields that `update` refreshes, so passing fresh lambdas each recomposition needs
 * no reattach. The listener is removed when the element leaves the chain or the node is released/reused.
 *
 * Two such elements are equal only when they hold the *same* callbacks by identity, since a lambda is
 * what it captures. Hoisted callbacks therefore compare equal and skip the refresh; fresh lambdas compare
 * unequal and refresh the node's fields on every pass.
 */

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
 * @see java.awt.Component.addMouseListener
 */
public fun SwingModifier.onHover(
    onEnter: () -> Unit = {},
    onExit: () -> Unit = {},
): SwingModifier = this then HoverElement(onEnter, onExit)

/**
 * Installs focus gained/lost handlers.
 *
 * @see java.awt.Component.addFocusListener
 */
public fun SwingModifier.onFocus(
    onGained: () -> Unit = {},
    onLost: () -> Unit = {},
): SwingModifier = this then FocusElement(onGained, onLost)

/**
 * Installs mouse press/release/click handlers. [onPress] fires on `MOUSE_PRESSED`, [onRelease] on
 * `MOUSE_RELEASED`, and [onClick] on a completed click; each receives the [MouseEvent] (button, click
 * count, point, modifiers). This is the low-level complement to a widget's domain `onClick`: use it
 * for arbitrary components (a Label, a FlowPanel) or for right/middle-button handling.
 *
 * Multiple `onPointerEvent` applications all fire. Callbacks are read live, so passing fresh lambdas
 * each recomposition is fine.
 *
 * @see java.awt.Component.addMouseListener
 */
public fun SwingModifier.onPointerEvent(
    onPress: ((MouseEvent) -> Unit)? = null,
    onRelease: ((MouseEvent) -> Unit)? = null,
    onClick: ((MouseEvent) -> Unit)? = null,
): SwingModifier = this then PointerEventElement(onPress, onRelease, onClick)

private class HoverElement(
    private val onEnter: () -> Unit,
    private val onExit: () -> Unit,
) : SwingModifier.NodeElement<Component, HoverElement.Node>() {
    override val targetType: Class<Component> get() = Component::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onEnter = onEnter
        node.onExit = onExit
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HoverElement) return false
        if (onEnter !== other.onEnter) return false
        return onExit === other.onExit
    }

    override fun hashCode(): Int = 31 * System.identityHashCode(onEnter) + System.identityHashCode(onExit)

    class Node : SwingModifier.Node<Component>() {
        var onEnter: () -> Unit = {}
        var onExit: () -> Unit = {}

        private val listener =
            object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?): Unit = onEnter()

                override fun mouseExited(e: MouseEvent?): Unit = onExit()
            }

        override fun onAttach(): Unit = component.addMouseListener(listener)

        override fun onDetach(): Unit = component.removeMouseListener(listener)
    }
}

private class FocusElement(
    private val onGained: () -> Unit,
    private val onLost: () -> Unit,
) : SwingModifier.NodeElement<Component, FocusElement.Node>() {
    override val targetType: Class<Component> get() = Component::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onGained = onGained
        node.onLost = onLost
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FocusElement) return false
        if (onGained !== other.onGained) return false
        return onLost === other.onLost
    }

    override fun hashCode(): Int = 31 * System.identityHashCode(onGained) + System.identityHashCode(onLost)

    class Node : SwingModifier.Node<Component>() {
        var onGained: () -> Unit = {}
        var onLost: () -> Unit = {}

        private val listener =
            object : FocusListener {
                override fun focusGained(e: FocusEvent?): Unit = onGained()

                override fun focusLost(e: FocusEvent?): Unit = onLost()
            }

        override fun onAttach(): Unit = component.addFocusListener(listener)

        override fun onDetach(): Unit = component.removeFocusListener(listener)
    }
}

private class PointerEventElement(
    private val onPress: ((MouseEvent) -> Unit)?,
    private val onRelease: ((MouseEvent) -> Unit)?,
    private val onClick: ((MouseEvent) -> Unit)?,
) : SwingModifier.NodeElement<Component, PointerEventElement.Node>() {
    override val targetType: Class<Component> get() = Component::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onPress = onPress
        node.onRelease = onRelease
        node.onClick = onClick
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointerEventElement) return false
        if (onPress !== other.onPress) return false
        if (onRelease !== other.onRelease) return false
        return onClick === other.onClick
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(onPress)
        result = 31 * result + System.identityHashCode(onRelease)
        result = 31 * result + System.identityHashCode(onClick)
        return result
    }

    class Node : SwingModifier.Node<Component>() {
        var onPress: ((MouseEvent) -> Unit)? = null
        var onRelease: ((MouseEvent) -> Unit)? = null
        var onClick: ((MouseEvent) -> Unit)? = null

        private val listener =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    onPress?.invoke(e)
                }

                override fun mouseReleased(e: MouseEvent) {
                    onRelease?.invoke(e)
                }

                override fun mouseClicked(e: MouseEvent) {
                    onClick?.invoke(e)
                }
            }

        override fun onAttach(): Unit = component.addMouseListener(listener)

        override fun onDetach(): Unit = component.removeMouseListener(listener)
    }
}

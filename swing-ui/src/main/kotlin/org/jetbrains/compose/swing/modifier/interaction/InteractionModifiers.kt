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
 * Interaction SwingModifiers - focusability and input listeners.
 *
 * The listener elements (onHover/onFocus/onPointerEvent) install one Swing listener for the node's
 * life whose body reads node fields refreshed by `update`, so passing fresh lambdas each recomposition
 * is fine - no reattach. They are removed when the element leaves the chain or the node is
 * released/reused.
 */

/**
 * Sets `isFocusable`, declaring whether this component can receive keyboard focus.
 *
 * Swing's traversal goes by the declaration rather than by what the component's type is focusable for:
 * a caption marked focusable takes its place in the Tab order, and a control marked non-focusable is
 * skipped.
 *
 * @see java.awt.Component.setFocusable
 */
public fun SwingModifier.focusable(focusable: Boolean): SwingModifier =
    this then
        propertyElement<Component, Boolean>(
            focusable,
            read = { it.isFocusable },
            write = { c, v -> c.isFocusable = v },
        )

/**
 * Sets `isEnabled` - whether the component responds to user input and paints in its enabled state.
 *
 * Sets `isEnabled` on **this component only**: disabling a container does not disable the components
 * inside it, so disable each child you want disabled.
 *
 * @see java.awt.Component.setEnabled
 */
public fun SwingModifier.enabled(enabled: Boolean): SwingModifier =
    this then
        propertyElement<Component, Boolean>(
            enabled,
            read = { it.isEnabled },
            // Honest Swing semantics: set on this component only; Swing does not cascade to children.
            write = { c, v -> c.isEnabled = v },
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
) : SwingModifier.Element<Component, HoverElement.Node> {
    override val targetType: Class<Component> get() = Component::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onEnter = onEnter
        node.onExit = onExit
    }

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
) : SwingModifier.Element<Component, FocusElement.Node> {
    override val targetType: Class<Component> get() = Component::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onGained = onGained
        node.onLost = onLost
    }

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
) : SwingModifier.Element<Component, PointerEventElement.Node> {
    override val targetType: Class<Component> get() = Component::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onPress = onPress
        node.onRelease = onRelease
        node.onClick = onClick
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

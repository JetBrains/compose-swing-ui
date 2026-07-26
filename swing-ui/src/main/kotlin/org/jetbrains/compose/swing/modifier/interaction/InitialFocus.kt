@file:JvmMultifileClass
@file:JvmName("InteractionModifiersKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component

/**
 * Declares that this component takes keyboard focus when its window first shows it - the field a form
 * or a dialog opens on.
 *
 * Focus is taken once, as soon as the component is on screen, and never again: a later re-show does not
 * pull the keyboard back from wherever the user has moved it. Where [focusTraversalIndex] and
 * [orderedFocusTraversal] decide the order Tab walks, this decides only where that walk starts.
 *
 * Declaring it on two components of one window is a contradiction the modifier does not resolve: the
 * one realized last takes the focus.
 */
public fun SwingModifier.initialFocus(): SwingModifier = this then InitialFocusElement

private object InitialFocusElement : SwingModifier.Element<Component, InitialFocusElement.Node> {
    override val targetType: Class<Component> get() = Component::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node): Unit = Unit

    class Node : SwingModifier.Node<Component>() {
        private val showing = ShowingWait()

        // The wait ends with the request it was waiting to make, which is what keeps the declaration to a
        // single request per attach.
        override fun onAttach(): Unit = showing.awaitShowing(component) { component.requestFocusInWindow() }

        override fun onDetach(): Unit = showing.cancel()
    }
}

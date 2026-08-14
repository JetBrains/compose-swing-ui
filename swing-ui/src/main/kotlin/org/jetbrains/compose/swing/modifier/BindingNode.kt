package org.jetbrains.compose.swing.modifier

import java.awt.Component

/**
 * A [SwingModifier.Node] holding the value currently bound to the node's component. Exactly one
 * value drives the component at a time; [onDetach] releases whatever is still bound.
 */
internal class BindingNode<C : Component, B : Any>(
    private val attach: (value: B, component: C) -> Unit,
    private val detach: (value: B, component: C) -> Unit,
) : SwingModifier.Node<C>() {
    /** The bound value; assign from the owning element's `update` with its latest value. */
    var value: B? = null
        set(next) {
            if (next === field) return
            field?.let { detach(it, component) }
            field = next
            next?.let { attach(it, component) }
        }

    override fun onDetach() {
        value = null
    }
}

/**
 * A [SwingModifier.NodeElement] that binds a value to a component for as long as the element occupies its
 * slot, backed by a [BindingNode]. Built through [binding], which documents the slot contract.
 *
 * Two elements are equal when they require the same component type, take the same slot and hold the
 * *same* value and callbacks - identity, matching what [BindingNode] rebinds on: an equal-looking
 * replacement is still a different value to give the component over to.
 */
internal class BindingElement<C : Component, B : Any>(
    override val targetType: Class<C>,
    private val value: B,
    private val attach: (value: B, component: C) -> Unit,
    private val detach: (value: B, component: C) -> Unit,
) : SwingModifier.NodeElement<C, BindingNode<C, B>>() {
    override val key: Any get() = attach.javaClass

    override fun create(): BindingNode<C, B> = BindingNode(attach, detach)

    override fun update(node: BindingNode<C, B>) {
        node.value = value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BindingElement<*, *>) return false
        if (targetType != other.targetType) return false
        if (value !== other.value) return false
        if (attach !== other.attach) return false
        return detach === other.detach
    }

    override fun hashCode(): Int {
        var result = targetType.hashCode()
        result = 31 * result + System.identityHashCode(value)
        result = 31 * result + System.identityHashCode(attach)
        result = 31 * result + System.identityHashCode(detach)
        return result
    }
}

/**
 * Binds [value] to the component of the node this chain is applied to, for as long as the element
 * occupies its slot: [attach] runs when the binding is established and [detach] when it ends.
 *
 * The binding follows the modifier node's lifecycle. A different [value] on a later recomposition
 * detaches the previous one before attaching the new, and the node detaching - the component leaving
 * the composition, or parking while deactivated - detaches outright, so a value never keeps driving a
 * component it no longer belongs to. Both callbacks are handed the
 * component the node is attached to, so a value that has since been bound to another component is
 * left driving that one. A `null` [value] declares no binding at all.
 *
 * One binding builder must declare exactly one [attach] callback (call this exactly once per builder):
 * that callback's class is the binding's last-wins slot, so every application of one builder shares a
 * slot while distinct builders stay independent.
 *
 * [target] is the component type the binding requires; [attach] and [detach] receive it already typed,
 * and a component that is not a [target] is rejected at apply with a clear error.
 *
 * Publish the component through `binding` when a caller must reach it - the holder is a handle the
 * caller owns and drives. When the only reader is the component's own composable, have the node act
 * on the component itself instead of publishing it.
 */
internal fun <C : Component, B : Any> SwingModifier.binding(
    target: Class<C>,
    value: B?,
    attach: (value: B, component: C) -> Unit,
    detach: (value: B, component: C) -> Unit,
): SwingModifier = if (value == null) this else this then BindingElement(target, value, attach, detach)

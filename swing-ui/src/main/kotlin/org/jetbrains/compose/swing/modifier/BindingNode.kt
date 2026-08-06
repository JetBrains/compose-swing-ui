package org.jetbrains.compose.swing.modifier

import java.awt.Component

/**
 * A [SwingModifier.Node] holding the value currently bound to the node's component. Assigning a
 * different value detaches the one held before binding the new, so exactly one value drives the
 * component at a time; [onDetach] releases whatever is still bound.
 *
 * Both callbacks name this node's own component, so a value this node is giving up - but another
 * component has already taken over - is left driving that other component.
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
 * A [SwingModifier.Element] that binds a value to a component for as long as the element occupies its
 * slot, backed by a [BindingNode]. Built through [binding].
 *
 * The last-wins slot is keyed by the class of the [attach] callback: each binding builder declares its
 * own `attach` - its own class - so two distinct bindings never collapse into one slot, while every
 * application of one builder shares that builder's slot.
 */
internal class BindingElement<C : Component, B : Any>(
    override val targetType: Class<C>,
    private val value: B,
    private val attach: (value: B, component: C) -> Unit,
    private val detach: (value: B, component: C) -> Unit,
) : SwingModifier.Element<C, BindingNode<C, B>> {
    override val key: Any get() = attach.javaClass

    override fun create(): BindingNode<C, B> = BindingNode(attach, detach)

    override fun update(node: BindingNode<C, B>) {
        node.value = value
    }
}

/**
 * Binds [value] to the component of the node this chain is applied to, for as long as the element
 * occupies its slot: [attach] runs when the binding is established and [detach] when it ends.
 *
 * The binding follows the modifier node's lifecycle. A different [value] on a later recomposition
 * detaches the previous one before attaching the new, and the node detaching - the component leaving
 * the composition, being recycled for reuse, or parking while deactivated - detaches outright, so a
 * value never keeps driving a component it no longer belongs to. Both callbacks are handed the
 * component the node is attached to, so a value that has since been bound to another component is
 * left driving that one. A `null` [value] declares no binding at all.
 *
 * One binding builder must declare exactly one [attach] callback (call this exactly once per builder):
 * that callback's class is the binding's last-wins slot, so every application of one builder shares a
 * slot while distinct builders stay independent.
 *
 * [target] is the component type the binding requires; [attach] and [detach] receive it already typed,
 * and a component that is not a [target] is rejected at apply with a clear error.
 */
internal fun <C : Component, B : Any> SwingModifier.binding(
    target: Class<C>,
    value: B?,
    attach: (value: B, component: C) -> Unit,
    detach: (value: B, component: C) -> Unit,
): SwingModifier = if (value == null) this else this then BindingElement(target, value, attach, detach)

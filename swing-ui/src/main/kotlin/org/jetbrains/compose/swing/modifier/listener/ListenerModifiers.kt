@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component

/**
 * Installs a listener instance on the target component via the modifier mechanism - the by-identity
 * listener seam, the one every builder taking a listener object is built on (the typed instance
 * builders like [mouseListener]/[actionListener] and the model builders like [changeListener]).
 *
 * The overload taking a callback and an adapter instead registers a listener the library builds, which
 * reads the callback when the event fires. That is the one to reach for when the handler is written at
 * the call site.
 *
 * The [instance] is added once via [attach] when the element enters the chain and removed via [detach]
 * when it leaves or the node is released/reused. Supplying a *different* instance (reference
 * inequality) on a later recomposition detaches the old one and attaches the new; supplying the same
 * instance is a no-op. Pass a stable instance (e.g. `remember {}`) to avoid that churn.
 *
 * Each call is its own slot: two listeners on one chain both install and both fire.
 *
 * [T] is the component type the listener attaches to; [attach]/[detach] receive it already typed, and
 * a node that is not a [T] is rejected at apply with a clear error.
 *
 * Reach for it last: it is the one place in the everyday API that names a component type, and a typed
 * builder or a callback modifier covers the ordinary cases. Adding and removing a listener through the
 * component it hands you is safe, which is what the seam exists for. Using that component to write a
 * property the composition declares, or to add or remove children, makes a second manager of something
 * the composition already owns - the write is undone by the next recomposition, or the two managers
 * corrupt each other's bookkeeping.
 *
 * @param instance the Swing/AWT listener (or model listener) object to install.
 * @param attach adds [instance] to the (already-typed) component.
 * @param detach removes [instance] from the component.
 */
public inline fun <reified T : Component, L : Any> SwingModifier.listener(
    instance: L,
    noinline attach: (component: T, listener: L) -> Unit,
    noinline detach: (component: T, listener: L) -> Unit,
): SwingModifier = this then InstanceListenerElement(T::class.java, instance, attach, detach)

/**
 * The additive [SwingModifier.NodeElement] backing the instance overload of `listener` and every
 * typed/model builder. It carries that overload's by-identity add/remove contract into its
 * [InstanceListenerNode].
 *
 * Equality follows that same by-identity contract: two elements are equal when they target the same
 * type and hold the same [instance], [attach] and [detach] objects, which is exactly the case in which
 * reconciling would change nothing.
 */
@PublishedApi
internal class InstanceListenerElement<T : Component, L : Any>(
    override val targetType: Class<T>,
    internal val instance: L,
    internal val attach: (component: T, listener: L) -> Unit,
    internal val detach: (component: T, listener: L) -> Unit,
) : SwingModifier.NodeElement<T, InstanceListenerNode<T, L>>() {
    override val additive: Boolean get() = true

    override fun create(): InstanceListenerNode<T, L> = InstanceListenerNode()

    override fun update(node: InstanceListenerNode<T, L>): Unit = node.swapTo(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstanceListenerElement<*, *>) return false
        if (targetType != other.targetType) return false
        if (instance !== other.instance) return false
        if (attach !== other.attach) return false
        return detach === other.detach
    }

    override fun hashCode(): Int {
        var result = targetType.hashCode()
        result = 31 * result + System.identityHashCode(instance)
        result = 31 * result + System.identityHashCode(attach)
        result = 31 * result + System.identityHashCode(detach)
        return result
    }
}

/**
 * The node backing [InstanceListenerElement]. It keeps the whole element as the unit of attachment,
 * pairing each instance with the attach/detach it was supplied with. An instance is thus always removed
 * through its own detach, even after a positional rebind hands the node an element carrying a different
 * listener type.
 */
@PublishedApi
internal class InstanceListenerNode<T : Component, L : Any> : SwingModifier.Node<T>() {
    private var pending: InstanceListenerElement<T, L>? = null
    private var attached: InstanceListenerElement<T, L>? = null

    /** Records the latest element, then reconciles attachments. Called from the element's `update`. */
    fun swapTo(element: InstanceListenerElement<T, L>) {
        pending = element
        reconcile()
    }

    override fun onAttach(): Unit = reconcile()

    private fun reconcile() {
        val next = pending ?: return
        val current = attached
        // The same instance stays registered, owned by the pairing that attached it; only an
        // identity change swaps the registration.
        if (current?.instance === next.instance) return
        current?.let { it.detach(component, it.instance) }
        next.attach(component, next.instance)
        attached = next
    }

    override fun onDetach() {
        attached?.let { it.detach(component, it.instance) }
        attached = null
    }
}

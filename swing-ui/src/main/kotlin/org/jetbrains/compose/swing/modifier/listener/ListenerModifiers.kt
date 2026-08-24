@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import kotlin.reflect.KClass

/**
 * Installs a listener instance on the target component via the modifier mechanism - the by-identity
 * listener seam, the one every builder taking a listener object is built on (the typed instance
 * builders like [mouseListener]/[actionListener] and the model builders like [changeListener]).
 *
 * The overload taking a callback and a [CallbackRegistration] instead registers a listener the library
 * builds, which reads the callback when the event fires. That is the one to reach for when the handler
 * is written at the call site.
 *
 * The [instance] is added once through [registration] when the element enters the chain and removed when it
 * leaves or the node is released/reused. Supplying a *different* instance (reference inequality) on a
 * later recomposition detaches the old one and attaches the new; supplying the same instance is a no-op.
 * Pass a stable instance (e.g. `remember {}`) to avoid that churn.
 *
 * [T] is the component type the listener attaches to; [registration] receives it already typed.
 *
 * Reach for it last: a typed builder or a callback modifier covers the ordinary cases. Adding and
 * removing a listener through the component it hands you is safe, which is what the seam exists for.
 * Using that component to write a property the composition declares, or to add or remove children,
 * makes a second manager of something the composition already owns. The write is undone by the next
 * recomposition, or the two managers corrupt each other's bookkeeping.
 *
 * @param instance the Swing/AWT listener (or model listener) object to install.
 * @param registration where [instance] is registered.
 */
public inline fun <reified T : Component, L : Any> SwingModifier.listener(
    instance: L,
    registration: ListenerRegistration<T, L>,
): SwingModifier = listener(T::class, instance, registration)

/**
 * Installs one library-built listener that reads the caller's [callback] when an event fires - the seam
 * every builder's lambda overload is built on.
 *
 * This is the callback half of the listener seam, and what is registered is what separates it from the
 * overload taking a listener instance. That one registers the caller's own object, whose identity is
 * the contract: a different object detaches the old one and attaches the new, so a caller passing a
 * fresh lambda has to `remember` it. Here the registered object is the one [registration] builds, and the
 * caller's [callback] lives in a node field the built listener reads when the event fires - a fresh
 * lambda each recomposition costs one field write and re-registers nothing. Hand over a listener
 * instance to reach the other overload; hand over a callback the library wraps to reach this one.
 *
 * The built listener is added through [registration] when the element enters the chain and removed when it
 * leaves or the node is released/reused.
 *
 * @param callback what the built listener reads when an event fires; refreshed on every pass.
 * @param registration where the built listener is registered, and how it is built.
 */
public inline fun <reified T : Component, C : Any, L : Any> SwingModifier.listener(
    callback: C,
    registration: CallbackRegistration<T, C, L>,
): SwingModifier = listener(T::class, callback, registration)

/**
 * Installs a listener instance, naming the target component type as a value.
 *
 * The same by-identity contract as the reified overload above, with [targetType] - the thing that one
 * reifies - spelled out. Reach for this when the component type is only known as a `Class`.
 *
 * @param targetType the component type [registration] receives.
 * @param instance the Swing/AWT listener (or model listener) object to install.
 * @param registration where [instance] is registered.
 */
public fun <T : Component, L : Any> SwingModifier.listener(
    targetType: KClass<T>,
    instance: L,
    registration: ListenerRegistration<T, L>,
): SwingModifier = this then InstanceListenerElement(targetType.java, instance, registration)

/**
 * Installs one library-built listener that reads the caller's [callback], naming the target component
 * type as a value.
 *
 * The same live-callback contract as the reified overload above, with [targetType] - the thing that
 * one reifies - spelled out. Reach for this when the component type is only known as a `Class`.
 *
 * @param targetType the component type [registration] receives.
 * @param callback what the built listener reads when an event fires; refreshed on every pass.
 * @param registration where the built listener is registered, and how it is built.
 */
public fun <T : Component, C : Any, L : Any> SwingModifier.listener(
    targetType: KClass<T>,
    callback: C,
    registration: CallbackRegistration<T, C, L>,
): SwingModifier = this then LiveCallbackListenerElement(targetType.java, callback, registration)

/**
 * The additive [SwingModifier.NodeElement] backing the instance overload of `listener` and every
 * typed/model builder. It carries that overload's by-identity add/remove contract into its
 * [InstanceListenerNode].
 *
 * Equality is what a registration is made of: two elements are equal when they target the same type and
 * hold the same [instance] on the same [registration]. That is what lets an element rebuilt at every pass
 * compare equal to the one already installed, so a listener stays where it is until the declaration
 * names a different registration.
 */
private class InstanceListenerElement<T : Component, L : Any>(
    override val targetType: Class<T>,
    val instance: L,
    val registration: ListenerRegistration<T, L>,
) : SwingModifier.NodeElement<T, InstanceListenerNode<T, L>>() {
    override val additive: Boolean get() = true

    override fun create(): InstanceListenerNode<T, L> = InstanceListenerNode()

    override fun update(node: InstanceListenerNode<T, L>): Unit = node.swapTo(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstanceListenerElement<*, *>) return false
        if (targetType != other.targetType) return false
        if (instance !== other.instance) return false
        return registration == other.registration
    }

    override fun hashCode(): Int {
        var result = targetType.hashCode()
        result = 31 * result + System.identityHashCode(instance)
        result = 31 * result + registration.hashCode()
        return result
    }
}

/**
 * The node backing [InstanceListenerElement]. It keeps the whole element as the unit of attachment,
 * pairing each instance with the registration it was registered on. An instance is thus always removed
 * through its own registration, even after a positional rebind hands the node an element carrying a
 * different listener type.
 */
private class InstanceListenerNode<T : Component, L : Any> : SwingModifier.Node<T>() {
    private var attached: InstanceListenerElement<T, L>? = null

    /**
     * Installs [element], taking off whatever this node had installed before it. Called from the
     * element's `update`, and only for an element the slot did not already carry: equal elements are
     * the same registration, and the slot keeps the one it has rather than pushing it here.
     */
    fun swapTo(element: InstanceListenerElement<T, L>) {
        attached?.let { it.registration.detach(component, it.instance) }
        element.registration.attach(component, element.instance)
        attached = element
    }

    override fun onDetach() {
        attached?.let { it.registration.detach(component, it.instance) }
        attached = null
    }
}

/**
 * The additive [SwingModifier.NodeElement] backing the callback overload of `listener`.
 *
 * Not a data class: [callback] is compared by identity, because a callback carries an `equals` of its
 * own - a function reference does - under which two callbacks the node must tell apart compare equal.
 * The element would skip its update and the node would keep calling the callback the caller replaced.
 */
private class LiveCallbackListenerElement<T : Component, C : Any, L : Any>(
    override val targetType: Class<T>,
    val callback: C,
    val registration: CallbackRegistration<T, C, L>,
) : SwingModifier.NodeElement<T, LiveCallbackListenerNode<T, C, L>>() {
    override val additive: Boolean get() = true

    override fun create(): LiveCallbackListenerNode<T, C, L> = LiveCallbackListenerNode(this)

    override fun update(node: LiveCallbackListenerNode<T, C, L>): Unit = node.swapTo(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiveCallbackListenerElement<*, *, *>) return false
        if (targetType != other.targetType) return false
        if (callback !== other.callback) return false
        return registration == other.registration
    }

    override fun hashCode(): Int {
        var result = targetType.hashCode()
        result = 31 * result + System.identityHashCode(callback)
        result = 31 * result + registration.hashCode()
        return result
    }
}

/**
 * The node backing [LiveCallbackListenerElement]. It takes the element it was created for and keeps the
 * latest one, so the listener it built reads the callback the current composition declares, and is
 * always removed through the registration that registered it.
 */
private class LiveCallbackListenerNode<T : Component, C : Any, L : Any>(
    private var element: LiveCallbackListenerElement<T, C, L>,
) : SwingModifier.Node<T>() {
    private var listener: L = element.registration.adapter { element.callback }

    override fun onAttach(): Unit = element.registration.attach(component, listener)

    override fun onDetach(): Unit = element.registration.detach(component, listener)

    /**
     * Records the latest element, which is all a fresh callback costs. Called from the element's
     * `update`, hence only while the node is attached: an [element] naming a different registration is a
     * different registration, and moving it needs the component.
     */
    fun swapTo(next: LiveCallbackListenerElement<T, C, L>) {
        val current = element
        element = next
        if (current.registration == next.registration) return
        current.registration.detach(component, listener)
        listener = next.registration.adapter { element.callback }
        next.registration.attach(component, listener)
    }
}

@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.util.EventObject
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
 * The [instance] is added once through [registration] when the element enters the chain and removed
 * when it leaves or the node is released/reused. Supplying a *different* instance (reference
 * inequality) on a later recomposition detaches the old one and attaches the new; supplying the same
 * instance is a no-op. Pass a stable instance (e.g. `remember {}`) to avoid that churn.
 *
 * [T] is the component type the listener attaches to; [registration] receives it already typed, and a
 * node that is not a [T] is rejected at apply with a clear error.
 *
 * Reach for it last: it is the one place in the everyday API that names a component type, and a typed
 * builder or a callback modifier covers the ordinary cases. Adding and removing a listener through the
 * component it hands you is safe, which is what the seam exists for. Using that component to write a
 * property the composition declares, or to add or remove children, makes a second manager of something
 * the composition already owns. The widget then holds a value nothing declares until the next write or
 * settlement overwrites it, or the two managers corrupt each other's bookkeeping.
 *
 * @param instance the Swing/AWT listener (or model listener) object to install.
 * @param registration where [instance] is registered.
 * @return this chain with [instance] declared on it.
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
 * fresh lambda has to `remember` it. Here the registered object is the one [registration] builds, and
 * the caller's [callback] is read when the event fires - a fresh lambda each recomposition costs one
 * write and re-registers nothing, and since it changes no registration the chain it sits in is applied
 * as the one applied last, without being walked for it. Hand over a listener instance to reach the
 * other overload; hand over a callback the library wraps to reach this one.
 *
 * The built listener is added through [registration] when the element enters the chain and removed when
 * it leaves or the node is released/reused. Each call is its own slot: two of them on one chain both
 * install and both fire.
 *
 * @param callback what the built listener reads when an event fires; refreshed on every pass.
 * @param registration where the built listener is registered, and how it is built.
 * @return this chain with the built listener declared on it.
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
 * @param targetType the component type [registration] receives; a node that is not one is rejected at
 *     apply with a clear error.
 * @param instance the Swing/AWT listener (or model listener) object to install.
 * @param registration where [instance] is registered.
 * @return this chain with [instance] declared on it.
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
 * @return this chain with the built listener declared on it.
 */
public fun <T : Component, C : Any, L : Any> SwingModifier.listener(
    targetType: KClass<T>,
    callback: C,
    registration: CallbackRegistration<T, C, L>,
): SwingModifier = this then LiveCallbackListenerElement(targetType.java, callback, registration)

/**
 * Registers a listener built by [registration] that runs [onEvent] with the component the event names as
 * its source as the receiver - the scoped counterpart of the callback overload above.
 *
 * A component this attaches to fires with itself as the source, so the receiver is the component the
 * modifier is on. An event carrying any other source is one the scope cannot be given, and it is refused
 * rather than dropped - the overload's whole contract is that receiver.
 *
 * Read the receiver's properties through `this` where the enclosing composable declares a parameter of
 * the same name: an enclosing local shadows the receiver.
 *
 * @param targetType the component type the listener is scoped to, checked against the node as well.
 * @param registration where the built listener is registered, and how it is built.
 * @param onEvent what the built listener runs; refreshed on every pass.
 */
internal fun <T : Component, E : EventObject, L : Any> SwingModifier.listener(
    targetType: KClass<T>,
    registration: CallbackRegistration<*, (E) -> Unit, L>,
    onEvent: T.(E) -> Unit,
): SwingModifier {
    // The element rejects a node that is not a T before the listener the registration builds is ever
    // attached to it.
    @Suppress("UNCHECKED_CAST")
    val scoped = registration as CallbackRegistration<T, (E) -> Unit, L>
    return listener(
        targetType = targetType,
        callback = { event: E ->
            val source = event.source
            check(targetType.isInstance(source)) {
                "a ${event.javaClass.simpleName} sourced at $source is not scoped to ${targetType.simpleName}"
            }
            // The check above is the instance test the cast needs.
            @Suppress("UNCHECKED_CAST")
            (source as T).onEvent(event)
        },
        registration = scoped,
    )
}

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

    override val name: String get() = registration.name

    override val declaredValues: Map<String, Any?> get() = mapOf("listener" to instance)

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
 * pairing each instance with the registration it was added through. An instance is thus always removed
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
 * What the element *registers* is one listener object, identified by [targetType] and the
 * [registration] it sits on; what it *carries* is the callback that listener reads when it fires. Those
 * are two different questions, and this element answers them with two different comparisons.
 *
 * [adopt] answers the registration alone. It is what the apply path asks, and a slot whose registration
 * is unchanged keeps its node and its listener however the callbacks compare - the newer callback is
 * written onto the node the listener reads it from. A chain differing only in its callbacks is therefore
 * applied without being diffed, and what a component rebuilding its own callback on every pass costs is
 * that one write.
 *
 * [equals] answers both, comparing the declared callback by identity. A modifier is also a composable's
 * parameter, and a `@Stable` parameter equal to the one passed last is a parameter the runtime skips the
 * composable for - never running the `update` block this element's apply path is reached through. A
 * callback of a new identity is the caller declaring something new, so it has to leave the chain unequal,
 * or the callback the caller replaced would be the one that keeps firing.
 */
private class LiveCallbackListenerElement<T : Component, C : Any, L : Any>(
    override val targetType: Class<T>,
    val declared: C,
    val registration: CallbackRegistration<T, C, L>,
) : SwingModifier.NodeElement<T, LiveCallbackListenerNode<T, C, L>>() {
    override val additive: Boolean get() = true

    override val name: String get() = registration.name

    override val declaredValues: Map<String, Any?> get() = mapOf("callback" to declared)

    override fun create(): LiveCallbackListenerNode<T, C, L> = LiveCallbackListenerNode(this)

    override fun update(node: LiveCallbackListenerNode<T, C, L>): Unit = node.swapTo(this)

    /**
     * Answers whether [next] registers what this element registered, and where it does, writes [next]'s
     * callback onto [node] - which is the one place the listener this element registered reads it from.
     */
    override fun adopt(
        node: LiveCallbackListenerNode<T, C, L>,
        next: SwingModifier.NodeElement<*, *>,
    ): Boolean {
        if (!registers(next)) return false
        // The registration includes the adapter that builds the listener - one per event source, and it
        // is the registration that fixes the callback type - so a matching element carries a callback of C.
        @Suppress("UNCHECKED_CAST")
        node.swapTo(next as LiveCallbackListenerElement<T, C, L>)
        return true
    }

    /** Whether [next] adds the same listener object to the same component as this element does. */
    private fun registers(next: SwingModifier.NodeElement<*, *>): Boolean =
        next is LiveCallbackListenerElement<*, *, *> &&
            targetType == next.targetType &&
            registration == next.registration

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiveCallbackListenerElement<*, *, *>) return false
        if (targetType != other.targetType) return false
        if (declared !== other.declared) return false
        return registration == other.registration
    }

    override fun hashCode(): Int {
        var result = targetType.hashCode()
        result = 31 * result + System.identityHashCode(declared)
        result = 31 * result + registration.hashCode()
        return result
    }
}

/**
 * The node backing [LiveCallbackListenerElement]. It keeps the element whose registration it holds and
 * the callback that registration's listener reads, so the listener runs what the current composition
 * declares, and is always removed through the registration that added it.
 */
private class LiveCallbackListenerNode<T : Component, C : Any, L : Any>(
    private var element: LiveCallbackListenerElement<T, C, L>,
) : SwingModifier.Node<T>() {
    /** What the built listener runs when it fires: the callback the latest pass declared for this slot. */
    var callback: C = element.declared

    private var listener: L = element.registration.adapter { callback }

    override fun onAttach(): Unit = element.registration.attach(component, listener)

    override fun onDetach(): Unit = element.registration.detach(component, listener)

    /**
     * Takes [next] as the element this node holds, and its callback as the one the listener runs. Called
     * while the node is attached: from the element's `update`, and from an adopt, which reaches here
     * with an element naming the same registration. A [next] naming a different one needs the component
     * to move the listener.
     */
    fun swapTo(next: LiveCallbackListenerElement<T, C, L>) {
        val current = element
        element = next
        callback = next.declared
        if (current.registration == next.registration) return
        current.registration.detach(component, listener)
        listener = next.registration.adapter { callback }
        next.registration.attach(component, listener)
    }
}

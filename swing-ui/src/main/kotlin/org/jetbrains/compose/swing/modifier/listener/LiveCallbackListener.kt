@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component

/**
 * Installs one library-built listener that reads the caller's [callback] when an event fires - the seam
 * every builder's lambda overload is built on.
 *
 * This is the callback half of the listener seam, and what is registered is what separates it from the
 * overload taking a listener instance. That one registers the caller's own object, whose identity is
 * the contract: a different object detaches the old one and attaches the new, so a caller passing a
 * fresh lambda has to `remember` it. Here the registered object is the one [adapter] builds, and the
 * caller's [callback] lives in a node field the adapter reads when the event fires - a fresh lambda
 * each recomposition costs one field write and re-registers nothing. Hand over a listener instance to
 * reach the other overload; hand over a callback the library wraps to reach this one.
 *
 * The built listener is added via [attach] when the element enters the chain and removed via [detach]
 * when it leaves or the node is released/reused. Each call is its own slot: two of them on one chain
 * both install and both fire.
 *
 * [adapter] runs once per registration. Its identity is what identifies the registration, so declare it
 * as a lambda capturing nothing - one per builder. A conditional chain that changes shape can hand this
 * element's positional slot an element built by a different builder; a different [adapter] is that case,
 * and it swaps the whole registration rather than the field.
 *
 * @param callback what the built listener reads when an event fires; refreshed on every pass.
 * @param adapter builds the listener object; its `current` argument yields the latest [callback].
 * @param attach adds the built listener to the (already-typed) component.
 * @param detach removes the built listener from the component.
 * @param registrationKey what [attach] and [detach] close over that belongs to the registration rather
 *     than to the callback, such as the property name a listener is bound to. A change to it is a
 *     different registration and swaps it; null when the pairing closes over nothing.
 */
public inline fun <reified T : Component, C : Any, L : Any> SwingModifier.listener(
    callback: C,
    noinline adapter: (current: () -> C) -> L,
    noinline attach: (component: T, listener: L) -> Unit,
    noinline detach: (component: T, listener: L) -> Unit,
    registrationKey: Any? = null,
): SwingModifier =
    this then
        LiveCallbackListenerElement(T::class.java, callback, adapter, attach, detach, registrationKey)

/**
 * The additive [SwingModifier.NodeElement] backing the callback overload of `listener`.
 *
 * Not a data class: [callback] is compared by identity, because a callback carries an `equals` of its
 * own - a function reference does - under which two callbacks the node must tell apart compare equal.
 * The element would skip its update and the node would keep calling the callback the caller replaced.
 */
@PublishedApi
internal class LiveCallbackListenerElement<T : Component, C : Any, L : Any>(
    override val targetType: Class<T>,
    internal val callback: C,
    internal val adapter: (current: () -> C) -> L,
    internal val attach: (component: T, listener: L) -> Unit,
    internal val detach: (component: T, listener: L) -> Unit,
    internal val registrationKey: Any?,
) : SwingModifier.NodeElement<T, LiveCallbackListenerNode<T, C, L>>() {
    override val additive: Boolean get() = true

    override fun create(): LiveCallbackListenerNode<T, C, L> = LiveCallbackListenerNode(this)

    override fun update(node: LiveCallbackListenerNode<T, C, L>): Unit = node.swapTo(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiveCallbackListenerElement<*, *, *>) return false
        if (targetType != other.targetType) return false
        if (callback !== other.callback) return false
        if (adapter !== other.adapter) return false
        return registrationKey == other.registrationKey
    }

    override fun hashCode(): Int {
        var result = targetType.hashCode()
        result = 31 * result + System.identityHashCode(callback)
        result = 31 * result + System.identityHashCode(adapter)
        result = 31 * result + registrationKey.hashCode()
        return result
    }
}

/**
 * The node backing [LiveCallbackListenerElement]. It takes the element it was created for and keeps the
 * latest one, so the listener it built reads the callback the current composition declares, and is
 * always removed through the detach of the pairing that added it.
 */
@PublishedApi
internal class LiveCallbackListenerNode<T : Component, C : Any, L : Any>(
    private var element: LiveCallbackListenerElement<T, C, L>,
) : SwingModifier.Node<T>() {
    private var listener: L = element.adapter { element.callback }

    override fun onAttach(): Unit = element.attach(component, listener)

    override fun onDetach(): Unit = element.detach(component, listener)

    /**
     * Records the latest element, which is all a fresh callback costs. Called from the element's
     * `update`, hence only while the node is attached: an [element] built by a different adapter, or
     * carrying a different registration key, is a different registration, and swapping it needs the
     * component.
     */
    fun swapTo(next: LiveCallbackListenerElement<T, C, L>) {
        val current = element
        element = next
        if (current.adapter === next.adapter && current.registrationKey == next.registrationKey) return
        current.detach(component, listener)
        listener = next.adapter { element.callback }
        next.attach(component, listener)
    }
}

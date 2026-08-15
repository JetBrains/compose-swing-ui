@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component

/**
 * Installs one library-built listener that reads the caller's [callback] live - the seam for a modifier
 * or a component whose callback is a lambda the caller writes inline, and the twin of [listener].
 *
 * The two differ in what is registered. [listener] registers the caller's own listener object, whose
 * identity is the contract: a different object detaches the old one and attaches the new, so a caller
 * passing a fresh lambda has to `remember` it. Here the registered object is the one [adapter] builds,
 * and the caller's [callback] lives in a node field the adapter reads when the event fires - a fresh
 * lambda each recomposition costs one field write and re-registers nothing. Use [listener] when the
 * caller hands over a listener instance; use this when the caller hands over a callback the library
 * wraps.
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
 */
internal inline fun <reified T : Component, C : Any, L : Any> SwingModifier.liveCallbackListener(
    callback: C,
    noinline adapter: (current: () -> C) -> L,
    noinline attach: (component: T, listener: L) -> Unit,
    noinline detach: (component: T, listener: L) -> Unit,
): SwingModifier = this then LiveCallbackListenerElement(T::class.java, callback, adapter, attach, detach)

/**
 * The additive [SwingModifier.NodeElement] backing [liveCallbackListener].
 *
 * Not a data class: [callback] is compared by identity, because a callback carries an `equals` of its
 * own - a function reference does - under which two callbacks the node must tell apart compare equal.
 * The element would skip its update and the node would keep calling the callback the caller replaced.
 */
internal class LiveCallbackListenerElement<T : Component, C : Any, L : Any>(
    override val targetType: Class<T>,
    val callback: C,
    val adapter: (current: () -> C) -> L,
    val attach: (component: T, listener: L) -> Unit,
    val detach: (component: T, listener: L) -> Unit,
) : SwingModifier.NodeElement<T, LiveCallbackListenerNode<T, C, L>>() {
    override val additive: Boolean get() = true

    override fun create(): LiveCallbackListenerNode<T, C, L> = LiveCallbackListenerNode(this)

    override fun update(node: LiveCallbackListenerNode<T, C, L>): Unit = node.swapTo(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiveCallbackListenerElement<*, *, *>) return false
        if (targetType != other.targetType) return false
        if (callback !== other.callback) return false
        return adapter === other.adapter
    }

    override fun hashCode(): Int {
        var result = targetType.hashCode()
        result = 31 * result + System.identityHashCode(callback)
        result = 31 * result + System.identityHashCode(adapter)
        return result
    }
}

/**
 * The node backing [LiveCallbackListenerElement]. It takes the element it was created for and keeps the
 * latest one, so the listener it built reads the callback the current composition declares, and is
 * always removed through the detach of the pairing that added it.
 */
internal class LiveCallbackListenerNode<T : Component, C : Any, L : Any>(
    private var element: LiveCallbackListenerElement<T, C, L>,
) : SwingModifier.Node<T>() {
    private var listener: L = element.adapter { element.callback }

    override fun onAttach(): Unit = element.attach(component, listener)

    override fun onDetach(): Unit = element.detach(component, listener)

    /**
     * Records the latest element, which is all a fresh callback costs. Called from the element's
     * `update`, hence only while the node is attached: an [element] built by a different adapter is a
     * different registration, and swapping it needs the component.
     */
    fun swapTo(next: LiveCallbackListenerElement<T, C, L>) {
        val current = element
        element = next
        if (current.adapter === next.adapter) return
        current.detach(component, listener)
        listener = next.adapter { element.callback }
        next.attach(component, listener)
    }
}

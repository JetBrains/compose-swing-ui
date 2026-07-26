@file:JvmMultifileClass
@file:JvmName("ListenerModifiersKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.Container
import java.awt.event.ComponentListener
import java.awt.event.ContainerListener
import java.awt.event.FocusListener
import java.awt.event.HierarchyListener
import java.awt.event.KeyListener
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.event.MouseWheelListener

/**
 * Installs a listener instance on the target component via the modifier mechanism - the single
 * listener seam in the library, the one every reactive listener is built on (the typed instance
 * builders like [mouseListener]/[actionListener], the model builders like [changeListener], the
 * multi-method interaction builders like `onHover`, and the built-in domain callbacks of every
 * reactive component).
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
 * The additive [SwingModifier.Element] backing [listener] and every typed/model builder. It owns the
 * by-identity add/remove contract through its [InstanceListenerNode]: the same instance is attached
 * once and removed on detach; supplying a different instance on recomposition detaches the old one and
 * attaches the new.
 *
 * Marked [InternalSwingUiApi]; it may change without notice in any release.
 */
@InternalSwingUiApi
@PublishedApi
internal class InstanceListenerElement<T : Component, L : Any>(
    override val targetType: Class<T>,
    internal val instance: L,
    internal val attach: (component: T, listener: L) -> Unit,
    internal val detach: (component: T, listener: L) -> Unit,
) : SwingModifier.Element<T, InstanceListenerNode<T, L>> {
    override val additive: Boolean get() = true

    override fun create(): InstanceListenerNode<T, L> = InstanceListenerNode()

    override fun update(node: InstanceListenerNode<T, L>): Unit = node.swapTo(this)
}

/**
 * The node backing [InstanceListenerElement]: it attaches the current instance on [onAttach], swaps to
 * a new instance on identity change (detach old, attach new), and removes the attached instance on
 * [onDetach].
 *
 * The node keeps the whole element as the unit of attachment, pairing each instance with the
 * attach/detach it was supplied with. An instance is thus always removed through its own detach,
 * even after a positional rebind hands the node an element carrying a different listener type.
 *
 * Marked [InternalSwingUiApi]; it may change without notice in any release.
 */
@InternalSwingUiApi
@PublishedApi
internal class InstanceListenerNode<T : Component, L : Any> : SwingModifier.Node<T>() {
    private var pending: InstanceListenerElement<T, L>? = null
    private var attached: InstanceListenerElement<T, L>? = null

    /** Records the latest element, then reconciles attachments. Called from the element's `update`. */
    @InternalSwingUiApi
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

/*
 * Typed instance builders - attach an EXISTING Swing/AWT listener object to a component as-is.
 *
 * Semantics for every builder:
 *  - the instance is added once, on install, and the SAME instance is removed on detach/reuse;
 *  - two of the same builder both install and both fire;
 *  - if a DIFFERENT instance (reference inequality) is supplied on a later recomposition, the old
 *    instance is detached and the new one attached - so pass a STABLE instance (e.g. `remember {}`) to
 *    avoid that churn;
 *  - multi-method interfaces are passed as objects; single-method (SAM) interfaces also accept a lambda
 *    via Kotlin SAM conversion, but a fresh lambda each recomposition is a new instance and triggers
 *    the detach-old/attach-new swap above - `remember {}` it to keep one stable instance.
 */

/** Attaches a [MouseListener] (`addMouseListener`/`removeMouseListener`). */
public fun SwingModifier.mouseListener(listener: MouseListener): SwingModifier =
    listener<Component, MouseListener>(
        listener,
        { c, l -> c.addMouseListener(l) },
        { c, l -> c.removeMouseListener(l) },
    )

/** Attaches a [MouseMotionListener] (`addMouseMotionListener`/`removeMouseMotionListener`). */
public fun SwingModifier.mouseMotionListener(listener: MouseMotionListener): SwingModifier =
    listener<Component, MouseMotionListener>(
        listener,
        { c, l -> c.addMouseMotionListener(l) },
        { c, l -> c.removeMouseMotionListener(l) },
    )

/** Attaches a [MouseWheelListener] (`addMouseWheelListener`/`removeMouseWheelListener`). */
public fun SwingModifier.mouseWheelListener(listener: MouseWheelListener): SwingModifier =
    listener<Component, MouseWheelListener>(
        listener,
        { c, l -> c.addMouseWheelListener(l) },
        { c, l -> c.removeMouseWheelListener(l) },
    )

/** Attaches a [KeyListener] (`addKeyListener`/`removeKeyListener`). */
public fun SwingModifier.keyListener(listener: KeyListener): SwingModifier =
    listener<Component, KeyListener>(
        listener,
        { c, l -> c.addKeyListener(l) },
        { c, l -> c.removeKeyListener(l) },
    )

/** Attaches a [FocusListener] (`addFocusListener`/`removeFocusListener`). */
public fun SwingModifier.focusListener(listener: FocusListener): SwingModifier =
    listener<Component, FocusListener>(
        listener,
        { c, l -> c.addFocusListener(l) },
        { c, l -> c.removeFocusListener(l) },
    )

/** Attaches a [ComponentListener] (`addComponentListener`/`removeComponentListener`). */
public fun SwingModifier.componentListener(listener: ComponentListener): SwingModifier =
    listener<Component, ComponentListener>(
        listener,
        { c, l -> c.addComponentListener(l) },
        { c, l -> c.removeComponentListener(l) },
    )

/** Attaches a [HierarchyListener] (`addHierarchyListener`/`removeHierarchyListener`). */
public fun SwingModifier.hierarchyListener(listener: HierarchyListener): SwingModifier =
    listener<Component, HierarchyListener>(
        listener,
        { c, l -> c.addHierarchyListener(l) },
        { c, l -> c.removeHierarchyListener(l) },
    )

/**
 * Attaches a [ContainerListener] (`addContainerListener`/`removeContainerListener`). Requires a
 * [Container] target (the add/remove pair lives on `java.awt.Container`).
 */
public fun SwingModifier.containerListener(listener: ContainerListener): SwingModifier =
    listener<Container, ContainerListener>(
        listener,
        { c, l -> c.addContainerListener(l) },
        { c, l -> c.removeContainerListener(l) },
    )

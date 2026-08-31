@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Container
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener

/**
 * Runs [onChildrenChange] whenever a child is added to or removed from the container. Requires a
 * [Container] target.
 *
 * [onChildrenChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @param onChildrenChange receives the event, whose `child` is the component that joined or left; the
 *   composition's own inserts and removals report here too.
 * @return this chain with the container callback declared on it.
 * @see java.awt.Container.addContainerListener
 */
public fun SwingModifier.containerListener(onChildrenChange: (ContainerEvent) -> Unit): SwingModifier =
    containerListener(
        onComponentAdded = onChildrenChange,
        onComponentRemoved = onChildrenChange,
    )

/**
 * Runs [onComponentAdded] when a child joins the container and [onComponentRemoved] when one
 * leaves. Requires a [Container] target. A direction left undeclared reports nowhere.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition
 * registers nothing again.
 *
 * Declaring none at all is refused.
 *
 * @param onComponentAdded runs once the child is in the container, so its index is already the new one.
 * @param onComponentRemoved runs once the child is out, so its `parent` is already `null`.
 * @return this chain with the container callbacks declared on it.
 * @see java.awt.Container.addContainerListener
 */
public fun SwingModifier.containerListener(
    onComponentAdded: (ContainerEvent) -> Unit = UNDECLARED,
    onComponentRemoved: (ContainerEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("containerListener", declared(onComponentAdded) || declared(onComponentRemoved))
    return listener(ContainerCallbacks(onComponentAdded, onComponentRemoved), CONTAINER_CALLBACKS)
}

/**
 * Attaches a [ContainerListener] (`addContainerListener`/`removeContainerListener`). Requires a
 * [Container] target.
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the container listener declared on it.
 * @see java.awt.Container.addContainerListener
 */
public fun SwingModifier.containerListener(listener: ContainerListener): SwingModifier = listener(listener, CONTAINER)

/** The lambdas [containerListener] was declared with, as one value the built listener reads. */
private class ContainerCallbacks(
    val onComponentAdded: (ContainerEvent) -> Unit,
    val onComponentRemoved: (ContainerEvent) -> Unit,
)

private val CONTAINER =
    ListenerRegistration<Container, ContainerListener>(
        name = "containerListener",
        Container::addContainerListener,
        Container::removeContainerListener,
    )

private val CONTAINER_CALLBACKS =
    CallbackRegistration<Container, ContainerCallbacks, ContainerListener>(
        adapter = { current ->
            object : ContainerListener {
                override fun componentAdded(event: ContainerEvent): Unit = current().onComponentAdded(event)

                override fun componentRemoved(event: ContainerEvent): Unit = current().onComponentRemoved(event)
            }
        },
        registration = CONTAINER,
    )

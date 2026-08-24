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
 * @see java.awt.Container.addContainerListener
 */
public fun SwingModifier.containerListener(
    onComponentAdded: (ContainerEvent) -> Unit = UNDECLARED,
    onComponentRemoved: (ContainerEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("containerListener", declared(onComponentAdded) || declared(onComponentRemoved))
    return liveCallbackListener<Container, ContainerCallbacks, ContainerListener>(
        callback = ContainerCallbacks(onComponentAdded, onComponentRemoved),
        adapter = { current ->
            object : ContainerListener {
                override fun componentAdded(event: ContainerEvent): Unit = current().onComponentAdded(event)

                override fun componentRemoved(event: ContainerEvent): Unit = current().onComponentRemoved(event)
            }
        },
        attach = { component, listener -> component.addContainerListener(listener) },
        detach = { component, listener -> component.removeContainerListener(listener) },
    )
}

/**
 * Attaches a [ContainerListener] (`addContainerListener`/`removeContainerListener`). Requires a
 * [Container] target (the add/remove pair lives on `java.awt.Container`).
 *
 * @see java.awt.Container.addContainerListener
 */
public fun SwingModifier.containerListener(listener: ContainerListener): SwingModifier =
    listener<Container, ContainerListener>(
        listener,
        Container::addContainerListener,
        Container::removeContainerListener,
    )

/** The lambdas [containerListener] was declared with, as one value the built listener reads. */
private class ContainerCallbacks(
    val onComponentAdded: (ContainerEvent) -> Unit,
    val onComponentRemoved: (ContainerEvent) -> Unit,
)

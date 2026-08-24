@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener

/**
 * Runs [onComponentChange] whenever the component is resized, moved, shown or hidden. Declare the
 * four separately to tell them apart.
 *
 * [onComponentChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see java.awt.Component.addComponentListener
 */
public fun SwingModifier.componentListener(onComponentChange: (ComponentEvent) -> Unit): SwingModifier =
    componentListener(
        onComponentResized = onComponentChange,
        onComponentMoved = onComponentChange,
        onComponentShown = onComponentChange,
        onComponentHidden = onComponentChange,
    )

/**
 * Runs each lambda on the change it is declared for. A change left undeclared reports nowhere.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition
 * registers nothing again.
 *
 * Declaring none at all is refused.
 *
 * @see java.awt.Component.addComponentListener
 */
public fun SwingModifier.componentListener(
    onComponentResized: (ComponentEvent) -> Unit = UNDECLARED,
    onComponentMoved: (ComponentEvent) -> Unit = UNDECLARED,
    onComponentShown: (ComponentEvent) -> Unit = UNDECLARED,
    onComponentHidden: (ComponentEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared(
        "componentListener",
        declared(onComponentResized) || declared(onComponentMoved) || declared(onComponentShown) ||
            declared(onComponentHidden),
    )
    return liveCallbackListener<Component, ComponentCallbacks, ComponentListener>(
        callback = ComponentCallbacks(onComponentResized, onComponentMoved, onComponentShown, onComponentHidden),
        adapter = { current ->
            object : ComponentListener {
                override fun componentResized(event: ComponentEvent): Unit = current().onComponentResized(event)

                override fun componentMoved(event: ComponentEvent): Unit = current().onComponentMoved(event)

                override fun componentShown(event: ComponentEvent): Unit = current().onComponentShown(event)

                override fun componentHidden(event: ComponentEvent): Unit = current().onComponentHidden(event)
            }
        },
        attach = { component, listener -> component.addComponentListener(listener) },
        detach = { component, listener -> component.removeComponentListener(listener) },
    )
}

/**
 * Attaches a [ComponentListener] (`addComponentListener`/`removeComponentListener`).
 *
 * @see java.awt.Component.addComponentListener
 */
public fun SwingModifier.componentListener(listener: ComponentListener): SwingModifier =
    listener<Component, ComponentListener>(
        listener,
        Component::addComponentListener,
        Component::removeComponentListener,
    )

/** The lambdas [componentListener] was declared with, as one value the built listener reads. */
private class ComponentCallbacks(
    val onComponentResized: (ComponentEvent) -> Unit,
    val onComponentMoved: (ComponentEvent) -> Unit,
    val onComponentShown: (ComponentEvent) -> Unit,
    val onComponentHidden: (ComponentEvent) -> Unit,
)

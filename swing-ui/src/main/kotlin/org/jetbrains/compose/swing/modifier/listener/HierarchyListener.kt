@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener

/**
 * Runs [onHierarchyChange] when the component's place in the hierarchy changes - it is handed to a
 * parent, loses one, or starts or stops being on screen. The event's change flags say which.
 *
 * [onHierarchyChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see java.awt.Component.addHierarchyListener
 */
public fun SwingModifier.hierarchyListener(onHierarchyChange: (HierarchyEvent) -> Unit): SwingModifier =
    listener<Component, (HierarchyEvent) -> Unit, HierarchyListener>(
        callback = onHierarchyChange,
        adapter = { current -> HierarchyListener { event -> current()(event) } },
        attach = { component, listener -> component.addHierarchyListener(listener) },
        detach = { component, listener -> component.removeHierarchyListener(listener) },
    )

/**
 * Attaches a [HierarchyListener] (`addHierarchyListener`/`removeHierarchyListener`).
 *
 * @see java.awt.Component.addHierarchyListener
 */
public fun SwingModifier.hierarchyListener(listener: HierarchyListener): SwingModifier =
    listener<Component, HierarchyListener>(
        listener,
        Component::addHierarchyListener,
        Component::removeHierarchyListener,
    )

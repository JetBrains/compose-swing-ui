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
 * @param onHierarchyChange receives the event; its `changeFlags` hold the `HierarchyEvent.*_CHANGED`
 *   bits, and `changed` is the component the change happened to - this component itself or one of its
 *   ancestors.
 * @return this chain with the hierarchy callback declared on it.
 * @see java.awt.Component.addHierarchyListener
 */
public fun SwingModifier.hierarchyListener(onHierarchyChange: (HierarchyEvent) -> Unit): SwingModifier =
    listener(onHierarchyChange, HIERARCHY_CALLBACKS)

/**
 * Attaches a [HierarchyListener] (`addHierarchyListener`/`removeHierarchyListener`).
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the hierarchy listener declared on it.
 * @see java.awt.Component.addHierarchyListener
 */
public fun SwingModifier.hierarchyListener(listener: HierarchyListener): SwingModifier = listener(listener, HIERARCHY)

private val HIERARCHY =
    ListenerRegistration<Component, HierarchyListener>(
        name = "hierarchyListener",
        Component::addHierarchyListener,
        Component::removeHierarchyListener,
    )

private val HIERARCHY_CALLBACKS =
    CallbackRegistration<Component, (HierarchyEvent) -> Unit, HierarchyListener>(
        adapter = { current -> HierarchyListener { event -> current()(event) } },
        registration = HIERARCHY,
    )

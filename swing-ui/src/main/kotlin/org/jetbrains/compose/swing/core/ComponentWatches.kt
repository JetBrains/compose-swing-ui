package org.jetbrains.compose.swing.core

import kotlinx.coroutines.DisposableHandle
import java.awt.Component
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.beans.PropertyChangeListener

/**
 * Runs [block] on every [HierarchyEvent] fired for [component] whose change flags intersect
 * [changeFlags], so which flags answer where a component stands is named at the call site rather than
 * written into a listener of its own.
 *
 * The listener it installs is anonymous, so this serves a watch that only needs the callback. A watch
 * whose listener must be found again on the component - to be asked what it holds, rather than only to
 * run - installs a named one instead.
 *
 * @return a [DisposableHandle] that removes the listener. Disposing is idempotent.
 */
internal fun onPlaceChanged(
    component: Component,
    changeFlags: Long,
    block: () -> Unit,
): DisposableHandle {
    val listener =
        HierarchyListener { event ->
            if (event.changeFlags and changeFlags != 0L) block()
        }
    component.addHierarchyListener(listener)
    return DisposableHandle { component.removeHierarchyListener(listener) }
}

/**
 * Runs [block] whenever [component] fires a change to its [property] bound property.
 *
 * @return a [DisposableHandle] that removes the listener. Disposing is idempotent.
 */
internal fun onPropertyChanged(
    component: Component,
    property: String,
    block: () -> Unit,
): DisposableHandle {
    val listener = PropertyChangeListener { block() }
    component.addPropertyChangeListener(property, listener)
    return DisposableHandle { component.removePropertyChangeListener(property, listener) }
}

package org.jetbrains.compose.swing.modifier.interaction

import java.awt.Component
import java.awt.event.HierarchyListener

/**
 * The first moment a component is on screen, as something to hand work to.
 *
 * A component is declared long before its window is realized, so anything that needs the component to
 * be showing - focus, which has no peer to grant it until then, a popup, which has nowhere to anchor
 * until then - cannot be done at the moment it is declared. This runs that work at the first moment the
 * component is showing, whether that is already the case or a hierarchy report away.
 *
 * One wait is in flight at a time: starting another, or canceling, ends the one before it.
 */
internal class FirstShowing {
    // The component the pending listener is registered on, or null while no wait is in flight.
    private var target: Component? = null

    private var pending: HierarchyListener? = null

    /**
     * Runs [action] the first moment [component] is showing - now if it already is, and otherwise on the
     * hierarchy's first report that it is. Cancels a wait already in flight.
     *
     * The component is passed per call rather than held for the object's lifetime because a
     * [org.jetbrains.compose.swing.modifier.SwingModifier.Node]'s component is only its own between
     * attach and detach, and a reused node meets a different one.
     */
    fun await(
        component: Component,
        action: () -> Unit,
    ) {
        cancel()
        if (component.isShowing) {
            action()
            return
        }
        val listener =
            showingChangeListener {
                // A showing change also reports a hide, so the component itself decides which report this
                // wait was after.
                if (component.isShowing) {
                    cancel()
                    action()
                }
            }
        component.addHierarchyListener(listener)
        target = component
        pending = listener
    }

    /** Ends the wait in flight without running its action. Idempotent. */
    fun cancel() {
        pending?.let { target?.removeHierarchyListener(it) }
        pending = null
        target = null
    }
}

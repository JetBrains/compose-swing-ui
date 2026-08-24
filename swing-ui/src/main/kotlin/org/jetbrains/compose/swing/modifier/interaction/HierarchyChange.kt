package org.jetbrains.compose.swing.modifier.interaction

import java.awt.Component
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener

// A component is declared before it is anywhere: the applier runs a node's update block between its
// top-down and bottom-up passes, so a wrapper reading its own parent while declaring finds none. What
// the hierarchy reports afterward is how a wrapper hears where it ended up. The listener built here
// answers one such question, so a caller states which change it is waiting for rather than filtering
// an event's flags itself.

/**
 * Reports [onShowingChanged] whenever the component starts or stops being on screen - with the component
 * itself, since a listener outlives no particular one.
 *
 * Both directions are reported, so a caller after one of them reads `isShowing` to tell which this is.
 */
internal fun showingChangeListener(onShowingChanged: (Component) -> Unit): HierarchyListener =
    HierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
            onShowingChanged(event.component)
        }
    }

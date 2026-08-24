package org.jetbrains.compose.swing.modifier.interaction

import java.awt.Component
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener

// A component is declared before it is anywhere: the applier runs a node's update block between its
// top-down and bottom-up passes, so a wrapper reading its own parent while declaring finds none. What
// the hierarchy reports afterward is how a wrapper hears where it ended up. The listeners built here
// each answer one such question, so a caller states which change it is waiting for rather than
// filtering an event's flags itself.

/**
 * Reports [onParentChanged] whenever the component is handed to a parent, loses one, or is handed from
 * one to another - with the component itself, since a listener outlives no particular one.
 *
 * This is the earliest a wrapper can hear that it has a place in the tree. It needs no peer, so a
 * component added to a container standing in no window is reported just the same - which `addNotify`,
 * waiting on displayability, would not do.
 *
 * Attach it through
 * [hierarchyListener][org.jetbrains.compose.swing.modifier.listener.hierarchyListener], and hold the
 * instance across compositions - `remember {}` - since the attachment is keyed by identity.
 */
internal fun parentChangeListener(onParentChanged: (Component) -> Unit): HierarchyListener =
    HierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() != 0L) {
            onParentChanged(event.component)
        }
    }

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

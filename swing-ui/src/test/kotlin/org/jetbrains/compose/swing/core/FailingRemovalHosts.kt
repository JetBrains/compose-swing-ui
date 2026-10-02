package org.jetbrains.compose.swing.core

import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingComponentNodeListener
import java.awt.Component
import java.awt.event.HierarchyListener
import java.awt.event.WindowListener
import java.beans.PropertyChangeListener
import javax.swing.JFrame
import javax.swing.JPanel

/**
 * A content host whose teardown steps throw while armed: a child's removal and a hierarchy listener's removal,
 * each after the removal itself has happened. It also counts the hierarchy listeners removed and the nodes
 * detached from it.
 */
internal class FailingRemovalPanel(
    var listenerRemoval: Throwable? = null,
    var childRemoval: Throwable? = null,
) : JPanel(),
    SwingComponentNodeListener {
    var listenerRemovals = 0
        private set
    var detachments = 0
        private set

    override fun onAttached(node: SwingComponentNode<*>) = Unit

    override fun onDetached(node: SwingComponentNode<*>) {
        detachments++
    }

    override fun remove(component: Component) {
        super.remove(component)
        childRemoval?.let { throw it }
    }

    override fun removeHierarchyListener(listener: HierarchyListener) {
        super.removeHierarchyListener(listener)
        listenerRemovals++
        listenerRemoval?.let { throw it }
    }
}

/** A window whose listener removals throw while armed, each after the removal itself has happened. */
internal class FailingRemovalFrame(
    var windowListenerRemoval: Throwable? = null,
    var propertyListenerRemoval: Throwable? = null,
) : JFrame() {
    override fun removeWindowListener(listener: WindowListener?) {
        super.removeWindowListener(listener)
        windowListenerRemoval?.let { throw it }
    }

    override fun removePropertyChangeListener(
        propertyName: String?,
        listener: PropertyChangeListener?,
    ) {
        super.removePropertyChangeListener(propertyName, listener)
        propertyListenerRemoval?.let { throw it }
    }
}

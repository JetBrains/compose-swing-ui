package org.jetbrains.compose.swing.node

import androidx.compose.runtime.AbstractApplier
import java.awt.Component
import java.awt.Container
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPopupMenu

/**
 * Applier for the menu tree: `JMenuBar`/`JMenu`/`JPopupMenu` containers and `JMenuItem`/`JSeparator`
 * leaves, placed by index. Every container a change pass touches is revalidated and repainted once,
 * in [onEndChanges].
 *
 * The root is a [JMenuBar] for a window menu bar, or a [JPopupMenu] for a context menu.
 *
 * @see org.jetbrains.compose.swing.node.MenuNode
 */
@PublishedApi
internal class MenuApplier(
    root: JComponent,
) : AbstractApplier<SwingNodeHolder<*>>(SwingNodeHolder(root)) {
    private val dirtyContainers: MutableSet<Container> =
        Collections.newSetFromMap(IdentityHashMap())

    /**
     * Menu nodes attach to their container on the bottom-up pass (see [insertBottomUp]); this pass
     * has nothing to do.
     */
    override fun insertTopDown(
        index: Int,
        instance: SwingNodeHolder<*>,
    ) = Unit

    override fun insertBottomUp(
        index: Int,
        instance: SwingNodeHolder<*>,
    ) {
        val container = menuContainer("add menu child ${instance.component}")
        container.add(instance.component, index)
        dirtyContainers += container
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        val container = menuContainer("remove menu children")
        repeat(count) { container.remove(index) }
        dirtyContainers += container
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        val container = menuContainer("move menu children")
        if (from == to) return

        val moved = ArrayList<Component>(count)
        repeat(count) {
            moved += container.getComponent(from)
            container.remove(from)
        }
        // Removing `count` items at `from` shifts indices above `from` down by `count`; mirrors
        // SwingApplier.move's index math.
        val insertIndex = if (from > to) to else to - count
        moved.forEachIndexed { offset, component ->
            container.add(component, insertIndex + offset)
        }
        dirtyContainers += container
    }

    override fun onClear() {
        val rootMenu = root.component
        removeAllChildren(rootMenu)
        (rootMenu as? Container)?.let { dirtyContainers += it }
    }

    override fun onEndChanges() {
        super.onEndChanges()
        for (container in dirtyContainers) {
            container.revalidate()
            // repaint() is load-bearing for remove/removeAll: Container.remove only calls
            // invalidateIfValid() and never repaints the vacated region, so without it a removed
            // child's pixels linger. Relayout is already covered by Component.reshape.
            container.repaint()
        }
        dirtyContainers.clear()
    }

    /**
     * The current node as a menu container that accepts `add`/`remove(index)`. A `JMenu`'s children
     * live in its popup; `JMenuBar` and `JPopupMenu` are used as-is.
     */
    private fun menuContainer(action: String): Container =
        when (val node = current.component) {
            is JMenu -> node.popupMenu
            is JMenuBar -> node
            is JPopupMenu -> node
            else -> error("Current menu node $node is not a menu container, cannot $action")
        }

    private fun removeAllChildren(node: Component) {
        when (node) {
            is JMenu -> node.removeAll()
            is JMenuBar -> node.removeAll()
            is JPopupMenu -> node.removeAll()
            else -> error("Cannot clear children of menu node $node")
        }
    }
}

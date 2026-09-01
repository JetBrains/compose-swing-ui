package org.jetbrains.compose.swing.node

import androidx.compose.runtime.AbstractApplier
import org.jetbrains.compose.swing.util.DeferredAction
import org.jetbrains.compose.swing.util.fastForEach
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPopupMenu

/**
 * Applier for the menu tree: `JMenuBar`/`JMenu`/`JPopupMenu` containers and `JMenuItem`/`JSeparator`
 * leaves, placed by index. Every container a change pass touches is revalidated and repainted once: the
 * applier hands that walk to [ComponentUpdateBatch]. Every popup of the tree that is on screen is packed
 * in [onEndChanges] and once more on the event-queue turn that follows, so a menu open while the pass ran
 * takes the size of what it shows now.
 *
 * Each node keeps its [SwingNodeHolder.children] in composition order, which is the index space the
 * runtime addresses. A parked child stands in that list with its component already detached - the
 * runtime keeps a deactivated group's place while the container no longer holds the item - so the
 * position handed to a container is counted over the siblings actually attached, and a remove or a
 * move addresses each child's component by identity rather than by container index.
 *
 * The root is a [JMenuBar] for a window menu bar, or a [JPopupMenu] for a context menu.
 *
 * @see org.jetbrains.compose.swing.node.MenuNode
 */
@PublishedApi
internal class MenuApplier(
    root: SwingNodeHolder<JComponent>,
) : AbstractApplier<SwingNodeHolder<*>>(root) {
    /** The bookkeeping for the batch of component updates in flight, read off the root like any node. */
    private val batch = root.requireOwner().updateBatch

    /**
     * Packs the showing popups once more, on the turn of the event queue after the one a change pass
     * was applied in: the runtime dispatches a parked node's [SwingNodeHolder.onDeactivate] - which
     * detaches its component - after [onEndChanges], so a popup packed there still holds the parked
     * item's space.
     */
    private val deferredPack = DeferredAction { this.root.packShowingPopups() }

    /**
     * Attaches the node to the composition its parent stands in. This MUST happen on the top-down pass -
     * see [SwingCompositionOwner]. A menu node takes its place in its container on the bottom-up pass.
     */
    override fun insertTopDown(
        index: Int,
        instance: SwingNodeHolder<*>,
    ) {
        instance.attachedTo(current.owner)
    }

    override fun insertBottomUp(
        index: Int,
        instance: SwingNodeHolder<*>,
    ) {
        val parent = current
        val container = parent.menuContainer("add menu child ${instance.component}")
        batch.holdForChildSettle(parent)
        container.add(instance.component, parent.attachedSiblingsBefore(index))
        parent.children.add(index, instance)
        batch.markChanged(container)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        val parent = current
        val container = parent.menuContainer("remove menu children")
        batch.holdForChildSettle(parent)
        // Each child leaves by component identity: a parked child's component is already detached, so
        // the container holds nothing at that child's composition index, and `Container.remove(Component)`
        // on a detached component is a no-op.
        parent.removeChildRun(index, count) { container.remove(it.component) }
        batch.markChanged(container)
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        if (from == to) return
        val parent = current
        val container = parent.menuContainer("move menu children")
        batch.holdForChildSettle(parent)

        parent.moveChildRun(
            from,
            to,
            count,
            detach = { container.remove(it.component) },
            place = { holder, index -> container.add(holder.component, parent.attachedSiblingsBefore(index)) },
        )
        batch.markChanged(container)
    }

    override fun onClear() {
        val rootMenu = root.component
        removeAllChildren(rootMenu)
        root.children.clear()
        (rootMenu as? Container)?.let { batch.markChanged(it) }
    }

    override fun onBeginChanges() {
        super.onBeginChanges()
        batch.begin()
    }

    override fun onEndChanges() {
        super.onEndChanges()
        batch.end {}
        // Packed after every pass, not only after container changes: an update block that lengthens an
        // item's text touches no container.
        root.packShowingPopups()
        deferredPack.schedule()
    }

    /**
     * Packs every popup of this subtree that is on screen: a realized popup keeps the size it was shown
     * with, so items added, removed or resized while it is open would otherwise be clipped or leave a
     * blank strip. Packing a popup that is not on screen is already a no-op, so the visibility check
     * only spares the walk.
     */
    private fun SwingNodeHolder<*>.packShowingPopups() {
        when (val node = component) {
            is JPopupMenu -> if (node.isVisible) node.pack()
            is JMenu -> node.popupMenu.let { if (it.isVisible) it.pack() }
            else -> Unit
        }
        children.fastForEach { it.packShowingPopups() }
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

/**
 * The Swing container this node holds its menu children in: a `JMenu`'s children live in its popup;
 * `JMenuBar` and `JPopupMenu` are used as-is.
 *
 * @param action what the container is wanted for, named where the node is no menu container at all.
 */
private fun SwingNodeHolder<*>.menuContainer(action: String): Container =
    when (val node = component) {
        is JMenu -> node.popupMenu
        is JMenuBar -> node
        is JPopupMenu -> node
        else -> error("Current menu node $node is not a menu container, cannot $action")
    }

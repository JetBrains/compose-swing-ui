package org.jetbrains.compose.swing.node

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import java.awt.Component
import java.awt.Container
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.RootPaneContainer

/**
 * The [androidx.compose.runtime.Applier] that [org.jetbrains.compose.swing.node.SwingNode] emits into, mutating
 * the Swing component tree as the composition changes.
 *
 * Construct one over a root [Container] and a [SnapshotStateObserver] owned by the surrounding
 * composition, and hand it to a `Composition` to host Compose-Swing content at the applier level; the
 * everyday entry points are the `setContent` functions, which build one internally and dispose the
 * observer with the composition. See `docs/CUSTOM-COMPONENTS.md` and `docs/ARCHITECTURE.md`.
 *
 * Placement of each child:
 * - With its declared [SwingNodeHolder.constraint] when non-null (e.g. a `BorderLayout` region),
 *   otherwise by index. The constraint comes from the child's parent via [LocalSwingConstraint].
 * - A child carrying a [SwingNodeHolder.slotAttachment] is installed into its host through that
 *   attachment's dedicated Swing setter and uninstalled the same way on removal so the host slot is
 *   released.
 *
 * Every container mutated during a change pass is revalidated and repainted once in [onEndChanges].
 *
 * Internal runtime type; not public API.
 *
 * @see org.jetbrains.compose.swing.node.SwingNode
 */
@PublishedApi
internal class SwingApplier internal constructor(
    root: Container,
    private val ownerObserver: SnapshotStateObserver,
) : AbstractApplier<SwingNodeHolder<*>>(SwingNodeHolder(root)) {
    /** Containers touched during the current change pass; revalidated in [onEndChanges]. */
    private val dirtyContainers: MutableSet<Container> =
        Collections.newSetFromMap(IdentityHashMap())

    private fun currentContainer(action: String): Container =
        current.component as? Container
            ?: error("Current node ${current.component} is not a Container, cannot $action")

    /**
     * The child of this node holding [component], or `null` where it holds none.
     *
     * Ordinary children are addressed through the AWT component array, which is what the host actually
     * holds, and the holder is found from the component rather than from the index it was recorded at.
     * The two are maintained together and normally agree; where a container has been changed behind the
     * applier they do not, and following the component keeps a later change landing on the child the host
     * really has instead of compounding the difference. The search is over one node's children, so it is
     * bounded by how many a single container was given.
     */
    private fun SwingNodeHolder<*>.childFor(component: Component): SwingNodeHolder<*>? =
        children.firstOrNull { it.component === component }

    /**
     * The container that actually holds a host's indexed children. A root-pane container such as
     * `JInternalFrame` forwards `add` to its content pane while still reporting its own component array
     * from `getComponent`/`remove(int)`, so every index-addressed operation goes through the content
     * pane to address the same children `add` created.
     */
    private val Container.childHost: Container
        get() = (this as? RootPaneContainer)?.contentPane ?: this

    override fun insertTopDown(
        index: Int,
        instance: SwingNodeHolder<*>,
    ) {
        // Stamp the owner's shared snapshot observer onto the node here, on the top-down pass. This MUST
        // happen on the down pass: a node's own update changes - which copy this observer onto a
        // snapshot-observing component such as Canvas - run between the top-down and bottom-up passes, so
        // a stamp deferred to insertBottomUp would not yet be visible when the node reads it, leaving that
        // component permanently unobserved (and a Canvas blank). The actual Swing attachment is still done
        // bottom-up (see insertBottomUp).
        instance.ownerObserver = ownerObserver
    }

    override fun insertBottomUp(
        index: Int,
        instance: SwingNodeHolder<*>,
    ) {
        val parent = current
        val container = currentContainer("add child ${instance.component}")
        // The owner's shared snapshot observer was already stamped onto this node on the top-down pass
        // (see insertTopDown); here we only perform the Swing attachment.
        val attachment = instance.slotAttachment
        val fillsSlot = attachment != null
        check(parent.children.isEmpty() || parent.childrenFillSlots == fillsSlot) {
            val held = if (parent.childrenFillSlots) "slot-attached children" else "children added by index"
            val arriving = if (fillsSlot) "slot-attached child" else "child added by index"
            "${container.javaClass.simpleName} already holds $held, so a $arriving cannot join them: a node's " +
                "children are one index space, and the two kinds are reached through different Swing calls. " +
                "Give the slot its own host node."
        }
        parent.childrenFillSlots = fillsSlot
        if (attachment != null) {
            // This node fills a single-occupancy slot of `container` reached through a dedicated Swing
            // setter (e.g. a JScrollPane region via setViewportView/setRowHeaderView/...), not the
            // generic Container.add. Install it through the attachment, capture the returned uninstall
            // on the holder, and record the holder in this node's composition-ordered child list so
            // remove/move can address it by index and release the host slot. Mark the container dirty so
            // the new content gets laid out.
            instance.slotUninstall = attachment.install(container, instance.component, index)
            parent.children.add(index, instance)
            dirtyContainers += container
            return
        }
        val constraint = instance.constraint
        // Always place the component at the composition `index` in the AWT component array, whether
        // or not it carries a layout constraint. `Container.add(Component, Object)` IGNORES the array
        // index and appends, while a constrained layout (e.g. BorderLayout) stores the component by
        // its region - so using the 2-arg form for constrained children would desync the
        // component-array order from the composition order, and the later index-based remove/move
        // (which address the AWT array) would then hit the wrong component. The 3-arg
        // `add(Component, Object, int)` inserts at the given array index AND applies the constraint,
        // keeping array order == composition order for every child.
        val childHost = container.childHost
        if (constraint != null) {
            childHost.add(instance.component, constraint, index)
        } else {
            childHost.add(instance.component, index)
        }
        parent.children.add(index, instance)
        dirtyContainers += container
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        val parent = current
        val container = currentContainer("remove children")
        if (parent.childrenFillSlots) {
            // A slot-hosting container: its children were installed through their slot attachments and
            // are not direct AWT-array entries, so address them by composition index in the child list
            // and run each holder's uninstall to release the host slot (e.g. clear a JScrollPane
            // region). Iterate the fixed sub-list and clear it in one shot.
            val removed = parent.children.subList(index, index + count)
            for (holder in removed) {
                holder.slotUninstall?.invoke()
                holder.slotUninstall = null
            }
            removed.clear()
            dirtyContainers += container
            return
        }
        // Ordinary children are the AWT array's own entries, so the component the host holds at `index`
        // is the one to drop, and its holder is found from it (see childFor). `index` stays put because
        // each removal shifts the next child down into its place.
        val childHost = container.childHost
        repeat(count) {
            val component = childHost.getComponent(index)
            childHost.remove(index)
            parent.children.remove(parent.childFor(component))
        }
        dirtyContainers += container
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        val parent = current
        val container = currentContainer("move children")
        if (from == to) return

        val children = parent.children
        if (parent.childrenFillSlots) {
            // Reorder slot children purely in the composition-order list: the host owns each
            // component's physical attachment (a JScrollPane region's position is fixed by its setter,
            // not by sibling order), so no Swing re-attachment is needed. Detach the run from the list
            // and re-insert it at the mirrored target, matching the index math below.
            val moved = ArrayList(children.subList(from, from + count))
            children.subList(from, from + count).clear()
            val targetBase = if (from > to) to else to - count
            children.addAll(targetBase, moved)
            return
        }

        // Ordinary children: detach the moved run from the AWT array, which holds them, so it can be
        // re-inserted at the mirrored target. Each component's holder comes from the component itself
        // (see childFor), so the run detached from the child list is the run the host actually gave up.
        val childHost = container.childHost
        val moved = ArrayList<SwingNodeHolder<*>>(count)
        repeat(count) {
            val component = childHost.getComponent(from)
            childHost.remove(from)
            parent.childFor(component)?.let { holder ->
                children.remove(holder)
                moved += holder
            }
        }

        // After removing `count` items starting at `from`, indices above `from` shifted down by
        // `count`. Mirror Compose HTML DomNodeWrapper.move index math.
        val targetBase = if (from > to) to else to - count
        // Re-insert each moved component at its exact, sequential array index (`targetBase + offset`)
        // so the AWT component-array order stays aligned with the composition order. Every add
        // carries an explicit index: the 3-arg form (index + constraint) for constrained children so
        // the layout region is restored, the 2-arg form for unconstrained children. Because each add
        // specifies its own index, no running cursor is needed and the run lands contiguously
        // regardless of constrained/unconstrained mix. The constraint is read off the holder now
        // rather than remembered at insert, so a child whose constraint changed since keeps the
        // current one.
        children.addAll(targetBase, moved)
        moved.forEachIndexed { offset, holder ->
            val constraint = holder.constraint
            val targetIndex = targetBase + offset
            if (constraint != null) {
                childHost.add(holder.component, constraint, targetIndex)
            } else {
                childHost.add(holder.component, targetIndex)
            }
        }
        dirtyContainers += container
    }

    override fun onClear() {
        // Only ever runs on the ROOT container, which is user-supplied and never slot-attached. The
        // children are discarded through the same child host that added them, so a root-pane root loses
        // its composed content and keeps its root pane. `removeAll()` there takes the whole composed
        // subtree with it, including any slot-hosting descendants (a JScrollPane's viewport/headers/
        // corners go away with their JScrollPane). That is the correct dispose path: the subtree is torn
        // down wholesale, so there is no host slot left to release. Clearing the root's own child lists
        // drops its references to that subtree; every deeper node's lists go with the node.
        val container = root.component as? Container ?: return
        container.childHost.removeAll()
        root.children.clear()
        root.childrenFillSlots = false
        dirtyContainers += container
    }

    override fun onEndChanges() {
        super.onEndChanges()
        for (container in dirtyContainers) {
            container.revalidate()
            // repaint() is load-bearing for the remove/removeAll case: Container.remove only calls
            // invalidateIfValid() and never repaints the vacated region, so without this the removed
            // child's pixels would linger. (The relayout case is already covered by Component.reshape.)
            container.repaint()
        }
        dirtyContainers.clear()
    }
}

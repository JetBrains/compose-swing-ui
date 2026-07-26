package org.jetbrains.compose.swing.core

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import java.awt.Component
import java.awt.Container
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.RootPaneContainer

/**
 * The [androidx.compose.runtime.Applier] that [org.jetbrains.compose.swing.SwingNode] emits into, mutating
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
 * @see org.jetbrains.compose.swing.SwingNode
 */
@PublishedApi
internal class SwingApplier internal constructor(
    root: Container,
    private val ownerObserver: SnapshotStateObserver,
) : AbstractApplier<SwingNodeHolder<*>>(SwingNodeHolder(root)) {
    /** Containers touched during the current change pass; revalidated in [onEndChanges]. */
    private val dirtyContainers: MutableSet<Container> =
        Collections.newSetFromMap(IdentityHashMap())

    /**
     * The holder behind each currently-added component, so [move] can re-apply the component's layout
     * constraint (Swing drops it on `remove`). The holder rather than the constraint value is cached
     * because a node's constraint can change while it is attached, and a holder always reports the
     * current one.
     */
    private val holderByComponent: MutableMap<Component, SwingNodeHolder<*>> = IdentityHashMap()

    /**
     * Slot-attached child holders (see [SwingNodeHolder.slotAttachment]) per host container, in
     * composition order, so [remove]/[move] can address them by index and run each holder's
     * [SwingNodeHolder.slotUninstall] to release the host slot. The position in this list is the
     * index handed to [SlotAttachment.install] so an ordered host can place the component
     * (e.g. `JTabbedPane.insertTab(..., index)`).
     */
    private val slotHoldersByContainer: MutableMap<Container, MutableList<SwingNodeHolder<*>>> =
        IdentityHashMap()

    private fun currentContainer(action: String): Container =
        current.component as? Container
            ?: error("Current node ${current.component} is not a Container, cannot $action")

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
        val container = currentContainer("add child ${instance.component}")
        // The owner's shared snapshot observer was already stamped onto this node on the top-down pass
        // (see insertTopDown); here we only perform the Swing attachment.
        val attachment = instance.slotAttachment
        if (attachment != null) {
            // This node fills a single-occupancy slot of `container` reached through a dedicated Swing
            // setter (e.g. a JScrollPane region via setViewportView/setRowHeaderView/...), not the
            // generic Container.add. Install it through the attachment, capture the returned uninstall
            // on the holder, and record the holder in this container's composition-ordered slot list
            // so remove/move can address it by index and release the host slot. Mark the container
            // dirty so the new content gets laid out.
            instance.slotUninstall = attachment.install(container, instance.component, index)
            slotHoldersByContainer.getOrPut(container) { ArrayList() }.add(index, instance)
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
        holderByComponent[instance.component] = instance
        dirtyContainers += container
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        val container = currentContainer("remove children")
        val slotHolders = slotHoldersByContainer[container]
        if (slotHolders != null) {
            // A slot-hosting container: its children were installed through their slot attachments and
            // are not direct AWT-array entries, so address them by composition index in the slot list
            // and run each holder's uninstall to release the host slot (e.g. clear a JScrollPane
            // region). Iterate the fixed sub-list and clear it in one shot.
            val removed = slotHolders.subList(index, index + count)
            for (holder in removed) {
                holder.slotUninstall?.invoke()
                holder.slotUninstall = null
            }
            removed.clear()
            dirtyContainers += container
            return
        }
        // Ordinary children mirror the AWT array. `cursor` stays at `index` because each removal
        // shifts the next child down into the slot.
        val childHost = container.childHost
        repeat(count) {
            val component = childHost.getComponent(index)
            childHost.remove(index)
            holderByComponent.remove(component)
        }
        dirtyContainers += container
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        val container = currentContainer("move children")
        if (from == to) return

        val slotHolders = slotHoldersByContainer[container]
        if (slotHolders != null) {
            // Reorder slot children purely in the composition-order list: the host owns each
            // component's physical attachment (a JScrollPane region's position is fixed by its setter,
            // not by sibling order), so no Swing re-attachment is needed. Detach the run from the list
            // and re-insert it at the mirrored target, matching the index math below.
            val moved = ArrayList(slotHolders.subList(from, from + count))
            slotHolders.subList(from, from + count).clear()
            val targetBase = if (from > to) to else to - count
            slotHolders.addAll(targetBase, moved)
            return
        }

        // Ordinary children: detach the moved run so it can be re-inserted at the mirrored target.
        val childHost = container.childHost
        val moved = ArrayList<Component>(count)
        repeat(count) {
            moved += childHost.getComponent(from)
            childHost.remove(from)
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
        moved.forEachIndexed { offset, component ->
            val constraint = holderByComponent[component]?.constraint
            val targetIndex = targetBase + offset
            if (constraint != null) {
                childHost.add(component, constraint, targetIndex)
            } else {
                childHost.add(component, targetIndex)
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
        // down wholesale, so there is no host slot left to release. Clearing the tracking maps drops
        // their identity references along with the subtree.
        val container = root.component as? Container ?: return
        container.childHost.removeAll()
        holderByComponent.clear()
        slotHoldersByContainer.clear()
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

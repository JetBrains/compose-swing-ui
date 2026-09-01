package org.jetbrains.compose.swing.node

import androidx.compose.runtime.AbstractApplier
import org.jetbrains.compose.swing.core.trace
import org.jetbrains.compose.swing.util.DeferredAction
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIndexed
import java.awt.Container
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.JLayeredPane
import javax.swing.RootPaneContainer

/**
 * The [androidx.compose.runtime.Applier] that [org.jetbrains.compose.swing.node.SwingNode] emits into, mutating
 * the Swing component tree as the composition changes.
 *
 * Construct one over a root node already attached to the surrounding composition, and hand it to a
 * `Composition` to host Compose-Swing content at the applier level; the everyday entry points are the
 * `setContent` functions, which build both internally. See `docs/CUSTOM-COMPONENTS.md` and
 * `docs/ARCHITECTURE.md`.
 *
 * Placement of each child:
 * - Every host node declares how it holds its children, as its [SwingNodeHolder.childPlacement]. Under
 *   [ChildPlacement.Indexed] children are added to the container by index and no child may name a region
 *   of the host; under [ChildPlacement.Slots] and [ChildPlacement.OrderedSlots] every child names one,
 *   and a child that does not match the host's declaration is refused, naming the host and the calls that
 *   would place it.
 * - A child the composition relocates - `movableContent` invoked under another parent - reaches its new
 *   host before its own modifier chain has run there, so it is taken into that host's children as it
 *   arrives and attached once the change pass has settled, under the placement its chain names at the
 *   host it is at now. That is also where such a child is held to the host's declaration.
 * - An indexed child is added with its declared [SwingNodeHolder.constraint] when non-null (e.g. a
 *   `BorderLayout` region), otherwise by index alone. The constraint is the one the child's own modifier
 *   chain declares.
 * - A child naming a region carries the [SwingNodeHolder.declaredSlot] that fills it. The child
 *   is installed into its host through that attachment's dedicated Swing setter and uninstalled the same
 *   way on removal, so the region is released. A child whose chain comes to name another region is moved
 *   between the two the same way, and one that stops naming a region is released from the one it fills
 *   and then refused, the way a child arriving at a region-holding host without one is. A
 *   [ChildPlacement.Slots] host shows one component per region, which is checked once the change pass has
 *   settled - a pass that replaces the occupant of a region need not take the outgoing child out before
 *   the incoming one arrives.
 * - The composition's own top-level children are the one case no composable declares: a [rootSlot]
 *   installs each of them into a region of [root], reached through a setter of its owner rather than by
 *   `Container.add`, and [root] shows the one top-level component that region holds - checked the way
 *   every other single-occupancy host is.
 *
 * Every container mutated during a change pass is revalidated and repainted once, by [ComponentUpdateBatch].
 *
 * Internal implementation type; not public API.
 *
 * @param root the node this composition is rooted at, attached to the composition that owns it.
 * @param rootSlot installs the children composed directly under [root], which then shows the single
 *   top-level component that slot holds; `null` - the default - adds them to [root] by index.
 * @see org.jetbrains.compose.swing.node.SwingNode
 */
@PublishedApi
internal class SwingApplier internal constructor(
    root: SwingNodeHolder<Container>,
    rootSlot: SlotAttachment? = null,
) : AbstractApplier<SwingNodeHolder<*>>(root) {
    /** The bookkeeping for the batch of component updates in flight, read off the root like any node. */
    private val batch = root.requireOwner().updateBatch

    /** What the change pass in flight has said about where children go. */
    private val changes = ChangeRecord()

    /** The regions this applier's hosts hold their children in. */
    private val regions = ChildRegions(this.root, rootSlot, batch, changes)

    override fun up() {
        // A node's own update changes run while the applier is positioned at it, so leaving the node is
        // the first point in the pass at which the region its chain names this time is on the holder and
        // its host is known - the node the applier returns to. Nothing is moved here: the pass may be
        // mid-swap, and a node that arrived this pass is left before it is installed, so both would look
        // like a component in the wrong region. What the pass leaves behind is settled in onEndChanges,
        // where a host whose children all name the region they are in costs one walk of its child list.
        val node = current
        super.up()
        if (node.declaredSlot?.name != node.installedSlot?.name) changes.recordRegionRestated(current)
    }

    override fun insertTopDown(
        index: Int,
        instance: SwingNodeHolder<*>,
    ) {
        trace("insert") {
            // Attach the node to the composition its parent stands in. This MUST happen on the top-down
            // pass - see SwingCompositionOwner.
            instance.attachedTo(current.owner)
            changes.announceInsert(instance)
        }
    }

    override fun insertBottomUp(
        index: Int,
        instance: SwingNodeHolder<*>,
    ) {
        val parent = current
        val container = parent.containerFor("add child ${instance.component}")
        // Held here rather than at each of the three ways out below: a node settling against its
        // children answers for the children it ends the pass with, however each of them got there.
        batch.holdForChildSettle(parent)
        if (!changes.takeAnnouncedInsert(instance)) {
            // The composition is relocating a node composed under another parent, and hands it over
            // before its modifier chain has run for this host, so what it carries is the placement it
            // named at the host it is leaving. Take it into this node's composition-ordered child list,
            // which is what a remove or a move later in the pass addresses by index, and attach it once
            // the pass has settled and its chain has named the placement it fills here.
            parent.children.add(index, instance)
            changes.recordRelocated(parent, instance)
            return
        }
        trace("attach") {
            // The node was already attached to its composition on the top-down pass (see insertTopDown);
            // here we only perform the Swing attachment.
            val attachment = regions.attachmentOf(parent, instance)
            parent.checkPlacementOf(container, instance, fillsRegion = attachment != null)
            if (attachment != null) {
                // This node fills a named region of `container` reached through a dedicated Swing setter
                // (e.g. a JScrollPane region via setViewportView/setRowHeaderView/...), not the generic
                // Container.add. Install it through the attachment, record on the holder what installed it
                // and how the region is released again, and record the holder in this node's
                // composition-ordered child list so remove/move can address it by index and release the
                // region. Mark the container dirty so the new content gets laid out, and the host for the
                // one-child-per-region check this pass ends with.
                val slotIndex = parent.slotIndexOf(index)
                instance.installedThrough(attachment, attachment.install(container, instance.component, slotIndex))
                parent.children.add(index, instance)
                if (parent.childPlacement is ChildPlacement.Slots) changes.recordSlotFilled(parent)
                batch.markChanged(container)
                return
            }
            parent.addToHost(container, instance, index)
            parent.children.add(index, instance)
            batch.markChanged(container)
        }
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        trace("remove") {
            val parent = current
            val container = parent.containerFor("remove children")
            batch.holdForChildSettle(parent)
            if (parent.childPlacement.holdsRegions) {
                // A region-holding container: its children were installed through their slot attachments and
                // are not direct AWT-array entries, so address them by composition index in the child list
                // and run each holder's uninstall to release the host region (e.g. clear a JScrollPane
                // region).
                parent.removeChildRun(index, count) { it.releaseInstalledSlot() }
                batch.markChanged(container)
                return
            }
            // Ordinary children are the AWT array's own entries, but where in that array a host keeps each of
            // them is the host's own business: a `JLayeredPane` sorts its children by the depth each one
            // declares, so what it holds at a composition index is some other child. The composition-order
            // child list is what says which children the pass drops, and each of them leaves the host by
            // component identity, which addresses the same child however the host has arranged them.
            val childHost = container.childHost
            parent.removeChildRun(index, count) { childHost.remove(it.component) }
            batch.markChanged(container)
        }
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        val parent = current
        val container = parent.containerFor("move children")
        if (from == to) return

        trace("move") {
            batch.holdForChildSettle(parent)
            if (parent.childPlacement.holdsRegions) {
                with(regions) { parent.moveRegionChildren(container, from, to, count) }
                return
            }

            // Ordinary children are the AWT array's own entries, and each of them leaves the host by
            // component identity, which addresses the same child whichever place in that array the host
            // has given it.
            val childHost = container.childHost
            parent.moveChildRun(
                from,
                to,
                count,
                detach = { childHost.remove(it.component) },
                place = { holder, index -> parent.addToHost(container, holder, index) },
            )
            batch.markChanged(container)
        }
    }

    override fun onClear() {
        // Only ever runs on the ROOT container, which is user-supplied and never slot-attached. The
        // children are discarded through the same child host that added them, so a root-pane root loses
        // its composed content and keeps its root pane. `removeAll()` there takes the whole composed
        // subtree with it, including any slot-hosting descendants (a JScrollPane's viewport/headers/
        // corners go away with their JScrollPane). The AWT hierarchy is therefore already gone, but the
        // holders still carry the uninstall action for the region each one fills; releasing those is left
        // to SwingNodeHolder.onRelease, which the runtime calls for every node. Clearing the root's own
        // child lists drops its references to that subtree; every deeper node's lists go with the node.
        // The root's placement stays as it was declared when the content mounted: the composition is
        // emptied, not re-hosted.
        val container = root.component as? Container ?: return
        container.childHost.removeAll()
        root.children.clear()
        changes.forget()
        batch.markChanged(container)
    }

    override fun onBeginChanges() {
        super.onBeginChanges()
        batch.begin()
    }

    override fun onEndChanges() {
        super.onEndChanges()
        batch.end(regions::reconcile)
    }
}

/**
 * The regions a host's children occupy, and what one batch of updates said about them.
 *
 * [SwingApplier] translates the runtime's applier protocol; this holds the model that protocol moves.
 * A batch names arrivals, relocations and restated regions as it runs, and [reconcile] brings every
 * host it touched to what its children declare once the batch has settled.
 */
private class ChildRegions(
    private val root: SwingNodeHolder<*>,
    private val rootSlot: SlotAttachment?,
    private val batch: ComponentUpdateBatch,
    private val changes: ChangeRecord,
) {
    /** The hosts still to be held to one child per region. */
    private val regionCheck =
        DeferredRegionCheck { host ->
            if (host === this.root) this.root.checkRootShowsOneChild() else host.checkOneChildPerRegion()
        }

    /** The debug-only child-index-space walk, deferred the same turn and for the same reason as [regionCheck]. */
    private val indexSpaceCheck = DeferredAction { this.root.checkChildIndexSpace() }

    init {
        // The root is the one node no composable declares, so the code that mounts the composition says
        // how its top-level children are attached: content that mounts with a `rootSlot` has every one of
        // them installed into the single region that slot fills. Recording that as the root's own placement
        // is what has the removal and the move of such a child follow the same route as its install, and
        // what holds the composition to the one top-level child that slot shows - the same rule this
        // applier holds every other host to, checked in the same place.
        if (rootSlot != null) this.root.childPlacement = ChildPlacement.Slots(ROOT_SLOT_NAME)
    }

    /**
     * The attachment that installs [child] into [host], or `null` where the host adds it by index.
     *
     * The node's own chain answers first, and only a child of the composition root falls back to
     * [rootSlot]: the root's slot says how the composition attaches to the host that mounted it, so
     * letting it reach deeper would install a node's own children into that same host slot instead of
     * into the parent they were composed under. Reading it through one expression is what has a top-level
     * child, which names no region of its own, keep the root slot it was installed through instead of
     * looking like a child that has given its region up.
     */
    fun attachmentOf(
        host: SwingNodeHolder<*>,
        child: SwingNodeHolder<*>,
    ): SlotAttachment? = child.declaredSlot?.attachment ?: rootSlot.takeIf { host === root }

    fun reconcile() {
        try {
            // The pass has settled, so every chain has run for the host its node belongs to now and a
            // relocated child names the placement it fills here. Attaching them first is what leaves the
            // two steps below reading hosts whose children are all attached.
            for ((host, child) in changes.relocated) trace("attach") { host.attachRelocatedChild(child) }
            // A chain has named its child's region for the last time this pass, and each of these hosts
            // can be brought to what its children declare. This runs whole: the check below reads the
            // region each child is really in, and a component that arrives in a region as this runs is
            // one more child of a host the check has to answer for.
            for (host in changes.hostsWithRestatedRegions) host.moveRestatedChildren()
            // Every region of these hosts now holds what the composition declares for it. Which children
            // still count is settled once this pass has been dispatched whole - see DeferredRegionCheck.
            for (host in changes.hostsWithFilledSlots) regionCheck.hold(host)
        } finally {
            changes.forget()
        }
        regionCheck.schedule()
        if (debugValidateChildIndexSpace) indexSpaceCheck.schedule()
    }

    /**
     * Attaches [child], which the composition relocated into this host earlier in the change pass, under
     * the placement its modifier chain names here: installed into the region of this host it fills, or
     * added to this host's index space where it names none. A child whose placement this host does not
     * hold its children by is refused here, with the same account of the two a child arriving outright is
     * refused with.
     *
     * A child taken back out by the same pass has no place here to attach to, and a node that is not a
     * container is no host to attach one to.
     */
    private fun SwingNodeHolder<*>.attachRelocatedChild(child: SwingNodeHolder<*>) {
        val index = children.indexOf(child)
        if (index < 0) return
        val container = component as? Container ?: return
        val attachment = attachmentOf(this, child)
        checkPlacementOf(container, child, fillsRegion = attachment != null)
        // The child counts as attached from here on, so a pass attaching several of them lands the run
        // contiguously and in composition order; the check above is made while it still stands apart, since
        // a child that is not attached answers for no sibling's placement.
        child.awaitingAttachment = false
        if (attachment != null) {
            child.installedThrough(attachment, attachment.install(container, child.component, slotIndexOf(index)))
            if (childPlacement is ChildPlacement.Slots) changes.recordSlotFilled(this)
        } else {
            addToHost(container, child, index)
        }
        batch.markChanged(container)
    }

    /**
     * Moves every child of this host that ends the pass naming a region other than the one its component
     * is installed in: the region it fills is released through the attachment that filled it, and the
     * child installed again through the attachment it names now, at the index it is composed at.
     *
     * Releasing first is what leaves a swap of two regions' occupants correct in either declaration order:
     * an attachment releases its region for the child that installed it, so a region a sibling has already
     * taken over is left to that sibling. A child naming no region at all is released and then refused,
     * the way a child arriving at a region-holding host without one is - it would be held by the host and
     * laid out by nobody.
     *
     * A parked child is skipped: it gave its region up in [SwingNodeHolder.onDeactivate] and keeps its
     * stale [SwingNodeHolder.declaredSlot] for as long as it stands in [children], so its declared and
     * installed regions never agree again - reinstalling it here would put a component the composition no
     * longer drives back into a region a live sibling may since have taken.
     */
    private fun SwingNodeHolder<*>.moveRestatedChildren() {
        // A host that holds children is a Container, since attaching them required one; a node that is
        // not one holds none and so has no region to move a child between.
        val container = component as? Container ?: return
        batch.holdForChildSettle(this)
        children.fastForEachIndexed { index, child ->
            if (!child.attachedToHost) return@fastForEachIndexed
            if (child.declaredSlot?.name != child.installedSlot?.name) {
                child.releaseInstalledSlot()
                batch.markChanged(container)
                val attachment = attachmentOf(this, child)
                checkChildKind(container, child, fillsRegion = attachment != null)
                if (attachment != null) {
                    val slotIndex = slotIndexOf(index)
                    child.installedThrough(attachment, attachment.install(container, child.component, slotIndex))
                    if (childPlacement is ChildPlacement.Slots) changes.recordSlotFilled(this)
                }
            }
        }
    }

    /**
     * Moves the run of [count] children starting at [from] to [to], on a host that holds its children in
     * regions of its own.
     *
     * Where each region is named apiece ([ChildPlacement.Slots]) its setter is what puts a component
     * there, so nothing physical follows the order of siblings and the composition-order list - what
     * addresses a child by index on a later remove or move - is the whole of the move. Where the one
     * region holds them in the order they are composed ([ChildPlacement.OrderedSlots], a `JTabbedPane`'s
     * strip) the position within it *is* where the child is, so every moved child is released from the
     * region it fills and installed again at the position it is composed at now.
     *
     * A child whose chain gives its region up in the same pass is released and then refused, the way one
     * arriving at a region-holding host without a region is.
     */
    fun SwingNodeHolder<*>.moveRegionChildren(
        container: Container,
        from: Int,
        to: Int,
        count: Int,
    ) {
        if (childPlacement !is ChildPlacement.OrderedSlots) {
            moveChildRun(from, to, count)
            return
        }
        val host = this
        moveChildRun(
            from,
            to,
            count,
            detach = { it.releaseInstalledSlot() },
            place = { child, index ->
                val attachment = attachmentOf(host, child)
                checkChildKind(container, child, fillsRegion = attachment != null)
                if (attachment != null) {
                    val slotIndex = slotIndexOf(index)
                    child.installedThrough(attachment, attachment.install(container, child.component, slotIndex))
                }
            },
        )
        batch.markChanged(container)
    }
}

/** The name of the root slot region a composition's own top-level children fill. */
private const val ROOT_SLOT_NAME: String = "content"

/**
 * The hosts to hold to one child per region, checked by [check] on the turn of the event queue after the
 * one the change pass was applied in. Only there has a parked node's deactivation - dispatched by the
 * runtime once the changes applying it are themselves applied - actually run, so only there does a host
 * hold the children the composition means it to.
 */
private class DeferredRegionCheck(
    check: (SwingNodeHolder<*>) -> Unit,
) {
    private val hosts: MutableSet<SwingNodeHolder<*>> = Collections.newSetFromMap(IdentityHashMap())
    private val turn =
        DeferredAction {
            val pending = hosts.toList()
            hosts.clear()
            for (host in pending) check(host)
        }

    /** Records [host] as one to hold to its regions once the pass in flight has settled. */
    fun hold(host: SwingNodeHolder<*>) {
        hosts += host
    }

    /** Asks for the check on the next turn of the event queue, once for however many passes are applied in this one. */
    fun schedule() {
        if (hosts.isEmpty()) return
        turn.schedule()
    }
}

/**
 * What one change pass has said about where children go, and what [ChildRegions.reconcile] therefore
 * owes each host once that pass has settled: children to attach, regions to bring to what a chain
 * restated, hosts to hold to one child per region.
 *
 * [SwingApplier] records into this as it walks; nothing here is acted on while the pass runs, because a
 * pass reaches a host several times and only what stands at the end of it is what the composition
 * declares. A pass may hold two children in one region while it runs - a replacement is inserted before
 * the child it replaces is removed - so a host looked at too early answers for a sibling that is on its
 * way out.
 *
 * The record lasts as long as the pass does: [forget] ends it.
 */
private class ChangeRecord {
    /** The announced inserts not taken in bottom-up yet. */
    private val announced: MutableSet<SwingNodeHolder<*>> =
        Collections.newSetFromMap(IdentityHashMap())

    private val relocations: MutableList<Pair<SwingNodeHolder<*>, SwingNodeHolder<*>>> = ArrayList()

    /**
     * The hosts a child of which named a region other than the one its component is installed in. Each is
     * brought to what its children declare before any host is held to its regions, so a component moves
     * once however many times the pass declared it, and the check that follows reads the regions the
     * children are really in.
     */
    private val restatedRegionHosts: MutableSet<SwingNodeHolder<*>> =
        Collections.newSetFromMap(IdentityHashMap())

    /**
     * The [ChildPlacement.Slots] hosts a child was installed into, held to the single occupant each of
     * their regions shows - and the composition root, where the content mounted under that placement, to
     * the one top-level child its root slot shows.
     */
    private val filledSlotHosts: MutableSet<SwingNodeHolder<*>> =
        Collections.newSetFromMap(IdentityHashMap())

    /**
     * Each relocated child against the host it arrived at, in arrival order. Such a child reaches its new
     * host before its own modifier chain has run there, so it is attached once the pass has settled and
     * the placement it names is what it declares at the host it is at now.
     */
    val relocated: List<Pair<SwingNodeHolder<*>, SwingNodeHolder<*>>> get() = relocations

    /** The hosts whose children named a region other than the one they are installed in. */
    val hostsWithRestatedRegions: Set<SwingNodeHolder<*>> get() = restatedRegionHosts

    /** The hosts a child was installed into a region of. */
    val hostsWithFilledSlots: Set<SwingNodeHolder<*>> get() = filledSlotHosts

    /**
     * Announces [node] as one the composition is inserting rather than relocating.
     *
     * An inserted node is handed over top-down first and bottom-up after, a relocated one the other way
     * about, so a node arriving bottom-up that was not announced on the way down is one the pass moved. A
     * node still awaiting attachment arrived as a relocated one and stays that, however many hosts the
     * pass hands it to.
     */
    fun announceInsert(node: SwingNodeHolder<*>) {
        if (!node.awaitingAttachment) announced += node
    }

    /** Whether [node] was announced as an insert, taking the announcement as it answers. */
    fun takeAnnouncedInsert(node: SwingNodeHolder<*>): Boolean = announced.remove(node)

    /** Records [child] as relocated into [host], and owed attachment for the rest of the pass. */
    fun recordRelocated(
        host: SwingNodeHolder<*>,
        child: SwingNodeHolder<*>,
    ) {
        child.awaitingAttachment = true
        relocations += host to child
    }

    /** Records that a child of [host] named a region other than the one its component is installed in. */
    fun recordRegionRestated(host: SwingNodeHolder<*>) {
        restatedRegionHosts += host
    }

    /** Records that [host] had a child installed into one of the regions it holds. */
    fun recordSlotFilled(host: SwingNodeHolder<*>) {
        filledSlotHosts += host
    }

    /**
     * Drops what the pass recorded, so no child is left standing in a host's children as one still to be
     * attached and no host is answered for twice.
     */
    fun forget() {
        for ((_, child) in relocations) child.awaitingAttachment = false
        relocations.clear()
        announced.clear()
        restatedRegionHosts.clear()
        filledSlotHosts.clear()
    }
}

/**
 * The Swing container this node holds its children in.
 *
 * @param action what the container is wanted for, named where the node is no container at all.
 */
private fun SwingNodeHolder<*>.containerFor(action: String): Container =
    component as? Container
        ?: error("Current node $component is not a Container, cannot $action")

/**
 * The container that actually holds a host's indexed children. A root-pane container such as
 * `JInternalFrame` forwards `add` to its content pane while still reporting its own component array
 * from `getComponent`/`remove(int)`, so every index-addressed operation goes through the content pane
 * to address the same children `add` created.
 */
internal val Container.childHost: Container
    get() = (this as? RootPaneContainer)?.contentPane ?: this

/**
 * Adds [child], composed at [index], to [container]'s child host at the place the host reads a position at.
 *
 * The host is always handed a position, whether or not the child carries a layout constraint:
 * `Container.add(Component, Object)` ignores the index and appends, while a constrained layout such as
 * `BorderLayout` stores the component by its region, so the two-argument form would tell a constrained
 * child's host nothing about where the composition puts it. What the host makes of the position is its
 * own. A plain container holds its children in exactly the order given, so it is handed the place among
 * the children already attached. A `JLayeredPane` reads the position as one within the depth the
 * constraint names, so it is handed the place among the attached siblings on that depth - a count over
 * every sibling would put the child one place lower within its depth for each sibling on another depth
 * ahead of it, or at the depth's bottom once the count ran past its end.
 */
private fun SwingNodeHolder<*>.addToHost(
    container: Container,
    child: SwingNodeHolder<*>,
    index: Int,
) {
    val childHost = container.childHost
    val position =
        if (childHost is JLayeredPane) {
            attachedSiblingsOnDepthBefore(childHost, child, index)
        } else {
            attachedSiblingsBefore(index)
        }
    val constraint = child.constraint
    if (constraint != null) {
        childHost.add(child.component, constraint, position)
    } else {
        childHost.add(child.component, position)
    }
}

/**
 * The place within its depth on [pane] that [child], composed at [index], takes: the attached siblings
 * ahead of it that sit on the same depth. A child's depth is the constraint its chain declares, or else
 * the layer `JLayeredPane.getLayer` reads for it.
 */
private fun SwingNodeHolder<*>.attachedSiblingsOnDepthBefore(
    pane: JLayeredPane,
    child: SwingNodeHolder<*>,
    index: Int,
): Int {
    val depth = child.depthOn(pane)
    var attached = 0
    children.fastForEach(0 until index) { if (it.attachedToHost && it.depthOn(pane) == depth) attached++ }
    return attached
}

private fun SwingNodeHolder<*>.depthOn(pane: JLayeredPane): Int = constraint as? Int ?: pane.getLayer(component)

/**
 * The index a region's attachment is handed for the child composed at [index]: `0` where the host's regions
 * hold one child each, and the place among the attached siblings where the host holds many in order.
 */
private fun SwingNodeHolder<*>.slotIndexOf(index: Int): Int =
    if (childPlacement is ChildPlacement.Slots) 0 else attachedSiblingsBefore(index)

/** Whether a node declaring this placement holds its children in named regions rather than by index. */
internal val ChildPlacement.holdsRegions: Boolean
    get() = this != ChildPlacement.Indexed

/**
 * Holds a child arriving at this host to the placement the host declares, and to the way the children
 * already here were attached.
 *
 * @param host the container the child is arriving at, named in every refusal.
 * @param child the arriving node.
 * @param fillsRegion whether the child is installed into a named region of the host rather than added to
 *   it by index.
 */
private fun SwingNodeHolder<*>.checkPlacementOf(
    host: Container,
    child: SwingNodeHolder<*>,
    fillsRegion: Boolean,
) {
    checkChildKind(host, child, fillsRegion)
    // Every attached child here was held to this same rule as it arrived, so they agree with one another
    // and the first of them answers for them all. A holder in `children` carries the attachment that
    // installed it exactly while it is installed in a region - a removal forgets it as it drops the child,
    // and a move among siblings keeps it - so what it carries is how that child was reached. They can
    // disagree with the arriving child only where the host declared one placement, took children under it,
    // and then declared another. A child still awaiting attachment carries no such answer yet and speaks
    // for none of them; a host holding nothing else has no child to compare against.
    val held = children.firstOrNull { it.attachedToHost } ?: return
    check((held.installedSlot != null) == fillsRegion) {
        mixedChildKinds(host, child, fillsRegion)
    }
}

/**
 * Holds a child to the placement this host declares: a host that holds its children in regions of its own
 * takes only children that fill one, and a host that adds them by index only children placed that way.
 *
 * Asked of a child arriving at the host and of one already here whose chain has come to say something
 * else, which is why it answers for that child alone: the child being looked at can be one this host is
 * in the middle of moving between two of its regions, and so momentarily installed in neither.
 *
 * @param host the container the child is held by, named in every refusal.
 * @param child the node being placed.
 * @param fillsRegion whether the child is installed into a named region of the host rather than added to
 *   it by index.
 */
private fun SwingNodeHolder<*>.checkChildKind(
    host: Container,
    child: SwingNodeHolder<*>,
    fillsRegion: Boolean,
) {
    val placement = childPlacement
    if (placement.holdsRegions) {
        check(fillsRegion) { childNamesNoRegion(host, child.component, placement) }
    } else {
        check(!fillsRegion) { hostHasNoRegions(host, child) }
    }
}

/**
 * Holds the composition root to the single top-level child its root slot shows, counting the children
 * the composition drives. Called once the pass that filled the slot has been dispatched whole, for the
 * same reason [checkOneChildPerRegion] is.
 *
 * A parked child is not one of them: it gave its slot up in [onDeactivate][SwingNodeHolder] and stands in
 * [SwingNodeHolder.children] only until the composition removes it for good, so counting it would refuse
 * a root that shows exactly one child - see [SwingNodeHolder.deactivated]. [checkOneChildPerRegion] needs
 * no such term, because releasing the slot is what clears the name it counts by.
 */
internal fun SwingNodeHolder<*>.checkRootShowsOneChild() {
    val shown = children.filter { !it.deactivated }
    if (shown.size < 2) return
    error(rootSlotFilledTwice(component, shown[0].component, shown[1].component))
}

/**
 * Holds this host to one child per region. Called on a [ChildPlacement.Slots] host once the change pass
 * that filled its regions has been dispatched whole and every child it holds is installed in the region
 * its own chain names, so each child is counted against the region its component is really in.
 */
internal fun SwingNodeHolder<*>.checkOneChildPerRegion() {
    val occupants = HashMap<String, SwingNodeHolder<*>>(children.size)
    for (child in children) {
        val name = child.installedSlot?.name ?: continue
        val occupant = occupants.put(name, child)
        if (occupant != null) {
            error(regionFilledTwice(component, name, occupant.component, child.component))
        }
    }
}

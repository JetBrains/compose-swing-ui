package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.trace
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.util.DeferredAction
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIn
import org.jetbrains.compose.swing.util.fastForEachIndexed
import java.awt.Component
import java.awt.Container
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.JLayeredPane
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/**
 * The regions a host's children occupy, and what one batch of updates said about them.
 *
 * [SwingApplier] translates the runtime's applier protocol; this holds the model that protocol moves.
 * A batch names arrivals, relocations and restated regions as it runs, and [reconcile] brings every
 * host it touched to what its children declare once the batch has settled.
 */
internal class ChildRegions(
    private val root: SwingNodeHolder<*>,
    private val rootSlotPolicy: RootSlotPolicy?,
    private val batch: ComponentUpdateBatch,
    private val changes: ChangeRecord,
    private val claims: ClaimedComponents,
) {
    /**
     * The hosts still to be held to one child per region, and the indexed hosts still to be held to the
     * children added to them.
     */
    private val childCheck =
        DeferredChildCheck { host ->
            if (!host.childPlacement.holdsRegions) {
                // A composed child of an indexed host that stands in no container was displaced by a
                // sibling its container added after it.
                val displaced = host.children.fastFirstOrNull { it.displaced } ?: return@DeferredChildCheck
                // Names the call that declares the host where it fills a region, since that call is where
                // the children are composed.
                val declared = host.declaredSlot?.let { " declared through ${it.name}" }.orEmpty()
                error(
                    "A ${host.component.declaredName}$declared shows one child at a time, so a child added to it " +
                        "took out the ${displaced.component.declaredName} it held. Compose one child there, " +
                        "wrapping several in a container of their own such as a Column or a Panel.",
                )
            } else {
                if (host === this.root) rootSlotPolicy?.onSettled()
                host.checkOneChildPerRegion()
            }
        }

    /**
     * The debug-only child-index-space walk, deferred the same turn and for the same reason as [childCheck].
     * The hosts [childCheck] holds are checked first, so a refusal is reported as it is outside diagnostics
     * even where an earlier pass scheduled this walk ahead of them.
     */
    private val indexSpaceCheck =
        DeferredAction {
            childCheck.checkPending()
            this.root.checkChildIndexSpace()
        }

    /**
     * Attaches [child], composed at [index], to [container]: installed into the region of this host it names,
     * or added to the host's index space where it names none.
     *
     * A region is reached through a dedicated Swing setter (e.g. a JScrollPane region via
     * setRowHeaderView/setColumnHeaderView/...), not the generic Container.add. The holder records what installed
     * it and how the region is released again, the container is marked dirty so the new content gets laid out
     * and the area it covers repainted, and a [ChildPlacement.Slots] host is held to one child per region once
     * the pass ends.
     */
    fun SwingNodeHolder<*>.attachCreated(
        container: Container,
        child: CreatedNodeHolder<*>,
        index: Int,
    ) {
        // A child naming no region keeps the attachment its host installed it through.
        val attachment = child.declaredSlot?.attachment ?: childPlacement.contentAttachment
        if (attachment != null) {
            val slotIndex = slotIndexOf(container, index)
            child.installation =
                Installation.Region(child.declaredSlot?.name, attachment.install(container, child.component, slotIndex))
            child.declaration.attachedUnder(container)
            changes.recordSlotFilled(this)
            batch.markSlotChanged(container, child)
        } else {
            val childHost = container.childHost
            val standing = childHost.componentCount
            addToHost(container, child, index)
            child.installation = Installation.Indexed
            // A container that shows one child, such as a JViewport, takes the one it holds out as it adds
            // another. A pass replacing the child adds the new one before it removes the old one, so only
            // the settled pass tells a replacement from a second child.
            if (childHost.componentCount == standing) childCheck.hold(this)
            batch.markChanged(childHost, child.component.bounds)
        }
    }

    /**
     * Takes in [child], whose parent already holds its component where it stands: nothing is added or
     * installed. Under a host holding regions, the region the child names is recorded as the one it fills.
     *
     * A claimed child naming no region cannot be the content of a host that installs its unnamed children:
     * the host would install a component its parent holds. The host's component must be the parent the claim
     * ran on, and the declaration the child's modifier made is held to it.
     */
    fun SwingNodeHolder<*>.takeClaimed(child: ExistingNodeHolder<*, *>) {
        check(child.parentComponent === component) {
            val parent = child.parentComponent.declaredName
            val newParent = component.declaredName
            "A ${child.component.declaredName} claimed by ExistingSwingNode was placed by a $parent, but the node " +
                "was relocated under ${if (parent == newParent) "another" else "a"} $newParent, which did not " +
                "place it. Keep the claim under the node whose component holds the part, or key it on the part there."
        }
        check(child.declaredSlot != null || childPlacement.contentAttachment == null) {
            val claimed = "A ${child.component.declaredName} claimed by ExistingSwingNode cannot be"
            when {
                this === root -> {
                    "$claimed the one top-level component of this content, which whoever shows the content " +
                        "installs. Compose it inside a container of that content."
                }

                childPlacement.regionNames.isEmpty() -> {
                    "$claimed the unnamed child of a ${component.declaredName}, which installs that child as its " +
                        "content while the claimed component's parent already holds it. Its ChildPlacement offers " +
                        "no region to name, so the part cannot be claimed under it."
                }

                else -> {
                    "$claimed the unnamed child of a ${component.declaredName}, which installs that child as its " +
                        "content while the claimed component's parent already holds it. " +
                        childPlacement.throughRegionCalls("Name the region it stands in")
                }
            }
        }
        child.checkPlacedByParent(child.declaration.parentLayoutElements)
        child.installation = Installation.Claimed(child.declaredSlot?.name.takeIf { childPlacement.holdsRegions })
        if (childPlacement.holdsRegions) changes.recordSlotFilled(this)
        claims.record(child)
    }

    fun reconcile() {
        try {
            // The pass has settled, so every modifier has run for the host its node belongs to now and a
            // relocated child names the placement it fills here. Attaching them first is what leaves the
            // two steps below reading hosts whose children are all attached.
            changes.relocated.fastForEach { (host, child) -> trace("attach") { host.attachRelocatedChild(child) } }
            // A modifier has named its child's region for the last time this pass, and each of these hosts
            // can be brought to what its children declare. This runs whole: the check below reads the
            // region each child is really in, and a component that arrives in a region as this runs is
            // one more child of a host the check has to answer for.
            for (host in changes.hostsWithRestatedRegions) host.moveRestatedChildren()
            // A root slot has a stronger contract than an ordinary region: some hosts permit an empty
            // region, while a custom root consumer may require exactly one child. Hold it even where this
            // pass removed its last child, since no slot arrival would otherwise schedule that validation.
            if (rootSlotPolicy != null) childCheck.hold(root)
            for (host in changes.hostsWithFilledSlots) childCheck.hold(host)
        } finally {
            changes.forget()
        }
        childCheck.schedule()
        if (root.owner?.diagnostics != null) indexSpaceCheck.schedule()
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
        checkPlacementOf(container, child)
        // The child counts as attached from here on, so a pass attaching several of them lands the run
        // contiguously and in composition order; the check above is made while it still stands apart, since
        // a child that is not attached answers for no sibling's placement.
        child.awaitingAttachment = false
        child.attachTo(this, container, index, this@ChildRegions)
    }

    /**
     * Moves every child of this host that ends the pass naming a region other than the one its component
     * is installed in: the region it fills is released through the attachment that filled it, and the
     * child installed again through the attachment it names now, at the index it is composed at.
     *
     * Releasing first is what leaves a swap of two regions' occupants correct in either declaration order:
     * an attachment releases its region for the child that installed it, so a region a sibling has already
     * taken over is left to that sibling. A child naming no region at all is released and then installed
     * through the host's content attachment, or refused where the host has none, the way a child arriving
     * at a region-holding host without one is - it would be held by the host and laid out by nobody.
     *
     * A parked child is skipped: it gave its region up in [SwingNodeHolder.onDeactivate] and keeps its stale
     * [SwingNodeHolder.declaredSlot] for as long as it stands in [SwingNodeHolder.children], so its declared
     * and installed regions never agree again - reinstalling it here would put a component the composition no
     * longer drives back into a region a live sibling may since have taken.
     */
    private fun SwingNodeHolder<*>.moveRestatedChildren() {
        // A host that holds children is a Container, since attaching them required one; a node that is
        // not one holds none and so has no region to move a child between.
        val container = component as? Container ?: return
        batch.holdForChildSettle(this)
        children.fastForEachIndexed { index, child ->
            if (!child.attachedToHost) return@fastForEachIndexed
            if (child.declaredSlot?.name != child.installation?.region) {
                child.leaveHost(container, batch)
                checkChildKind(container, child, fillsRegion(this, child))
                child.attachTo(this, container, index, this@ChildRegions)
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
     * A child whose modifier gives its region up in the same pass is released and then refused, the way one
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
            detach = { it.leaveHost(container, batch) },
            place = { child, index ->
                checkChildKind(container, child, fillsRegion(host, child))
                child.attachTo(host, container, index, this@ChildRegions)
            },
        )
    }
}

/**
 * The hosts to hold to one child per region, or to the children added to them, checked by [check] on the
 * turn of the event queue after the one the change pass was applied in. Only there has a parked node's
 * deactivation - dispatched by the runtime once the changes applying it are themselves applied - actually
 * run, so only there does a host hold the children the composition means it to.
 */
private class DeferredChildCheck(
    private val check: (SwingNodeHolder<*>) -> Unit,
) {
    private val hosts: MutableSet<SwingNodeHolder<*>> = Collections.newSetFromMap(IdentityHashMap())
    private val turn = DeferredAction(::checkPending)

    /** Checks the hosts held so far, ahead of the turn [schedule] asked for. */
    fun checkPending() {
        val pending = hosts.toList()
        hosts.clear()
        for (host in pending) check(host)
    }

    /** Records [host] as one to hold to its children once the pass in flight has settled. */
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
 * owes each host once that pass has settled: children to attach, regions to bring to what a modifier
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
internal class ChangeRecord {
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
     * the one top-level child the root shows as its content.
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

    /**
     * Records that [host] had a child installed into one of the regions it holds. Only a
     * [ChildPlacement.Slots] host is recorded, since it is the one held to one child per region.
     */
    fun recordSlotFilled(host: SwingNodeHolder<*>) {
        if (host.childPlacement is ChildPlacement.Slots) filledSlotHosts += host
    }

    /**
     * Drops what the pass recorded, so no child is left standing in a host's children as one still to be
     * attached and no host is answered for twice.
     */
    fun forget() {
        relocations.fastForEach { (_, child) -> child.awaitingAttachment = false }
        relocations.clear()
        announced.clear()
        restatedRegionHosts.clear()
        filledSlotHosts.clear()
    }
}

/**
 * The components the [ExistingSwingNode]s of one composition claim, each against the one node claiming it.
 *
 * Two live claims on one component would each give back what the other declared, so a second claim is
 * refused as it arrives. A claim stands until the runtime releases or parks its node, which comes after
 * the pass that removes the node has been applied: a node replacing another in one pass would have the
 * declarations it made given back by the node it replaced.
 */
internal class ClaimedComponents {
    /** Each claimed component, against the node claiming it. */
    private val claims = IdentityHashMap<Component, ExistingNodeHolder<*, *>>()

    /**
     * Records [claim] as the node claiming its component until it is released or parked. A relocated node
     * arrives again.
     */
    fun record(claim: ExistingNodeHolder<*, *>) {
        val standing = claims[claim.component]
        if (standing != null) check(standing === claim) { claimedTwice(standing, claim) }
        claims[claim.component] = claim
        claim.releaseBlock = { forget(claim) }
    }

    private fun forget(claim: ExistingNodeHolder<*, *>) {
        if (claims[claim.component] === claim) claims.remove(claim.component)
    }

    /**
     * One component claimed by two live [ExistingSwingNode]s, whose declarations would both configure it. Names
     * the declaration where both nodes declare it through the same slot or the same call.
     */
    private fun claimedTwice(
        first: ExistingNodeHolder<*, *>,
        second: ExistingNodeHolder<*, *>,
    ): String {
        val component = second.component.declaredName
        val firstParent = first.parentComponent.declaredName
        val secondParent = second.parentComponent.declaredName
        val region = second.declarationName?.takeIf { it == first.declarationName }
        val parents =
            if (first.parentComponent === second.parentComponent) {
                "in one $secondParent"
            } else {
                "under a $firstParent and a $secondParent"
            }
        return if (region != null) {
            "$region is declared twice at once $parents, and both declarations would configure its $component. " +
                "Declare one, and put the choice inside it."
        } else {
            "A $component is declared twice at once $parents, and both declarations would configure it. " +
                "Declare one, and put the choice inside it."
        }
    }

    private val ExistingNodeHolder<*, *>.declarationName: String? get() = declaredSlot?.name ?: declaringCall
}

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
 * The host is always handed a position. A conventional layout is also handed folded parent data when
 * the child has one:
 * `Container.add(Component, Object)` ignores the index and appends, while a constrained layout such as
 * `BorderLayout` stores the component by its region, so the two-argument form would tell a constrained
 * child's host nothing about where the composition puts it. What the host makes of the position is its
 * own. A plain container holds its children in exactly the order given, so it is handed the place among
 * the children standing there already. A `JLayeredPane` reads the position as one within the depth the
 * parent data names, so it is handed the place among the standing siblings on that depth - a count over
 * every sibling would put the child one place lower within its depth for each sibling on another depth
 * ahead of it, or at the depth's bottom once the count ran past its end. A
 * [MeasurementLayoutManager] is added without parent data, then receives that data and the ordered
 * parent-layout elements together from [ParentDeclaration.attachedUnder].
 */
internal fun SwingNodeHolder<*>.addToHost(
    container: Container,
    child: SwingNodeHolder<*>,
    index: Int,
) {
    val childHost = container.childHost
    child.declaration.checkAttachableUnder(childHost)
    val position =
        if (childHost is JLayeredPane) {
            standingSiblingsOnDepthBefore(childHost, child, index)
        } else {
            standingSiblingsBefore(childHost, index)
        }
    val parentData = child.declaration.parentData
    if (parentData != null && childHost.layout !is MeasurementLayoutManager) {
        childHost.add(child.component, parentData, position)
    } else {
        childHost.add(child.component, position)
    }
    child.declaration.attachedUnder(childHost)
}

/**
 * The place among the children standing in [host] that the child composed at [index] takes.
 *
 * Standing in [host] is what a position handed to `Container.add` counts over, and a child the
 * composition holds can stand somewhere else entirely - see [attachedToHost]. Only a sibling that
 * [stands in it by composition][standsByCompositionIn] is counted.
 */
private fun SwingNodeHolder<*>.standingSiblingsBefore(
    host: Container,
    index: Int,
): Int {
    var standing = 0
    children.fastForEachIn(0 until index) { if (it.standsByCompositionIn(host)) standing++ }
    return standing
}

/**
 * The place within its depth on [pane] that [child], composed at [index], takes: the siblings standing
 * ahead of it, counted as [standingSiblingsBefore] counts them, that sit on the same depth. A child's depth
 * is the constraint its modifier declares, or else the layer `JLayeredPane.getLayer` reads for it.
 */
private fun SwingNodeHolder<*>.standingSiblingsOnDepthBefore(
    pane: JLayeredPane,
    child: SwingNodeHolder<*>,
    index: Int,
): Int {
    val depth = child.depthOn(pane)
    var standing = 0
    children.fastForEachIn(0 until index) {
        if (it.standsByCompositionIn(pane) && it.depthOn(pane) == depth) standing++
    }
    return standing
}

private fun SwingNodeHolder<*>.depthOn(pane: JLayeredPane): Int =
    declaration.parentData as? Int ?: pane.getLayer(component)

/**
 * The index a region's attachment is handed for the child composed at [index]: `0` where the host's regions
 * hold one child each, and the place among the siblings standing in [host] where it holds many in order.
 *
 * A sibling standing somewhere else - see [attachedToHost] - takes no place among them.
 */
private fun SwingNodeHolder<*>.slotIndexOf(
    host: Container,
    index: Int,
): Int = if (childPlacement is ChildPlacement.Slots) 0 else standingSiblingsBefore(host, index)

/**
 * Records [host] as changed over the area [child]'s component covers, in [host]'s coordinates, wherever
 * its region put it. Called just before a region is released and just after one is filled: an attachment
 * going through `Container.add` and `Container.remove` only invalidates.
 */
private fun ComponentUpdateBatch.markSlotChanged(
    host: Container,
    child: SwingNodeHolder<*>,
) {
    val component = child.component
    val parent = component.parent
    markChanged(host, parent?.let { SwingUtilities.convertRectangle(it, component.bounds, host) })
}

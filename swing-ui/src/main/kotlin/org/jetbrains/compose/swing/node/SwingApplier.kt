// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.AbstractApplier
import org.jetbrains.compose.swing.core.disposingOnFailure
import org.jetbrains.compose.swing.core.trace
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIn
import java.awt.Container

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
 *   except the unnamed child a [ChildPlacement.Slots] content attachment installs. A child that does not
 *   match the host's declaration is refused, naming the host and the calls that would place it.
 * - A child the composition relocates - `movableContent` invoked under another parent - reaches its new
 *   host before its own modifier chain has run there, so it is taken into that host's children as it
 *   arrives and attached once the change pass has settled, under the placement its modifier names at the
 *   host it is at now. That is also where such a child is held to the host's declaration.
 * - An indexed child is added with its declared [ParentDeclaration.parentData] when non-null (e.g. a
 *   `BorderLayout` region), otherwise by index alone. The constraint is the one the child's own modifier
 *   chain declares.
 * - A child naming a region carries the [SwingNodeHolder.declaredSlot] that fills it. The child
 *   is installed into its host through that attachment's dedicated Swing setter and uninstalled the same
 *   way on removal, so the region is released. A child whose modifier comes to name another region is moved
 *   between the two the same way, and one that stops naming a region is released from the one it fills
 *   and then installed as the host's content, or refused where the host has none, the way a child
 *   arriving at a region-holding host without one is. A [ChildPlacement.Slots] host shows one component
 *   per region, which is checked once the change pass has settled - a pass that replaces the occupant of
 *   a region need not take the outgoing child out before the incoming one arrives.
 * - A [VirtualSwingNode] has no component. Its children are added to the container of the nearest ancestor that
 *   has one, at the place their composition order among that ancestor's other children gives them, and receive
 *   its declarations ahead of their own. It is placed only under a [ChildPlacement.Indexed] host. A child
 *   inserted under a group that is not placed yet is attached as the group is placed. A child inserted, relocated
 *   or moved under a group whose declaration changed in the pass joins its host in [GroupDeclarationEffect].
 * - A child [ExistingSwingNode] claims stands where its parent put it, so the applier never adds, removes
 *   or moves its component: it holds the child in its child list, and under a region-holding host records
 *   the region the child names as filled.
 * - A [ChildPlacement.Slots] host that declares a content attachment installs each created child that names
 *   no region through it, and shows the one such child it holds - checked the way every other
 *   single-occupancy region is. The composition's own top-level children are the one case no composable
 *   declares, so the code that mounts a composition declares such a placement on [root].
 *
 * An immediate insertion whose attachment fails is removed from the composition child list; changes to Swing
 * components made before the failure are not rolled back.
 *
 * Every container mutated during a change pass is revalidated once, by [ComponentUpdateBatch]. The applier
 * repaints the area a child leaves or arrives in.
 *
 * Internal implementation type; not public API.
 *
 * @param rootHolder the node this composition is rooted at, attached to the composition that owns it.
 * @param rootSlotPolicy observes the root after a pass has settled, before its single-child invariant is
 *   checked. The standard invariant check follows if the policy returns normally. `null` uses only the
 *   standard check.
 * @see org.jetbrains.compose.swing.node.SwingNode
 */
@PublishedApi
internal class SwingApplier internal constructor(
    private val rootHolder: SwingNodeHolder<Container>,
    rootSlotPolicy: RootSlotPolicy? = null,
) : AbstractApplier<NodeHolder>(rootHolder) {
    /** The bookkeeping for the batch of component updates in flight, read off the root like any node. */
    private val batch = rootHolder.requireOwner().updateBatch

    /** What the change pass in flight has said about where children go. */
    private val changes = ChangeRecord()

    /** The components this composition's [ExistingSwingNode]s claim. */
    private val claims = ClaimedComponents()

    /** The regions this applier's hosts hold their children in. */
    private val regions = ChildRegions(rootHolder, rootSlotPolicy, batch, changes, claims)

    private val flattenedRun = ArrayList<SwingNodeHolder<*>>()

    /**
     * Gives the components of each group whose update stored a different declaration in this pass that declaration,
     * after the pass's node lifecycle callbacks, then settles each such group's host. Called by
     * [GroupDeclarationEffect].
     */
    internal fun declareChangedGroups() {
        val changed = batch.changedGroups.take() ?: return
        regions.declareChangedGroups(changed)
    }

    init {
        require(rootSlotPolicy == null || rootHolder.childPlacement.contentAttachment != null) {
            "A root slot policy needs a root that installs its unnamed child as content."
        }
    }

    override fun up() {
        // A node's own update changes run while the applier is positioned at it, so leaving the node is
        // the first point in the pass at which the region its modifier names this time is on the holder and
        // its host is known - the node the applier returns to. Nothing is moved here: the pass may be
        // mid-swap, and a node that arrived this pass is left before it is installed, so both would look
        // like a component in the wrong region. What the pass leaves behind is settled in onEndChanges,
        // where a host whose children all name the region they are in costs one walk of its child list.
        val node = current
        super.up()
        node.checkUpdatedPlacement(current.componentHost, regions)
    }

    override fun insertTopDown(
        index: Int,
        instance: NodeHolder,
    ) {
        trace("insert") {
            // A relocated node arrives here after insertBottomUp has marked it awaiting attachment, and is not
            // inserted for the first time.
            if (instance.attachmentWait != AttachmentWait.Relocation) instance.insertedUnder(current)
            // Attach the node to the composition its parent stands in. This MUST happen on the top-down
            // pass - see SwingCompositionOwner.
            instance.insertInto(current)
            changes.announceInsert(instance)
        }
    }

    override fun insertBottomUp(
        index: Int,
        instance: NodeHolder,
    ) {
        val parent = current
        val host = parent.componentHost
        val container = host.containerFor { "add child ${instance.declaredName}" }
        // Settlement observes the final children after every insertion path, including changes inside a group.
        batch.holdForChildSettle(host)
        if (!changes.takeAnnouncedInsert(instance)) {
            // The composition is relocating a node composed under another parent, and hands it over
            // before its modifier chain has run for this host, so what it carries is the placement it
            // named at the host it is leaving. Take it into this node's composition-ordered child list,
            // which is what a remove or a move later in the pass addresses by index, and attach it once
            // the pass has settled and its modifier has named the placement it fills here.
            parent.insertChild(index, instance)
            changes.recordRelocated(parent, instance)
            return
        }
        if (!parent.placesChildren) {
            // Inserts run bottom-up, so a new group's children arrive before the group itself: the group attaches
            // them as it is placed.
            instance.attachmentWait = AttachmentWait.Placement
            parent.insertChild(index, instance)
            return
        }
        trace("attach") {
            // The node was already attached to its composition on the top-down pass (see insertTopDown);
            // here we only perform the Swing attachment. The composition list includes the arriving node
            // before attachment, so its position is counted over the host's flattened children.
            instance.checkPlacementUnder(host, container, regions)
            parent.insertChild(index, instance)
            disposingOnFailure({ parent.removeChildren(index, 1) }) {
                instance.attachTo(parent, container, index, regions)
            }
        }
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        trace("remove") {
            val parent = current
            val host = parent.componentHost
            val container = host.containerFor { "remove children" }
            batch.holdForChildSettle(host)
            // Where a host keeps each child is the host's own business: a region holds one through its own
            // setter, and a `JLayeredPane` sorts its index space by the depth each child declares. The
            // composition-order child list is what says which children the pass drops, and each of them leaves
            // through the installation that put it where it stands.
            flattenedRun.clear()
            parent.children.fastForEachIn(index until index + count) { it.flattenInto(flattenedRun) }
            flattenedRun.fastForEach { it.leaveHost(container, batch) }
            flattenedRun.clear()
            parent.removeChildren(index, count)
        }
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        val parent = current
        val host = parent.componentHost
        val container = host.containerFor { "move children" }
        if (from == to) return

        trace("move") {
            batch.holdForChildSettle(host)
            when {
                host.childPlacement.holdsRegions -> {
                    with(regions) { host.moveRegionChildren(container, from, to, count) }
                }

                // The children of a group that is not placed are in no container yet, so only their order changes.
                !parent.placesChildren -> {
                    parent.moveChildren(from, to, count)
                }

                else -> {
                    // Each child leaves through the installation that put it where it stands and is attached again at
                    // the place it is composed at now. A child's pixels change only where it overlaps a sibling it now
                    // stands above or below, which lies inside its own area, so its bounds before it leaves and after
                    // it is attached again are what the pass repaints.
                    val target = parent.moveChildren(from, to, count)
                    flattenedRun.clear()
                    parent.children.fastForEachIn(target until target + count) { it.flattenInto(flattenedRun) }
                    flattenedRun.fastForEach { it.leaveHost(container, batch) }
                    flattenedRun.clear()
                    var index = target
                    parent.children.fastForEachIn(target until target + count) { child ->
                        if (child.attachedToHost) {
                            child.attachTo(parent, container, index, regions)
                        }
                        index++
                    }
                }
            }
        }
    }

    /** Removes the composed children and their parent links while preserving the caller's root. */
    override fun onClear() {
        // The root is user-supplied. Each root child leaves through what installed it; claimed components and
        // children the composition did not add stay.
        val container = rootHolder.component
        rootHolder.flattenedChildren.fastForEach { it.leaveHost(container, batch) }
        rootHolder.removeChildren(0, rootHolder.children.size)
        changes.forget()
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
 * Policy invoked after a root-slot pass settles, before the root's single-child invariant is checked.
 */
internal fun interface RootSlotPolicy {
    fun onSettled()
}

/**
 * The Swing container this node holds its children in.
 *
 * @param action what the container is wanted for, built only where the node is no container at all.
 */
private inline fun SwingNodeHolder<*>.containerFor(action: () -> String): Container =
    component as? Container
        ?: error("Current node $component is not a Container, cannot ${action()}")

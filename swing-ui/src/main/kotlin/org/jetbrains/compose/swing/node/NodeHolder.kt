package org.jetbrains.compose.swing.node

import androidx.compose.runtime.ComposeNodeLifecycleCallback
import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIn
import org.jetbrains.compose.swing.util.fastForEachIndexed
import java.awt.Container
import java.util.Collections
import java.util.IdentityHashMap

/**
 * A node of [SwingApplier]: a [SwingNodeHolder], which has a component, or a [VirtualNodeHolder], which groups
 * children without one. A group's children are added to the container of the nearest ancestor that has a
 * component, among that ancestor's other children in composition order.
 */
internal sealed interface NodeHolder : ComposeNodeLifecycleCallback {
    /**
     * The children the applier keeps under this node, in composition order. This is the index space the runtime
     * addresses: a group is one entry here, however many components it places.
     *
     * A child is in this list from the moment the applier takes it in, which for a relocated child, or a child of
     * a group not placed yet, is before its component is attached - see [attachmentWait].
     */
    val children: MutableList<NodeHolder>

    /**
     * What this node waits for before it is attached; see [AttachmentWait].
     * Marking a virtual node waiting also marks its projected component children waiting, stopping at each
     * component host. Each node clears its own wait when it attaches.
     */
    var attachmentWait: AttachmentWait

    /**
     * Whether the runtime deactivated this node. A deactivated node stays in its parent's [children] until the
     * composition removes it, and is never attached again.
     */
    val deactivated: Boolean

    /** What this node's composition owns and every node under it shares; see [SwingCompositionOwner]. */
    val owner: SwingCompositionOwner?

    /**
     * The node the applier last inserted this one under. `null` on a composition's root and on a removed node. Set as
     * the node is inserted top-down, so a relocated node, which is inserted bottom-up first, has its new parent before
     * the change pass attaches it. Style resolution and host resolution ([componentHost], [childGroupDeclarations])
     * walk it.
     *
     * Written only as the node enters or leaves a child list: [insertInto] sets it, [removeChildren] clears it.
     */
    var parent: NodeHolder?

    /**
     * The node to whose component this node's children are added: this node where it has a component, else the
     * [componentHost] of its [parent].
     */
    val componentHost: SwingNodeHolder<*>

    /** Whether a child inserted under this node is attached at once, rather than when this node is placed. */
    val placesChildren: Boolean

    /**
     * The group declarations each child of this node receives ahead of its own, outermost first. A node with a
     * component gives none: its children are added to its own container. A group gives its parent's, then its own.
     */
    val childGroupDeclarations: List<ParentElement>

    /** The component class or composable name used in placement refusals. */
    val declaredName: String

    /**
     * Takes this node and its children out of the composition they are in and into [owner]. Moving a node to the
     * composition it is in changes nothing.
     *
     * @param owner the composition to join, or `null` to leave the one it is in.
     * @param forGood whether a modifier is reset as on release rather than only detached.
     */
    fun moveTo(
        owner: SwingCompositionOwner?,
        forGood: Boolean,
    )

    /** A group keeps nothing to reset. */
    override fun onReuse() = Unit

    /**
     * Called as the applier inserts this node under [parent] for the first time, before the node's update runs. A
     * node whose component its own factory builds has nothing to find there.
     */
    fun insertedUnder(parent: NodeHolder) = Unit

    /**
     * Checks this node's declarations, including its group declarations, against [host] before [container]
     * changes. A group checks each child it will attach, so a refusal leaves the host unchanged.
     *
     * @param host the node whose component this node's components are added to.
     * @param container the component of [host].
     * @param regions the regions of the applier placing this node.
     */
    fun checkPlacementUnder(
        host: SwingNodeHolder<*>,
        container: Container,
        regions: ChildRegions,
    )

    /**
     * Attaches this node, projected at [index] under [parent], to [container], through [regions].
     *
     * @param parent the node this one is composed under.
     * @param container the component of [parent]'s [componentHost].
     * @param index the node's index among its parent's children; unused for a group.
     * @param regions the regions of the applier attaching this node.
     */
    fun attachTo(
        parent: NodeHolder,
        container: Container,
        index: Int,
        regions: ChildRegions,
    )

    /**
     * Gives each component this node places the declarations of the groups it is composed in, in composition order, in
     * [GroupDeclarationEffect] after a group above it changed them: a component the pass left waiting joins
     * [container] as `attachTo` attaches it, one that stands in [container] publishes its complete checked
     * declaration again, and any other is skipped.
     *
     * @param parent the node this one is composed under.
     * @param index the node's index among its parent's children.
     * @param container the component of [parent]'s [componentHost].
     * @param regions the regions of the applier attaching a waiting component.
     */
    fun takeGroupDeclarations(
        parent: NodeHolder,
        index: Int,
        container: Container,
        regions: ChildRegions,
    )

    /**
     * Checks what this node's update declared about its place in [host], as the applier leaves the node: a region
     * the node names that it is not installed in is recorded for the end of the pass, and a constraint on a
     * component that fills a region is refused. A group names no region and fills none, so it has nothing to check.
     *
     * @param host the node whose component this node's component is added to.
     * @param regions the regions of the applier leaving this node.
     */
    fun checkUpdatedPlacement(
        host: SwingNodeHolder<*>,
        regions: ChildRegions,
    ) = Unit

    /**
     * Updates [projection] for insertion. A node with a component changes the projection's children; a group counts
     * itself instead.
     * @param projection the parent component's child projection.
     */
    fun insertIn(projection: ChildProjection) = projection.childrenChanged()

    /**
     * Updates [projection] for removal. A node with a component changes the projection's children; a group counts
     * itself instead.
     * @param projection the parent component's child projection.
     */
    fun removeFrom(projection: ChildProjection) = projection.childrenChanged()

    /** Appends the component nodes represented by this node to [destination], in composition order. */
    fun flattenInto(destination: MutableList<SwingNodeHolder<*>>)

    /**
     * Refuses an [ExistingSwingNode] composed directly under this node where it cannot claim its component here. A
     * node with a component allows it: the claim runs on that component.
     */
    fun checkClaimable() = Unit
}

/**
 * Whether this node is one of [groups] or is composed in one of them: a walk up through the groups, which stops at the
 * first node with a component, since that node holds its own children.
 *
 * @param groups the groups whose declarations changed in the pass in flight.
 */
internal fun NodeHolder.composedInAnyOf(groups: List<VirtualNodeHolder>): Boolean {
    val host = componentHost
    var current: NodeHolder? = this
    while (current != null && current !== host) {
        if (current in groups) return true
        current = current.parent
    }
    return false
}

/**
 * Joins [container] a node the pass left waiting, as `attachTo` attaches it, checking it there first. Where the node
 * is not waiting, nothing happens and `false` returns.
 *
 * @param parent the node this one is composed under.
 * @param index the node's index among its parent's children.
 * @param container the component of [parent]'s [componentHost].
 * @param regions the regions of the applier attaching a waiting component.
 */
internal fun NodeHolder.joinHostIfWaiting(
    parent: NodeHolder,
    index: Int,
    container: Container,
    regions: ChildRegions,
): Boolean {
    if (!awaitingAttachment) return false
    checkPlacementUnder(parent.componentHost, container, regions)
    attachTo(parent, container, index, regions)
    return true
}

/** The index of each of this node's children, so a pass relocating several of them walks the list once. */
internal fun NodeHolder.childIndices(): Map<NodeHolder, Int> =
    IdentityHashMap<NodeHolder, Int>(children.size).also { indices ->
        children.fastForEachIndexed { index, child -> indices[child] = index }
    }

/**
 * Attaches this node and every descendant to [owner], detaching them from their previous composition,
 * and returns this node. A [SwingComponentNodeListener] component receives each change.
 *
 * An applier attaches an inserted node to its parent's composition, so the
 * owner travels the node tree. The root is attached by the composition itself, being the one node
 * no parent hands an owner down to, and detached by it, with `null`, as it is disposed.
 *
 * A subtree inserted from another composition, as moved `movableContent`, leaves it whole and then
 * joins this one whole: each node's modifier nodes detach and its reads and pending layout requests
 * there are dropped, then its modifier nodes attach and update again. A deactivated node only takes
 * the new composition.
 *
 * A listener's throw from `onDetached` or `onAttached`, or a modifier node's throw from `onDetach`, is
 * reported to the uncaught-exception handler, and the owner change finishes as if the callback had
 * returned. A modifier node's `onAttach` failure detaches that node and ends the pass and recomposer;
 * a rollback `onDetach` failure is suppressed on it, and other applier work is not rolled back.
 * Tracked reads and pending requests in the previous owner are dropped for this node and its attached
 * modifier nodes. Children receive the new owner after this node's modifier nodes attach successfully.
 */
internal fun <H : NodeHolder> H.attachedTo(owner: SwingCompositionOwner?): H {
    moveTo(owner, forGood = owner == null)
    return this
}

/** Whether this node awaits attachment, for any [AttachmentWait] reason. */
internal val NodeHolder.awaitingAttachment: Boolean
    get() = attachmentWait != AttachmentWait.None

/**
 * Whether a group this node is composed in changed its declaration in this pass: until [GroupDeclarationEffect],
 * the node joins no host and publishes nothing, and only a withdrawal of its detached layout nodes reaches its
 * host.
 */
internal val NodeHolder.awaitsGroupEffect: Boolean
    get() = owner?.updateBatch?.changedGroups?.await(this) == true

/**
 * Moves each child of this node to [owner]. With a `null` [owner] every child leaves its composition even when one
 * throws, and the first failure propagates with the later ones suppressed on it; otherwise the children join
 * [owner] without [forGood].
 *
 * @param owner the composition the children join, or `null` for them to leave the one they are in.
 * @param forGood whether a modifier is reset as on release rather than only detached, where [owner] is `null`.
 */
internal fun NodeHolder.moveChildrenTo(
    owner: SwingCompositionOwner?,
    forGood: Boolean,
) {
    if (owner != null) {
        children.fastForEach { it.moveTo(owner, forGood = false) }
        return
    }
    var failure: Throwable? = null
    children.toList().fastForEach { child ->
        failure = collectFailure(failure) { child.moveTo(null, forGood) }
    }
    failure?.let { throw it }
}

/**
 * Whether the applier has attached this child to its host and not deactivated it since. A child the pass has taken
 * in but not attached yet was never attached, and a deactivated one is not attached any more - the runtime keeps
 * its place in the composition, so it stays in [NodeHolder.children] with its component already detached.
 *
 * It says nothing about where the component is now: a look and feel moves one out of the container it was
 * declared in - a tool bar dragged into a window of its own - without the composition hearing of it.
 */
internal val NodeHolder.attachedToHost: Boolean
    get() = !awaitingAttachment && !deactivated

/**
 * Inserts [child] into the composition list and updates its host's projection before attachment.
 *
 * @param index the child's position among this node's composition children.
 * @param child the node being inserted or relocated.
 */
internal fun NodeHolder.insertChild(
    index: Int,
    child: NodeHolder,
) {
    val host = componentHost
    val projection = if (this === host) host.childProjection else null
    children.add(index, child)
    if (projection != null) {
        child.insertIn(projection)
    } else {
        componentHost.childProjection.invalidate()
    }
}

/**
 * Removes [count] composition children at [index]. A child a destination has already taken keeps its new parent.
 */
internal fun NodeHolder.removeChildren(
    index: Int,
    count: Int,
) {
    children.fastForEachIn(index until index + count) { child ->
        // A relocated child keeps its wait: the pass that relocated it attaches it once it settles.
        if (child.attachmentWait != AttachmentWait.Relocation) child.attachmentWait = AttachmentWait.Placement
        if (this === componentHost) child.removeFrom(componentHost.childProjection)
        if (child.parent === this) child.parent = null
    }
    children.subList(index, index + count).clear()
    if (this !== componentHost) componentHost.childProjection.invalidate()
}

/**
 * Moves the run of [count] children at [from] to [to] in this host's composition-order child list, and
 * returns the index of the run once it has moved: taking a run out at [from] shifts the positions
 * above it down by [count], which is the index space [to] is given in.
 *
 * The run and the children it travels past swap places, so the move is a rotation of the span between
 * the two positions, and no child leaves the list.
 */
internal fun NodeHolder.moveChildren(
    from: Int,
    to: Int,
    count: Int,
): Int {
    val projection = if (this === componentHost) componentHost.childProjection else null
    val target = if (from > to) to else to - count
    Collections.rotate(
        children.subList(minOf(from, target), maxOf(from + count, to)),
        if (from > to) count else -count,
    )
    if (projection != null) {
        projection.childrenChanged()
    } else {
        componentHost.childProjection.invalidate()
    }
    return target
}

/**
 * The ordered component projection. Without groups, it is the host's own child list without a copy.
 * With groups, the flattened list and its identity index are rebuilt at most once between structural mutations.
 */
internal class ChildProjection(
    private val parent: SwingNodeHolder<*>,
) {
    private var virtualChildrenCount = 0
    private var flattened: Flattened? = null

    // Every direct child is a component node while the group count is zero: a group's insertIn counts it, and
    // only a group is not a SwingNodeHolder.
    private val directChildren =
        object : AbstractList<SwingNodeHolder<*>>() {
            override val size: Int get() = parent.children.size

            override fun get(index: Int): SwingNodeHolder<*> = parent.children[index] as SwingNodeHolder<*>
        }

    init {
        parent.children.fastForEach { it.insertIn(this) }
    }

    val children: List<SwingNodeHolder<*>>
        get() = if (virtualChildrenCount == 0) directChildren else flattened().components

    private fun flattened(): Flattened = flattened ?: Flattened(parent).also { flattened = it }

    /** The components [parent]'s children place, in composition order, and the index of each. */
    private class Flattened(
        parent: SwingNodeHolder<*>,
    ) {
        val components =
            ArrayList<SwingNodeHolder<*>>().also { list -> parent.children.fastForEach { it.flattenInto(list) } }
        val indices =
            IdentityHashMap<NodeHolder, Int>(components.size).also { map ->
                components.fastForEachIndexed { index, child -> map[child] = index }
            }
    }

    /** The host's composition children changed: with groups, the next read rebuilds the flattened list. */
    fun childrenChanged() {
        if (virtualChildrenCount != 0) invalidate()
    }

    fun insertGroup() {
        virtualChildrenCount++
        invalidate()
    }

    fun removeGroup() {
        virtualChildrenCount--
        invalidate()
    }

    fun invalidate() {
        flattened = null
    }

    fun indexOf(
        child: NodeHolder,
        compositionIndex: Int,
    ): Int {
        var result = compositionIndex
        if (virtualChildrenCount != 0) {
            result = flattened().indices[child] ?: -1
            check(result >= 0) {
                "The component is absent from its host's child projection. Insert it into the composition " +
                    "child list and invalidate the host projection before resolving its index."
            }
        }
        return result
    }
}

/** What a node waits for before the applier attaches it to its host. */
@JvmInline
internal value class AttachmentWait private constructor(
    private val reason: Int,
) {
    companion object {
        /** Attached, or never taken in. */
        val None = AttachmentWait(0)

        /**
         * The composition moved the node here before its modifier ran for this host; the pass attaches it once it
         * settles.
         */
        val Relocation = AttachmentWait(1)

        /**
         * Taken into a child list but not placed: a new group, a child under a group not placed yet, a child the
         * pass took out, a relocated child the settling pass has taken, or a component under a group whose
         * declaration changed in the pass, which joins in [GroupDeclarationEffect].
         */
        val Placement = AttachmentWait(2)
    }
}

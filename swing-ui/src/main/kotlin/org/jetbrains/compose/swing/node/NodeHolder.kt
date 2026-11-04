package org.jetbrains.compose.swing.node

import androidx.compose.runtime.ComposeNodeLifecycleCallback
import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIn
import java.awt.Container
import java.util.Collections

/** A node of [SwingApplier]: a [SwingNodeHolder], which has a component. */
internal sealed interface NodeHolder : ComposeNodeLifecycleCallback {
    /**
     * The children the applier keeps under this node, in composition order. This is the index space the runtime
     * addresses.
     *
     * A child is in this list from the moment the applier takes it in, which for a relocated child is before its
     * component is attached - see [awaitingAttachment].
     */
    val children: MutableList<NodeHolder>

    /**
     * Whether the host holding this node in its [children] has yet to attach the component. Only the applier writes
     * it.
     */
    var awaitingAttachment: Boolean

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
     * the change pass attaches it.
     *
     * Written only as the node enters or leaves a child list: [insertInto] sets it, [removeChildren] clears it.
     */
    var parent: NodeHolder?

    /** The node to whose component this node's children are added: this node. */
    val componentHost: SwingNodeHolder<*>

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

    /**
     * Called as the applier inserts this node under [parent] for the first time, before the node's update runs. A
     * node whose component its own factory builds has nothing to find there.
     */
    fun insertedUnder(parent: NodeHolder) = Unit

    /**
     * Checks this node's declarations against [host] before [container] changes.
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
     * Attaches this node, composed at [index] under [parent], to [container], through [regions].
     *
     * @param parent the node this one is composed under.
     * @param container the component of [parent]'s [componentHost].
     * @param index the node's index among its parent's children.
     * @param regions the regions of the applier attaching this node.
     */
    fun attachTo(
        parent: NodeHolder,
        container: Container,
        index: Int,
        regions: ChildRegions,
    )

    /** Appends the component nodes represented by this node to [destination], in composition order. */
    fun flattenInto(destination: MutableList<SwingNodeHolder<*>>)
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

/** Removes [count] composition children at [index]. */
internal fun NodeHolder.removeChildren(
    index: Int,
    count: Int,
) {
    children.fastForEachIn(index until index + count) { child -> child.parent = null }
    children.subList(index, index + count).clear()
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
    val target = if (from > to) to else to - count
    Collections.rotate(
        children.subList(minOf(from, target), maxOf(from + count, to)),
        if (from > to) count else -count,
    )
    return target
}

package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIn
import org.jetbrains.compose.swing.util.fastForEachIndexed

/**
 * The place among this host's attached children that the child composed at [index] takes: the siblings
 * ahead of it that are attached already. A host mid-pass holds every child the composition put here,
 * including any it has yet to attach or has parked, so the position handed to a host is counted rather
 * than composed.
 *
 * @param index the composition-order index of the child.
 */
internal fun SwingNodeHolder<*>.attachedSiblingsBefore(index: Int): Int {
    var attached = 0
    flattenedChildren.fastForEachIn(0 until index) { if (it.attachedToHost) attached++ }
    return attached
}

/**
 * Moves the run of [count] children at [from] to [to] in this host's composition-order child list, hands
 * each moved child to [detach], and then hands back the ones attached to this host with the index each
 * of them has, for the caller to turn into the place its host reads.
 *
 * A child the pass has yet to attach is not handed to [place]: it has no place yet, and takes
 * the position the list gives it once the pass has settled.
 *
 * The host holds no group, which is what makes its composition children and [flattenedChildren] the same list,
 * so the index [moveChildren] computes over the first addresses the second.
 *
 * @param from the index of the first child of the run.
 * @param to the index the run moves to, counted before the run is taken out.
 * @param count how many children the run holds.
 * @param detach receives each moved child before any of them is placed.
 * @param place receives each moved child attached to this host with its index.
 */
internal inline fun SwingNodeHolder<*>.moveChildren(
    from: Int,
    to: Int,
    count: Int,
    crossinline detach: (child: SwingNodeHolder<*>) -> Unit,
    crossinline place: (child: SwingNodeHolder<*>, index: Int) -> Unit,
) {
    val target = moveChildren(from, to, count)
    // The whole run leaves its host before any of it goes back: a place addresses the position among the
    // children that stay, and a run still at its old position would push each of them one along.
    flattenedChildren.fastForEachIn(target until target + count) { detach(it) }
    flattenedChildren.subList(target, target + count).fastForEachIndexed { offset, child ->
        if (child.attachedToHost) place(child, target + offset)
    }
}

/**
 * Inserts this subtree into [parent]'s composition on the top-down pass. A moved subtree resolves its
 * styles against its new ancestors before its nodes are published for inspection.
 */
internal fun NodeHolder.insertInto(parent: NodeHolder) {
    val moved = owner != null
    this.parent = parent
    attachedTo(parent.owner)
    if (moved) restyleSubtree(parent.owner?.updateBatch)
    if (isDebugInspectorInfoEnabled) {
        val components = ArrayList<SwingNodeHolder<*>>()
        flattenInto(components)
        components.fastForEach { it.publishForInspection() }
    }
}

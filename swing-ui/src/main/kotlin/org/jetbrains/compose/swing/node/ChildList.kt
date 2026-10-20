package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.util.fastForEachIn
import org.jetbrains.compose.swing.util.fastForEachIndexed
import java.util.Collections

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
    children.fastForEachIn(0 until index) { if (it.attachedToHost) attached++ }
    return attached
}

/**
 * Moves the run of [count] children at [from] to [to] in this host's composition-order child list, and
 * returns the index the run is at once it has moved: taking a run out at [from] shifts the positions
 * above it down by [count], which is the index space [to] is given in.
 *
 * The run and the children it travels past swap places, so the move is a rotation of the span between
 * the two positions, and no child leaves the list.
 *
 * @param from the index of the first child of the run.
 * @param to the index the run moves to, counted before the run is taken out.
 * @param count how many children the run holds.
 */
internal fun SwingNodeHolder<*>.moveChildren(
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

/**
 * Takes the run of [count] children at [index] out of this host's composition-order child list, handing
 * each of them to [release] before the list loses it.
 *
 * @param index the index of the first child of the run.
 * @param count how many children the run holds.
 * @param release receives each child before it leaves the list.
 */
internal inline fun SwingNodeHolder<*>.removeChildren(
    index: Int,
    count: Int,
    crossinline release: (SwingNodeHolder<*>) -> Unit,
) {
    children.fastForEachIn(index until index + count) {
        release(it)
        it.parentTo(null)
    }
    children.subList(index, index + count).clear()
}

/**
 * Moves the run of [count] children at [from] to [to] in this host's composition-order child list, hands
 * each moved child to [detach], and then hands back the ones attached to this host with the index each
 * of them has, for the caller to turn into the place its host reads.
 *
 * A child the pass has yet to attach is not handed to [place]: it has no place yet, and takes
 * the position the list gives it once the pass has settled.
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
    children.fastForEachIn(target until target + count) { detach(it) }
    children.subList(target, target + count).fastForEachIndexed { offset, child ->
        if (child.attachedToHost) place(child, target + offset)
    }
}

/**
 * Inserts this subtree into [parent]'s composition on the top-down pass. A moved subtree resolves its
 * styles against its new ancestors before its nodes are published for inspection.
 */
internal fun SwingNodeHolder<*>.insertInto(parent: SwingNodeHolder<*>) {
    val moved = owner != null
    parentTo(parent)
    attachedTo(parent.owner)
    if (moved) restyleSubtree(parent.owner?.updateBatch)
    publishForInspection()
}

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.snapshots.SnapshotStateObserver

/**
 * What one composition owns and every node under it shares.
 *
 * A node takes it from its parent as it is inserted, so it travels the node tree rather than being
 * handed out by the applier - which has no stake in it, and reads what it needs off its own root like
 * any other node. Only the root is attached from outside, by the composition that owns it.
 *
 * A node is attached on the top-down insert pass. That is earlier than the tree it stands in strictly
 * requires, and deliberately so: a node's update block runs between the top-down and bottom-up passes
 * and reads what is here - it is where a snapshot-observing component such as `Canvas` adopts the
 * observer - so an attach left to the bottom-up pass would reach a component already attached and
 * painted, leaving it observing nothing.
 *
 * One owner stands for the whole life of the composition behind it, so what a node reads through it on
 * its first pass is what it reads on every later one.
 */
internal interface SwingCompositionOwner {
    /**
     * The observer every snapshot-observing component in this composition registers with - `Canvas` and
     * its like - each as its own scope. `null` under a composition holding no such component, which is
     * what a menu is.
     */
    val observer: SnapshotStateObserver?

    /**
     * Runs the frame this composition's pending writes are owed inside the event being dispatched,
     * rather than from an event of its own - which is what puts a declaration back onto a widget before
     * the user sees the change it answers. See [MirrorState.report].
     *
     * Declines while a frame is already running, leaving that settlement to the one the event queued,
     * and skips entirely a composition that no recomposer of this library is driving. Runs on the event
     * dispatch thread.
     */
    fun settleNow()

    /**
     * The batch of component updates in flight, which is how a settle that cannot run until the batch
     * has attached a node's children is handed over - see [SwingNodeHolder.childSettle].
     */
    val updateBatch: ComponentUpdateBatch
}

/**
 * The composition this node stands in. Fails on a node no composition has attached, which an applier's
 * root never is: it is attached before the applier is built over it.
 */
internal fun SwingNodeHolder<*>.requireOwner(): SwingCompositionOwner =
    checkNotNull(owner) { "The node holding $component has not been attached to a composition" }

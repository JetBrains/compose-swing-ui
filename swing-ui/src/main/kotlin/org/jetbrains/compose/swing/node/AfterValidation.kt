@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Runs [action] on the Event Dispatch Thread. Calls made while this node's component tree lock is held are
 * coalesced by node and callback identity until the next event-queue turn; reuse the same callback instance to
 * coalesce repeated requests. Calls made without the lock run immediately and cancel a matching queued call.
 * Detaching this node or disposing its composition drops its pending calls.
 *
 * @param action receives this node when it runs.
 * @throws IllegalStateException if this node is not attached or the call is off the Event Dispatch Thread.
 */
public fun <T : SwingModifier.Node> T.requestAfterValidation(action: (T) -> Unit) {
    checkEventDispatchThread()
    val owner = requireOwner()
    owner.requestAfterValidation(this, action)
}

/**
 * Runs [action] on the Event Dispatch Thread, as [SwingModifier.Node.requestAfterValidation] does for a modifier
 * node; [action] receives this node.
 *
 * @throws IllegalStateException if this node is not attached or the call is off the Event Dispatch Thread.
 */
public fun <N : SwingComponentNode<*>> N.requestAfterValidation(action: (N) -> Unit) {
    checkEventDispatchThread()
    check(isAttached) { "The node holding $component is not attached" }
    requireOwner().requestAfterValidation(this, action)
}

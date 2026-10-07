package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.util.IdentityHashMap
import javax.swing.SwingUtilities

/** Schedules one shared event-queue turn for every composition's pending after-validation actions. */
internal class AfterValidationCoordinator(
    private val post: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
) {
    private val queuedQueues = IdentityHashMap<AfterValidationActionQueue, Boolean>()
    private val queues = PendingBuffers<AfterValidationActionQueue>()
    private var scheduled = false

    fun schedule(queue: AfterValidationActionQueue) {
        if (queuedQueues.put(queue, true) == null) queues.pending.add(queue)
        scheduleDrain()
    }

    fun remove(queue: AfterValidationActionQueue) {
        queuedQueues.remove(queue)
        queues.pending.removeAll { it === queue }
    }

    private fun drain() {
        val batch = queues.takePending()
        for (queue in batch) {
            queuedQueues.remove(queue)
            queue.startDrain()
        }

        var failure: Throwable? = null
        try {
            for (queue in batch) {
                val result = runCatching { queue.drain() }
                val caught = result.exceptionOrNull()
                if (failure == null) failure = caught
            }
        } finally {
            queues.release(batch)
        }
        failure?.let { throw it }
    }

    private fun scheduleDrain() {
        if (scheduled) return
        scheduled = true
        post {
            scheduled = false
            drain()
        }
    }
}

/** One composition's identity-deduplicated requests, drained by its shared coordinator. */
internal class AfterValidationActionQueue(
    private val owner: SwingCompositionOwner,
    private val coordinator: AfterValidationCoordinator,
) {
    private val actions = IdentityHashMap<Any, IdentityHashMap<Any, PendingAction<*>>>()
    private val requests = PendingBuffers<PendingAction<*>>()
    private val drainingRequests = java.util.ArrayDeque<MutableList<PendingAction<*>>>()
    private var disposed = false

    /** Queues [action] for this composition's attached [node], unless it detaches before the drain. */
    fun <N : Any> request(
        node: N,
        action: (N) -> Unit,
    ) {
        checkEventDispatchThread()
        check(isAttachedTo(node, owner)) { "The target does not belong to this composition" }
        if (disposed) return

        val callbacks = actions.getOrPut(node) { IdentityHashMap() }
        val request = callbacks[action] ?: PendingAction(node, action).also { callbacks[action] = it }
        if (!request.pending) {
            request.pending = true
            requests.pending.add(request)
            coordinator.schedule(this)
        }
    }

    fun cancel(target: Any) {
        actions.remove(target)?.values?.forEach(::cancelPending)
    }

    fun cancel(
        target: Any,
        action: Any,
    ) {
        val callbacks = actions[target] ?: return
        callbacks.remove(action)?.let(::cancelPending)
        if (callbacks.isEmpty()) actions.remove(target)
    }

    fun dispose() {
        disposed = true
        actions.values.forEach { callbacks -> callbacks.values.forEach { it.pending = false } }
        actions.clear()
        requests.pending.clear()
        coordinator.remove(this)
    }

    internal fun drain() {
        val batch = drainingRequests.removeLast()
        var failure: Throwable? = null
        try {
            for (request in batch) {
                if (!request.pending) continue
                request.pending = false
                remove(request)
                val result =
                    runCatching {
                        if (!disposed && isAttachedTo(request.target, owner)) {
                            request.invoke()
                        }
                    }
                val caught = result.exceptionOrNull()
                if (failure == null) failure = caught
            }
        } finally {
            requests.release(batch)
        }
        failure?.let { throw it }
    }

    private fun remove(request: PendingAction<*>) {
        val callbacks = actions[request.target] ?: return
        if (callbacks[request.action] === request) callbacks.remove(request.action)
        if (callbacks.isEmpty()) actions.remove(request.target)
    }

    private fun cancelPending(request: PendingAction<*>) {
        request.pending = false
        requests.pending.remove(request)
    }

    internal fun startDrain() {
        drainingRequests.addLast(requests.takePending())
    }

    private class PendingAction<N : Any>(
        val target: N,
        val action: (N) -> Unit,
    ) {
        var pending: Boolean = false

        fun invoke(): Unit = action(target)
    }
}

private fun isAttachedTo(
    scope: Any,
    owner: SwingCompositionOwner,
): Boolean =
    when (scope) {
        is SwingModifier.Node -> scope.isAttached && scope.holder?.owner === owner
        is SwingComponentNode<*> -> scope.isAttached && scope.owner === owner
        else -> false
    }

/** A composition's scheduling operations; pending storage is created only by a deferred request. */
internal class AfterValidationActions(
    private val owner: SwingCompositionOwner,
    private val coordinator: AfterValidationCoordinator,
) {
    private var queue: AfterValidationActionQueue? = null
    private var disposed = false

    fun <N : SwingModifier.Node> requestAfterValidation(
        node: N,
        action: (N) -> Unit,
    ) {
        checkEventDispatchThread()
        val holder = checkNotNull(node.holder) { "Node is not attached" }
        check(holder.owner === owner) { "The node does not belong to this composition" }
        request(node, holder.component, action)
    }

    fun cancelAfterValidation(node: SwingModifier.Node) {
        queue?.cancel(node)
    }

    fun invalidateLayout(node: SwingComponentNode<*>) {
        checkEventDispatchThread()
        check(node.owner === owner) { "The component node does not belong to this composition" }
        if (disposed || !node.isAttached) return
        if (Thread.holdsLock(node.component.treeLock)) {
            node.component.invalidate()
            deferred().request(node, RevalidateComponent)
        } else {
            runNow(node, RevalidateComponent)
        }
    }

    fun <N : SwingComponentNode<*>> requestAfterValidation(
        node: N,
        action: (N) -> Unit,
    ) {
        checkEventDispatchThread()
        check(node.owner === owner) { "The component node does not belong to this composition" }
        request(node, node.component, action)
    }

    fun cancelAfterValidation(node: SwingComponentNode<*>) {
        queue?.cancel(node)
    }

    fun dispose() {
        disposed = true
        queue?.dispose()
    }

    private fun <N : Any> request(
        node: N,
        component: Component,
        action: (N) -> Unit,
    ) {
        if (disposed) return
        if (Thread.holdsLock(component.treeLock)) {
            deferred().request(node, action)
        } else {
            runNow(node, action)
        }
    }

    private fun deferred(): AfterValidationActionQueue =
        queue ?: AfterValidationActionQueue(owner, coordinator).also { queue = it }

    private fun <T : Any> runNow(
        target: T,
        action: (T) -> Unit,
    ) {
        queue?.cancel(target, action)
        action(target)
    }
}

private val RevalidateComponent: (SwingComponentNode<*>) -> Unit = { it.component.revalidate() }

internal val sharedAfterValidationCoordinator: AfterValidationCoordinator = AfterValidationCoordinator()

private class PendingBuffers<T> {
    private val buffers = arrayListOf<MutableList<T>>(ArrayList(), ArrayList())
    private val available = java.util.ArrayDeque<Int>().apply { addLast(1) }
    private var pendingIndex = 0

    val pending: MutableList<T> get() = buffers[pendingIndex]

    fun takePending(): MutableList<T> {
        val batch = pending
        pendingIndex =
            if (available.isEmpty()) {
                buffers.add(ArrayList())
                buffers.lastIndex
            } else {
                available.removeLast()
            }
        return batch
    }

    fun release(batch: MutableList<T>) {
        batch.clear()
        available.addLast(buffers.indexOfFirst { it === batch })
    }
}

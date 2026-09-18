package org.jetbrains.compose.swing.node

import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The snapshot observer of one composition, which every attached node records its reads with, the node
 * itself as the scope. Created and started on first use, so a composition that never observes holds none.
 *
 * @param onChangedExecutor runs the observer's change notifications.
 */
internal class OwnerSnapshotObserver(
    onChangedExecutor: (callback: () -> Unit) -> Unit,
) {
    private val lazyObserver = lazy { SnapshotStateObserver(onChangedExecutor).apply { start() } }

    val observer: SnapshotStateObserver by lazyObserver

    /** Whether [observer] was created. */
    val isStarted: Boolean get() = lazyObserver.isInitialized()

    /**
     * One forwarder per distinct `onChanged` instance, by identity as the observer groups reads, kept while any
     * scope observes under it: it drops a change reported after the node detached, which [clear] cannot
     * withdraw once the observer has queued it.
     */
    private val forwarders = IdentityHashMap<(Nothing) -> Unit, Forwarder<*>>()

    /** Runs [block], recording its reads under [node] and [onChanged]. */
    fun <T : SwingModifier.Node> observeReads(
        node: T,
        onChanged: (T) -> Unit,
        block: () -> Unit,
    ) {
        @Suppress("UNCHECKED_CAST") // Each forwarder is stored under the onChanged it was created for.
        val forwarder = forwarders.getOrPut(onChanged) { Forwarder(onChanged) } as Forwarder<T>
        forwarder.scopes += node
        observer.observeReads(node, forwarder, block)
    }

    /** Drops every read recorded under [scope], without creating the observer where none was created. */
    fun clear(scope: Any) {
        if (!isStarted) return
        observer.clear(scope)
        forwarders.values.removeIf { forwarder ->
            forwarder.scopes.remove(scope)
            forwarder.scopes.isEmpty()
        }
    }

    /** Stops the observer and drops its reads, if it was created. */
    fun dispose() {
        if (isStarted) {
            observer.stop()
            observer.clear()
            forwarders.clear()
        }
    }
}

/** Calls [onChanged] with a scope that is still attached, recording the scopes that observe under it. */
private class Forwarder<T : SwingModifier.Node>(
    private val onChanged: (T) -> Unit,
) : (Any) -> Unit {
    val scopes: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())

    override fun invoke(scope: Any) {
        @Suppress("UNCHECKED_CAST") // Only this forwarder's own observeReads records a scope under it.
        val target = scope as T
        if (target.isAttached) onChanged(target)
    }
}

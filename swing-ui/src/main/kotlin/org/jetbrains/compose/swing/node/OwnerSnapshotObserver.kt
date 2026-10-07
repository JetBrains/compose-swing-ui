package org.jetbrains.compose.swing.node

import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The snapshot observer of one composition, which every attached node records its reads with, the node
 * itself as the scope: a modifier node, or a component node. Created and started on first use, so a
 * composition that never observes holds none.
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
     * modifier node observes under it: it drops a change reported for a node that is no longer attached or that
     * [clear] has since dropped, which [clear] cannot withdraw once the observer has queued it.
     */
    private val modifierForwarders = IdentityHashMap<(Nothing) -> Unit, Forwarder<*>>()

    /** The forwarders of component nodes, kept apart since one `onChanged` may serve both kinds. */
    private val componentForwarders = IdentityHashMap<(Nothing) -> Unit, Forwarder<*>>()

    /** Runs [block], recording its reads under the modifier [node] and [onChanged]. */
    fun <T : SwingModifier.Node> observeReads(
        node: T,
        onChanged: (T) -> Unit,
        block: () -> Unit,
    ) = observeScopeReads(modifierForwarders, node, onChanged, SwingModifier.Node::isAttached, block)

    /** Runs [block], recording its reads under the component [node] and [onChanged]. */
    fun <N : SwingComponentNode<*>> observeReads(
        node: N,
        onChanged: (N) -> Unit,
        block: () -> Unit,
    ) = observeScopeReads(componentForwarders, node, onChanged, SwingComponentNode<*>::isAttached, block)

    private fun <T : Any> observeScopeReads(
        forwarders: IdentityHashMap<(Nothing) -> Unit, Forwarder<*>>,
        node: T,
        onChanged: (T) -> Unit,
        isAttached: (T) -> Boolean,
        block: () -> Unit,
    ) {
        @Suppress("UNCHECKED_CAST") // Each forwarder is stored under its onChanged, in its kind's map.
        val forwarder = forwarders.getOrPut(onChanged) { Forwarder(onChanged, isAttached) } as Forwarder<T>
        forwarder.scopes += node
        observer.observeReads(node, forwarder, block)
    }

    /** Drops every read recorded under the modifier [node], without creating the observer where none was created. */
    fun clear(node: SwingModifier.Node) = clearScope(modifierForwarders, node)

    /** Drops every read recorded under the component [node], without creating the observer where none was created. */
    fun clear(node: SwingComponentNode<*>) = clearScope(componentForwarders, node)

    private fun clearScope(
        forwarders: IdentityHashMap<(Nothing) -> Unit, Forwarder<*>>,
        scope: Any,
    ) {
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
            modifierForwarders.clear()
            componentForwarders.clear()
        }
    }
}

/** Calls [onChanged] with a scope that is still attached and still observes under it, recording the scopes that do. */
private class Forwarder<T : Any>(
    private val onChanged: (T) -> Unit,
    private val isAttached: (T) -> Boolean,
) : (Any) -> Unit {
    val scopes: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())

    override fun invoke(scope: Any) {
        if (scope !in scopes) return
        @Suppress("UNCHECKED_CAST") // Only this forwarder's own observeReads records a scope under it, for a T.
        val target = scope as T
        if (isAttached(target)) onChanged(target)
    }
}

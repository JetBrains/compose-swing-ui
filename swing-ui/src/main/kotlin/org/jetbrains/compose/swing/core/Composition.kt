package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.Component
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Client property key for storing the [CompositionContext] associated with a component,
 * so that nested compositions can discover their parent and share its recomposition scope.
 */
internal const val COMPOSITION_KEY: String = "org.jetbrains.compose.swing.composition"

/**
 * Finds the parent [CompositionContext] by walking the Swing component tree, reading the
 * [COMPOSITION_KEY] client property off each [JComponent].
 *
 * The walk is **self-first**: the receiver itself is checked before its ancestors, so a component
 * stamped with a context (e.g. an interop host, or a window root pane) is discovered by a
 * `setContent` call on that very component, not only by its descendants.
 */
internal fun Component.findParentCompositionContext(): CompositionContext? {
    var current: Component? = this
    while (current != null) {
        if (current is JComponent) {
            (current.getClientProperty(COMPOSITION_KEY) as? CompositionContext)?.let { return it }
        }
        current = current.parent
    }
    return null
}

/**
 * Publishes [context] as [host]'s [COMPOSITION_KEY] client property so that descendant `setContent`
 * calls (and a self-first [findParentCompositionContext] on [host] itself) discover it as their parent.
 *
 * [host] may be `null` (a non-[JComponent] container has no client-property bag to stamp); the call is
 * then a no-op and the returned action does nothing.
 *
 * @return an idempotent action that clears the stamp. The caller invokes it from its own teardown.
 */
internal fun publishCompositionContext(
    host: JComponent?,
    context: CompositionContext,
): () -> Unit {
    if (host == null) return {}
    host.putClientProperty(COMPOSITION_KEY, context)
    return { host.putClientProperty(COMPOSITION_KEY, null) }
}

/**
 * Asserts the caller is on the Swing Event Dispatch Thread, failing loudly otherwise.
 *
 * Composition entry points and applier mutations must run on the EDT; calling them off-EDT
 * leads to non-deterministic corruption that is hard to diagnose, so we fail fast instead.
 */
internal fun checkEventDispatchThread() {
    check(SwingUtilities.isEventDispatchThread()) {
        "Compose-Swing must be used on the Event Dispatch Thread, but was called on " +
            "'${Thread.currentThread().name}'. Wrap the call in SwingUtilities.invokeLater { }."
    }
}

/**
 * Mounts a single island [Composition] as a child of a [CompositionContext].
 *
 * The island shares its parent's recomposition runtime (the parent context owns the recomposer,
 * clock, and scope); this mount owns its [Composition], and the [SnapshotStateObserver] where the
 * island has one. Disposing it disposes what it owns - just this island, never the parent.
 *
 * An island over an applier that observes snapshot state is the composition owner for the components
 * that do (e.g. `Canvas`): it owns one [SnapshotStateObserver] shared by every such component in this
 * composition, each registered as its own scope. The applier stamps that observer onto each node it
 * inserts, so a component reaches it from its [SwingNodeHolder] rather than resolving a
 * `CompositionLocal`.
 */
internal class SwingCompositionMount private constructor(
    private val composition: Composition,
    private val observer: SnapshotStateObserver?,
) {
    fun setContent(content: @Composable () -> Unit) {
        composition.setContent(content)
    }

    /**
     * Applies [writeState] to the island's driving state and then recomposes this island
     * **synchronously**, applying its changes on the caller's thread and driving both passes to
     * completion before returning. This bypasses the parent recomposer's asynchronous, frame-clock-gated
     * loop: the island is a [ControlledComposition], so its own [recompose]/[applyChanges] run inline.
     *
     * Intended for a host that must have its Swing subtree fully materialized the instant it returns - a
     * `ListCellRenderer` stamping the same reused composition for each row Swing asks it to paint.
     *
     * The state writes are performed inside a mutable snapshot whose read/write observers feed this
     * composition directly (via [ControlledComposition.recordReadOf]/[ControlledComposition.recordWriteOf]),
     * rather than left to the parent recomposer to observe on its own (asynchronous) schedule. That is
     * what makes each stamp's write invalidate the cell body *now*, so a synchronous [recompose] here
     * re-runs it - without it, only the parent recomposer would eventually pick the write up, and this
     * synchronous recompose would see nothing to do.
     *
     * Once the mount is [dispose]d a stamp is a no-op: a Swing widget keeps invoking a renderer it
     * captured (focus and layout passes dispatch against a widget while its window is torn down), so
     * this call stays safe on a disposed island instead of recomposing it. [writeState] is skipped
     * too - recording reads and writes against a disposed composition is dead work.
     *
     * Must be called on the Event Dispatch Thread.
     */
    fun recomposeSynchronously(writeState: () -> Unit) {
        if (composition.isDisposed) return
        val controlled = composition as? ControlledComposition
        if (controlled == null) {
            writeState()
            return
        }
        // Recompose the island inside a mutable snapshot whose read/write observers feed this
        // composition, exactly as a recomposer wraps a composition it drives. This is load-bearing: the
        // composition only re-records the state it reads (its `observations`) when composed under such a
        // snapshot, so without this wrapper the FIRST stamp would compose but a SECOND would find nothing
        // observing the row inputs and skip recomposing - the cell would freeze on the first row's value.
        val snapshot =
            Snapshot.takeMutableSnapshot(
                readObserver = { controlled.recordReadOf(it) },
                writeObserver = { controlled.recordWriteOf(it) },
            )
        try {
            snapshot.enter {
                // The row inputs are written here so the write observer records them against the
                // composition, invalidating the scopes that read them; recompose() then re-runs those
                // scopes and applyChanges() flushes them through the applier so the Swing tree is
                // materialized before this returns. Both are synchronous and take no frame from the
                // parent recomposer's clock.
                writeState()
                if (controlled.recompose()) {
                    controlled.applyChanges()
                }
            }
        } finally {
            snapshot.apply().check()
            snapshot.dispose()
        }
    }

    /** Disposes this island's [Composition] and stops the owner-level [SnapshotStateObserver] it owns. */
    fun dispose() {
        composition.dispose()
        observer?.stop()
        observer?.clear()
    }

    companion object {
        /**
         * Mounts a child composition of [parent]. [applierFactory] builds the [Applier] over the
         * owner's freshly started [SnapshotStateObserver], which [SwingApplier] stamps onto every node
         * it inserts so a snapshot-observing component can adopt it.
         */
        fun nested(
            parent: CompositionContext,
            applierFactory: (SnapshotStateObserver) -> Applier<*>,
        ): SwingCompositionMount {
            GlobalSnapshotManager.ensureStarted()
            // One observer shared by every snapshot-observing component (e.g. Canvas) in this owner. The
            // change callback runs directly: apply notifications are already pumped on the event dispatch
            // thread (see GlobalSnapshotManager) and a component reacts with repaint(), which is
            // thread-safe, so no extra thread marshaling is needed.
            val observer = SnapshotStateObserver { onChanged -> onChanged() }.apply { start() }
            return SwingCompositionMount(
                composition = Composition(applierFactory(observer), parent),
                observer = observer,
            )
        }

        /**
         * Mounts a child composition of [parent] over an applier that observes no snapshot state, so the
         * island owns no [SnapshotStateObserver] and registers none globally. A menu composition holds
         * no component that paints from observed reads.
         */
        fun nestedUnobserved(
            parent: CompositionContext,
            applierFactory: () -> Applier<*>,
        ): SwingCompositionMount {
            GlobalSnapshotManager.ensureStarted()
            return SwingCompositionMount(composition = Composition(applierFactory(), parent), observer = null)
        }
    }
}

/**
 * The parent-container layout constraint a child Swing node should be added with.
 *
 * A container provides the placement for the children it composes - a `BorderPanel` its region per
 * slot, any container `org.jetbrains.compose.swing.SwingConstraint` - and [SwingNode] records the
 * value on the node (see [SwingNodeHolder.constraint]) so the applier adds the component with that
 * constraint. This lets the parent decide placement without the child knowing its container's layout
 * manager.
 *
 * A container node consumes the value for its OWN placement and provides the default again to its
 * content, so a constraint never reaches past the children the container that declared it composes.
 *
 * Defaults to `null`, meaning "add by index" (no explicit constraint).
 */
@PublishedApi
internal val LocalSwingConstraint: ProvidableCompositionLocal<Any?> = staticCompositionLocalOf { null }

/**
 * Carries a [SlotAttachment] down to the single [SwingNode] a slot hosts, the way
 * [LocalSwingConstraint] carries a layout constraint.
 *
 * A parent that owns a Swing host with dedicated single-occupancy slots (e.g. a `JScrollPane`'s
 * viewport / header / corner regions) provides this via [SlotNode]. [SwingNode] reads it into
 * [SwingNodeHolder.slotAttachment] so the applier installs the component through the attachment
 * instead of the generic `Container.add`. This lets the parent dictate how its content is attached
 * without the child knowing the host.
 *
 * Defaults to `null`, meaning "add as an ordinary child by index".
 */
@PublishedApi
internal val LocalSlotAttachment: ProvidableCompositionLocal<SlotAttachment?> =
    staticCompositionLocalOf { null }

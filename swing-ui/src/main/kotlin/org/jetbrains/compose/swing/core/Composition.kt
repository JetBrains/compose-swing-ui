package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import java.awt.Component
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Client property key under which a component's [CompositionContext] is stored, so nested
 * compositions can find their parent and share its recomposition scope.
 */
internal val COMPOSITION_KEY: Key<CompositionContext> = Key("org.jetbrains.compose.swing.composition")

/**
 * Finds the parent [CompositionContext] by walking the Swing component tree, reading two things off each
 * component on the way up: the [COMPOSITION_KEY] client property a host stamps on a [JComponent], and the
 * context a live `setContent` island on that component composes its content under. The nearest ancestor
 * that answers wins, so the innermost composition around this component is the one it joins.
 *
 * The client-property walk is self-first: it checks the receiver before its ancestors, so a component
 * stamped with a context (an interop host, or a window root pane) is found by a `setContent` call on that
 * component itself, not only by its descendants. An island's context answers for what hangs **inside**
 * its container, so the receiver's own islands are passed over: a container asks where it hangs, not what
 * it already carries.
 */
internal fun Component.findParentCompositionContext(): CompositionContext? {
    var current: Component? = this
    while (current != null) {
        current.compositionContextHere(walkStartedAt = this)?.let { return it }
        current = current.parent
    }
    return null
}

/**
 * What this component alone answers the walk with: the [COMPOSITION_KEY] stamp a host published on it,
 * or the context of a live island composing into it. An island answers only for what hangs inside its
 * container, so the component the walk started at contributes its stamp and none of its islands.
 */
private fun Component.compositionContextHere(walkStartedAt: Component): CompositionContext? =
    (this as? JComponent)?.get(COMPOSITION_KEY)
        ?: takeIf { it !== walkStartedAt }?.islandCompositionContextOrNull()

/**
 * Sets [context] as this component's [COMPOSITION_KEY] client property, so descendant `setContent` calls
 * - and a self-first [findParentCompositionContext] on this component itself - find it as their parent,
 * and clears it again when passed `null`.
 *
 * The stamp is the caller's: clear it from the same teardown that ends the composition behind it. A
 * container that is no [JComponent] carries no client-property bag, so a caller holding one of those
 * stamps nothing.
 */
internal fun JComponent.setCompositionContext(context: CompositionContext?) {
    this[COMPOSITION_KEY] = context
}

/**
 * Asserts the caller is on the Swing Event Dispatch Thread, failing loudly otherwise.
 *
 * Composition entry points and applier mutations must run on the EDT. Off-EDT, they corrupt state in
 * ways that are hard to diagnose, so this fails fast instead.
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
 * The island shares its parent's recomposition runtime - the parent context owns the recomposer, clock
 * and scope. This mount owns only its [Composition] and, where the island has one, its
 * [SnapshotStateObserver]; disposing it disposes just this island, never the parent.
 *
 * An island over an applier that observes snapshot state is the composition owner for the components
 * that do (`Canvas`, for example): it owns one [SnapshotStateObserver] shared by every such component,
 * each registered as its own scope. The applier stamps that observer onto every node it inserts, so a
 * component reaches it through its [org.jetbrains.compose.swing.node.SwingNodeHolder] instead of
 * resolving a `CompositionLocal`.
 */
internal class SwingCompositionMount private constructor(
    private val composition: Composition,
    private val observer: SnapshotStateObserver?,
) {
    fun setContent(content: @Composable () -> Unit) {
        composition.setContent(content)
    }

    /**
     * Applies [writeState] to the island's driving state, then recomposes this island synchronously:
     * both passes run and complete on the caller's thread before this returns. This bypasses the parent
     * recomposer's asynchronous, frame-clock-gated loop, using the island's own
     * [ControlledComposition.recompose] and [ControlledComposition.applyChanges] directly.
     *
     * Intended for a host that must have its Swing subtree fully materialized the instant it returns - a
     * `ListCellRenderer` stamping the same reused composition for each row Swing asks it to paint.
     *
     * [writeState] runs inside a mutable snapshot whose read/write observers feed this composition
     * directly, so its writes invalidate the composition now instead of waiting for the parent
     * recomposer's own schedule.
     *
     * Once the mount is [dispose]d, a stamp is a no-op instead of an error: a Swing widget keeps invoking
     * a renderer it captured even while its window is torn down (during focus and layout passes), so
     * this call must stay safe to make on a disposed island. [writeState] is skipped too, since recording
     * reads and writes against a disposed composition is dead work.
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
        // Recompose inside a mutable snapshot whose observers feed this composition, the way a
        // recomposer wraps a composition it drives. A composition only re-records the state it reads
        // when composed under such a snapshot: without this, a second stamp would find nothing observing
        // the row inputs and skip recomposing, freezing the cell on the first row's value.
        val snapshot =
            Snapshot.takeMutableSnapshot(
                readObserver = { controlled.recordReadOf(it) },
                writeObserver = { controlled.recordWriteOf(it) },
            )
        try {
            snapshot.enter {
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
         * owner's freshly started [SnapshotStateObserver], which
         * [org.jetbrains.compose.swing.node.SwingApplier] stamps onto every node it inserts so a
         * snapshot-observing component can adopt it.
         */
        fun nested(
            parent: CompositionContext,
            applierFactory: (SnapshotStateObserver) -> Applier<*>,
        ): SwingCompositionMount {
            GlobalSnapshotManager.ensureStarted()
            // Shared by every snapshot-observing component (e.g. Canvas) in this owner. The callback runs
            // directly, with no invokeLater: an ordinary write's notification already runs on the EDT
            // (see GlobalSnapshotManager), and marshaling would only add a turn's latency to what the
            // callback actually does here - schedule a repaint(), which is thread-safe regardless of what
            // thread calls it, so nothing here depends on the notification having arrived on the EDT.
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

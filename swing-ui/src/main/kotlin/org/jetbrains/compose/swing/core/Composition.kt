package org.jetbrains.compose.swing.core

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.node.ComponentUpdateBatch
import org.jetbrains.compose.swing.node.SwingCompositionOwner
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.tooling.InspectedContent
import org.jetbrains.compose.swing.tooling.InspectionGate
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
 * context a live `setContent` composition rooted on that component composes its content under. The nearest
 * ancestor that answers wins, so the innermost composition around this component is the one it joins.
 *
 * The client-property walk is self-first: it checks the receiver before its ancestors, so a component
 * stamped with a context (an interop host, or a window root pane) is found by a `setContent` call on that
 * component itself, not only by its descendants. A content composition's context answers for what hangs
 * **inside** its container, so the receiver's own content compositions are passed over: a container asks
 * where it hangs, not what it already carries.
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
 * or the context of a live content composition composing into it. A content composition answers only for
 * what hangs inside its container, so the component the walk started at contributes its stamp alone.
 */
private fun Component.compositionContextHere(walkStartedAt: Component): CompositionContext? =
    (this as? JComponent)?.get(COMPOSITION_KEY)
        ?: takeIf { it !== walkStartedAt }?.contentCompositionContextOrNull()

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
 * A single content composition - a [Composition] rooted on a Swing container, mounted as a child of a
 * [CompositionContext].
 *
 * The parent context owns the recomposer, clock and scope this composition runs on. The content
 * composition owns only its own [Composition] and what that composition's nodes share; disposing it
 * disposes only this composition, never the parent.
 *
 * It is that shared thing: it is the content composition's [SwingCompositionOwner], holding the observer
 * every snapshot-observing component in it registers with, the batch its updates are held in, and the
 * call that settles a reported change. Nodes see it through that interface alone, so a node reaches what
 * its composition owns without reaching the composition's lifecycle. It attaches its applier's root, and
 * every node below takes it from its parent as it is inserted.
 */
internal class SwingContentComposition private constructor(
    private val parent: CompositionContext,
    observed: Boolean,
    applierFactory: (SwingCompositionOwner) -> AbstractApplier<SwingNodeHolder<*>>,
) : SwingCompositionOwner {
    /**
     * The observer's callback runs directly, with no `invokeLater`: an ordinary write's notification
     * already runs on the event dispatch thread (see [GlobalSnapshotManager]), and marshaling would only
     * add a turn's latency to what the callback actually does - schedule a `repaint()`, which is
     * thread-safe whatever thread calls it.
     */
    override val observer: SnapshotStateObserver? =
        if (observed) SnapshotStateObserver { onChanged -> onChanged() }.apply { start() } else null

    override fun settleNow(): Unit = parent.swingFrameClock()?.settleInPlace() ?: Unit

    override val updateBatch: ComponentUpdateBatch = ComponentUpdateBatch()

    // Built after everything a node reads through this owner, because the applier attaches its root to
    // this owner as it is created and the composition inserts nodes against it from its first pass.
    private val applier = applierFactory(this)

    private val composition = Composition(applier, parent)

    /**
     * The component this composition is rooted at, when it is one that carries a client-property bag.
     * This is the component the composition publishes its slot table on, and so the one an inspecting
     * walk up from a declared component reaches.
     */
    private val host: JComponent? = applier.root.component as? JComponent

    /** Whether this composition records where it declared each component. */
    private val inspection = InspectionGate()

    fun setContent(content: @Composable () -> Unit) {
        composition.setContent { InspectedContent(host, inspection.isRecording, content) }
    }

    /**
     * Applies [writeState] to this composition's driving state, then recomposes it synchronously: both
     * passes run and complete on the caller's thread before this returns. This bypasses the parent
     * recomposer's asynchronous, frame-clock-gated loop, using this composition's own
     * [ControlledComposition.recompose] and [ControlledComposition.applyChanges] directly.
     *
     * Intended for a host that must have its Swing subtree fully materialized the instant it returns - a
     * `ListCellRenderer` stamping the same reused composition for each row Swing asks it to paint.
     *
     * [writeState] runs inside a mutable snapshot whose read/write observers feed this composition
     * directly, so its writes invalidate the composition now instead of waiting for the parent
     * recomposer's own schedule.
     *
     * Once this composition is [dispose]d, a stamp is a no-op instead of an error: a Swing widget keeps
     * invoking a renderer it captured even while its window is torn down (during focus and layout
     * passes), so this call must stay safe to make on a disposed composition. [writeState] is skipped
     * too, since recording reads and writes against a disposed composition is dead work.
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

    /**
     * Disposes this content composition's [Composition] and the [SwingCompositionOwner] it owns.
     *
     * Must be called on the Event Dispatch Thread. A handle a caller holds can be disposed from
     * anywhere - a coroutine's completion, most of all - and this writes the Swing tree and the
     * library's record of what is mounted, so it fails loudly rather than corrupting either.
     */
    fun dispose() {
        checkEventDispatchThread()
        composition.dispose()
        observer?.stop()
        observer?.clear()
    }

    companion object {
        /**
         * Mounts a child composition of [parent] whose nodes register their snapshot reads with an
         * observer of its own, which is what a component that paints from observed reads adopts.
         */
        fun nested(
            parent: CompositionContext,
            applierFactory: (SwingCompositionOwner) -> AbstractApplier<SwingNodeHolder<*>>,
        ): SwingContentComposition = mount(parent, observed = true, applierFactory)

        /**
         * Mounts a child composition of [parent] that observes no snapshot state and registers none
         * globally. A menu composition holds no component that paints from observed reads.
         */
        fun nestedUnobserved(
            parent: CompositionContext,
            applierFactory: (SwingCompositionOwner) -> AbstractApplier<SwingNodeHolder<*>>,
        ): SwingContentComposition = mount(parent, observed = false, applierFactory)

        private fun mount(
            parent: CompositionContext,
            observed: Boolean,
            applierFactory: (SwingCompositionOwner) -> AbstractApplier<SwingNodeHolder<*>>,
        ): SwingContentComposition {
            GlobalSnapshotManager.ensureStarted()
            return SwingContentComposition(parent, observed, applierFactory)
        }
    }
}

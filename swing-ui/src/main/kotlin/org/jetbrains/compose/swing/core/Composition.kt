package org.jetbrains.compose.swing.core

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.AfterValidationActions
import org.jetbrains.compose.swing.node.ComponentUpdateBatch
import org.jetbrains.compose.swing.node.OwnerSnapshotObserver
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingCompositionOwner
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.sharedAfterValidationCoordinator
import org.jetbrains.compose.swing.tooling.InspectedContent
import org.jetbrains.compose.swing.tooling.InspectionGate
import org.jetbrains.compose.swing.util.Key
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import java.awt.Component
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

/**
 * Client property key under which a component's [CompositionContext] is stored, so nested
 * compositions can find their parent and share its recomposition scope.
 */
internal val COMPOSITION_KEY: Key<CompositionContext> = Key("org.jetbrains.compose.swing.composition")

/**
 * Finds the parent [CompositionContext] by walking the Swing component tree, reading two things off each
 * component on the way up: the [COMPOSITION_KEY] client property a host publishes on a [JComponent], and the
 * context a live `setContent` composition rooted on that component composes its content under. The nearest
 * ancestor that answers wins, so the innermost composition around this component is the one it joins.
 *
 * The client-property walk is self-first: it checks the receiver before its ancestors, so a component
 * published with a context (an interop host, or a window root pane) is found by a `setContent` call on that
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
 * What this component alone answers the walk with: the [COMPOSITION_KEY] context a host published on it,
 * or the context of a live content composition composing into it. A content composition answers only for
 * what hangs inside its container, so the component the walk started at contributes its published context alone.
 */
private fun Component.compositionContextHere(walkStartedAt: Component): CompositionContext? =
    (this as? JComponent)?.get(COMPOSITION_KEY)
        ?: takeIf { it !== walkStartedAt }?.contentCompositionOrNull()?.publishedContext

/**
 * Sets [context] as this component's [COMPOSITION_KEY] client property, so descendant `setContent` calls
 * - and a self-first [findParentCompositionContext] on this component itself - find it as their parent,
 * and clears it again when passed `null`.
 *
 * The published context is the caller's: clear it from the same teardown that ends the composition behind it. A
 * container that is no [JComponent] carries no client-property bag, so a caller holding one of those
 * publishes nothing.
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
 * Runs [block] in a mutable snapshot that reports its reads to [readObserver] and its writes to
 * [writeObserver], then applies what it wrote.
 *
 * A [block] that throws publishes nothing: the snapshot is dropped, so the state the failed pass wrote
 * is not what anything reads next.
 *
 * This is [Snapshot.withMutableSnapshot] with observers, which that helper does not take, and with the
 * snapshot disposed even where applying it conflicts.
 */
private inline fun <R> withMutableSnapshot(
    noinline readObserver: (Any) -> Unit,
    noinline writeObserver: (Any) -> Unit,
    block: () -> R,
): R {
    val snapshot = Snapshot.takeMutableSnapshot(readObserver, writeObserver)
    return try {
        val result = snapshot.enter(block)
        snapshot.apply().check()
        result
    } finally {
        snapshot.dispose()
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
 * every node below takes it from its parent as it is inserted. It detaches the root as it is disposed, and when
 * its construction fails.
 */
internal class SwingContentComposition private constructor(
    private val parent: CompositionContext,
    private val root: SwingNodeHolder<*>,
    applierFactory: () -> AbstractApplier<SwingNodeHolder<*>>,
) : SwingCompositionOwner {
    /**
     * The observer's callbacks run on the event dispatch thread: inline when the change is published
     * there, which is where [GlobalSnapshotManager] publishes an ordinary write, and posted with
     * `invokeLater` when a snapshot is applied on another thread.
     */
    override val snapshotObserver: OwnerSnapshotObserver =
        OwnerSnapshotObserver { onChanged ->
            if (SwingUtilities.isEventDispatchThread()) onChanged() else SwingUtilities.invokeLater(onChanged)
        }

    private val afterValidationActions = AfterValidationActions(this, sharedAfterValidationCoordinator)

    override fun <N : SwingModifier.Node> requestAfterValidation(
        node: N,
        action: (N) -> Unit,
    ): Unit = afterValidationActions.requestAfterValidation(node, action)

    override fun cancelAfterValidation(node: SwingModifier.Node): Unit =
        afterValidationActions.cancelAfterValidation(node)

    override fun <N : SwingComponentNode<*>> requestAfterValidation(
        node: N,
        action: (N) -> Unit,
    ): Unit = afterValidationActions.requestAfterValidation(node, action)

    override fun invalidateLayout(node: SwingComponentNode<*>): Unit = afterValidationActions.invalidateLayout(node)

    override fun cancelAfterValidation(node: SwingComponentNode<*>): Unit =
        afterValidationActions.cancelAfterValidation(node)

    override fun settleNow(): Unit = parent.swingFrameClock()?.settleInPlace() ?: Unit

    override val updateBatch: ComponentUpdateBatch = ComponentUpdateBatch()

    override val diagnostics: SwingCompositionDiagnostics? = parent.effectCoroutineContext[SwingCompositionDiagnostics]

    override val coroutineContext: CoroutineContext = parent.effectCoroutineContext

    // Built after everything a node reads through this owner, because the root attaches to this owner here and
    // the composition inserts nodes against it from its first pass. A construction that throws hands its caller
    // nothing to dispose, so the root is detached and the owner released before the failure gets out.
    private val applier =
        disposingOnFailure(::release) {
            root.attachedTo(this)
            applierFactory()
        }

    private val composition = Composition(applier, parent)

    /**
     * The component this composition is rooted at, when it is one that carries a client-property bag.
     * This is the component the composition publishes its slot table on, and so the one an inspecting
     * walk up from a declared component reaches.
     */
    private val host: JComponent? = root.component as? JComponent

    /** Whether this composition records where it declared each component. */
    private val inspection = InspectionGate()

    /**
     * Composes [content] as this composition's content, on the call.
     *
     * A first pass that throws leaves nothing standing: this composition is disposed - its observer
     * withdrawn from the global apply observers with it - before the failure reaches the caller.
     */
    fun setContent(content: @Composable () -> Unit) {
        disposingOnFailure(::dispose) {
            composition.setContent { InspectedContent(host, inspection.isRecording, content) }
        }
    }

    /**
     * Applies [writeState] to this composition's driving state, then recomposes it synchronously: both
     * passes run and complete on the caller's thread before this returns. This bypasses the parent
     * recomposer's asynchronous, frame-clock-gated loop, using this composition's own
     * [ControlledComposition.recompose] and [ControlledComposition.applyChanges] directly.
     *
     * Intended for a host that must have its Swing subtree fully materialized the instant it returns - a
     * `ListCellRenderer` rendering the same reused composition for each row Swing asks it to paint.
     *
     * [writeState] runs inside a mutable snapshot whose read/write observers feed this composition
     * directly, so its writes invalidate the composition now instead of waiting for the parent
     * recomposer's own schedule.
     *
     * Once this composition is [dispose]d, a render is a no-op instead of an error: a Swing widget keeps
     * invoking a renderer it captured even while its window is torn down (during focus and layout
     * passes), so this call must stay safe to make on a disposed composition. [writeState] is skipped
     * too, since recording reads and writes against a disposed composition is dead work.
     *
     * A pass that throws publishes none of its writes. Composing leaves the composition invalidated - the
     * runtime restores the invalidations a failed pass was going to answer - so the parent recomposer
     * recomposes it on a frame of its own, reading whatever the pass published. Published, that is the
     * state the throw came out of, so the throw happens again inside the recomposer and ends it. Dropped,
     * the failure reaches the caller that provoked it and nothing else composes the state it failed on.
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
        // The observers are what make this composition re-record the state it reads, the way a recomposer
        // wraps a composition it drives: without them a second render would find nothing observing the row
        // inputs and skip recomposing, freezing the cell on the first row's value.
        withMutableSnapshot(
            readObserver = { controlled.recordReadOf(it) },
            writeObserver = { controlled.recordWriteOf(it) },
        ) {
            writeState()
            if (controlled.recompose()) {
                controlled.applyChanges()
            }
        }
    }

    /**
     * Disposes this content composition's [Composition] and the [SwingCompositionOwner] it owns, and detaches the
     * applier's root.
     *
     * Must be called on the Event Dispatch Thread. A handle a caller holds can be disposed from
     * anywhere - a coroutine's completion, most of all - and this writes the Swing tree and the
     * library's record of what is mounted, so it fails loudly rather than corrupting either.
     */
    fun dispose() {
        checkEventDispatchThread()
        try {
            composition.dispose()
        } finally {
            release()
        }
    }

    /** Detaches the root and ends what this owner holds for its nodes. */
    private fun release() {
        try {
            root.attachedTo(null)
        } finally {
            afterValidationActions.dispose()
            snapshotObserver.dispose()
        }
    }

    companion object {
        /**
         * Mounts a child composition of [parent] rooted at [root], which [applierFactory] builds the applier over
         * once [root] is attached.
         */
        fun <T : Component> nested(
            parent: CompositionContext,
            root: SwingNodeHolder<T>,
            applierFactory: (root: SwingNodeHolder<T>) -> AbstractApplier<SwingNodeHolder<*>>,
        ): SwingContentComposition {
            GlobalSnapshotManager.ensureStarted()
            return SwingContentComposition(parent, root) { applierFactory(root) }
        }
    }
}

/**
 * Runs [dispose] if [block] throws, then rethrows the original failure with any cleanup failure suppressed.
 */
internal inline fun <R> disposingOnFailure(
    dispose: () -> Unit,
    block: () -> R,
): R =
    try {
        block()
    } catch (
        @Suppress("TooGenericExceptionCaught") failure: Throwable,
    ) {
        runCatching(dispose).onFailure { cleanupFailure ->
            // The failure keeps its identity even when the cleanup also fails.
            if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
        }
        throw failure
    }

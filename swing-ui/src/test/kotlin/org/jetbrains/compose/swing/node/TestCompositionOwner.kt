package org.jetbrains.compose.swing.node

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import kotlin.coroutines.CoroutineContext

/**
 * The [SwingCompositionOwner] a test attaches an applier's root to, standing in for the content
 * composition that owns one in production. Its observer is created on first access, as a composition's is.
 */
internal class TestCompositionOwner : SwingCompositionOwner {
    override val snapshotObserver: OwnerSnapshotObserver = OwnerSnapshotObserver { onChanged -> onChanged() }

    /** A case that drives an applier without a composition around it has no pass to settle. */
    override fun settleNow(): Unit = Unit

    override val updateBatch: ComponentUpdateBatch = ComponentUpdateBatch()

    override val diagnostics: SwingCompositionDiagnostics? = null

    /**
     * A job of its own, so a node's `coroutineScope` cancels independently of this owner's lifetime.
     * [Dispatchers.Unconfined] is this fixture's own fixed choice, not something a test injects - it is
     * what lets a case with no frame clock or event-loop pump of its own observe a launched coroutine run
     * its first suspension point synchronously.
     */
    @Suppress("InjectDispatcher")
    override val coroutineContext: CoroutineContext = Job() + Dispatchers.Unconfined

    /** Stops the observer this owner started, if it started one, and cancels what its effect context ran. */
    fun dispose() {
        snapshotObserver.dispose()
        coroutineContext[Job]?.cancel()
    }
}

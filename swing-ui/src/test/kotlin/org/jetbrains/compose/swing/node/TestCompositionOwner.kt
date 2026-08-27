package org.jetbrains.compose.swing.node

import androidx.compose.runtime.snapshots.SnapshotStateObserver

/**
 * The [SwingCompositionOwner] a test attaches an applier's root to, standing in for the mount that owns
 * one in production.
 *
 * A case that drives an applier without a composition around it observes nothing unless it asks for
 * [observing]: what it needs from an owner is the update batch every applier holds its pass in.
 */
internal class TestCompositionOwner private constructor(
    override val observer: SnapshotStateObserver?,
) : SwingCompositionOwner {
    override val updateBatch: ComponentUpdateBatch = ComponentUpdateBatch()

    /** Stops the observer this owner started, if it started one. */
    fun dispose() {
        observer?.stop()
        observer?.clear()
    }

    companion object {
        /** An owner over a freshly started observer, for a case whose components observe snapshot state. */
        fun observing(): TestCompositionOwner =
            TestCompositionOwner(SnapshotStateObserver { onChanged -> onChanged() }.apply { start() })

        /** An owner that observes nothing, which is what a menu composition has. */
        fun unobserved(): TestCompositionOwner = TestCompositionOwner(observer = null)
    }
}

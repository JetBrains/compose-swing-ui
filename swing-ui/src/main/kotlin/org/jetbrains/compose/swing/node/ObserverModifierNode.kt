package org.jetbrains.compose.swing.node

import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.modifier.SwingModifier

/** A [SwingModifier.Node] notified through [onObservedReadsChanged] when a value read in [observeReads] changes. */
public interface ObserverModifierNode {
    /**
     * Runs once on the event dispatch thread, after the snapshot changing a value read in [observeReads] is
     * committed. Call [observeReads] again to keep observing.
     */
    public fun onObservedReadsChanged()
}

/**
 * Runs [block], recording its reads under this node; after one of them changes, calls
 * [ObserverModifierNode.onObservedReadsChanged] once, while the node is attached.
 *
 * @throws IllegalStateException if this node is not attached.
 */
public fun <T> T.observeReads(block: () -> Unit): Unit where T : SwingModifier.Node, T : ObserverModifierNode =
    observeReads(OnObservedReadsChanged, block)

/**
 * Runs [block], recording its reads under this node and [onChanged]; after one of them changes, calls
 * [onChanged] with this node once, while it is attached. Reads are grouped by the [onChanged] instance, as
 * [SnapshotStateObserver.observeReads] groups them: pass the same instance on every call, and use one
 * instance per set of reads that must not drop another's.
 *
 * @throws IllegalStateException if this node is not attached.
 */
public fun <T : SwingModifier.Node> T.observeReads(
    onChanged: (T) -> Unit,
    block: () -> Unit,
) {
    requireOwner().snapshotObserver.observeReads(this, onChanged, block)
}

private val OnObservedReadsChanged: (SwingModifier.Node) -> Unit = {
    (it as ObserverModifierNode).onObservedReadsChanged()
}

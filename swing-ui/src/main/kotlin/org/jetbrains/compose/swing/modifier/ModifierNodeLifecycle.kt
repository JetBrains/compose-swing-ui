package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.util.fastForEach

/**
 * Runs each node's [SwingModifier.Node.onDetach] in order, and only then marks the nodes detached. A node whose
 * lifecycle fails a library check stops the remaining runs, and the nodes are marked detached before the failure
 * propagates.
 */
internal fun List<NodeRecord<*, *>>.detachAll(diagnostics: SwingCompositionDiagnostics?) {
    try {
        fastForEach { it.runDetachLifecycle(diagnostics) }
    } finally {
        fastForEach { it.markDetached() }
    }
}

package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.util.fastForEach

/**
 * Runs each node's [SwingModifier.Node.onDetach] in order, and only then marks the nodes detached. A step that
 * fails does not stop the others: every lifecycle step is attempted and every node is marked detached, and the
 * first failure propagates with the later ones suppressed on it. A failed library check before a node's callback
 * prevents that callback from running.
 */
internal fun List<NodeRecord<*, *>>.detachAll(diagnostics: SwingCompositionDiagnostics?) {
    var first: Throwable? = null
    fastForEach { node -> first = collectFailure(first) { node.runDetachLifecycle(diagnostics) } }
    fastForEach { node -> first = collectFailure(first) { node.markDetached() } }
    first?.let { throw it }
}

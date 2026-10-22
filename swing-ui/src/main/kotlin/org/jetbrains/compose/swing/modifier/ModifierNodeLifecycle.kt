package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.core.runEveryStep
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.util.fastForEach

/**
 * Detaches every node of the modifier from the composition it stands in, in [SwingModifierState.standingInUnwindOrder]:
 * the nodes run [SwingModifier.Node.onDetach] as [detachAll] does, and only then is any node marked detached. Every
 * slot stands, for [attachNodes] to attach the same nodes again.
 */
internal fun SwingModifierState.detachNodes(diagnostics: SwingCompositionDiagnostics?): Unit =
    standingInUnwindOrder().detachAll(diagnostics)

/**
 * Attaches the nodes [detachNodes] detached to the composition [holder] stands in now, as a chain attaching whole
 * does: every node is marked attached, then the parent-layout nodes, the property nodes and the additive
 * component nodes in turn run [SwingModifier.Node.onAttach] and their element's `update`. If `onAttach` throws, the
 * node is detached and the failure propagates. A failure in a step does not skip the cleanup after it: every node
 * that never attached is detached and the chain is put back in order, and the first failure propagates with the
 * later ones suppressed on it.
 */
internal fun SwingModifierState.attachNodes(
    holder: SwingNodeHolder<*>,
    diagnostics: SwingCompositionDiagnostics?,
) {
    val held = startWrite()
    val keys = ArrayList<Any>(records.size)
    forEachStandingKeyInUnwindOrder { keys += it }
    keys.reverse()
    val chainOrder = ArrayList(chain)
    val marked =
        MarkedChain(
            keys.map { records.getValue(it) },
            chainOrder.filterIsInstance<ElementRecord<*, *>>(),
            chainOrder.filterIsInstance<LayoutNodeRecord>(),
        )
    records.clear()
    chain.clear()
    marked.markAttached(holder)
    val batch = holder.owner?.updateBatch
    val outerState = batch?.diffingState
    val outerDeclaration = batch?.diffingDeclaration
    batch?.diffingState = this
    batch?.diffingDeclaration = chainOrder.map { it.element }
    try {
        runEveryStep(
            {
                marked.runLayoutLifecycles(this, diagnostics)
                marked.runComponentLifecycles(this, keys, diagnostics)
                applied = held
            },
            { marked.detachOrphaned(this) },
            { chain.sortBy(chainOrder::indexOf) },
        )
    } finally {
        batch?.diffingState = outerState
        batch?.diffingDeclaration = outerDeclaration
    }
}

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

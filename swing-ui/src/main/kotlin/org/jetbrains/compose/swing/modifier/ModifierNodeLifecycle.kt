package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.core.runEveryStep
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.util.fastForEach

/**
 * Detaches every node of the modifier from its composition, in [SwingModifierState.standingInUnwindOrder]:
 * the nodes run [SwingModifier.Node.onDetach] as [detachAll] does, and only then is any node marked detached. Every
 * record stays standing, for [attachNodes] to attach the same nodes again.
 *
 * @param diagnostics where a failing callback is reported, or `null` for none.
 */
internal fun SwingModifierState.detachNodes(diagnostics: SwingCompositionDiagnostics?): Unit =
    standingInUnwindOrder().detachAll(diagnostics)

/**
 * Attaches the nodes [detachNodes] detached to the composition [holder] belongs to, as a chain attaching whole
 * does: every node is marked attached, then the parent-layout nodes, the property nodes and the additive
 * component nodes in turn run [SwingModifier.Node.onAttach] and their element's `update`. If `onAttach` throws, the
 * node is detached and the failure propagates. A failure in a step does not skip the cleanup after it: every node
 * that never attached is detached and the chain is put back in order, and the first failure propagates with the
 * later ones suppressed on it.
 *
 * @param holder the holder whose component the nodes target and whose owner supplies the update batch.
 * @param diagnostics where a failing callback is reported, or `null` for none.
 */
internal fun SwingModifierState.attachNodes(
    holder: SwingNodeHolder<*>,
    diagnostics: SwingCompositionDiagnostics?,
) {
    val held = startWrite()
    val keys = ArrayList<ComponentNodeRecord<*, *>>()
    records?.fastForEach { record ->
        if (record.isStandingFor(ComponentNodeType, null) && record.key != null) {
            keys.add(ComponentNodeType.typed(record))
        }
    }
    // A copy: the withdrawals below take these records out of the store while the steps still need them.
    val positional = ArrayList<NodeRecord<*, *>>(positionalCount)
    records?.fastForEach { if (it.isStandingFor(null, null) && it.key == null) positional.add(it) }
    val marked =
        MarkedChain(
            keys,
            positional.filterIsInstance<ComponentNodeRecord<*, *>>(),
            positional.filterIsInstance<ParentLayoutNodeRecord>(),
        )
    keys.fastForEach { records?.withdraw(it) }
    positional.fastForEach { records?.withdraw(it) }
    marked.markAttached(holder)
    val batch = holder.owner?.updateBatch
    val outerState = batch?.diffingState
    val outerDeclaration = batch?.diffingDeclaration
    batch?.diffingState = this
    batch?.diffingDeclaration =
        ArrayList<SwingModifier.Element>(positional.size).also { elements ->
            positional.fastForEach { elements.add(it.element) }
        }
    try {
        runEveryStep(
            {
                marked.runLayoutLifecycles(this, diagnostics)
                marked.runComponentLifecycles(this, diagnostics)
                adopt(held)
            },
            { marked.detachOrphaned(this) },
            {
                val ordered = ArrayList<NodeRecord<*, *>>(positional.size)
                positional.fastForEach { if (it.isStanding) ordered.add(it) }
                records?.order?.replace(null, null, ordered)
            },
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
 *
 * @param diagnostics where a failing callback is reported, or `null` for none.
 */
internal fun List<NodeRecord<*, *>>.detachAll(diagnostics: SwingCompositionDiagnostics?) {
    var first: Throwable? = null
    fastForEach { record -> first = collectFailure(first) { record.runDetachLifecycle(diagnostics) } }
    fastForEach { record -> first = collectFailure(first) { record.node.detach() } }
    first?.let { throw it }
}

/**
 * Withdraws a record before restoration, then releases its detached descendants and references.
 *
 * @param record the record to withdraw and release.
 * @param diagnostics where a failing callback is reported, or `null` for none.
 */
internal fun NodeRecords.detachAndRemove(
    record: NodeRecord<*, *>,
    diagnostics: SwingCompositionDiagnostics?,
) {
    withdraw(record)
    val node = record.node
    try {
        if (node.isAttached) record.detach(diagnostics)
    } finally {
        clear(ComponentNodeType, node)
        clear(ParentLayoutNodeType, node)
        record.clear()
    }
}

/**
 * Removes excess positional records before any retained declaration is updated.
 *
 * @param nodeType the family whose excess records are removed.
 * @param declared how many positional records of [nodeType] the modifier declares.
 * @param diagnostics where a failing callback is reported, or `null` for none.
 */
internal fun NodeRecords.trimPositionalNodes(
    nodeType: ModifierNodeType<*>,
    declared: Int,
    diagnostics: SwingCompositionDiagnostics?,
): ModifierChanges {
    var existing = 0
    fastForEach { if (it.isStandingFor(nodeType, null) && it.key == null) existing++ }
    if (existing <= declared) return ModifierChanges.None
    var changes = ModifierChanges.None
    // A copy: detaching a record takes it, and the records of its node, out of the store being walked.
    toList().asReversed().fastForEach { record ->
        if (existing > declared && record.isStandingFor(nodeType, null) && record.key == null) {
            detachAndRemove(record, diagnostics)
            changes += ModifierChanges.NodesAddedOrRemoved
            existing--
        }
    }
    return changes
}

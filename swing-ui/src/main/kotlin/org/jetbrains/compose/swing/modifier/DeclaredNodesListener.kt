package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.util.fastForEach

/**
 * A component that is handed the modifier nodes it carries, in the order its modifier declares them - what a
 * component that paints or lays out through its nodes implements.
 */
public interface DeclaredNodesListener {
    /**
     * Receives the nodes [SwingModifier.Node.visitDeclaredNodes] visits, on the event dispatch thread, after a
     * modifier pass in which one of them attached, detached or changed place; an empty list once none stands.
     * Every node handed over is attached.
     */
    public fun onDeclaredNodesChanged(nodes: List<SwingModifier.Node>)
}

/** Visits the nodes [SwingModifier.Node.visitDeclaredNodes] visits, for this holder's modifier. */
internal fun SwingNodeHolder<*>.visitDeclaredNodes(block: (SwingModifier.Node) -> Unit) {
    val state = modifierState ?: return
    val chain = state.chain
    val batch = owner?.updateBatch
    val declaring = if (batch != null && batch.diffingState === state) batch.diffingDeclaration else null
    if (declaring == null) {
        chain.fastForEach { block(it.node) }
        return
    }
    // Mid-pass, each family's slots stand in declared order: walk the declaration, taking each element's slot
    // from its own family's cursor. An element whose slot has yet to attach has none.
    var layoutIndex = -1
    var componentIndex = -1
    declaring.fastForEach { element ->
        val layout = element is ParentLayoutNodeElement<*>
        var index = if (layout) layoutIndex else componentIndex
        do index++ while (index < chain.size && (chain[index] is LayoutNodeRecord) != layout)
        if (layout) layoutIndex = index else componentIndex = index
        val node = chain.getOrNull(index)?.node
        if (node != null && node.isAttached) block(node)
    }
}

/**
 * Hands a [DeclaredNodesListener] component the nodes its modifier declares, after a pass in which [chainChanged] -
 * and an empty list only where [handedNodes], the nodes last handed over being any.
 */
internal fun SwingNodeHolder<*>.notifyDeclaredNodes(
    chainChanged: Boolean,
    handedNodes: Boolean,
) {
    val chain = modifierState?.chain ?: return
    if (!chainChanged || (chain.isEmpty() && !handedNodes)) return
    (component as? DeclaredNodesListener)?.onDeclaredNodesChanged(chain.map { it.node })
}

package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.forEachStyle
import org.jetbrains.compose.swing.node.runReportingUncaught
import org.jetbrains.compose.swing.util.fastForEach

/**
 * A component that is handed the modifier nodes it carries, in the order its modifier declares them - what a
 * component that paints or lays out through its nodes implements.
 */
public interface DeclaredNodesListener {
    /**
     * Receives [componentNode] and the nodes [SwingModifier.Node.visitDeclaredNodes] visits on the event
     * dispatch thread, after a modifier pass in which one of them attached, detached, changed place, or was
     * written with a new element whose node [automatically invalidates][SwingModifier.Node.shouldAutoInvalidate],
     * after a composition-local refresh that rewrote such a node, or after a layout node the component's style
     * declares attached, detached or was written; an empty list when none is declared. Every node handed over is
     * attached.
     *
     * A throw is reported to the thread's uncaught-exception handler, as if this had returned.
     *
     * @param componentNode the node of the component whose modifier declares [nodes].
     * @param nodes the attached nodes, in declaration order.
     */
    public fun onDeclaredNodesChanged(
        componentNode: SwingComponentNode<*>,
        nodes: List<SwingModifier.Node>,
    )
}

/**
 * Visits the nodes [SwingModifier.Node.visitDeclaredNodes] visits, for this holder's modifier.
 *
 * @param block receives each visited node.
 */
internal fun SwingNodeHolder<*>.visitDeclaredNodes(block: (SwingModifier.Node) -> Unit) {
    val records = modifierState?.records ?: return
    val batch = owner?.updateBatch
    val declaring = if (batch != null && batch.diffingState === modifierState) batch.diffingDeclaration else null
    if (declaring == null) {
        records.fastForEach { if (it.isStandingFor(null, null) && it.key == null) block(it.node) }
        visitStyleLayoutNodes(block)
        return
    }
    // Mid-pass, each family's records are standing in declared order: walk the declaration, taking each element's
    // record from its own family's cursor. An element whose record has yet to attach has none.
    var layoutIndex = 0
    var componentIndex = 0
    declaring.fastForEach { element ->
        val layout = element is ParentLayoutNodeElement<*>
        val family = if (layout) ParentLayoutNodeType else ComponentNodeType
        val index = records.nextPositionalIndex(if (layout) layoutIndex else componentIndex, family)
        if (layout) layoutIndex = index + 1 else componentIndex = index + 1
        if (index < records.size && records[index].isAttached) block(records[index].node)
    }
    visitStyleLayoutNodes(block)
}

/**
 * Notifies a [DeclaredNodesListener] component after a pass in which [chainChanged] is true.
 * Includes the layout nodes declared by the component's style. Sends an empty list only when [handedNodes]
 * is true, meaning the previous notification included nodes.
 */
internal fun SwingNodeHolder<*>.notifyDeclaredNodes(
    chainChanged: Boolean,
    handedNodes: Boolean,
) {
    if (modifierState == null || !chainChanged) return
    val listener = component as? DeclaredNodesListener ?: return
    val nodes = ArrayList<SwingModifier.Node>()
    visitDeclaredNodes { nodes += it }
    if (nodes.isNotEmpty() || handedNodes) {
        runReportingUncaught { listener.onDeclaredNodesChanged(this, nodes) }
    }
}

/** Visits the attached layout nodes the component's style declares, in the order it declares them. */
private fun SwingNodeHolder<*>.visitStyleLayoutNodes(block: (SwingModifier.Node) -> Unit) {
    modifierState?.forEachStyle(this) { style ->
        style.layoutElements.fastForEach { if (it is ParentLayoutNode && it.isAttached) block(it) }
    }
}

/**
 * Hands a [DeclaredNodesListener] component the nodes its modifier declares, after the layout nodes its style
 * declares detached; an empty list where none is declared. Inside a modifier pass of this holder, the pass hands them
 * over as it ends, so a pass that also changes the modifier's nodes hands them once.
 */
internal fun SwingNodeHolder<*>.handDeclaredNodes() {
    val listener = component as? DeclaredNodesListener ?: return
    val state = modifierState
    if (state != null && owner?.updateBatch?.diffingState === state) return
    val nodes = ArrayList<SwingModifier.Node>()
    visitDeclaredNodes { nodes += it }
    runReportingUncaught { listener.onDeclaredNodesChanged(this, nodes) }
}

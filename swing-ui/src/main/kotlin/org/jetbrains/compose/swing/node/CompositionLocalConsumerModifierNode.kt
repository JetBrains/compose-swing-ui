package org.jetbrains.compose.swing.node

import androidx.compose.runtime.CompositionLocal
import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.ElementRecord
import org.jetbrains.compose.swing.modifier.NodeRecord
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.notifyDeclaredNodes
import org.jetbrains.compose.swing.modifier.takesWrite
import org.jetbrains.compose.swing.util.fastForEach

/**
 * Opts a [SwingModifier.Node] subtype into reading a [CompositionLocal] the node's component was declared
 * under, through [currentValueOf]. The counterpart of AndroidX `CompositionLocalConsumerModifierNode`.
 *
 * When a static local changes value, or a local starts or stops being provided, where the component was declared,
 * every node implementing this reacts, whether or not it read that local: the
 * [update][SwingModifier.NodeElement.update] of a [SwingModifier.ComponentNode]'s element runs again, and the parent
 * measures, places and paints the component of a
 * [ParentLayoutNode][org.jetbrains.compose.swing.layout.ParentLayoutNode] again. A dynamic local read in a
 * `ComponentNode` element's `update` runs that `update` again when its value changes; to follow a dynamic local's
 * value in a `ParentLayoutNode`, read it inside [observeReads]. Any other read, such as one in
 * [onAttach][SwingModifier.Node.onAttach], is taken once.
 */
public interface CompositionLocalConsumerModifierNode

/**
 * The value [local] resolves to in the composition scope where this node's component was declared.
 * Answers for every [CompositionLocal] in scope there.
 *
 * @throws IllegalStateException if the node is not attached.
 */
public fun <T, N> N.currentValueOf(
    local: CompositionLocal<T>,
): T where N : SwingModifier.Node, N : CompositionLocalConsumerModifierNode {
    val holder = checkNotNull(holder) { "Node is not attached" }
    return holder.compositionLocalMap[local]
}

/** Whether this holder's modifier holds a slot whose node is a [CompositionLocalConsumerModifierNode]. */
internal fun SwingNodeHolder<*>.observesLocals(): Boolean {
    var observes = false
    forEachLocalConsumer { observes = true }
    return observes
}

/**
 * Runs consumer component slots' `update` again, with that of every property slot declared after the first
 * consumer one, lays out and repaints the parent of consumer layout slots, and hands the [DeclaredNodesListener]
 * its nodes where a rewritten component slot holds a node it needs
 * ([DeclaredNodesListener.needsNodesAfterWrite]).
 */
internal fun SwingNodeHolder<*>.refreshLocalConsumers() {
    val state = modifierState ?: return
    val diagnostics = requireOwner().diagnostics
    val held = state.startWrite()
    state.rewritePropertySlotsFrom(component, diagnostics) { it.node is CompositionLocalConsumerModifierNode }
    var laidOut = false
    var handsOver = false
    val listener = component as? DeclaredNodesListener
    state.chain.fastForEach { record ->
        if (record.node is CompositionLocalConsumerModifierNode) {
            if (record is ElementRecord<*, *>) {
                record.refresh(component, diagnostics)
                if (listener.takesWrite(record.node)) handsOver = true
            } else {
                laidOut = true
            }
        }
    }
    state.applied = held
    if (laidOut) {
        component.revalidate()
        (component.parent ?: component).repaint()
    }
    if (handsOver) notifyDeclaredNodes(chainChanged = true, handedNodes = state.handedNodes)
}

private fun SwingNodeHolder<*>.forEachLocalConsumer(action: (NodeRecord<*, *>) -> Unit) {
    modifierState?.forEachLocalConsumer(action)
}

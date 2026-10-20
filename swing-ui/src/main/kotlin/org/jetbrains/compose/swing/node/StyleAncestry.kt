package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.modifier.ComponentNodeType
import org.jetbrains.compose.swing.modifier.SwingModifierState
import org.jetbrains.compose.swing.style.StyleElement
import org.jetbrains.compose.swing.style.StyleNode
import org.jetbrains.compose.swing.util.fastForEach

/** The style node in this modifier state's keyed slots, or `null` where the modifier declares no style. */
internal val SwingModifierState.styleNode: StyleNode?
    get() = records?.get(ComponentNodeType, null, StyleElement::class.java)?.node as? StyleNode

/** The style node of this node's modifier while it is attached. */
internal val SwingNodeHolder<*>.styleNode: StyleNode? get() = modifierState?.styleNode

/** This node where its style provides a key or a local, otherwise the nearest such node above it. */
internal val SwingNodeHolder<*>.publisherBelow: SwingNodeHolder<*>?
    get() = if (styleNode?.providesStyles == true) this else publisherAbove

/** Resolves descendant styles when the current update batch ends, or immediately when no batch is running. */
internal fun SwingNodeHolder<*>.restyleBelow() {
    val batch = owner?.updateBatch
    children.fastForEach { it.restyleSubtree(batch) }
}

/** Resolves again every style of this subtree: once [batch] ends where it is running, otherwise at once. */
internal fun SwingNodeHolder<*>.restyleSubtree(batch: ComponentUpdateBatch?) {
    styleNode?.let { if (batch != null && batch.isRunning) batch.holdForRestyle(it) else it.restyle() }
    children.fastForEach { it.restyleSubtree(batch) }
}

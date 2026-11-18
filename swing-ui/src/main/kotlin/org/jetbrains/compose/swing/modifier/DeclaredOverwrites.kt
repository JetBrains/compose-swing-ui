// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.node.runReportingUncaught
import org.jetbrains.compose.swing.util.fastForEach
import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

/**
 * What a [SwingModifier.NodeElement] declares through [SwingModifier.NodeElement.overwrites] and
 * [SwingModifier.NodeElement.rewriteOn] for as long as its [node] is attached: the captured originals of the
 * properties it overwrites, the listener for the bean properties whose change replays it, and its place in the
 * order of the modifier's other `rewriteOn` declarations.
 *
 * A replay keeps the order the declaration was last applied in, so declarations applied before it stay unable to
 * answer what it writes, while the declarations applied after it are written again by the replay.
 */
internal class DeclaredOverwrites<T : Component>(
    private val node: SwingModifier.ComponentNode<T>,
    private val state: SwingModifierState,
    private val properties: List<ComponentPropertyDescriptor<T, *>>,
    private val rewriteOn: List<String>,
    private val held: Set<String>,
) : PropertyChangeListener {
    /** The originals of the properties the element overwrites, taken before the node attaches. */
    private val captured = ArrayList<PropertyCaptures.Capture<*, *>>(1)

    /** The order shared by the modifier's `rewriteOn` declarations, or `null` where this one declares none. */
    private val ordering: PropertyRewrites? = if (rewriteOn.isEmpty()) null else state.rewrites()

    /** This declaration's order in its most recent apply. Zero means it has not been applied. */
    private var sequence = 0L

    /** Whether a replay is running, which keeps [sequence] where the last apply put it. */
    private var replaying = false

    /** Captures the overwritten properties, sharing each original with every declaration of the same name. */
    fun capture() {
        val captures = state.captures()
        val component = node.component
        properties.fastForEach { captured.add(it.capture(captures, component)) }
    }

    /** Starts answering the changes [rewriteOn] names. */
    fun listen() {
        val component = node.component
        rewriteOn.fastForEach { component.addPropertyChangeListener(it, this) }
    }

    /**
     * Runs [block], an `update` of the element, in this declaration's order: an apply takes the next order, and
     * a replay keeps the one it had.
     *
     * @param block writes the declaration.
     */
    fun <R> ordered(block: () -> R): R {
        val ordering = ordering ?: return block()
        if (!replaying) sequence = ordering.next()
        return inOrder(ordering, block)
    }

    /**
     * Stops listening, runs the node's [SwingModifier.Node.onDetach] in this declaration's order, then puts back
     * every original captured, even when an earlier step throws. A throw from `onDetach`, or from a listener the
     * caller registered on a property being put back, is reported to the uncaught handler when
     * [reportCallbackFailure] is set, and the remaining originals are still put back. Otherwise the first failure
     * propagates, with the later ones suppressed on it.
     *
     * @param reportCallbackFailure whether a throw from `onDetach` or a restore is reported instead of propagating.
     */
    fun detach(reportCallbackFailure: Boolean) {
        var failure: Throwable? = null
        val component = node.component
        rewriteOn.fastForEach { name ->
            failure = collectFailure(failure) { component.removePropertyChangeListener(name, this) }
        }
        failure =
            collectFailure(failure) {
                if (ordering == null) {
                    node.runDetachLifecycle(reportCallbackFailure)
                } else {
                    inOrder(ordering) { node.runDetachLifecycle(reportCallbackFailure) }
                }
            }
        captured.fastForEach { original ->
            failure =
                collectFailure(failure) {
                    if (reportCallbackFailure) runReportingUncaught { original.release() } else original.release()
                }
        }
        captured.clear()
        failure?.let { throw it }
    }

    override fun propertyChange(event: PropertyChangeEvent) {
        val ordering = ordering ?: return
        if (!ordering.mayReapply(sequence)) return
        replaying = true
        try {
            // The names the element holds or overwrites: a replay writes only the later declarations holding one
            // of them, or one that a declaration so written holds.
            state.refreshLocalConsumer(node, properties.mapTo(HashSet(held)) { it.name })
        } finally {
            replaying = false
        }
    }

    private fun <R> inOrder(
        ordering: PropertyRewrites,
        block: () -> R,
    ): R {
        ordering.enter(sequence)
        try {
            return block()
        } finally {
            ordering.exit()
        }
    }
}

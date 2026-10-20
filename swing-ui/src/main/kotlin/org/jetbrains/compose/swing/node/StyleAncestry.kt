package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.modifier.ComponentNodeType
import org.jetbrains.compose.swing.modifier.SwingModifierState
import org.jetbrains.compose.swing.style.StyleNode
import org.jetbrains.compose.swing.util.fastForEach

internal fun SwingModifierState.finishStyleReconciliation() {
    var previous: StyleNode? = null
    firstStyle = null
    records?.fastForEach { record ->
        if (record.isStandingFor(ComponentNodeType, null)) {
            val style = record.node as? StyleNode
            if (style != null) {
                style.previousStyle = previous
                style.nextStyle = null
                if (previous == null) firstStyle = style else previous.nextStyle = style
                previous = style
            }
        }
    }
}

/** The first standing style declaration. */
internal val SwingModifierState.styleNode: StyleNode? get() = firstStyle

internal val SwingNodeHolder<*>.styleNode: StyleNode? get() = modifierState?.styleNode

/** Visits style declarations in order without walking their property records. */
internal inline fun SwingModifierState.forEachStyle(
    holder: SwingNodeHolder<*>? = null,
    block: (StyleNode) -> Unit,
) {
    if (holder?.owner?.updateBatch?.diffingState === this) {
        records?.fastForEach { record ->
            if (record.isStandingFor(ComponentNodeType, null)) {
                val style = record.node as? StyleNode
                if (style != null) block(style)
            }
        }
    } else {
        var style = firstStyle
        while (style != null) {
            val next = style.nextStyle
            block(style)
            style = next
        }
    }
}

internal inline fun SwingModifierState.forEachStyleReversed(
    holder: SwingNodeHolder<*>? = null,
    block: (StyleNode) -> Unit,
) {
    if (holder?.owner?.updateBatch?.diffingState === this) {
        records?.asReversed()?.fastForEach { record ->
            if (record.isStandingFor(ComponentNodeType, null)) {
                val style = record.node as? StyleNode
                if (style != null) block(style)
            }
        }
    } else {
        var style = firstStyle
        while (true) {
            val next = style?.nextStyle ?: break
            style = next
        }
        while (style != null) {
            val previous = style.previousStyle
            block(style)
            style = previous
        }
    }
}

internal val SwingNodeHolder<*>.providesStyles: Boolean
    get() {
        var provides = false
        modifierState?.forEachStyle(this) { if (it.providesStyles) provides = true }
        return provides
    }

/** Resolves descendant styles when the current update batch ends, or immediately when no batch is running. */
internal fun SwingNodeHolder<*>.restyleBelow() {
    val batch = owner?.updateBatch
    children.fastForEach { it.restyleSubtree(batch) }
}

/** Resolves again every style of this subtree: once [batch] ends where it is running, otherwise at once. */
internal fun SwingNodeHolder<*>.restyleSubtree(batch: ComponentUpdateBatch?) {
    val state = modifierState
    if (state != null && state.styleNode != null) {
        state.forEachStyle(this) { it.restyle(replay = false) }
        if (batch != null) {
            batch.holdForRestyle(this)
            if (!batch.isRunning && owner?.snapshotObserver?.isDelivering != true) batch.drainRestyles()
        } else {
            refreshStyle()
        }
    }
    children.fastForEach { it.restyleSubtree(batch) }
}

/** Resolves stale styles after ancestor changes. */
internal fun SwingNodeHolder<*>.refreshStyle() {
    val state = modifierState ?: return
    var first: StyleNode? = null
    state.forEachStyle(this) {
        if (first == null && it.isStale) first = it
    }
    first?.restyle()
}

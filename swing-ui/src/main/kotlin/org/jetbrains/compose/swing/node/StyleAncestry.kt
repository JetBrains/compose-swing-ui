package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.defaults.refreshInheritedDefaults
import org.jetbrains.compose.swing.modifier.ComponentNodeType
import org.jetbrains.compose.swing.modifier.SwingModifierState
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.StyleElement
import org.jetbrains.compose.swing.style.StyleNode
import org.jetbrains.compose.swing.style.UnstyledComponent
import org.jetbrains.compose.swing.style.collectsNothingUnder
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

/** Whether an ancestor provides a style for [AllComponents]. */
internal val SwingNodeHolder<*>.receivesAllComponents: Boolean
    get() {
        var ancestor = publisherAbove
        while (ancestor != null) {
            var provides = false
            ancestor.modifierState?.forEachStyle(ancestor) { if (it.providesAllComponents) provides = true }
            if (provides) return true
            ancestor = ancestor.publisherAbove
        }
        return false
    }

internal val SwingNodeHolder<*>.providesStyles: Boolean
    get() {
        var provides = false
        modifierState?.forEachStyle(this) { if (it.providesStyles) provides = true }
        return provides
    }

/**
 * Whether the style declarations of this node's applied modifier, or the unstyled declaration where it declares
 * none, that can collect something under the current ancestors are not the ones holding a slot. The ancestors
 * decide that for every declaration of a node at once, so comparing their counts is enough.
 */
private fun SwingNodeHolder<*>.hasStaleStyleSlots(state: SwingModifierState): Boolean {
    var slots = 0
    state.forEachStyle(this) { slots++ }
    var styles = 0
    var collecting = 0
    state.declared.foldIn(Unit) { _, element ->
        if (element is StyleElement<*, *>) {
            styles++
            if (!element.collectsNothingUnder(this)) collecting++
        }
    }
    if (styles == 0 && !UnstyledComponent.collectsNothingUnder(this)) collecting++
    return collecting != slots
}

/**
 * Resolves descendant styles when the current update batch ends, or immediately when this node stands in no
 * composition. Outside a batch and a snapshot delivery, the queued styles are resolved once the whole walk is held.
 */
internal fun SwingNodeHolder<*>.restyleBelow() {
    val batch = owner?.updateBatch
    children.fastForEach { it.restyleSubtree(batch) }
    if (batch != null && !batch.isRunning && owner?.snapshotObserver?.isDelivering != true) batch.drainRestyles()
}

/** Resolves again every style of this subtree: held in [batch] for its drain where there is one, otherwise at once. */
internal fun SwingNodeHolder<*>.restyleSubtree(batch: ComponentUpdateBatch?) {
    val state = modifierState
    // Every component takes part: one that declares no style takes the unstyled declaration.
    if (state != null) {
        state.forEachStyle(this) { it.restyle(replay = false) }
        state.ancestorsChanged = true
        if (batch != null) {
            batch.holdForRestyle(this)
        } else {
            refreshStyle()
        }
    }
    children.fastForEach { it.restyleSubtree(batch) }
}

/** Diffs again where ancestor changes give a style declaration a slot or take one away, then resolves stale styles. */
internal fun SwingNodeHolder<*>.refreshStyle() {
    val state = modifierState ?: return
    if (state.ancestorsChanged) {
        state.ancestorsChanged = false
        if (hasStaleStyleSlots(state)) refreshInheritedDefaults(restylesSlots = true)
    }
    var first: StyleNode? = null
    state.forEachStyle(this) {
        if (first == null && it.isStale) first = it
    }
    first?.restyle()
}

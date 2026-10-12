package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.invalidateLayout
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.node.requestAfterValidation

/** Replays placement after validation, unless a later placement pass has already satisfied the request. */
private val ReplayPlacement: (SwingComponentNode<ConstrainedPanel>) -> Unit = { node ->
    val panel = node.component
    if (panel.policyLayout.placementPending) panel.replayPlacementNow()
}

/**
 * Runs [block], which computes one answer, recording its reads under [MeasurePolicyLayout.node] and [onChanged];
 * without a node, runs it unobserved.
 *
 * Calls under one callback combine their reads within a snapshot. A call in a newer snapshot drops reads recorded
 * only in older snapshots. Independent size operations use separate callbacks so observing one keeps the other's reads.
 */
internal fun MeasurePolicyLayout.observe(
    onChanged: (SwingComponentNode<ConstrainedPanel>) -> Unit,
    block: () -> Unit,
) {
    val node = node
    if (node != null) {
        node.observeReads(onChanged, block)
    } else {
        block()
    }
}

/** A Foundation parent owns the next offer while its child's result is invalid or mismatched. */
internal fun ChildMeasurables.defersToFoundationParent(
    width: Int,
    height: Int,
): Boolean {
    if (!hasFoundationParent || panel.isPreferredSizeSet) return false
    return measured?.let { it.width != width || it.height != height } ?: true
}

/** This result if it was made for [width] by [height], else null. */
internal fun MeasureResult?.madeFor(
    width: Int,
    height: Int,
): MeasureResult? = this?.takeIf { it.width == width && it.height == height }

/** Runs [body] over the children of a layout pass, with this container in [LayoutState.Measuring]. */
internal inline fun <R> ChildMeasurables.measuringPass(body: (List<Measurable>) -> R): R {
    val children = layoutPassOf()
    val previous = enter(LayoutState.Measuring)
    try {
        return body(children)
    } finally {
        layoutState = previous
    }
}

/** Runs the policy for this panel's assigned inner rectangle. */
internal fun ChildMeasurables.measureAtAssignedSizeObserved(
    width: Int,
    height: Int,
): MeasureResult {
    var result: MeasureResult? = null
    owner.observe(SizeReadKind.Settled) {
        result =
            measuringPass { children ->
                with(owner.policy) { PolicyMeasureScope.measureAtAssignedSize(children, width, height) }
            }
    }
    return checkNotNull(result)
}

/**
 * Requests a placement replay inside the bounds this panel already holds: this panel's own layout pass run outside a
 * validation, invalidating neither this panel nor an ancestor. The lines this panel and each Foundation container
 * above it put are then dirty, and the next read of one works them out again. Where a container above read such a line
 * in its last measure, the panel is revalidated instead; where it read one only in its last placement, that container
 * places its children again too.
 *
 * While this panel runs a block of its policy, all of that is left until Swing releases the tree lock: a placement run
 * inside a block would place children that block goes on to measure or place. The same holds while the panel validates
 * the children its own replay resized. A later layout pass clears the request when it places the children first.
 */
internal fun ConstrainedPanel.placeChildrenAgain() {
    val node = policyLayout.node ?: return
    policyLayout.placementPending = true
    node.requestAfterValidation(ReplayPlacement)
}

/** Runs a placement replay [placeChildrenAgain] requested. */
internal fun ConstrainedPanel.replayPlacementNow() {
    synchronized(treeLock) { replayPlacementLocked() }
}

private fun ConstrainedPanel.replayPlacementLocked() {
    val measurables = policyLayout.measurables
    var panel = this
    // A stock child's width can change an ancestor's intrinsic height while replay keeps that ancestor valid.
    panel.policyLayout.intrinsics.invalidate()
    var readBy = LayoutState.Idle
    while (readBy == LayoutState.Idle) {
        panel.policyLayout.measurables.lineScope.generation++
        val record = panel.decoration.parentMeasurables?.find(panel) ?: break
        readBy = record.lineReadDuring
        panel = record.owner.panel
        panel.policyLayout.intrinsics.invalidate()
    }
    if (readBy == LayoutState.Measuring) return policyLayout.node?.invalidateLayout() ?: Unit
    measurables.during(RunningCause.PlacementReplay) { doLayout() }
    // A child this pass resized is left invalid, and nothing above it is: no validation is coming to lay
    // it out, so it is laid out here, as a validation laying this panel out would.
    measurables.during(RunningCause.Validation) {
        measurables.layoutPass.fastForEach { if (!it.component.isValid) it.component.validate() }
    }
    if (readBy == LayoutState.LayingOut) panel.placeChildrenAgain()
}

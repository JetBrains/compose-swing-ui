package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.observeReads
import javax.swing.SwingUtilities

/**
 * The entry every [Layout] appends to its panel's modifier, after the caller's. Its node is what each answer a
 * [MeasurePolicyLayout] caches records its reads under.
 *
 * `observeReads` keeps only the reads of the latest call under one callback, while Swing caches each answer
 * on its own and asks for it again only after that answer is invalidated. Each answer therefore records
 * under a callback of its own, so computing one answer never drops the reads behind another that is still
 * cached.
 */
internal object LayoutObservation : SwingModifier.NodeElement<ConstrainedPanel, LayoutObservationNode>() {
    override val targetType: Class<ConstrainedPanel> get() = ConstrainedPanel::class.java

    override val name: String get() = "layoutObservation"

    override fun create(): LayoutObservationNode = LayoutObservationNode()

    override fun update(node: LayoutObservationNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * The node a [Layout]'s panel records its answers' reads under, from its `onAttach` to its `onDetach`.
 *
 * Attaching revalidates the panel so answers computed while no node was registered are computed again
 * under observation; on a first mount the panel has no parent yet and `revalidate` returns at once.
 */
internal class LayoutObservationNode : SwingModifier.ComponentNode<ConstrainedPanel>() {
    override fun onAttach() {
        component.policyLayout.node = this
        component.revalidate()
    }

    override fun onDetach() {
        component.policyLayout.node = null
    }

    /**
     * Revalidates the panel, for a changed read behind an answer it measured; see
     * [ConstrainedPanel.revalidateOutsideValidation].
     */
    fun remeasure() = component.revalidateOutsideValidation()
}

/**
 * Runs [block], which computes one answer, recording its reads under the attached [MeasurePolicyLayout.node]
 * and [onChanged]; without a node, runs it unobserved.
 */
internal fun MeasurePolicyLayout.observe(
    onChanged: (LayoutObservationNode) -> Unit,
    block: () -> Unit,
) {
    val node = node
    if (node != null) {
        node.observeReads(onChanged, block)
    } else {
        block()
    }
}

/**
 * Places the children again inside the bounds this panel already holds: this panel's own layout pass run
 * outside a validation, invalidating neither this panel nor an ancestor. The lines this panel and each Foundation
 * container above it put are then dirty, and the next read of one works them out again. Where a container above read
 * such a line in its last measure, the panel is revalidated instead; where it read one only in its last placement, that
 * container places its children again too.
 *
 * While this panel runs a block of its policy, all of that is left to the end of the running event: a placement run
 * inside a block would place children that block goes on to measure or place. The same holds while the panel
 * validates the children its own replay resized, and while a validation
 * [may be running][ConstrainedPanel.mayBeValidating]: a placement run there would lay out a container that validation
 * has not finished with. A valid panel answering a size query places at once, since the query lays nothing out.
 */
internal fun ConstrainedPanel.placeChildrenAgain() {
    val measurables = policyLayout.measurables
    val running = measurables.layoutState != LayoutState.Idle || measurables.isValidating
    // Outside a block and a validation of its own, the panel is in a validation only while it answers a size query.
    val queriedWhileValid = isValid && measurables.isInValidation
    if (running || (mayBeValidating && !queriedWhileValid)) {
        return SwingUtilities.invokeLater(this::placeChildrenAgain)
    }
    var panel = this
    var readBy = LayoutState.Idle
    while (readBy == LayoutState.Idle) {
        panel.policyLayout.measurables.lineScope.generation++
        val record = panel.decoration.parentMeasurables?.find(panel) ?: break
        readBy = record.lineReadDuring
        panel = record.owner.panel
    }
    if (readBy == LayoutState.Measuring) return revalidateOutsideValidation()
    measurables.during(RunningCause.PlacementReplay) { doLayout() }
    // A child this pass resized is left invalid, and nothing above it is: no validation is coming to lay
    // it out, so it is laid out here, as a validation laying this panel out would.
    measurables.during(RunningCause.Validation) {
        measurables.layoutPass.fastForEach { if (!it.component.isValid) it.component.validate() }
    }
    if (readBy == LayoutState.LayingOut) panel.placeChildrenAgain()
}

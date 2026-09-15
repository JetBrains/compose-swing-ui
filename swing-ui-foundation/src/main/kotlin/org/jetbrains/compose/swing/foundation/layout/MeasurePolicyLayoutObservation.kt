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
     * Revalidates the panel, for a changed read behind an answer it measured. A change made while the panel runs a
     * block of its policy is revalidated once the running event is over: a layout pass marks the panel valid as it
     * ends, which would drop an invalidation made during it.
     */
    fun remeasure() {
        if (component.policyLayout.measurables.layoutState == LayoutState.Idle) {
            component.revalidate()
        } else {
            SwingUtilities.invokeLater(component::revalidate)
        }
    }
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
 * outside a validation, invalidating neither this panel nor an ancestor.
 */
internal fun ConstrainedPanel.placeChildrenAgain() {
    policyLayout.measurables.during(RunningCause.PlacementReplay) { doLayout() }
    // A child this pass resized is left invalid, and nothing above it is: no validation is coming to lay
    // it out, so it is laid out here, as a validation laying this panel out would.
    policyLayout.measurables.layoutPass.fastForEach { if (!it.component.isValid) it.component.validate() }
}

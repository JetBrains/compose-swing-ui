package org.jetbrains.compose.swing.layout

import java.awt.Component
import java.awt.Container
import java.awt.LayoutManager2

/**
 * A parent layout manager that interprets its children's [ParentLayoutElement]s.
 *
 * The runtime gives a manager the child's folded `parentData` and all remaining parent-layout
 * `elements` in one call, after the child has been added to the parent. It calls again when either
 * declaration changes, without removing and adding the child, so implementations retain whatever
 * per-child state belongs to the component. `elements` are in modifier declaration order, outermost
 * first.
 *
 * A layout manager that does not implement this interface receives ordinary `LayoutManager2` parent-data
 * registration and cannot accept remaining parent-layout elements.
 */
public interface MeasurementLayoutManager : LayoutManager2 {
    /** Declares how [component] is placed and measured by this parent. */
    public fun declareComponentLayout(
        component: Component,
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    )

    /** Factory for tokens interpreted by a [MeasurementLayoutManager]. */
    public companion object {
        /**
         * Creates a stable family token for declarations interpreted by a measurement-layout parent.
         *
         * Foundation and custom policies can keep this opaque token without depending on a concrete
         * manager implementation; the runtime checks the actual parent uniformly before mutation.
         */
        public fun parentProtocol(description: String): ParentProtocol =
            parentProtocolOf(description) { parent: Container -> parent.layout is MeasurementLayoutManager }
    }
}

package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component

/** Keeps focused legacy behavior tests concise while exercising the public parent-layout handoff. */
internal fun MeasurePolicyLayout.declareLayoutChain(
    component: Component,
    chain: List<LayoutModifier>,
) {
    declareComponentLayout(component, measurables.declaredBy(component), chain)
}

/** Replaces raw parent data through the same atomic declaration path the runtime now uses. */
internal fun MeasurePolicyLayout.replaceLayoutConstraint(
    component: Component,
    constraint: Any?,
) {
    declareComponentLayout(component, constraint, measurables.of(component).layoutChain)
}

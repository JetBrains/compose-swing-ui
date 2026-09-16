package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component

/** Declares [chain] for [component], keeping the parent data it was added with. */
internal fun MeasurePolicyLayout.declareLayoutChain(
    component: Component,
    chain: List<LayoutModifierNode>,
) {
    measurables.of(component).run { declare(parentData, chain) }
}

/** A node for each layout modifier [declare] states on a child, in the order its container reads them. */
internal fun layoutChainOf(declare: ConstrainedScope.() -> SwingModifier): List<LayoutModifierNode> =
    BoxScopeInstance.declare().foldIn(mutableListOf()) { chain, element ->
        chain.also { if (element is LayoutModifierNodeElement<*>) it.add(element.create()) }
    }

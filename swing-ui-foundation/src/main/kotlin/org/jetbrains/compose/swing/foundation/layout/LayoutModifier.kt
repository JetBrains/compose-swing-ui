@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Measures and places the child with [measure], a layout modifier written as a lambda: [measure] measures the
 * child's measurable under the constraints it is handed, and answers with [MeasureScope.layout].
 *
 * Its intrinsic answers are those every [LayoutModifierNode] gives by default. [measure] may also run to answer a
 * container's size query, so it must have no effect beyond its answer; a modifier that drives state across passes
 * is a [LayoutModifierNodeElement].
 *
 * @param measure measures and places the child under the constraints this modifier receives.
 * @return this modifier with the measurement declared on it.
 */
context(scope: ConstrainedScope)
public fun SwingModifier.layout(
    measure: MeasureScope.(measurable: Measurable, constraints: Constraints) -> MeasureResult,
): SwingModifier = with(scope) { layout(LayoutElement(measure)) }

/** The `ConstrainedScope.layout` lambda declaration, equal to another only for the same block. */
private data class LayoutElement(
    private val measure: MeasureScope.(Measurable, Constraints) -> MeasureResult,
) : LayoutModifierNodeElement<LayoutModifierImpl>() {
    override val name: String get() = "layout"

    override val declaredValues: Map<String, Any?> get() = mapOf("measure" to measure)

    override fun create(): LayoutModifierImpl = LayoutModifierImpl(measure)

    override fun update(node: LayoutModifierImpl) {
        node.measureBlock = measure
    }
}

/** Measures and places the child with [measureBlock]. */
private class LayoutModifierImpl(
    var measureBlock: MeasureScope.(Measurable, Constraints) -> MeasureResult,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult = measureBlock(measurable, constraints)
}

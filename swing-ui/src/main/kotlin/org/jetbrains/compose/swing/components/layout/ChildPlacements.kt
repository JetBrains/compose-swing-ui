package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component

/**
 * What the children of one [Row] or [Column] have declared for themselves: the share of the leftover
 * space each claims, and the cross-axis placement each names in place of its container's.
 *
 * A scope hands this to the modifier elements its `weight` and `align` extensions build and to the
 * [LinearLayout] that reads them back, so a child's declaration reaches the layout without the child
 * knowing its container. Each entry is written by the modifier element that declared it and removed
 * again when that element leaves the child's chain, and every write is answered with whether it changed
 * anything - an unchanged redeclaration asks for no new layout pass.
 */
internal class ChildPlacements {
    private val weights = HashMap<Component, WeightPlacement>()
    private val alignments = HashMap<Component, AxisAlignment>()
    private val fills = HashSet<Component>()

    /** What [component] claims of the leftover space, or `null` when it takes the size it prefers. */
    fun weightOf(component: Component): WeightPlacement? = weights[component]

    /** Where [component] sits across the axis, or `null` to leave that to its container. */
    fun alignmentOf(component: Component): AxisAlignment? = alignments[component]

    /** Whether [component] takes its container's whole extent across the axis. */
    fun fillsCrossAxis(component: Component): Boolean = component in fills

    /** Records [placement] for [component]; `true` when that differs from what was held. */
    fun setWeight(
        component: Component,
        placement: WeightPlacement,
    ): Boolean = weights.put(component, placement) != placement

    /** Drops what [component] claimed; `true` when something was held. */
    fun clearWeight(component: Component): Boolean = weights.remove(component) != null

    /** Records [alignment] for [component]; `true` when that differs from what was held. */
    fun setAlignment(
        component: Component,
        alignment: AxisAlignment,
    ): Boolean = alignments.put(component, alignment) != alignment

    /** Drops the alignment [component] named; `true` when one was held. */
    fun clearAlignment(component: Component): Boolean = alignments.remove(component) != null

    /** Records that [component] fills the cross axis; `true` when it did not already. */
    fun setFill(component: Component): Boolean = fills.add(component)

    /** Drops [component]'s cross-axis fill; `true` when one was held. */
    fun clearFill(component: Component): Boolean = fills.remove(component)
}

/**
 * A child's claim on the space its container has left over: [weight] shares of it, and whether the child
 * occupies all of what it is granted ([fill]) or only as much of it as it prefers.
 */
internal data class WeightPlacement(
    val weight: Float,
    val fill: Boolean,
)

/**
 * Builds the claim a scope's `weight` extension declares, rejecting a weight that grants no space.
 * An infinite weight is taken as the largest finite one, so a child asking for everything gets it
 * rather than an arithmetic answer.
 */
internal fun weightPlacement(
    weight: Float,
    fill: Boolean,
): WeightPlacement {
    require(weight > 0f) { "A weight must be greater than zero, but was $weight." }
    return WeightPlacement(weight.coerceAtMost(Float.MAX_VALUE), fill)
}

/** Holds the child's claim in [placements] for as long as the element stays in the child's chain. */
internal class WeightNode(
    private val placements: ChildPlacements,
) : SwingModifier.Node<Component>() {
    /** Records [placement], asking for a new layout pass when it changes the claim in force. */
    fun apply(placement: WeightPlacement) {
        if (placements.setWeight(component, placement)) component.revalidate()
    }

    override fun onDetach() {
        if (placements.clearWeight(component)) component.revalidate()
    }
}

/** The element a scope's `weight` extension adds to a child's modifier chain. */
internal class WeightElement(
    private val placements: ChildPlacements,
    private val placement: WeightPlacement,
) : SwingModifier.Element<Component, WeightNode> {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): WeightNode = WeightNode(placements)

    override fun update(node: WeightNode): Unit = node.apply(placement)
}

/** Holds the child's cross-axis placement in [placements] while the element stays in its chain. */
internal class AlignNode(
    private val placements: ChildPlacements,
) : SwingModifier.Node<Component>() {
    /** Records [alignment], asking for a new layout pass when it changes the placement in force. */
    fun apply(alignment: AxisAlignment) {
        if (placements.setAlignment(component, alignment)) component.revalidate()
    }

    override fun onDetach() {
        if (placements.clearAlignment(component)) component.revalidate()
    }
}

/** The element a scope's `align` extension adds to a child's modifier chain. */
internal class AlignElement(
    private val placements: ChildPlacements,
    private val alignment: AxisAlignment,
) : SwingModifier.Element<Component, AlignNode> {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): AlignNode = AlignNode(placements)

    override fun update(node: AlignNode): Unit = node.apply(alignment)
}

/** Holds the child's cross-axis fill in [placements] while the element stays in its chain. */
internal class FillNode(
    private val placements: ChildPlacements,
) : SwingModifier.Node<Component>() {
    /** Records the fill, asking for a new layout pass when the child did not already carry one. */
    fun apply() {
        if (placements.setFill(component)) component.revalidate()
    }

    override fun onDetach() {
        if (placements.clearFill(component)) component.revalidate()
    }
}

/** The element a scope's cross-axis fill extension adds to a child's modifier chain. */
internal class FillElement(
    private val placements: ChildPlacements,
) : SwingModifier.Element<Component, FillNode> {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): FillNode = FillNode(placements)

    override fun update(node: FillNode): Unit = node.apply()
}

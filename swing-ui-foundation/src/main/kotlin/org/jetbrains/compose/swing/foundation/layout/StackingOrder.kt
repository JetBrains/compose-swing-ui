package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component
import java.awt.Container

/**
 * The declaration order of a stacking container's children, and the component array derived from it.
 *
 * A `Container` holds one array of children, and that array answers three questions at once: a
 * `JComponent` paints from the end of it back to the front, hands a mouse event to the first child the
 * point falls in, and a layout manager walks it by index. So the front of the array is the top of the
 * stack, and a container that sorts the array by a declared z-index can no longer read declaration order
 * off it. This keeps that order, and [restack] arranges the array from it.
 *
 * [container] is the container whose children these are, and [zIndexOf] reads where a child declared it
 * sits - the two containers that stack their children keep that value in different places.
 *
 * Every add and every removal keeps this in step with the array; a container hands over [declared],
 * [dropped] and [cleared] from the `addImpl`, `remove(int)` - which `Container.remove(Component)` reaches
 * as well - and `removeAll` it overrides.
 */
internal class StackingOrder(
    private val container: Container,
    private val zIndexOf: (Component) -> Float,
) {
    private val order = ArrayList<Component>()

    /**
     * Records [child] at [index] in declaration order, the `-1` a plain `add` passes being the last
     * place, and does nothing for a child the container did not take.
     *
     * Call this once the container has taken the child, which `Container.addImpl` says by setting the
     * child's parent: it does that as it puts the child in the array, before it registers the child with
     * the layout manager, where a constraint of a kind the container cannot read is refused. A child the
     * array kept is one this order holds too, whether or not that registration threw.
     */
    public fun declared(
        child: Component,
        index: Int,
    ) {
        if (child.parent !== container) return
        order.add(if (index < 0) order.size else index, child)
    }

    /**
     * Gives up [child]. The children left keep the order they had, so none of them moves.
     *
     * The child is found by identity, because the lookup behind `Container.remove(Component)` resolves
     * what it is handed by equality, and the two answer differently where a child is equal to a sibling.
     */
    public fun dropped(child: Component) {
        order.removeAll { it === child }
    }

    /** Gives up every child, for the `Container.removeAll` that empties the array itself. */
    public fun cleared() {
        order.clear()
    }

    /**
     * Puts the component array in stacking order: the child declaring the largest z-index at the front,
     * where a `JComponent` paints it last and hands it a mouse event first, and children declaring the
     * same one in the reverse of declaration order, so the last declared of them is the higher.
     *
     * A child keeps its focus and its native resources through this: moving a component within the parent
     * it already has is a move within the component array, and nothing more.
     *
     * The array and this order hold the same children, except while an add is in flight - the arriving
     * child reaches the layout manager before it is recorded, and that add stacks the children again once
     * it has been.
     */
    public fun restack() {
        if (order.size != container.componentCount) return
        val stacked = order.asReversed().sortedByDescending(zIndexOf)
        var moved = false
        for (index in stacked.indices) {
            val child = stacked[index]
            if (container.getComponent(index) === child) continue
            container.setComponentZOrder(child, index)
            moved = true
        }
        if (moved) container.repaint()
    }
}

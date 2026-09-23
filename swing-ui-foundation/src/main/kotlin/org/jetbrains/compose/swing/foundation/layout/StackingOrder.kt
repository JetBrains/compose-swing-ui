package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.util.fastForEach
import java.awt.Component

/**
 * The declaration order of a stacking container's children, and the component array derived from it.
 *
 * A `Container` holds one array of children, and that array answers four questions at once: a
 * `JComponent` paints from the end of it back to the front, hands a mouse event to the first child the
 * point falls in, a layout manager walks it by index, and a layout-ordered focus traversal breaks a tie of
 * positions by it. So the front of the array is the top of the stack, and a container that sorts the array
 * by z-index can no longer read declaration order off it. This keeps that order, and [restack] arranges the
 * array from it.
 *
 * [container] is the container whose children these are, and [measurables] reads where a child sits.
 *
 * Every add and every removal keeps this in step with the array; a container hands over [declared],
 * [dropped] and [cleared] from the `addImpl`, `remove(int)` - which `Container.remove(Component)` reaches
 * as well - and `removeAll` it overrides.
 */
internal class StackingOrder(
    private val container: ConstrainedPanel,
    private val measurables: ChildMeasurables,
) {
    private val _order = ArrayList<Component>()

    /** The children in the order their parent declared them. */
    val order: List<Component>
        get() = _order

    /** The component array [restack] arranges, held only while it runs. */
    private val stacked = ArrayList<Component>()

    /**
     * Records [child] at [index] in declaration order, the `-1` a plain `add` passes being the last
     * place, and does nothing for a child the container did not take.
     *
     * Call this once the container has taken the child, which `Container.addImpl` says by setting the
     * child's parent: it does that as it puts the child in the array, before it registers the child with
     * the layout manager, where a constraint of a kind the container cannot read is refused. A child the
     * array kept is one this order holds too, whether or not that registration threw.
     */
    fun declared(
        child: Component,
        index: Int,
    ) {
        if (child.parent !== container) return
        _order.add(if (index < 0) _order.size else index, child)
    }

    /**
     * Gives up [child]. The children left keep the order they had, so none of them moves.
     *
     * The child is found by identity, because the lookup behind `Container.remove(Component)` resolves
     * what it is handed by equality, and the two answer differently where a child is equal to a sibling.
     */
    fun dropped(child: Component) {
        _order.removeAll { it === child }
    }

    /**
     * The array index that stacks [child] where [restack] would put it once it is declared at [index], so
     * the add itself moves no sibling; [index] is answered unchanged where it is out of range, for
     * `Container.addImpl` to refuse, or while the array and this order disagree.
     */
    fun stackedIndex(
        child: Component,
        index: Int,
    ): Int {
        val siblings = if (child.parent === container) order.size - 1 else order.size
        if (!isInStep || index < -1 || index > siblings) return index
        val declaredAt = if (index < 0) siblings else index
        val zIndex = measurables.find(child)?.zIndex ?: 0f
        var above = 0
        var position = 0
        order.fastForEach { sibling ->
            if (sibling === child) return@fastForEach
            val comparison = (measurables.find(sibling)?.zIndex ?: 0f).compareTo(zIndex)
            if (comparison > 0 || (comparison == 0 && position >= declaredAt)) above++
            position++
        }
        return above + topmostIndex
    }

    /** The array index of the topmost child, past the container's glass pane while it holds one. */
    val topmostIndex: Int
        get() = if (container.glassPane == null) 0 else 1

    /** Whether this order holds as many children as the component array it arranges. */
    val isInStep: Boolean
        get() = order.size == container.componentCount - topmostIndex

    /** Gives up every child, for the `Container.removeAll` that empties the array itself. */
    fun cleared() {
        _order.clear()
    }

    /**
     * Puts the component array in stacking order: the child with the largest z-index at the front, where
     * a `JComponent` paints it last and hands it a mouse event first, and children with the same one in
     * the reverse of declaration order, so the last declared of them is the higher.
     *
     * A child keeps its focus and its native resources through this: moving a component within the parent
     * it already has is a move within the component array, and nothing more.
     *
     * The array and this order hold the same children, except while an add is in flight - the arriving
     * child reaches the layout manager before it is recorded, and an add that found the two apart stacks the
     * children again once it has been.
     *
     * A `setComponentZOrder` here also invalidates the container; see [ConstrainedPanel.invalidate].
     */
    fun restack() {
        if (!isInStep) return
        val topmost = topmostIndex
        try {
            // An insertion sort of the reversed declaration order: stable, so tied children stay in reverse
            // declaration order, and linear where the order already holds.
            for (declared in order.size - 1 downTo 0) {
                val child = order[declared]
                val zIndex = measurables.find(child)?.zIndex ?: 0f
                var at = stacked.size
                while (at > 0 && (measurables.find(stacked[at - 1])?.zIndex ?: 0f).compareTo(zIndex) < 0) at--
                stacked.add(at, child)
            }
            for (index in stacked.indices) {
                val child = stacked[index]
                if (container.getComponent(topmost + index) === child) continue
                container.setComponentZOrder(child, topmost + index)
                // Every pair of children whose order changed includes one moved here, so its bounds hold
                // every pixel that now paints differently.
                container.repaint(child.x, child.y, child.width, child.height)
            }
        } finally {
            stacked.clear()
        }
    }
}

package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.util.fastAny
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component

/**
 * A modifier node that is one step of its component's decoration, at the node's position in the modifier:
 * it wraps every step declared after it and the component's own painting, and is wrapped by every step
 * declared before it.
 *
 * The element creating it must be [additive][SwingModifier.NodeElement.additive], since that is what gives the
 * node its position among the other steps; a node whose element is not additive is refused as it attaches. A
 * child declares that element through [decoration].
 *
 * The library gathers [isOpaque] and outsets after each modifier pass that attaches, detaches, moves or writes a
 * step, so an element's `update` needs no call for it. A node that changes its outsets or isOpaque between passes
 * calls [invalidateDecoration]. A change that only affects [paint] is a `component.repaint()`.
 */
public abstract class DecorationModifierNode<T : Component> :
    SwingModifier.ComponentNode<T>(),
    Decorator {
    /**
     * Refuses an element that is not additive. An element naming [Decoratable] as its target type is refused
     * before this node is created; otherwise this node is refused if the component is not a [Decoratable].
     *
     * @throws IllegalStateException if the element creating this node is not
     *   [additive][SwingModifier.NodeElement.additive], or the component does not paint through a decoration.
     */
    final override fun onAttach() {
        check(declaredNodes().fastAny { it === this }) {
            "A decoration step takes its place among the others from its element, so ${javaClass.name} must be " +
                "created by an additive element"
        }
        checkNotNull(component as? Decoratable) {
            "A decoration step requires a ${Decoratable::class.java.name} target, but the component is a " +
                "${component.javaClass.name}"
        }
    }

    /**
     * Runs [onRemovedFromDecoration]. The component is handed the decoration without this step once the modifier
     * pass ends.
     */
    final override fun onDetach() {
        onRemovedFromDecoration()
    }

    /** Runs where [onDetach] would, while the node is still attached and still part of the decoration. */
    protected open fun onRemovedFromDecoration() {}

    /**
     * Gathers the component's decoration again, after this step's outsets or isOpaque changed between modifier
     * passes, and repaints it. An element's `update` needs no call. Does nothing while the node is not attached.
     */
    public fun invalidateDecoration() {
        if (!isAttached) return
        if (!publishDecoration(component as Decoratable, declaredNodes())) component.repaint()
    }
}

/** The nodes declared on this node's component, in declaration order. */
internal fun SwingModifier.Node.declaredNodes(): List<SwingModifier.Node> {
    val nodes = ArrayList<SwingModifier.Node>()
    visitDeclaredNodes { nodes += it }
    return nodes
}

/** Writes to [decoratable] the decoration [nodes] declare; see [publishSteps]. */
internal fun publishDecoration(
    decoratable: Decoratable,
    nodes: List<SwingModifier.Node>,
): Boolean = publishSteps(decoratable, DecorationSteps.of(nodes))

/**
 * Writes to [decoratable] a decoration of [steps], and repaints it; nothing where the value held equals it. Returns
 * whether it wrote.
 *
 * @throws IllegalStateException if [decoratable] is not a [Component].
 */
internal fun publishSteps(
    decoratable: Decoratable,
    steps: DecorationSteps,
): Boolean {
    val component =
        checkNotNull(decoratable as? Component) {
            "A ${Decoratable::class.java.name} paints as a component, and ${decoratable.javaClass.name} is not one"
        }
    val held = decoratable.decoration
    val isOpaque = steps.isOpaque
    if (held.steps == steps && held.hasOpaqueSteps == isOpaque) return false
    decoratable.decoration = if (steps.isEmpty) Decoration.None else Decoration(steps, NoPaintOutsets, isOpaque)
    component.repaint()
    return true
}

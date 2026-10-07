package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.layout.fitted
import org.jetbrains.compose.swing.foundation.layout.writeFitted
import org.jetbrains.compose.swing.foundation.util.fastAny
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.requestAfterValidation
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
 * The library gathers [outsets] and [isOpaque] after each modifier pass that attaches, detaches, moves or writes a
 * step, so an element's `update` needs no call for them. Decoration nodes automatically invalidate their component's
 * declared-node consumer after an element update. A node with fixed metadata can opt out and own its repaint, as
 * [DrawModifierNode] does. A node that changes one of them between passes calls [invalidateDecoration]. A change that
 * only affects [paint] is a `component.repaint()`. The outsets a step reserves
 * reach the component's insets under a Foundation container. Under any other parent only the part of them the
 * component's [PaintOutsets][org.jetbrains.compose.swing.foundation.layout.PaintOutsets] value leaves in layout does,
 * none by default.
 */
public abstract class DecorationModifierNode<T : Component> :
    SwingModifier.ComponentNode<T>(),
    Decorator {
    /** An element update may change the metadata its component gathers from the declared decoration nodes. */
    public override val shouldAutoInvalidate: Boolean get() = true

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

    /** Whether this step paints its content unchanged and takes no outsets, so the component is not decorated by it. */
    internal open val paintsNothing: Boolean get() = false

    /**
     * Gathers the component's decoration again, after this step's [outsets] or [isOpaque] changed between modifier
     * passes, and repaints it. An element's `update` needs no call. Does nothing while the node is not attached.
     */
    public fun invalidateDecoration() {
        if (!isAttached) return
        if (!publishDecoration(component as Decoratable, declaredNodes(), requesterNode = this)) component.repaint()
    }
}

/** The nodes declared on this node's component, in declaration order. */
internal fun SwingModifier.Node.declaredNodes(): List<SwingModifier.Node> {
    val nodes = ArrayList<SwingModifier.Node>()
    visitDeclaredNodes { nodes += it }
    return nodes
}

/**
 * Writes to [decoratable] the decoration [nodes] declare, inside the layer its container places it with, fitted to
 * the Foundation containers around it; see [publishSteps].
 */
internal fun publishDecoration(
    decoratable: Decoratable,
    nodes: List<SwingModifier.Node>,
    requesterNode: SwingModifier.Node,
): Boolean =
    publishSteps(decoratable, DecorationSteps.of(nodes, decoratable.decoration.steps.containerLayer), requesterNode)

/** Publishes [nodes] using the component lifetime so a request survives removal of its final modifier node. */
internal fun publishDecoration(
    decoratable: Decoratable,
    nodes: List<SwingModifier.Node>,
    requesterComponent: SwingComponentNode<*>,
): Boolean =
    publishSteps(
        decoratable,
        DecorationSteps.of(nodes, decoratable.decoration.steps.containerLayer),
        requesterComponent,
    )

/**
 * Writes to [decoratable] a decoration of [steps], fitted to the Foundation containers around it, and repaints it;
 * nothing where the value held equals it. Returns whether it wrote.
 *
 * @throws IllegalStateException if [decoratable] is not a [Component].
 */
internal fun publishSteps(
    decoratable: Decoratable,
    steps: DecorationSteps,
    requesterNode: SwingModifier.Node,
): Boolean = publishSteps(decoratable, steps, requesterNode as Any)

/** Publishes [steps] using the component lifetime so a request survives removal of its final modifier node. */
internal fun publishSteps(
    decoratable: Decoratable,
    steps: DecorationSteps,
    requesterComponent: SwingComponentNode<*>,
): Boolean = publishSteps(decoratable, steps, requesterComponent as Any)

private fun publishSteps(
    decoratable: Decoratable,
    steps: DecorationSteps,
    requester: Any,
): Boolean {
    val component =
        checkNotNull(decoratable as? Component) {
            "A ${Decoratable::class.java.name} paints as a component, and ${decoratable.javaClass.name} is not one"
        }
    val held = decoratable.decoration
    val value = held.fitted(component, steps, steps.isOpaque)
    if (value === held) return false
    when (requester) {
        is SwingModifier.Node -> writeFitted(decoratable, held, value, requesterNode = requester)
        is SwingComponentNode<*> -> writeFitted(decoratable, held, value, requesterComponent = requester)
        else -> error("Publishing a decoration requires an attached requester")
    }
    component.repaint()
    return true
}

/** A node that owns [requestAfterValidation] for the component this decoration belongs to. */
private val RevalidateRequestedComponent: (SwingModifier.Node) -> Unit = { node ->
    when (node) {
        is SwingModifier.ComponentNode<*> -> node.component.revalidate()
        is ParentLayoutNode -> node.component.revalidate()
        else -> error("Revalidation requires a component node")
    }
}

/** Revalidates the component this attached node belongs to after Swing finishes validation. */
internal fun SwingModifier.Node.revalidateComponentAfterValidation() {
    val component =
        when (this) {
            is SwingModifier.ComponentNode<*> -> component
            is ParentLayoutNode -> component
            else -> error("Revalidation requires a component node")
        }
    if (Thread.holdsLock(component.treeLock)) component.invalidate()
    requestAfterValidation(RevalidateRequestedComponent)
}

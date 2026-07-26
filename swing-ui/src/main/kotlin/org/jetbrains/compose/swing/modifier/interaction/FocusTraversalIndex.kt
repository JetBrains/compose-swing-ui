@file:JvmMultifileClass
@file:JvmName("InteractionModifiersKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import javax.swing.JComponent
import javax.swing.LayoutFocusTraversalPolicy

/**
 * Assigns this component a position in its container's keyboard focus-traversal order. Lower indices
 * are reached first when tabbing forward. Effective only inside a container that installs the
 * composition-order policy via [orderedFocusTraversal]; components without an index follow the indexed
 * ones in their natural order.
 *
 * @param index the traversal position; lower is earlier.
 */
public fun SwingModifier.focusTraversalIndex(index: Int): SwingModifier =
    this then
        propertyElement<JComponent, Any?>(
            index,
            read = { it.getClientProperty(FOCUS_TRAVERSAL_INDEX_KEY) },
            write = { c, v -> c.putClientProperty(FOCUS_TRAVERSAL_INDEX_KEY, v) },
        )

/**
 * Makes this container a focus-cycle root whose Tab order follows its children's
 * [focusTraversalIndex] values (ascending), rather than their on-screen geometry. Children without an
 * index are visited after the indexed ones. Requires a `JComponent` target.
 *
 * Only the order changes: which components Tab stops on stays Swing's own judgement. A control is a
 * stop exactly where it would be in a hand-written form, so captions, layout containers and anything
 * that cannot take the keyboard are stepped over. [focusable] declares that judgement for the
 * component it is applied to.
 */
public fun SwingModifier.orderedFocusTraversal(): SwingModifier = this then OrderedFocusTraversalElement

/**
 * The `JComponent` client-property key under which [focusTraversalIndex] stores a component's traversal
 * position. Read by the [orderedFocusTraversal] policy to order children.
 */
private const val FOCUS_TRAVERSAL_INDEX_KEY: String = "org.jetbrains.compose.swing.focusTraversalIndex"

private object OrderedFocusTraversalElement :
    SwingModifier.Element<JComponent, OrderedFocusTraversalElement.Node> {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node): Unit = node.apply()

    class Node : SwingModifier.Node<JComponent>() {
        private var original: SavedFocusTraversal? = null

        override fun onAttach() {
            original =
                SavedFocusTraversal(
                    cycleRoot = component.isFocusCycleRoot,
                    policyProvider = component.isFocusTraversalPolicyProvider,
                    policy = component.focusTraversalPolicy,
                )
        }

        fun apply() {
            component.isFocusCycleRoot = true
            component.isFocusTraversalPolicyProvider = true
            component.focusTraversalPolicy = CompositionOrderFocusTraversalPolicy
        }

        override fun onDetach() {
            val saved = original ?: return
            component.focusTraversalPolicy = saved.policy
            component.isFocusTraversalPolicyProvider = saved.policyProvider
            component.isFocusCycleRoot = saved.cycleRoot
        }
    }
}

private class SavedFocusTraversal(
    val cycleRoot: Boolean,
    val policyProvider: Boolean,
    val policy: FocusTraversalPolicy?,
)

/**
 * The policy every ordered container is given: Swing's own layout traversal policy, sorting the cycle
 * by [focusTraversalIndex] instead of by geometry and leaving the judgement of what a stop is to the
 * policy a Swing form is traversed with.
 */
private object CompositionOrderFocusTraversalPolicy : LayoutFocusTraversalPolicy() {
    init {
        setComparator(CompositionOrderComparator)
    }
}

/**
 * Orders components by declared traversal position first and by containment order after.
 *
 * No two distinct components of one tree compare equal: the sorted cycle is looked up by comparison, so
 * a component tying with another would be located at that other one's place in it.
 */
private object CompositionOrderComparator : Comparator<Component> {
    override fun compare(
        first: Component,
        second: Component,
    ): Int {
        val byIndex = first.effectiveTraversalIndex().compareTo(second.effectiveTraversalIndex())
        return if (byIndex != 0) byIndex else compareContainmentOrder(first, second)
    }
}

/**
 * The position this component sorts at: its own [focusTraversalIndex], or the earliest one declared
 * anywhere inside it where that is earlier. A container therefore never sorts after its own contents,
 * and a component with no index anywhere sorts after every indexed one.
 */
private fun Component.effectiveTraversalIndex(): Int {
    var earliest = (this as? JComponent)?.getClientProperty(FOCUS_TRAVERSAL_INDEX_KEY) as? Int ?: Int.MAX_VALUE
    if (this is Container) {
        for (child in components) earliest = minOf(earliest, child.effectiveTraversalIndex())
    }
    return earliest
}

/**
 * Compares two components by where they sit in the tree, in the depth-first order a focus cycle is
 * walked in: an ancestor precedes its descendants, and cousins follow the order their nearest common
 * container holds their branches in.
 */
private fun compareContainmentOrder(
    first: Component,
    second: Component,
): Int {
    val firstPath = first.pathFromTop()
    val secondPath = second.pathFromTop()
    var shared = 0
    while (shared < firstPath.size && shared < secondPath.size && firstPath[shared] === secondPath[shared]) {
        shared++
    }
    return when (shared) {
        firstPath.size, secondPath.size -> firstPath.size.compareTo(secondPath.size)
        else -> compareSiblingOrder(firstPath[shared], secondPath[shared])
    }
}

/** This component and its containers, outermost first. */
private fun Component.pathFromTop(): List<Component> {
    val path = ArrayDeque<Component>()
    var node: Component? = this
    while (node != null) {
        path.addFirst(node)
        node = node.parent
    }
    return path
}

/** Compares two children of one container by the order it holds them in. */
private fun compareSiblingOrder(
    first: Component,
    second: Component,
): Int {
    val parent = first.parent
    return parent?.let { it.getComponentZOrder(first).compareTo(it.getComponentZOrder(second)) } ?: 0
}

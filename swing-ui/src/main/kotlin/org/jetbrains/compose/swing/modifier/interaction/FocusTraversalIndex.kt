@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.util.Key
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import javax.swing.JComponent
import javax.swing.LayoutFocusTraversalPolicy

/**
 * Assigns this component a position in its container's keyboard focus-traversal order. Lower indices
 * are reached first when tabbing forward. Effective only inside a container that installs the
 * index-order policy via [orderedFocusTraversal]; components without an index follow the indexed
 * ones in their on-screen order.
 *
 * @param index the traversal position.
 * @return this modifier with the traversal position declared on it.
 */
public fun SwingModifier.focusTraversalIndex(index: Int): SwingModifier = property(FocusTraversalIndexProperty, index)

/**
 * Makes this container a focus-cycle root whose Tab order follows its children's
 * [focusTraversalIndex] values (ascending), rather than their on-screen geometry. Children without an
 * index are visited after the indexed ones, in the on-screen order Swing's default traversal gives them.
 * Requires a `JComponent` target.
 *
 * Only the order changes: which components Tab stops on stays Swing's own judgement. A control is a
 * stop exactly where it would be in a hand-written form, so captions, layout containers and anything
 * that cannot take the keyboard are stepped over. [focusable] declares that judgement for the
 * component it is applied to.
 *
 * @see java.awt.Container.setFocusTraversalPolicy
 */
public fun SwingModifier.orderedFocusTraversal(): SwingModifier = this then OrderedFocusTraversalElement

/**
 * The `JComponent` client-property key under which [focusTraversalIndex] stores a component's traversal
 * position. Read by the [orderedFocusTraversal] policy to order children.
 */
private val FOCUS_TRAVERSAL_INDEX_KEY: Key<Int> = Key("org.jetbrains.compose.swing.focusTraversalIndex")

private object OrderedFocusTraversalElement :
    SwingModifier.NodeElement<JComponent, OrderedFocusTraversalElement.Node>() {
    override val name: String get() = "orderedFocusTraversal"
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node): Unit = node.apply()

    // The declaration carries nothing, so the sole instance is the only element equal to it.
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    class Node : SwingModifier.ComponentNode<JComponent>() {
        private var original: SavedFocusTraversal? = null

        override fun onAttach() {
            original =
                SavedFocusTraversal(
                    cycleRoot = component.isFocusCycleRoot,
                    policyProvider = component.isFocusTraversalPolicyProvider,
                    // `getFocusTraversalPolicy` answers a cycle root that holds no policy of its own
                    // with the one it inherits. Only a policy the container holds is one to hand back;
                    // writing null leaves it inheriting again.
                    policy = if (component.isFocusTraversalPolicySet) component.focusTraversalPolicy else null,
                )
        }

        fun apply() {
            component.isFocusCycleRoot = true
            component.isFocusTraversalPolicyProvider = true
            component.focusTraversalPolicy = IndexOrderFocusTraversalPolicy()
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
 * The policy [orderedFocusTraversal] installs, one instance per container, sorting the focus cycle by
 * [focusTraversalIndex] first and by the on-screen order `LayoutFocusTraversalPolicy` reads after.
 */
private class IndexOrderFocusTraversalPolicy : LayoutFocusTraversalPolicy() {
    private val layoutOrder: Comparator<in Component> = super.getComparator()

    init {
        setComparator(compareBy<Component> { it.effectiveTraversalIndex() }.then(layoutOrder))
    }

    /**
     * The plain on-screen comparator, not the one this policy sorts by: `LayoutFocusTraversalPolicy`
     * sets the cycle root's `ComponentOrientation` on it before each traversal query, so the
     * on-screen order inside [layoutOrder] reads rows in the container's current direction.
     */
    override fun getComparator(): Comparator<in Component> = layoutOrder
}

/**
 * The position this component sorts at: its own [focusTraversalIndex], or the earliest one declared
 * anywhere inside it where that is earlier. A container therefore never sorts after its own contents,
 * and a component with no index anywhere sorts after every indexed one.
 */
private fun Component.effectiveTraversalIndex(): Int {
    var earliest = (this as? JComponent)?.get(FOCUS_TRAVERSAL_INDEX_KEY) ?: Int.MAX_VALUE
    if (this is Container) {
        for (child in components) earliest = minOf(earliest, child.effectiveTraversalIndex())
    }
    return earliest
}

private val FocusTraversalIndexProperty =
    ComponentPropertyDescriptor<JComponent, Int?>(
        name = "focusTraversalIndex",
        read = { it[FOCUS_TRAVERSAL_INDEX_KEY] },
        write = { component, value -> component[FOCUS_TRAVERSAL_INDEX_KEY] = value },
    )

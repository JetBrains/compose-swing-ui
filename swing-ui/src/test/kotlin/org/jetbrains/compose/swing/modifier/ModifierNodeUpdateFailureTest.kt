package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.node.CompositionLocalConsumerModifierNode
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.TestCompositionOwner
import javax.swing.JButton
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * A component node whose element fails to update it once its `onAttach` has run comes apart once, and is updated
 * again when declared again.
 */
class ModifierNodeUpdateFailureTest {
    @Test
    fun aChainAttachedWholeWhosePropertyNodeFailsToUpdateComesApartOnce() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())

        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(
                SwingModifier.then(LifecycleElement("first", events)).then(LifecycleElement("failing", events)),
            )
        }
        events.clear()

        holder.resetModifierState()

        assertEquals(listOf("onDetach failing", "onDetach first"), events.sorted())
    }

    @Test
    fun aChainAttachedWholeWhoseSubscriptionNodeFailsToUpdateComesApartOnce() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())

        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(
                SwingModifier
                    .then(LifecycleElement("first", events, additive = true))
                    .then(LifecycleElement("failing", events, additive = true)),
            )
        }
        events.clear()

        holder.resetModifierState()

        assertEquals(listOf("onDetach failing", "onDetach first"), events.sorted())
    }

    @Test
    fun aPropertyNodeEnteringAStandingChainThatFailsToUpdateComesApartOnce() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        holder.applyModifierDiff(SwingModifier.then(LifecycleElement("first", events)))
        events.clear()

        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(
                SwingModifier.then(LifecycleElement("first", events)).then(LifecycleElement("failing", events)),
            )
        }
        assertEquals(listOf("onAttach failing"), events)
        events.clear()

        holder.resetModifierState()

        assertEquals(listOf("onDetach failing", "onDetach first"), events.sorted())
    }

    @Test
    fun aReplacementSubscriptionNodeThatFailsToUpdateTakesItsSlotAndComesApartOnce() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        holder.applyModifierDiff(SwingModifier.then(OtherSubscriptionElement(events)))
        events.clear()

        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(SwingModifier.then(LifecycleElement("failing", events, additive = true)))
        }
        assertEquals(listOf("onAttach failing", "onDetach other"), events, "the replaced node comes apart")
        events.clear()

        holder.resetModifierState()

        assertEquals(listOf("onDetach failing"), events, "the replacement comes apart once")
    }

    @Test
    fun aSubscriptionNodeWhoseUpdateThrewIsUpdatedAgainWhenDeclaredAgain() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        val element = FailingOnceElement("once", events, additive = true)
        assertFailsWith<IllegalStateException> { holder.applyModifierDiff(SwingModifier.then(element)) }

        holder.applyModifierDiff(SwingModifier.then(element))

        assertEquals(listOf("onAttach once", "update once"), events, "the node whose update threw is updated again")
        assertSame(element.created.single(), element.updated.single())
        events.clear()

        holder.resetModifierState()

        assertEquals(listOf("onDetach once"), events)
    }

    @Test
    fun aKeyedReplacementWhoseUpdateThrewIsUpdatedAgainWhenDeclaredAgain() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        holder.applyModifierDiff(SwingModifier.then(LifecycleElement("key", events)))
        val element = FailingOnceElement("key", events, additive = false)
        assertFailsWith<IllegalStateException> { holder.applyModifierDiff(SwingModifier.then(element)) }
        events.clear()

        holder.applyModifierDiff(SwingModifier.then(element))

        assertEquals(listOf("update key"), events, "the node whose update threw is updated again")
        assertSame(element.created.single(), element.updated.single())
        events.clear()

        holder.resetModifierState()

        assertEquals(listOf("onDetach key"), events)
    }

    @Test
    fun aPropertyNodeWhoseUpdateThrewTakesBackTheModifierDeclaredBeforeIt() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        val standing = SwingModifier.then(SlotValueElement("a", events))
        holder.applyDeclaredModifier(standing)
        assertFailsWith<IllegalStateException> {
            holder.applyDeclaredModifier(SwingModifier.then(SlotValueElement("failing", events)))
        }
        events.clear()

        holder.applyDeclaredModifier(standing)

        assertEquals(
            listOf("update a"),
            events,
            "the node whose update threw is updated with the modifier declared again",
        )
        events.clear()

        holder.resetModifierState()

        assertEquals(listOf("onDetach slot"), events)
    }

    @Test
    fun aLocalConsumerNodeWhoseRefreshThrewIsUpdatedAgainWhenDeclaredAgain() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        val element = LocalConsumerElement(events)
        val modifier = SwingModifier.then(element)
        holder.applyDeclaredModifier(modifier)
        element.fails = true
        assertFailsWith<IllegalStateException> {
            checkNotNull(holder.modifierState).refreshLocalConsumer(element.created.single())
        }
        element.fails = false
        events.clear()

        holder.applyDeclaredModifier(modifier)

        assertEquals(listOf("update"), events, "the node whose refresh threw is updated again")
    }

    /** An additive element whose node reads composition locals, and whose update fails while [fails]. */
    private class LocalConsumerElement(
        private val events: MutableList<String>,
    ) : SwingModifier.NodeElement<JComponent, LocalConsumerNode>() {
        val created = ArrayList<LocalConsumerNode>()
        var fails = false

        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val additive: Boolean get() = true

        override fun create(): LocalConsumerNode = LocalConsumerNode().also { created += it }

        override fun update(node: LocalConsumerNode) {
            check(!fails) { "update fails" }
            events += "update"
        }

        override fun equals(other: Any?): Boolean = other === this

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private class LocalConsumerNode :
        SwingModifier.ComponentNode<JComponent>(),
        CompositionLocalConsumerModifierNode

    /** An element under [key] whose update fails the first time only, recording its node's lifecycle. */
    private class FailingOnceElement(
        override val key: String,
        private val events: MutableList<String>,
        override val additive: Boolean,
    ) : SwingModifier.NodeElement<JComponent, LifecycleNode>() {
        val created = ArrayList<LifecycleNode>()
        val updated = ArrayList<LifecycleNode>()
        private var failed = false

        override val targetType: Class<JComponent> get() = JComponent::class.java

        override fun create(): LifecycleNode = LifecycleNode(key, events).also { created += it }

        override fun update(node: LifecycleNode) {
            if (!failed) {
                failed = true
                error("update fails")
            }
            events += "update $key"
            updated += node
        }

        override fun equals(other: Any?): Boolean = other === this

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /** A property element whose one slot's node is updated with [value], failing to update it with `"failing"`. */
    private class SlotValueElement(
        private val value: String,
        private val events: MutableList<String>,
    ) : SwingModifier.NodeElement<JComponent, LifecycleNode>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val key: Any get() = "slot"

        override fun create(): LifecycleNode = LifecycleNode("slot", events)

        override fun update(node: LifecycleNode) {
            events += "update $value"
            check(value != "failing") { "update fails" }
        }

        override fun equals(other: Any?): Boolean =
            other is SlotValueElement && value == other.value && events === other.events

        override fun hashCode(): Int = 31 * value.hashCode() + System.identityHashCode(events)
    }

    /**
     * An element keyed by [name] that records its node's lifecycle, and whose update fails when [name] is
     * `"failing"`.
     */
    private class LifecycleElement(
        override val name: String,
        private val events: MutableList<String>,
        override val additive: Boolean = false,
    ) : SwingModifier.NodeElement<JComponent, LifecycleNode>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val key: Any get() = name

        override fun create(): LifecycleNode = LifecycleNode(name, events)

        override fun update(node: LifecycleNode) {
            check(name != "failing") { "update fails" }
        }

        override fun equals(other: Any?): Boolean =
            other is LifecycleElement && name == other.name && additive == other.additive && events === other.events

        override fun hashCode(): Int = 31 * name.hashCode() + System.identityHashCode(events)
    }

    /** A subscription element of another kind than [LifecycleElement], recording its node's lifecycle. */
    private class OtherSubscriptionElement(
        private val events: MutableList<String>,
    ) : SwingModifier.NodeElement<JComponent, LifecycleNode>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val additive: Boolean get() = true

        override fun create(): LifecycleNode = LifecycleNode("other", events)

        override fun update(node: LifecycleNode) = Unit

        override fun equals(other: Any?): Boolean = other is OtherSubscriptionElement && events === other.events

        override fun hashCode(): Int = System.identityHashCode(events)
    }

    private class LifecycleNode(
        private val name: String,
        private val events: MutableList<String>,
    ) : SwingModifier.ComponentNode<JComponent>() {
        override fun onAttach() {
            events += "onAttach $name"
        }

        override fun onDetach() {
            events += "onDetach $name"
        }
    }
}

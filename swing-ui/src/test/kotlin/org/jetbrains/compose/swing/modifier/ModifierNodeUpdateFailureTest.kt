package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.node.CompositionLocalConsumerModifierNode
import org.jetbrains.compose.swing.node.ObserverModifierNode
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.node.observeReads
import javax.swing.JButton
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    fun anAdditiveNodeWhoseInPlaceUpdateThrewIsUpdatedAgainWhenDeclaredAgain() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        val first = FailingOnceElement("once", events, additive = true)
        assertFailsWith<IllegalStateException> { holder.applyModifierDiff(SwingModifier.then(first)) }
        holder.applyModifierDiff(SwingModifier.then(first))
        events.clear()

        val second = FailingOnceElement("once", events, additive = true)
        assertFailsWith<IllegalStateException> { holder.applyDeclaredModifier(SwingModifier.then(second)) }

        holder.applyDeclaredModifier(SwingModifier.then(second))

        assertEquals(listOf("update once"), events, "the node whose in-place update threw is updated again")
        assertSame(
            first.created.single(),
            second.updated.single(),
            "the node created on the first, failed apply is the one updated when declared again",
        )
        events.clear()

        holder.resetModifierState()

        assertEquals(listOf("onDetach once"), events, "resetModifierState detaches the node that was created")
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

    @Test
    fun aNodeWhoseOnAttachThrowsIsDetachedAndTheNodesMarkedAfterItAreUnwound() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        val watched = mutableIntStateOf(0)
        val first = LifecycleElement("first", events, additive = true)
        val failing = FailingAttachElement(watched)
        val last = LifecycleElement("last", events, additive = true)

        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(SwingModifier.then(first).then(failing).then(last))
        }

        val failingNode = failing.created.single()
        assertFalse(
            failingNode.isAttached,
            "a node whose onAttach threw must be detached, not left attached with no slot holding it",
        )
        assertIs<ModifierNodeDetachedCancellationException>(
            failingNode.cancellation,
            "detaching the node must cancel what its coroutine scope started during the failed onAttach",
        )
        watched.intValue = 1
        Snapshot.sendApplyNotifications()
        assertFalse(failingNode.changed, "a node whose onAttach threw must not still hear a change to what it read")

        val lastNode = last.created.single()
        assertFalse(
            lastNode.isAttached,
            "a node marked attached while the chain was walked, but whose onAttach never got a turn, must be unwound",
        )
        assertEquals(
            listOf("onAttach first"),
            events,
            "the node standing before the failure ran; neither the failing node nor the one behind it runs onDetach",
        )
        events.clear()

        holder.resetModifierState()

        assertEquals(listOf("onDetach first"), events, "the node still standing before the failure detaches on reset")
    }

    @Test
    fun aNodeThatFailsToCreateUnwindsTheNodesMarkedBeforeItInTheSameChainWalk() {
        val events = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        val first = LifecycleElement("first", events, additive = true)

        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(SwingModifier.then(first).then(FailingCreateElement))
        }

        val node = first.created.single()
        assertFalse(
            node.isAttached,
            "a node marked attached while a later element in the same chain walk failed to create must be unwound",
        )
        assertEquals(emptyList(), events, "a node whose onAttach never ran must not run onDetach either")
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
        val created = ArrayList<LifecycleNode>()

        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val key: Any get() = name

        override fun create(): LifecycleNode = LifecycleNode(name, events).also { created += it }

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

    /** An additive element whose node opens a coroutine scope and observes [watched] before failing to attach. */
    private class FailingAttachElement(
        private val watched: MutableIntState,
    ) : SwingModifier.NodeElement<JComponent, FailingAttachNode>() {
        val created = ArrayList<FailingAttachNode>()

        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val additive: Boolean get() = true

        override fun create(): FailingAttachNode = FailingAttachNode(watched).also { created += it }

        override fun update(node: FailingAttachNode) = Unit

        override fun equals(other: Any?): Boolean = other === this

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * A node whose `onAttach` launches a coroutine on its own scope and observes [watched], recording the
     * cancellation its scope receives and whether it is told of a later change, then fails to attach.
     */
    private class FailingAttachNode(
        private val watched: MutableIntState,
    ) : SwingModifier.ComponentNode<JComponent>(),
        ObserverModifierNode {
        var cancellation: Throwable? = null
        var changed = false

        override fun onAttach() {
            coroutineScope.launch {
                try {
                    awaitCancellation()
                } catch (cancelled: CancellationException) {
                    cancellation = cancelled
                    throw cancelled
                }
            }
            observeReads { watched.intValue }
            error("onAttach fails")
        }

        override fun onObservedReadsChanged() {
            changed = true
        }
    }

    /** A subscription element whose node cannot be created. */
    private object FailingCreateElement : SwingModifier.NodeElement<JComponent, LifecycleNode>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val additive: Boolean get() = true

        override fun create(): LifecycleNode = error("create fails")

        override fun update(node: LifecycleNode) = Unit

        override fun equals(other: Any?): Boolean = other === this

        override fun hashCode(): Int = System.identityHashCode(this)
    }
}

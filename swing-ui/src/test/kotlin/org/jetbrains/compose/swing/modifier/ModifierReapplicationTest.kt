package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.interaction.focusAccelerator
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Every count here is taken through the public [SwingModifier.NodeElement] seam - an element counts its own
 * [SwingModifier.NodeElement.update] calls and writes a real Swing property - so the assertions read the
 * writes the widget receives, not the diff machinery that decides them.
 */
class ModifierReapplicationTest {
    private fun mouseEntered(component: JComponent): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_ENTERED, 0L, 0, 0, 0, 0, false)

    private fun mouseEnterListener(onEnter: () -> Unit): MouseListener = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent?): Unit = onEnter()
    }

    @Test
    fun anUnchangedDeclarationIsWrittenOnce() = runComposeSwingTest {
        var tick by mutableStateOf(0)
        val writes = AtomicInteger()
        setContent {
            // Reading the counter's state here recomposes the whole content, so the button's modifier is
            // rebuilt from scratch on every tick - a whole new run of cells, declaring the same values
            // each time.
            Label("tick $tick")
            Button(
                "X",
                onClick = { },
                modifier =
                    SwingModifier
                        .background(Color.GREEN)
                        .then(CountingToolTipElement("hello", writes)),
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertEquals("hello", button.toolTipText, "the element should apply its value on first composition")
        assertEquals(1, writes.get(), "the first composition should write once")

        tick++
        awaitIdle()
        tick++
        awaitIdle()

        assertEquals(Color.GREEN, button.background, "the applied background should still hold")
        assertEquals("hello", button.toolTipText, "the applied value should still hold")
        assertEquals(1, writes.get(), "a declaration that did not change is written once, not once per recomposition")
    }

    @Test
    fun aChangedValueIsWrittenAgain() = runComposeSwingTest {
        var text by mutableStateOf("first")
        val writes = AtomicInteger()
        setContent {
            Button("X", onClick = { }, modifier = SwingModifier.then(CountingToolTipElement(text, writes)))
        }
        val button = onNodeOfType<JButton>().fetch()
        assertEquals("first", button.toolTipText, "the element should apply its initial value")
        assertEquals(1, writes.get(), "the first composition should write once")

        text = "second"
        awaitIdle()

        assertEquals("second", button.toolTipText, "a changed value must reach the widget")
        assertEquals(2, writes.get(), "a changed value costs exactly one further write")
    }

    @Test
    fun anUnchangedElementDeclaredBeforeAChangedOneIsNotWrittenAgain() = runComposeSwingTest {
        var accent by mutableStateOf(Color.GREEN)
        val writes = AtomicInteger()
        setContent {
            Button(
                "X",
                onClick = { },
                modifier =
                    SwingModifier
                        .then(CountingToolTipElement("hello", writes))
                        .background(accent),
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertEquals(Color.GREEN, button.background, "the background element should apply its initial value")
        assertEquals(1, writes.get(), "the tooltip element should write once on first composition")

        accent = Color.BLUE
        awaitIdle()

        // The modifier as a whole changed, so it is diffed element by element: the background slot takes
        // the new color while the tooltip slot, declared ahead of it and untouched, is left alone.
        // Nothing has written when that slot is reached.
        assertEquals(Color.BLUE, button.background, "the changed element must take its new value")
        assertEquals("hello", button.toolTipText, "the unchanged element's value must still hold")
        assertEquals(1, writes.get(), "an unchanged element declared before a changed one is not written again")
    }

    @Test
    fun aSubscriptionAfterAChangedPropertyIsNotWrittenAgain() = runComposeSwingTest {
        var accent by mutableStateOf(Color.GREEN)
        val writes = AtomicInteger()
        var enters = 0
        val hover = mouseEnterListener { enters++ }
        setContent {
            Button(
                "X",
                onClick = { },
                modifier = SwingModifier.background(accent).then(CountingHoverElement(writes, hover)),
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertEquals(1, writes.get(), "the subscription element should write once on first composition")

        accent = Color.BLUE
        awaitIdle()

        // A property slot that writes has every property slot after it written again, so the modifier's
        // own order decides which declaration stands where two of them land on one property. A
        // subscription writes a registration instead, which overlaps nothing another slot writes, so
        // there is no order to settle: its slot keeps the element it holds whatever wrote before it.
        assertEquals(Color.BLUE, button.background, "the changed property must take its new value")
        button.dispatchEvent(mouseEntered(button))
        assertEquals(1, enters, "the subscription installed once still stands")
        assertEquals(1, writes.get(), "a subscription declared after a changed property is not written again")
    }

    @Test
    fun aReactivatedNodeHasItsWholeChainAppliedToTheFreshComponent() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var tick by mutableStateOf(0)
        val writes = AtomicInteger()
        var enterCount = 0
        val hover = mouseEnterListener { enterCount++ }
        // One modifier instance for every pass: what the fresh component gets cannot depend on the
        // declaration having changed, because this declaration provably never does.
        val chain =
            SwingModifier
                .background(Color.GREEN)
                .then(CountingToolTipElement("hello", writes))
                .listener(hover, MOUSE_EVENTS)
        setContent {
            Label("tick $tick")
            ReusableContentHost(active = active) {
                Button("X", onClick = { }, modifier = chain)
            }
        }
        val beforePark = onNodeOfType<JButton>().fetch()
        assertEquals(Color.GREEN, beforePark.background, "the property elements should apply before parking")
        assertEquals("hello", beforePark.toolTipText, "the custom element should apply before parking")
        beforePark.dispatchEvent(mouseEntered(beforePark))
        assertEquals(1, enterCount, "the listener should fire once before parking")
        assertEquals(1, writes.get(), "the first composition should write once")

        // A plain recomposition of this very modifier writes nothing further, which is what makes the
        // reactivation below the only thing the next assertions can be measuring.
        tick++
        awaitIdle()
        assertEquals(1, writes.get(), "recomposing an unchanged modifier must not write again")

        // Park the content and bring it back. Reactivation builds a fresh component from the node's
        // factory and drives it as a node's first composition would, so the fresh component only
        // carries the modifier if that first apply writes all of it.
        active = false
        awaitIdle()
        active = true
        awaitIdle()

        val afterReactivation = onNodeOfType<JButton>().fetch<JButton>()
        assertNotSame(beforePark, afterReactivation, "reactivation builds a fresh component, not the parked one")
        assertEquals(
            2,
            writes.get(),
            "the fresh component's first apply writes the modifier even though it is unchanged",
        )
        assertEquals("hello", afterReactivation.toolTipText, "the custom element must apply to the fresh component")
        assertEquals(
            Color.GREEN,
            afterReactivation.background,
            "the property element must apply to the fresh component",
        )
        afterReactivation.dispatchEvent(mouseEntered(afterReactivation))
        assertEquals(2, enterCount, "the listener must be installed on the fresh component")
    }

    @Test
    fun aCallbackElementFiresTheCallbackDeclaredLast() = runComposeSwingTest {
        var second by mutableStateOf(false)
        var captured = ""
        setContent {
            // The lambda captures this pass's local, so a stale callback reports the stale value
            // instead of quietly reading the current one at fire time.
            val declared = if (second) "second" else "first"
            Button("X", onClick = { }, modifier = SwingModifier.then(HoverCallbackElement { captured = declared }))
        }
        val button = onNodeOfType<JButton>().fetch()

        button.dispatchEvent(mouseEntered(button))
        assertEquals("first", captured, "the element should install the callback declared first")

        second = true
        awaitIdle()
        button.dispatchEvent(mouseEntered(button))

        assertEquals("second", captured, "a fresh callback must reach the installed listener")
    }

    @Test
    fun anElementDroppedFromTheChainRestoresTheValueFromBeforeIt() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        val writes = AtomicInteger()
        setContent {
            Button(
                "X",
                onClick = {
                },
                modifier =
                    if (styled) {
                        SwingModifier.then(
                            CountingToolTipElement("hello", writes),
                        )
                    } else {
                        SwingModifier
                    },
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        val original = JButton("X").toolTipText
        assertEquals("hello", button.toolTipText, "the element should apply while present")

        styled = false
        awaitIdle()

        assertEquals(original, button.toolTipText, "dropping the element restores the value it found")
        assertEquals(1, writes.get(), "an element that left the modifier is not written again")
    }

    /**
     * A user-authored property element that writes the target's tooltip and counts every write it makes.
     *
     * It declares equality over everything it carries - its [text] by value and the [writes] counter it
     * reports through by identity - which is what an element whose payload is plain data does, and what
     * lets two elements built from one declaration stand in for each other.
     */
    private class CountingToolTipElement(
        private val text: String,
        private val writes: AtomicInteger,
    ) : SwingModifier.NodeElement<JComponent, CountingToolTipElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override fun create(): Node = Node()

        override fun update(node: Node) {
            writes.incrementAndGet()
            node.write(text)
        }

        override fun equals(other: Any?): Boolean =
            other is CountingToolTipElement && text == other.text && writes === other.writes

        override fun hashCode(): Int = 31 * text.hashCode() + System.identityHashCode(writes)

        class Node : SwingModifier.ComponentNode<JComponent>() {
            private var original: String? = null

            override fun onAttach() {
                original = component.toolTipText
            }

            fun write(text: String) {
                component.toolTipText = text
            }

            override fun onDetach() {
                component.toolTipText = original
            }
        }
    }

    /**
     * A user-authored subscription element that installs a hover listener and counts every write it
     * makes.
     *
     * It carries the counter it reports through and the listener it installs, both compared by
     * identity, so the slot holding one keeps it across a pass that builds another from the same
     * declaration.
     */
    private class CountingHoverElement(
        private val writes: AtomicInteger,
        private val listener: MouseListener,
    ) : SwingModifier.NodeElement<JComponent, CountingHoverElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val additive: Boolean get() = true

        override fun create(): Node = Node(listener)

        override fun update(node: Node) {
            writes.incrementAndGet()
        }

        override fun equals(other: Any?): Boolean =
            other is CountingHoverElement && writes === other.writes && listener === other.listener

        override fun hashCode(): Int = 31 * System.identityHashCode(writes) + System.identityHashCode(listener)

        class Node(
            private val listener: MouseListener,
        ) : SwingModifier.ComponentNode<JComponent>() {
            override fun onAttach() {
                component.addMouseListener(listener)
            }

            override fun onDetach() {
                component.removeMouseListener(listener)
            }
        }
    }

    /**
     * A user-authored subscription element carrying a callback. The listener is installed once, in
     * [Node.onAttach], and reads the callback from the node's field, so refreshing that field is what
     * keeps the callback current.
     *
     * It carries a lambda, which is a fresh object on every pass and no two of which are known to do
     * the same thing, so it is equal only to itself and is refreshed on every pass.
     */
    private class HoverCallbackElement(
        private val onEnter: () -> Unit,
    ) : SwingModifier.NodeElement<JComponent, HoverCallbackElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val additive: Boolean get() = true

        override fun create(): Node = Node()

        override fun update(node: Node) {
            node.onEnter = onEnter
        }

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)

        class Node : SwingModifier.ComponentNode<JComponent>() {
            var onEnter: () -> Unit = {}

            private val listener =
                object : MouseAdapter() {
                    override fun mouseEntered(e: MouseEvent?) {
                        onEnter()
                    }
                }

            override fun onAttach() {
                component.addMouseListener(listener)
            }

            override fun onDetach() {
                component.removeMouseListener(listener)
            }
        }
    }

    /** A subscription element no component but a text field is the target of. */
    private class TextFieldOnlyElement :
        SwingModifier.NodeElement<JTextField, SwingModifier.ComponentNode<JTextField>>() {
        override val targetType: Class<JTextField> get() = JTextField::class.java

        override val additive: Boolean get() = true

        override fun create(): SwingModifier.ComponentNode<JTextField> = SwingModifier.ComponentNode()

        override fun update(node: SwingModifier.ComponentNode<JTextField>) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * An element whose node reads the component as it comes apart, so a test can see what the rest of
     * the modifier had standing at that moment.
     */
    private class ReadOnDetachElement(
        private val onTeardown: (JComponent) -> Unit,
    ) : SwingModifier.NodeElement<JComponent, ReadOnDetachElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override fun create(): Node = Node(onTeardown)

        override fun update(node: Node) = Unit

        override fun equals(other: Any?): Boolean = other is ReadOnDetachElement && onTeardown === other.onTeardown

        override fun hashCode(): Int = System.identityHashCode(onTeardown)

        class Node(
            private val onTeardown: (JComponent) -> Unit,
        ) : SwingModifier.ComponentNode<JComponent>() {
            override fun onDetach() {
                onTeardown(component)
            }
        }
    }

    @Test
    fun reusingNodeInstanceAcrossMultipleComponentsFailsLoudly() = runComposeSwingTest {
        val sharedNode = object : SwingModifier.ComponentNode<Component>() {}
        val sharedElement =
            object : SwingModifier.NodeElement<Component, SwingModifier.ComponentNode<Component>>() {
                override val targetType: Class<Component> get() = Component::class.java

                override fun create(): SwingModifier.ComponentNode<Component> = sharedNode

                override fun update(node: SwingModifier.ComponentNode<Component>) = Unit

                override fun equals(other: Any?): Boolean = this === other

                override fun hashCode(): Int = System.identityHashCode(this)
            }

        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label(text = "A", modifier = sharedElement)
                    Label(text = "B", modifier = sharedElement)
                }
            }
        assertTrue(
            failure.message.orEmpty().contains("may not be attached to multiple components"),
            "Reusing a Node instance across components must fail loudly, but was: ${failure.message}",
        )
    }

    private companion object {
        /**
         * The registration handed to `listener`, held once so a modifier built from it is the same declaration
         * on every pass.
         */
        val MOUSE_EVENTS =
            ListenerRegistration<JButton, MouseListener>(
                name = "mouseListener",
                { component, listener -> component.addMouseListener(listener) },
                { component, listener -> component.removeMouseListener(listener) },
            )
    }

    @Test
    fun aSubscriptionSlotRefusingTheElementDeclaredForItKeepsTheOneItHolds() {
        var enterCount = 0
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        holder.applyModifierDiff(SwingModifier.then(HoverCallbackElement { enterCount++ }))
        val button = holder.component
        button.dispatchEvent(mouseEntered(button))
        assertEquals(1, enterCount, "the declared listener should fire while it stands")

        // A conditional modifier hands the position an element of another kind, and the component is not
        // the target that one requires. The slot cannot host it, and the refusal leaves the slot with
        // the only node it has - the one it was holding.
        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(SwingModifier.then(TextFieldOnlyElement()))
        }

        button.dispatchEvent(mouseEntered(button))
        assertEquals(2, enterCount, "the slot that could not be replaced still holds its listener")

        // The node is released, which takes every slot apart. A slot already taken apart would fail here.
        holder.resetModifierState()

        button.dispatchEvent(mouseEntered(button))
        assertEquals(2, enterCount, "releasing the node removes the listener exactly once")
    }

    @Test
    fun aPropertySlotRefusingTheElementDeclaredForItComesApartOnce() {
        val order = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        holder.applyModifierDiff(SwingModifier.then(DetachOrderElement("tip", order)))

        // The key is declared through an element of another kind, whose target the component is not. The slot
        // restores before the arriving element is refused.
        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(SwingModifier.then(TextFieldOnlyPropertyElement("tip")))
        }
        assertEquals(listOf("tip"), order, "the slot comes apart before its replacement is refused")

        // A slot left standing after it came apart would fail here.
        holder.resetModifierState()

        assertEquals(listOf("tip"), order, "a slot that came apart is not taken apart again")
    }

    /** A property element keyed by [name] that no component but a text field is the target of. */
    private class TextFieldOnlyPropertyElement(
        override val name: String,
    ) : SwingModifier.NodeElement<JTextField, SwingModifier.ComponentNode<JTextField>>() {
        override val targetType: Class<JTextField> get() = JTextField::class.java

        override val key: Any get() = name

        override fun create(): SwingModifier.ComponentNode<JTextField> = SwingModifier.ComponentNode()

        override fun update(node: SwingModifier.ComponentNode<JTextField>) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    @Test
    fun aSlotDeclaredAgainComesApartWhereTheChainDeclaresIt() = runComposeSwingTest {
        var tipped by mutableStateOf(true)
        var styled by mutableStateOf(true)
        val writes = AtomicInteger()
        val seen = ArrayList<String?>()
        val probe = ReadOnDetachElement { seen += it.toolTipText }
        setContent {
            Button(
                "X",
                onClick = { },
                modifier =
                    when {
                        !styled -> SwingModifier
                        tipped -> SwingModifier.then(CountingToolTipElement("declared", writes)).then(probe)
                        else -> SwingModifier.then(probe)
                    },
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertEquals("declared", button.toolTipText, "the tooltip element should apply while present")

        // The tooltip leaves the modifier and is declared again, so its slot is the newer of the two while
        // the modifier still declares it first. What the modifier declares is what the unwind follows.
        tipped = false
        awaitIdle()
        tipped = true
        awaitIdle()
        styled = false
        awaitIdle()

        assertEquals(listOf<String?>("declared"), seen, "the slot declared later comes apart first")
    }

    @Test
    fun aParkedChainComesApartWhereItDeclares() = runComposeSwingTest {
        var tipped by mutableStateOf(true)
        var active by mutableStateOf(true)
        val writes = AtomicInteger()
        val seen = ArrayList<String?>()
        val probe = ReadOnDetachElement { seen += it.toolTipText }
        setContent {
            ReusableContentHost(active = active) {
                Button(
                    "X",
                    onClick = { },
                    modifier =
                        if (tipped) {
                            SwingModifier.then(CountingToolTipElement("declared", writes)).then(probe)
                        } else {
                            SwingModifier.then(probe)
                        },
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch()
        assertEquals("declared", button.toolTipText, "the tooltip element should apply while present")

        // Parking drains the whole modifier at once, in the order a removal takes it apart in.
        tipped = false
        awaitIdle()
        tipped = true
        awaitIdle()
        active = false
        awaitIdle()

        assertEquals(listOf<String?>("declared"), seen, "a parked modifier unwinds the way a removal does")
    }

    @Test
    fun aChainIsAppliedAgainAfterAPassThatThrewPartWayThroughIt() {
        // A pass can throw after a slot is already installed - an element whose target the component is
        // not, reached once an earlier one has attached. What the modifier declared is then recorded for
        // slots the pass did not reach, so the next pass has to write against what the slots hold
        // rather than against the order the failed pass left behind.
        val writes = AtomicInteger()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        holder.applyModifierDiff(SwingModifier.background(Color.BLUE))

        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(
                SwingModifier
                    .background(Color.BLUE)
                    .then(CountingToolTipElement("hello", writes))
                    .focusAccelerator('x'),
            )
        }
        assertEquals(1, writes.get(), "the slot the failed pass reached is written once")

        holder.applyModifierDiff(SwingModifier.background(Color.BLUE).then(CountingToolTipElement("hello", writes)))

        val button = holder.component
        assertEquals(Color.BLUE, button.background, "the modifier the pass after the failure declares is applied")
        assertEquals("hello", button.toolTipText, "including the slot the failed pass had already installed")
        assertEquals(2, writes.get(), "that slot is written against what it holds, not adopted against a lost order")
    }

    @Test
    fun aSlotAPassThatThrewInstalledComesApartBeforeTheDeclaredOnes() {
        val order = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        holder.applyModifierDiff(
            SwingModifier.then(DetachOrderElement("first", order)).then(DetachOrderElement("second", order)),
        )

        // The pass throws after installing "third", so nothing records where the modifier declared it. Its
        // slot is the most recently attached, which is what its place in the unwind is taken from.
        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(
                SwingModifier
                    .then(DetachOrderElement("first", order))
                    .then(DetachOrderElement("second", order))
                    .then(DetachOrderElement("third", order))
                    .focusAccelerator('x'),
            )
        }
        assertEquals(emptyList(), order, "the failed pass takes no slot apart")

        holder.resetModifierState()

        assertEquals(listOf("third", "second", "first"), order, "the slot with no declared place comes apart first")
    }

    @Test
    fun aChainAttachedAfterAPassThatThrewHavingTakenEverySlotApartComesApartOnce() {
        val order = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        holder.applyModifierDiff(SwingModifier.then(DetachOrderElement("first", order)))

        // The pass takes "first" apart, then throws before it records what it declares, so the order it
        // leaves behind still names a slot that no longer stands.
        assertFailsWith<IllegalStateException> { holder.applyModifierDiff(SwingModifier.focusAccelerator('x')) }
        holder.applyModifierDiff(SwingModifier.then(DetachOrderElement("first", order)))
        order.clear()

        holder.resetModifierState()

        assertEquals(listOf("first"), order, "the chain attached after the failure comes apart once")
    }

    @Test
    fun aChainWhoseNodeFailsToAttachComesApartOnlyWhereItAttached() {
        val order = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())

        assertFailsWith<IllegalStateException> {
            holder.applyModifierDiff(
                SwingModifier
                    .then(DetachOrderElement("first", order))
                    .then(FailingAttachElement)
                    .then(DetachOrderElement("last", order)),
            )
        }
        assertEquals(emptyList(), order, "the failed pass takes no slot apart")

        // A slot recorded for a node whose onAttach never finished would fail here.
        holder.resetModifierState()

        assertEquals(listOf("first"), order, "only the slot whose node attached comes apart")
    }

    @Test
    fun aSlotAPassThatThrewCreatingItsReplacementTookApartAttachesAgainWhenDeclaredAgain() {
        assertSlotTakenApartByAFailedPassAttachesAgain(failOnAttach = false)
    }

    @Test
    fun aSlotAPassThatThrewAttachingItsReplacementTookApartAttachesAgainWhenDeclaredAgain() {
        assertSlotTakenApartByAFailedPassAttachesAgain(failOnAttach = true)
    }

    private fun assertSlotTakenApartByAFailedPassAttachesAgain(failOnAttach: Boolean) {
        val order = ArrayList<String>()
        val holder = SwingNodeHolder(JButton("Save")).attachedTo(TestCompositionOwner())
        val standing = SwingModifier.then(DetachOrderElement("tip", order))
        holder.applyDeclaredModifier(standing)
        assertFailsWith<IllegalStateException> {
            holder.applyDeclaredModifier(SwingModifier.then(FailingReplacementElement("tip", failOnAttach)))
        }
        assertEquals(listOf("tip"), order, "the slot comes apart before its replacement fails")
        order.clear()

        holder.applyDeclaredModifier(standing)
        holder.resetModifierState()

        assertEquals(listOf("tip"), order, "the modifier declared again attaches the slot, which comes apart once")
    }

    /** A property element keyed by [name] whose node fails to be created, or fails as it attaches. */
    private class FailingReplacementElement(
        override val name: String,
        private val failOnAttach: Boolean,
    ) : SwingModifier.NodeElement<JComponent, FailingAttachElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val key: Any get() = name

        override fun create(): FailingAttachElement.Node {
            check(failOnAttach) { "create fails" }
            return FailingAttachElement.Node()
        }

        override fun update(node: FailingAttachElement.Node) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /** A property element whose node fails as it attaches. */
    private object FailingAttachElement : SwingModifier.NodeElement<JComponent, FailingAttachElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override fun create(): Node = Node()

        override fun update(node: Node) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)

        class Node : SwingModifier.ComponentNode<JComponent>() {
            override fun onAttach(): Unit = error("onAttach fails")
        }
    }

    /**
     * A user-authored property element whose slot reports its name as it comes apart, keyed by that name
     * so that two of them take two slots.
     */
    private class DetachOrderElement(
        override val name: String,
        private val order: MutableList<String>,
    ) : SwingModifier.NodeElement<JComponent, DetachOrderElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val key: Any get() = name

        override fun create(): Node = Node(name, order)

        override fun update(node: Node) = Unit

        override fun equals(other: Any?): Boolean =
            other is DetachOrderElement && name == other.name && order === other.order

        override fun hashCode(): Int = 31 * name.hashCode() + System.identityHashCode(order)

        class Node(
            private val name: String,
            private val order: MutableList<String>,
        ) : SwingModifier.ComponentNode<JComponent>() {
            override fun onDetach() {
                order += name
            }
        }
    }
}

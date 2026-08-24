package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

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
            // Reading the counter's state here recomposes the whole content, so the button's chain is
            // rebuilt from scratch on every tick - a whole new chain of cells, declaring the same
            // values each time.
            Label("tick $tick")
            Button(
                "X",
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
            Button("X", modifier = SwingModifier.then(CountingToolTipElement(text, writes)))
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
    fun onlyTheChangedElementOfAChainIsWrittenAgain() = runComposeSwingTest {
        var accent by mutableStateOf(Color.GREEN)
        val writes = AtomicInteger()
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier
                        .background(accent)
                        .then(CountingToolTipElement("hello", writes)),
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertEquals(Color.GREEN, button.background, "the background element should apply its initial value")
        assertEquals(1, writes.get(), "the tooltip element should write once on first composition")

        accent = Color.BLUE
        awaitIdle()

        // The chain as a whole changed, so it is diffed element by element: the background slot takes
        // the new color while the tooltip slot, whose declaration is untouched, is left alone.
        assertEquals(Color.BLUE, button.background, "the changed element must take its new value")
        assertEquals("hello", button.toolTipText, "the unchanged element's value must still hold")
        assertEquals(1, writes.get(), "an unchanged element beside a changed one is not written again")
    }

    @Test
    fun aReactivatedNodeHasItsWholeChainAppliedToTheFreshComponent() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var tick by mutableStateOf(0)
        val writes = AtomicInteger()
        var enterCount = 0
        val hover = mouseEnterListener { enterCount++ }
        // One chain instance for every pass: what the fresh component gets cannot depend on the
        // declaration having changed, because this declaration provably never does.
        val chain =
            SwingModifier
                .background(Color.GREEN)
                .then(CountingToolTipElement("hello", writes))
                .listener(hover, ADD_MOUSE_LISTENER, REMOVE_MOUSE_LISTENER)
        setContent {
            Label("tick $tick")
            ReusableContentHost(active = active) {
                Button("X", modifier = chain)
            }
        }
        val beforePark = onNodeOfType<JButton>().fetch()
        assertEquals(Color.GREEN, beforePark.background, "the property elements should apply before parking")
        assertEquals("hello", beforePark.toolTipText, "the custom element should apply before parking")
        beforePark.dispatchEvent(mouseEntered(beforePark))
        assertEquals(1, enterCount, "the listener should fire once before parking")
        assertEquals(1, writes.get(), "the first composition should write once")

        // A plain recomposition of this very chain writes nothing further, which is what makes the
        // reactivation below the only thing the next assertions can be measuring.
        tick++
        awaitIdle()
        assertEquals(1, writes.get(), "recomposing an unchanged chain must not write again")

        // Park the content and bring it back. Reactivation builds a fresh component from the node's
        // factory and drives it as a node's first composition would, so the fresh component only
        // carries the chain if that first apply writes all of it.
        active = false
        awaitIdle()
        active = true
        awaitIdle()

        val afterReactivation = onNodeOfType<JButton>().fetch<JButton>()
        assertNotSame(beforePark, afterReactivation, "reactivation builds a fresh component, not the parked one")
        assertEquals(
            2,
            writes.get(),
            "the fresh component's first apply writes the chain even though it is unchanged",
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
            Button("X", modifier = SwingModifier.then(HoverCallbackElement { captured = declared }))
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
                modifier = if (styled) SwingModifier.then(CountingToolTipElement("hello", writes)) else SwingModifier,
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        val original = JButton("X").toolTipText
        assertEquals("hello", button.toolTipText, "the element should apply while present")

        styled = false
        awaitIdle()

        assertEquals(original, button.toolTipText, "dropping the element restores the value it found")
        assertEquals(1, writes.get(), "an element that left the chain is not written again")
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

        class Node : SwingModifier.Node<JComponent>() {
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

        class Node : SwingModifier.Node<JComponent>() {
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

    private companion object {
        /**
         * The add/remove pair handed to `listener`, held once so a chain built from them is the same
         * declaration on every pass.
         */
        val ADD_MOUSE_LISTENER: (JButton, MouseListener) -> Unit =
            { component, listener -> component.addMouseListener(listener) }
        val REMOVE_MOUSE_LISTENER: (JButton, MouseListener) -> Unit =
            { component, listener -> component.removeMouseListener(listener) }
    }
}

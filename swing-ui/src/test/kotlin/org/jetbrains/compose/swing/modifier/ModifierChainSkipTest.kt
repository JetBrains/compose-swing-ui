package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.beans.PropertyChangeEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a pass costs a chain whose declaration did not change, measured through a user-authored element
 * that counts what the diff asks of it.
 *
 * A chain is skipped whole when it declares what the one applied last declares; where it does not, the
 * diff walks it and skips each element declaring what its slot already holds. The two skips are counted
 * apart here: the walk through the element's `key`, which the diff asks each element for once when it
 * partitions the chain into slots, and the re-apply through its `update`.
 *
 * A listener callback is read when its event fires rather than written onto its node, so it is not part
 * of what its element declares: a component rebuilding one on every pass - as one built by a helper
 * outside the composition is - leaves its chain declaring what it declared last, and pays no walk for
 * it. What that callback costs instead, and that the newest one is still what fires, is
 * [org.jetbrains.compose.swing.modifier.listener.LiveCallbackListenerTest].
 */
class ModifierChainSkipTest {
    private val passes = 20

    @Test
    fun anUnchangedChainOfPlainElementsIsSkippedWhole() = runComposeSwingTest {
        val counts = ChainCounts()
        var tick by mutableStateOf(0)
        setContent {
            ProbePanel(tick, counts, SwingModifier.then(ChainProbeElement(counts)))
        }

        repeat(passes) {
            tick++
            awaitIdle()
        }

        assertEquals(passes + 1, counts.passes.get(), "every tick must re-execute the component")
        assertEquals(1, counts.walks.get(), "an unchanged chain is never walked again")
        assertEquals(1, counts.updates.get(), "an unchanged element is written once")
    }

    @Test
    fun aChainCarryingACallbackBuiltOutsideTheCompositionIsSkippedWhole() = runComposeSwingTest {
        val counts = ChainCounts()
        val declarations = AtomicInteger()
        var tick by mutableStateOf(0)
        setContent {
            declarations.incrementAndGet()
            // The tick reaches a sibling and the panel's own parameters stand still, so the chain rebuilt
            // around a fresh callback is the only thing left that can re-execute the panel.
            Label("tick $tick")
            ProbePanel(
                tick = 0,
                counts = counts,
                modifier =
                    SwingModifier
                        .then(ChainProbeElement(counts))
                        .propertyChangeListener(forwardingPropertyChange { }),
            )
        }

        repeat(passes) {
            tick++
            awaitIdle()
        }

        // How many passes drive the content is the harness's to say - a tick reaches it at least once -
        // so the panel is counted against what its caller declared rather than against a number of its own.
        assertTrue(
            declarations.get() >= passes + 1,
            "every tick must re-execute the content that declares the panel",
        )
        assertEquals(
            declarations.get(),
            counts.passes.get(),
            "a callback of a new identity is a parameter the caller changed, so the component is never skipped",
        )
        assertEquals(1, counts.walks.get(), "a callback of a new identity leaves the chain declaring the same thing")
        assertEquals(1, counts.updates.get(), "an unchanged element is written once")
    }

    @Test
    fun aLabelSkipsItsWholeChainOnEveryPass() = runComposeSwingTest {
        val counts = ChainCounts()
        var tick by mutableStateOf(0)
        setContent {
            // The text changes on every tick, so the label re-executes and re-applies a chain rebuilt
            // from scratch - which equals the one it applied last, and is skipped for it.
            Label("tick $tick", modifier = SwingModifier.then(ChainProbeElement(counts)))
        }

        repeat(passes) {
            tick++
            awaitIdle()
        }

        assertEquals(
            "tick $passes",
            onNodeOfType<JLabel>().fetch().text,
            "the label must re-execute on every pass, so the counts read a skipped chain, not one never re-applied",
        )
        assertEquals(1, counts.walks.get(), "a component declaring no callback of its own leaves the chain equal")
        assertEquals(1, counts.updates.get(), "an unchanged element is written once")
    }

    @Test
    fun aListBoxSkipsItsWholeChainOnEveryPass() = runComposeSwingTest {
        val counts = ChainCounts()
        var tick by mutableStateOf(0)
        setContent {
            Label("tick $tick")
            // A row count that moves drives the list to re-execute; the rows it holds are a fresh list
            // declaring the same items, and the chain the caller declares is the same one on every pass.
            ListBox(
                items = List(3) { row -> "row $row" },
                modifier = SwingModifier.then(ChainProbeElement(counts)),
                visibleRowCount = tick,
            )
        }

        repeat(passes) {
            tick++
            awaitIdle()
        }

        assertEquals(
            passes,
            onNodeOfType<JList<*>>().fetch().visibleRowCount,
            "the list must re-execute on every pass, so the counts read a skipped chain, not one never re-applied",
        )
        assertEquals(1, counts.walks.get(), "the list's own selection callback leaves its chain declaring the same")
        assertEquals(1, counts.updates.get(), "the caller's element is written once all the same")
    }

    @Test
    fun aTextFieldSkipsItsWholeChainOnEveryPass() = runComposeSwingTest {
        val counts = ChainCounts()
        var tick by mutableStateOf(0)
        setContent {
            // A field skips a pass that changes nothing about it, so its width is what drives it to
            // re-execute; the chain the caller declares is the same one on every pass.
            TextField(
                value = "text",
                onValueChange = {},
                modifier = SwingModifier.then(ChainProbeElement(counts)),
                columns = tick,
            )
        }

        repeat(passes) {
            tick++
            awaitIdle()
        }

        assertEquals(
            passes,
            onNodeOfType<JTextField>().fetch().columns,
            "the field must re-execute on every pass, so the counts read a skipped chain, not one never re-applied",
        )
        assertEquals(1, counts.walks.get(), "the field's own edit callback leaves its chain declaring the same")
        assertEquals(1, counts.updates.get(), "the caller's element is written once all the same")
    }

    /**
     * A panel whose chain comes from the caller and which writes [tick] onto the component it renders, so
     * a caller moving the tick re-executes the body and the counts read the diff rather than a skipped
     * composable.
     */
    @Composable
    private fun ProbePanel(
        tick: Int,
        counts: ChainCounts,
        modifier: SwingModifier,
    ) {
        counts.passes.incrementAndGet()
        SwingNode(
            factory = { JPanel() },
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }

    /**
     * Adapts a plain callback into the listener lambda a builder takes, outside any composable - the
     * shape a component's own private helper has, and one that hands back a fresh lambda per call.
     */
    private fun forwardingPropertyChange(onChange: () -> Unit): (PropertyChangeEvent) -> Unit = { onChange() }

    /** What one run asks of the component under test and of the one element of its chain. */
    private class ChainCounts {
        val passes: AtomicInteger = AtomicInteger()
        val walks: AtomicInteger = AtomicInteger()
        val updates: AtomicInteger = AtomicInteger()
    }

    /**
     * A property element that writes the target's tooltip and reports what the diff asks of it: its
     * [key], which only the partition of a chain being diffed asks for, and its `update`, called
     * whenever the slot takes it as new data.
     *
     * Two elements built from one [counts] declare the same thing and are equal, so the element never
     * defeats the skip it measures.
     */
    private class ChainProbeElement(
        private val counts: ChainCounts,
    ) : SwingModifier.NodeElement<JComponent, ChainProbeElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val key: Any
            get() {
                counts.walks.incrementAndGet()
                return javaClass
            }

        override fun create(): Node = Node()

        override fun update(node: Node) {
            counts.updates.incrementAndGet()
            node.write("probe")
        }

        override fun equals(other: Any?): Boolean = other is ChainProbeElement && counts === other.counts

        override fun hashCode(): Int = System.identityHashCode(counts)

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
}

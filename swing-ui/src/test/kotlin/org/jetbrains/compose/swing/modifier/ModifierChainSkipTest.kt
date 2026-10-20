package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.interaction.onFocus
import org.jetbrains.compose.swing.modifier.interaction.onHover
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.modifier.listener.containerListener
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.modifier.listener.focusListener
import org.jetbrains.compose.swing.modifier.listener.internalFrameListener
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.modifier.listener.keyListener
import org.jetbrains.compose.swing.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.modifier.listener.treeExpansionListener
import org.jetbrains.compose.swing.modifier.listener.treeWillExpandListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performMouseEnter
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.beans.PropertyChangeEvent
import java.util.EventObject
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val DeclaredNameProperty =
    ComponentPropertyDescriptor<JLabel, String?>(
        name = "name",
        read = { it.name },
        write = { label, value -> label.name = value },
    )

/**
 * What a pass costs a modifier whose declaration did not change, measured through a user-authored element
 * that counts what the diff asks of it.
 *
 * A modifier is skipped whole when it declares what the one applied last declares; where it does not, the
 * diff walks it and skips each element declaring what its slot already holds. The two skips are counted
 * apart here: key reads after the initial application reveal another partition or diff, and calls to
 * `update` reveal another write. The initial application may read a key more than once.
 *
 * A listener callback is read when its event fires rather than written onto its node, so it is not part
 * of what its element declares: a component rebuilding one on every pass - as one built by a helper
 * outside the composition is - leaves its modifier declaring what it declared last, and pays no walk for
 * it. What that callback costs instead, and that the newest one is still what fires, is
 * [org.jetbrains.compose.swing.modifier.listener.LiveCallbackListenerTest].
 */
class ModifierChainSkipTest {
    companion object {
        private fun callbackOfPass(tick: Int): (Any) -> Unit = { check(tick >= 0) }

        private fun actionOfPass(tick: Int): () -> Unit = { check(tick >= 0) }

        private fun skipped(
            name: String,
            factory: () -> JComponent,
            declare: SwingModifier.() -> SwingModifier,
        ): Arguments = Arguments.of(Named.of(name, factory), declare)

        @JvmStatic
        fun listenersKeepingTheirCallbacks(): List<Arguments> = listOf(
            skipped("typed action listener", { JButton() }) { actionListener<JButton>(onButtonAction) },
            skipped("typed item listener", { JCheckBox() }) { itemListener<JCheckBox>(onCheckBoxItem) },
            skipped("typed change listener", { JSlider() }) { changeListener<JSlider>(onSliderChange) },
            skipped("typed list selection listener", { JList<String>() }) {
                listSelectionListener<JList<*>>(onListSelection)
            },
            skipped("typed property change listener", { JPanel() }) {
                propertyChangeListener<JPanel>("name", onPanelProperty)
            },
            skipped("component listener, one callback", { JPanel() }) { componentListener(onAnyEvent) },
            skipped("component listener, every callback", { JPanel() }) {
                componentListener(onAnyEvent, onAnyEvent, onAnyEvent, onAnyEvent)
            },
            skipped("container listener", { JPanel() }) { containerListener(onAnyEvent, onAnyEvent) },
            skipped("document listener", { JTextField() }) { documentListener(onAnyEvent, onAnyEvent, onAnyEvent) },
            skipped("focus listener", { JPanel() }) { focusListener(onAnyEvent, onAnyEvent) },
            skipped("internal frame listener", { JInternalFrame() }) {
                internalFrameListener(
                    onAnyEvent,
                    onAnyEvent,
                    onAnyEvent,
                    onAnyEvent,
                    onAnyEvent,
                    onAnyEvent,
                    onAnyEvent,
                )
            },
            skipped("key listener", { JPanel() }) { keyListener(onAnyEvent, onAnyEvent, onAnyEvent) },
            skipped("mouse listener", { JPanel() }) {
                mouseListener(onAnyEvent, onAnyEvent, onAnyEvent, onAnyEvent, onAnyEvent)
            },
            skipped("mouse motion listener", { JPanel() }) { mouseMotionListener(onAnyEvent, onAnyEvent) },
            skipped("tree expansion listener", { JTree() }) { treeExpansionListener(onAnyEvent, onAnyEvent) },
            skipped("tree will-expand listener", { JTree() }) {
                treeWillExpandListener(onAnyAnswer, onAnyAnswer)
            },
            skipped("onHover", { JPanel() }) { onHover(onAnyAction, onAnyAction) },
            skipped("onFocus", { JPanel() }) { onFocus(onAnyAction, onAnyAction) },
            skipped("onPointerEvent", { JPanel() }) { onPointerEvent(onAnyEvent, onAnyEvent, onAnyEvent) },
        )

        private fun changed(
            name: String,
            factory: () -> JComponent,
            declare: SwingModifier.(Int) -> SwingModifier,
        ): Arguments = Arguments.of(Named.of(name, factory), declare)

        @JvmStatic
        fun callbacksOfANewIdentity(): List<Arguments> = listOf(
            changed("onHover", { JPanel() }) { tick -> onHover(onExit = actionOfPass(tick)) },
            changed("onFocus", { JPanel() }) { tick -> onFocus(onLost = actionOfPass(tick)) },
            changed("onPointerEvent", { JPanel() }) { tick -> onPointerEvent(onClick = callbackOfPass(tick)) },
            changed("typed listener", { JButton() }) { tick ->
                actionListener<JButton> { callbackOfPass(tick)(it) }
            },
            changed("multi-method listener", { JPanel() }) { tick ->
                mouseListener(onMouseExited = callbackOfPass(tick))
            },
        )
    }

    private val passes = 20

    @Test
    fun anUnchangedChainOfPlainElementsIsSkippedWhole() = runComposeSwingTest {
        val counts = ChainCounts()
        var tick by mutableStateOf(0)
        setContent {
            ProbePanel(tick, counts, SwingModifier.then(ChainProbeElement(counts)))
        }

        val initialKeyReads = counts.keyReads.get()
        assertTrue(initialKeyReads > 0, "the initial application must read the element key")
        repeat(passes) {
            tick++
            awaitIdle()
            assertEquals(initialKeyReads, counts.keyReads.get(), "an unchanged modifier must not read keys again")
        }

        assertEquals(passes + 1, counts.passes.get(), "every tick must re-execute the component")
        assertEquals(1, counts.updates.get(), "an unchanged element is written once")
    }

    @Test
    fun aChainDeclaringTheClientPropertyItDeclaredLastIsSkippedWhole() = runComposeSwingTest {
        val counts = ChainCounts()
        var tick by mutableStateOf(0)
        setContent {
            Label(
                "tick $tick",
                modifier = SwingModifier.clientProperty(STYLE_KEY, "small").then(ChainProbeElement(counts)),
            )
        }

        repeat(passes) {
            tick++
            awaitIdle()
        }

        assertEquals(1, counts.updates.get(), "an entry declaring what it declared last writes nothing again")
    }

    @Test
    fun aChainDeclaringThePropertyItDeclaredLastIsSkippedWhole() = runComposeSwingTest {
        val counts = ChainCounts()
        var tick by mutableStateOf(0)
        setContent {
            Label("tick $tick", modifier = SwingModifier.declaredName("small").then(ChainProbeElement(counts)))
        }

        val initialKeyReads = counts.keyReads.get()
        assertTrue(initialKeyReads > 0, "the initial application must read the element key")
        repeat(passes) {
            tick++
            awaitIdle()
            assertEquals(initialKeyReads, counts.keyReads.get(), "an unchanged modifier must not read keys again")
        }

        assertEquals(1, counts.updates.get(), "a property declaring what it declared last writes nothing again")
    }

    @Test
    fun aChainCarryingACallbackBuiltOutsideTheCompositionIsSkippedWhole() = runComposeSwingTest {
        val counts = ChainCounts()
        val declarations = AtomicInteger()
        var tick by mutableStateOf(0)
        setContent {
            declarations.incrementAndGet()
            // The tick reaches a sibling and the panel's own parameters stand still, so the modifier rebuilt
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

        val initialKeyReads = counts.keyReads.get()
        assertTrue(initialKeyReads > 0, "the initial application must read the element key")
        repeat(passes) {
            tick++
            awaitIdle()
            assertEquals(initialKeyReads, counts.keyReads.get(), "an unchanged modifier must not read keys again")
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
        assertEquals(1, counts.updates.get(), "an unchanged element is written once")
    }

    @Test
    fun aListenerGivenABoundReferenceOnAnEqualReceiverReachesTheNewReceiver() =
        assertNewReceiverHears({ JButton() }, { onNodeOfType<JButton>().performClick() }) { receiver ->
            val onAction: (ActionEvent) -> Unit = receiver::onEvent
            actionListener(onAction)
        }

    @Test
    fun aTypedListenerGivenABoundReferenceOnAnEqualReceiverReachesTheNewReceiver() =
        assertNewReceiverHears({ JButton() }, { onNodeOfType<JButton>().performClick() }) { receiver ->
            val onAction: JButton.(ActionEvent) -> Unit = receiver::onScopedEvent
            actionListener(onAction)
        }

    @Test
    fun aMultiMethodListenerGivenABoundReferenceOnAnEqualReceiverReachesTheNewReceiver() =
        assertNewReceiverHears({ JLabel() }, { onNodeOfType<JLabel>().performMouseEnter() }) { receiver ->
            mouseListener(onMouseEntered = receiver::onEvent)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("listenersKeepingTheirCallbacks")
    fun aListenerKeepingItsCallbacksIsSkippedWhole(
        factory: () -> JComponent,
        declare: SwingModifier.() -> SwingModifier,
    ) = assertSkippedWhole(factory, declare)

    @ParameterizedTest(name = "{0}")
    @MethodSource("callbacksOfANewIdentity")
    fun aCallbackOfANewIdentityReExecutesItsComponent(
        factory: () -> JComponent,
        declare: SwingModifier.(tick: Int) -> SwingModifier,
    ) = assertReExecutedForANewCallback(factory, declare)

    @Test
    fun aComponentListenerGivenANewCallbackForAnyMethodReExecutesItsComponent() =
        assertReExecutedForEachNewCallback({ JPanel() }, 4, ::callbackOfPass) { at ->
            componentListener(at(0), at(1), at(2), at(3))
        }

    @Test
    fun aContainerListenerGivenANewCallbackForAnyMethodReExecutesItsComponent() =
        assertReExecutedForEachNewCallback({ JPanel() }, 2, ::callbackOfPass) { at -> containerListener(at(0), at(1)) }

    @Test
    fun aDocumentListenerGivenANewCallbackForAnyMethodReExecutesItsComponent() =
        assertReExecutedForEachNewCallback({ JTextField() }, 3, ::callbackOfPass) { at ->
            documentListener(at(0), at(1), at(2))
        }

    @Test
    fun aFocusListenerGivenANewCallbackForAnyMethodReExecutesItsComponent() =
        assertReExecutedForEachNewCallback({ JPanel() }, 2, ::callbackOfPass) { at -> focusListener(at(0), at(1)) }

    @Test
    fun anInternalFrameListenerGivenANewCallbackForAnyMethodReExecutesItsComponent() =
        assertReExecutedForEachNewCallback({ JInternalFrame() }, 7, ::callbackOfPass) { at ->
            internalFrameListener(at(0), at(1), at(2), at(3), at(4), at(5), at(6))
        }

    @Test
    fun aKeyListenerGivenANewCallbackForAnyMethodReExecutesItsComponent() =
        assertReExecutedForEachNewCallback({ JPanel() }, 3, ::callbackOfPass) { at -> keyListener(at(0), at(1), at(2)) }

    @Test
    fun aMouseListenerGivenANewCallbackForAnyMethodReExecutesItsComponent() =
        assertReExecutedForEachNewCallback({ JPanel() }, 5, ::callbackOfPass) { at ->
            mouseListener(at(0), at(1), at(2), at(3), at(4))
        }

    @Test
    fun aMouseMotionListenerGivenANewCallbackForAnyMethodReExecutesItsComponent() = assertReExecutedForEachNewCallback(
        { JPanel() },
        2,
        ::callbackOfPass,
    ) { at -> mouseMotionListener(at(0), at(1)) }

    @Test
    fun aTreeExpansionListenerGivenANewCallbackForAnyMethodReExecutesItsComponent() =
        assertReExecutedForEachNewCallback(
            { JTree() },
            2,
            ::callbackOfPass,
        ) { at -> treeExpansionListener(at(0), at(1)) }

    @Test
    fun aTreeWillExpandListenerGivenANewCallbackForAnyMethodReExecutesItsComponent() =
        assertReExecutedForEachNewCallback(
            { JTree() },
            2,
            ::answerOfPass,
        ) { at -> treeWillExpandListener(at(0), at(1)) }

    @Test
    fun aLabelSkipsItsWholeChainOnEveryPass() = runComposeSwingTest {
        val counts = ChainCounts()
        var tick by mutableStateOf(0)
        setContent {
            // The text changes on every tick, so the label re-executes and re-applies a modifier rebuilt
            // from scratch - which equals the one it applied last, and is skipped for it.
            Label("tick $tick", modifier = SwingModifier.then(ChainProbeElement(counts)))
        }

        val initialKeyReads = counts.keyReads.get()
        assertTrue(initialKeyReads > 0, "the initial application must read the element key")
        repeat(passes) {
            tick++
            awaitIdle()
            assertEquals(initialKeyReads, counts.keyReads.get(), "an unchanged modifier must not read keys again")
        }

        onNodeOfType<JLabel>().assertProperty(
            "tick $passes",
            "the label must re-execute on every pass, so the counts read a skipped modifier, not one never re-applied",
        ) { text }
        assertEquals(1, counts.updates.get(), "an unchanged element is written once")
    }

    @Test
    fun aListBoxSkipsItsWholeChainOnEveryPass() = runComposeSwingTest {
        val counts = ChainCounts()
        var tick by mutableStateOf(0)
        setContent {
            Label("tick $tick")
            // A row count that moves drives the list to re-execute; the rows it holds are a fresh list
            // declaring the same items, and the modifier the caller declares is the same one on every pass.
            ListBox(
                items = List(3) { row -> "row $row" },
                modifier = SwingModifier.then(ChainProbeElement(counts)),
                visibleRowCount = tick,
            )
        }

        val initialKeyReads = counts.keyReads.get()
        assertTrue(initialKeyReads > 0, "the initial application must read the element key")
        repeat(passes) {
            tick++
            awaitIdle()
            assertEquals(initialKeyReads, counts.keyReads.get(), "an unchanged modifier must not read keys again")
        }

        onNodeOfType<JList<*>>().assertProperty(
            passes,
            "the list must re-execute on every pass, so the counts read a skipped modifier, not one never re-applied",
        ) { visibleRowCount }
        assertEquals(1, counts.updates.get(), "the caller's element is written once all the same")
    }

    @Test
    fun aTextFieldSkipsItsWholeChainOnEveryPass() = runComposeSwingTest {
        val counts = ChainCounts()
        var tick by mutableStateOf(0)
        setContent {
            // A field skips a pass that changes nothing about it, so its width is what drives it to
            // re-execute; the modifier the caller declares is the same one on every pass.
            TextField(
                value = "text",
                onValueChange = {},
                modifier = SwingModifier.then(ChainProbeElement(counts)),
                columns = tick,
            )
        }

        val initialKeyReads = counts.keyReads.get()
        assertTrue(initialKeyReads > 0, "the initial application must read the element key")
        repeat(passes) {
            tick++
            awaitIdle()
            assertEquals(initialKeyReads, counts.keyReads.get(), "an unchanged modifier must not read keys again")
        }

        onNodeOfType<JTextField>().assertProperty(
            passes,
            "the field must re-execute on every pass, so the counts read a skipped modifier, not one never re-applied",
        ) { columns }
        assertEquals(1, counts.updates.get(), "the caller's element is written once all the same")
    }

    /**
     * Recomposes a sibling of a component whose own parameters stand still and whose chain ends in what
     * [declare] builds on every pass, and asserts the component is executed once only: a chain equal to
     * the one passed last is a parameter the runtime skips the component for.
     */
    private fun assertSkippedWhole(
        factory: () -> JComponent,
        declare: SwingModifier.() -> SwingModifier,
    ) = assertComponentPasses(factory, { declare() }) { 1 }

    /**
     * As [assertSkippedWhole], with [declare] handed the pass's tick to build a callback of a new identity
     * from, and asserts the component is executed on every pass that declares it.
     */
    private fun assertReExecutedForANewCallback(
        factory: () -> JComponent,
        declare: SwingModifier.(tick: Int) -> SwingModifier,
    ) = assertComponentPasses(factory, declare) { declarations -> declarations }

    /**
     * Asserts the component is executed once per tick, for a builder taking [callbackCount] callbacks:
     * `at(index)` yields the callback for the index-th of them, and each tick gives exactly one of them a new
     * identity, every index in turn.
     */
    private fun <C : Any> assertReExecutedForEachNewCallback(
        factory: () -> JComponent,
        callbackCount: Int,
        newCallback: (generation: Int) -> C,
        declare: SwingModifier.(at: (index: Int) -> C) -> SwingModifier,
    ) {
        val callbacks = HashMap<Pair<Int, Int>, C>()
        assertComponentPasses(
            factory,
            { tick ->
                declare { index ->
                    val generation = (tick + callbackCount - 1 - index) / callbackCount
                    callbacks.getOrPut(index to generation) { newCallback(generation) }
                }
            },
        ) { passes + 1 }
    }

    private fun assertComponentPasses(
        factory: () -> JComponent,
        declare: SwingModifier.(tick: Int) -> SwingModifier,
        expectedPasses: (declarations: Int) -> Int,
    ) = runComposeSwingTest {
        val counts = ChainCounts()
        val declarations = AtomicInteger()
        var tick by mutableStateOf(0)
        setContent {
            declarations.incrementAndGet()
            Label("tick $tick")
            ProbePanel(
                tick = 0,
                counts = counts,
                modifier = SwingModifier.then(ChainProbeElement(counts)).declare(tick),
                factory = factory,
            )
        }

        repeat(passes) {
            tick++
            awaitIdle()
        }

        assertTrue(
            declarations.get() >= passes + 1,
            "every tick must re-execute the content that declares the component",
        )
        assertEquals(
            expectedPasses(declarations.get()),
            counts.passes.get(),
            "the component is skipped exactly when its chain declares the callbacks it declared last",
        )
    }

    /** An answer that captures [tick], so each pass's is of a new identity. */
    private fun answerOfPass(tick: Int): (Any) -> Boolean = { tick >= 0 }

    /**
     * A panel whose modifier comes from the caller and which writes [tick] onto the component it renders, so
     * a caller moving the tick re-executes the body and the counts read the diff rather than a skipped
     * composable.
     */
    @Composable
    private fun ProbePanel(
        tick: Int,
        counts: ChainCounts,
        modifier: SwingModifier,
        factory: () -> JComponent = { JPanel() },
    ) {
        counts.passes.incrementAndGet()
        SwingNode(
            factory = factory,
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }

    private fun SwingModifier.declaredName(name: String): SwingModifier = property(DeclaredNameProperty, name)

    /**
     * Declares what [declare] builds from a receiver, replaces that receiver with an equal but distinct one,
     * and asserts the component is executed again and the event [fire] delivers reaches the new receiver
     * only: a callback on another receiver is a new callback, whatever its own `equals` says.
     */
    private fun assertNewReceiverHears(
        factory: () -> JComponent,
        fire: suspend ComposeSwingTest.() -> Unit,
        declare: SwingModifier.(EventReceiver) -> SwingModifier,
    ) = runComposeSwingTest {
        val counts = ChainCounts()
        var receiver by mutableStateOf(EventReceiver(1), neverEqualPolicy())
        setContent {
            ProbePanel(tick = 0, counts = counts, modifier = SwingModifier.declare(receiver), factory = factory)
        }
        val replaced = receiver
        val passesBefore = counts.passes.get()

        receiver = EventReceiver(1)
        awaitIdle()
        assertEquals(replaced, receiver, "precondition: the two receivers are equal")
        assertEquals(
            passesBefore + 1,
            counts.passes.get(),
            "a callback on a distinct receiver re-executes the component",
        )
        fire()

        assertEquals(0, replaced.heard.get(), "the replaced receiver hears nothing")
        assertEquals(1, receiver.heard.get(), "the new receiver hears the event")
    }

    /** Equal to any receiver of the same [id], counting the events its callbacks hear. */
    private data class EventReceiver(
        val id: Int,
    ) {
        val heard: AtomicInteger = AtomicInteger()

        fun onEvent(event: EventObject) {
            checkNotNull(event.source)
            heard.incrementAndGet()
        }

        fun onScopedEvent(
            component: Component,
            event: EventObject,
        ) {
            check(event.source === component)
            heard.incrementAndGet()
        }
    }

    /**
     * Adapts a plain callback into the listener lambda a builder takes, outside any composable - the
     * shape a component's own private helper has, and one that hands back a fresh lambda per call.
     */
    private fun forwardingPropertyChange(onChange: () -> Unit): (PropertyChangeEvent) -> Unit = { onChange() }

    /** What one run asks of the component under test and of the one element of its modifier chain. */
    private class ChainCounts {
        val passes: AtomicInteger = AtomicInteger()
        val keyReads: AtomicInteger = AtomicInteger()
        val updates: AtomicInteger = AtomicInteger()
    }

    /**
     * A property element that writes the target's tooltip and reports what the diff asks of it: its
     * [key], which only the partition of a modifier chain being diffed asks for, and its `update`, called
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
                counts.keyReads.incrementAndGet()
                return javaClass
            }

        override fun create(): Node = Node()

        override fun update(node: Node) {
            counts.updates.incrementAndGet()
            node.write("probe")
        }

        override fun equals(other: Any?): Boolean = other is ChainProbeElement && counts === other.counts

        override fun hashCode(): Int = System.identityHashCode(counts)

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
}

private val onAnyEvent: (Any) -> Unit = {}
private val onAnyAction: () -> Unit = {}
private val onAnyAnswer: (Any) -> Boolean = { true }
private val onButtonAction: JButton.(ActionEvent) -> Unit = {}
private val onCheckBoxItem: JCheckBox.(ItemEvent) -> Unit = {}
private val onSliderChange: JSlider.(ChangeEvent) -> Unit = {}
private val onListSelection: JList<*>.(ListSelectionEvent) -> Unit = {}
private val onPanelProperty: JPanel.(PropertyChangeEvent) -> Unit = {}

/** A styling key of the kind a caller hands a look and feel. */
private const val STYLE_KEY = "JComponent.sizeVariant"

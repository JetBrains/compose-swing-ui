package org.jetbrains.compose.swing.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Layer
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.node.ExistingSwingNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.recordAddsAndRemovesOf
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.SwingStyleKind
import org.jetbrains.compose.swing.style.stylePropertyOf
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JLabel
import javax.swing.JLayer
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InheritableLayoutElementTest {
    @Test
    fun claimedChildIgnoresRefusedDefaults() = runComposeSwingTest {
        var provided by mutableStateOf<Int?>(1)
        setContent {
            Panel(style = providedInset(provided)) {
                SwingNode(factory = { JPanel().apply { add(JLabel("claimed")) } }) {
                    ExistingSwingNode<JPanel, _, JLabel>(
                        claim = { getComponent(0) as JLabel },
                        modifier = SwingModifier.styleable(SwingStyle, ProvidedLeafKind),
                    )
                }
            }
        }
        val claimed = onNodeWithText("claimed").fetch<JLabel>()
        val parent = claimed.parent
        val events = parent.recordAddsAndRemovesOf(claimed)
        provided = null
        awaitIdle()
        provided = 2
        awaitIdle()
        assertEquals(
            emptyList(),
            events,
            "a value the parent does not accept never adds, removes or moves the claimed component",
        )
        assertSame(parent, claimed.parent)
    }

    @Test
    fun acceptedDefaultsUpdateAndDetach() = runComposeSwingTest {
        var provided by mutableStateOf<Int?>(1)
        val layout = InsetLayout()
        setContent {
            Panel(style = providedInset(provided)) {
                SwingNode(factory = { JPanel(layout) }) { Label("inheriting") }
            }
        }
        val inheriting = onNodeWithText("inheriting").fetch()
        val node = layout.elementsOf(inheriting).filterIsInstance<InsetNode>().single()
        assertEquals(1, node.amount)
        assertSame(inheriting, node.component, "an attached node reaches the component whose style declares it")

        provided = 3
        awaitIdle()
        assertSame(
            node,
            layout.elementsOf(inheriting).filterIsInstance<InsetNode>().single(),
            "a changed value writes the node it created",
        )
        assertEquals(3, node.amount)

        provided = null
        awaitIdle()
        assertEquals(
            emptyList(),
            layout.elementsOf(inheriting).filterIsInstance<InsetNode>(),
            "a withdrawn value leaves the parent's declaration",
        )
        assertFailsWith<IllegalStateException>("a detached node has no component") { node.component }
    }

    @Test
    fun ownValueOverridesDefault() = runComposeSwingTest {
        val layout = InsetLayout()
        setContent {
            Panel(style = providedInset(1)) {
                SwingNode(factory = { JPanel(layout) }) {
                    Label("own", style = { InsetProperty.provide(5) })
                }
            }
        }
        onNodeWithText("own").assertProperty(listOf(5), "one node stands, holding the label's own value") {
            layout.elementsOf(this).filterIsInstance<InsetNode>().map { it.amount }
        }
    }

    @Test
    fun refusedDefaultsPreserveParentSlots() = runComposeSwingTest {
        var provided by mutableStateOf<Int?>(1)
        val other = OtherMeasuringLayout()
        setContent {
            Panel(style = providedInset(provided)) {
                Panel(PanelLayout.Border()) {
                    Label("north", SwingModifier.north())
                    Label("center")
                }
                Panel(PanelLayout.Box()) { Label("box") }
                SwingNode(factory = { JPanel(other) }) { Label("measured elsewhere") }
                ScrollPane { Viewport { Label("viewport") } }
                SplitPane {
                    Label("first", SwingModifier.first())
                    Label("second", SwingModifier.second())
                }
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                    Label("tab", SwingModifier.tab("tab"))
                }
                Layer(onMouseEvent = {}) { Label("view") }
            }
        }
        val north = onNodeWithText("north").fetch()
        val border = north.parent.layout as BorderLayout

        for (next in listOf(2, null, 3)) {
            provided = next
            awaitIdle()

            assertEquals(BorderLayout.NORTH, border.getConstraints(north), "$next: the region stays registered")
            assertEquals(0, north.y, "$next: the north child stays at the top")
            assertSame(north, border.getLayoutComponent(BorderLayout.NORTH), "$next")
            assertTrue(
                other.declarations.all { it.elements.isEmpty() },
                "$next: a refusing measuring parent receives nothing",
            )
            assertSame(onNodeWithText("viewport").fetch(), onNodeOfType<JScrollPane>().fetch().viewport.view)
            val split = onNodeOfType<JSplitPane>().fetch()
            assertSame(onNodeWithText("first").fetch(), split.leftComponent, "$next")
            assertSame(onNodeWithText("second").fetch(), split.rightComponent, "$next")
            assertSame(onNodeWithText("tab").fetch(), onNodeOfType<JTabbedPane>().fetch().getComponentAt(0), "$next")
            assertSame(onNodeWithText("view").fetch(), onNodeOfType<JLayer<*>>().fetch().view, "$next")
        }
        assertTrue(other.declarations.isNotEmpty(), "the measuring parent is declared to")
    }

    @Test
    fun refusedDefaultsDoNotNotifyParent() = runComposeSwingTest {
        var provided by mutableStateOf<Int?>(1)
        val other = OtherMeasuringLayout()
        setContent {
            Panel(style = providedInset(provided)) {
                SwingNode(factory = { JPanel(other) }) {
                    Label("alone")
                    Label("beside", SwingModifier then MeasuredAnywhere)
                }
            }
        }
        onNodeWithText("beside").assertProperty(listOf<ParentLayoutElement>(MeasuredAnywhere)) {
            other.elementsOf(this)
        }
        val declarations = other.declarations.size
        val invalidations = other.invalidations.size

        for (next in listOf(2, null, 3)) {
            provided = next
            awaitIdle()

            assertEquals(declarations, other.declarations.size, "$next: the parent is not declared to again")
            assertEquals(invalidations, other.invalidations.size, "$next: the parent's layout is not invalidated")
        }
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun onlyAcceptedLayoutChangesNotifyParent(
        joining: Boolean,
        changeWeight: Boolean,
    ) = runComposeSwingTest {
        var amount by mutableStateOf(1)
        val other = OtherMeasuringLayout()
        setContent {
            SwingNode(factory = { JPanel(other) }) {
                val marker = if (joining) SwingModifier else SwingModifier.name("$amount")
                val inset = if (!joining || amount > 1) Inset(amount) else SwingModifier
                Label("beside", marker then Weight(if (changeWeight) amount else 1) then inset)
            }
        }
        val declarations = other.declarations.size
        val invalidations = other.invalidations.size
        amount = 2
        awaitIdle()
        if (!joining) onNodeWithText("beside").assertProperty("2") { name }
        if (changeWeight) {
            assertEquals(declarations + 1, other.declarations.size, "one accepted change declares once")
            assertEquals(
                2,
                (
                    other.declarations
                        .last()
                        .elements
                        .single() as WeightNode
                ).amount,
            )
            assertTrue(other.invalidations.size > invalidations)
        } else {
            assertEquals(declarations, other.declarations.size)
            assertEquals(invalidations, other.invalidations.size)
            if (joining) {
                amount = 1
                awaitIdle()
                assertEquals(declarations, other.declarations.size, "removing a refused node leaves the parent alone")
                assertEquals(invalidations, other.invalidations.size)
            }
        }
    }

    @Test
    fun movingToRefusingParentKeepsNode() = runComposeSwingTest {
        var accepted by mutableStateOf(true)
        val layout = InsetLayout()
        val label = movableContentOf { Label("moved") }
        setContent {
            Panel(style = providedInset(1)) {
                SwingNode(factory = { JPanel(layout) }) { if (accepted) label() }
                SwingNode(factory = { JPanel(OtherMeasuringLayout()) }) { if (!accepted) label() }
            }
        }
        val moved = onNodeWithText("moved").fetch()
        val node = layout.elementsOf(moved).filterIsInstance<InsetNode>().single()

        accepted = false
        awaitIdle()

        assertSame(moved, onNodeWithText("moved").fetch())
        assertTrue(moved.parent.layout is OtherMeasuringLayout, "the component stands under the refusing parent")
        assertSame(moved, node.component, "the node stays attached where it is left out")

        accepted = true
        awaitIdle()

        assertSame(
            node,
            layout.elementsOf(moved).filterIsInstance<InsetNode>().single(),
            "the accepting parent is handed the node the component kept",
        )
    }

    @Test
    fun nonMeasuringParentRejectsAcceptedLayout() {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent {
                        SwingNode(factory = { JPanel(FlowLayout()) }) {
                            Label("refused", SwingModifier then Inset(1) then InheritableAnywhere)
                        }
                    }
                }
            }
        val message = failure.message.orEmpty()
        assertTrue(
            message.contains(" declares SwingModifier.${InheritableAnywhere.name}(). "),
            "The refusal must name the accepted element alone, but said: $message",
        )
    }

    @Test
    fun refusedDefaultsPreserveCardOrder() = runComposeSwingTest {
        var provided by mutableStateOf<Int?>(1)
        setContent {
            Panel(style = providedInset(provided)) {
                Panel(PanelLayout.Card(selectedCard = "second")) {
                    Label("first", SwingModifier.card("first"))
                    Label("second", SwingModifier.card("second"))
                    Label("third", SwingModifier.card("third"))
                }
            }
        }
        val cards = onAllNodesOfType<JLabel>().fetchAll()
        val events = mutableListOf<String>()
        cards.forEach { card ->
            card.addComponentListener(
                object : ComponentAdapter() {
                    override fun componentShown(event: ComponentEvent) {
                        events += "shown ${card.text}"
                    }

                    override fun componentHidden(event: ComponentEvent) {
                        events += "hidden ${card.text}"
                    }
                },
            )
        }

        for (next in listOf(2, null, 3)) {
            provided = next
            awaitIdle()

            onNodeWithText("second").assertIsVisible()
            onNodeWithText("first").assertIsNotVisible()
            onNodeWithText("third").assertIsNotVisible()
            assertEquals(emptyList(), events, "$next: the deck re-registers no card, so none is hidden or shown")
        }

        val deck = onNodeWithText("second").fetch().parent
        val order =
            List(3) {
                (deck.layout as CardLayout).next(deck)
                cards.single { card -> card.isVisible }.text
            }
        assertEquals(listOf("third", "first", "second"), order, "the deck keeps its card order")
    }

    @ParameterizedTest(name = "{0}, style={2}")
    @MethodSource("refusedElements")
    fun rejectParentDataAndAdditiveLayout(
        kind: String,
        element: ParentLayoutElement,
        styled: Boolean,
    ) {
        val property = stylePropertyOf<Unit> { element }
        assertFailsWith<IllegalArgumentException>("$kind must be refused, style=$styled") {
            runComposeSwingTest {
                setContent {
                    SwingNode(factory = { JPanel(InsetLayout()) }) {
                        Label(
                            "refused",
                            modifier = if (styled) SwingModifier else SwingModifier then element,
                            style = { if (styled) property.provide(Unit) },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun additiveInheritableParentDataFolds() = runComposeSwingTest {
        val layout = InsetLayout()
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                Label("folded", SwingModifier then AdditiveParentData(1) then AdditiveParentData(2))
            }
        }
        assertEquals(listOf(1, 2), layout.parentDataOf(onNodeWithText("folded").fetch()), "each declaration folds")
    }

    @Test
    fun inheritableParentDataBesideARegionIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            runComposeSwingTest {
                setContent {
                    ScrollPane {
                        Label(
                            "refused",
                            SwingModifier.corner(JScrollPane.UPPER_LEFT_CORNER) then InheritableParentData,
                        )
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeFactMismatches")
    fun nodeFactsMustMatchElement(
        fact: String,
        element: ParentLayoutNodeElement<*>,
    ) {
        assertFailsWith<IllegalStateException>("A node differing in $fact must be refused") {
            runComposeSwingTest {
                setContent {
                    SwingNode(factory = { JPanel(InsetLayout()) }) {
                        Label("refused", SwingModifier then element)
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun refusedElements(): List<Arguments> = listOf(
            Arguments.of("parent data", InheritableParentData, true),
            Arguments.of("node inset", AdditiveNodeInset, true),
            Arguments.of("inheritable layout", AdditiveInheritableLayout, true),
            Arguments.of("node inset", AdditiveNodeInset, false),
            Arguments.of("inheritable layout", AdditiveInheritableLayout, false),
        )

        @JvmStatic
        fun nodeFactMismatches(): List<Arguments> = listOf(
            Arguments.of("inheritable", NotInheritableNodeInset),
            Arguments.of("parentProtocol", OtherProtocolNodeInset),
        )
    }
}

private val InsetProperty = stylePropertyOf<Int> { Inset(it) }

private fun providedInset(amount: Int?): SwingStyle<ContainerStyleScope> =
    SwingStyle { amount?.let { provideNestedStyle(AllComponents) { InsetProperty.provide(it) } } }

private val ProvidedLeafKind =
    SwingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    ) {
        applyNestedStyle(AllComponents)
    }

private val InsetProtocol: ParentProtocol = parentProtocolOf("inset parent") { it.layout is InsetLayout }

private data class Inset(
    val amount: Int,
) : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val inheritable: Boolean get() = true

    override fun create(): InsetNode = InsetNode(amount)

    override fun update(node: InsetNode) {
        node.amount = amount
    }
}

private class InsetNode(
    var amount: Int,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val inheritable: Boolean get() = true
}

private data object NotInheritableNodeInset : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override fun create(): InsetNode = InsetNode(0)

    override fun update(node: InsetNode) = Unit
}

private data object OtherProtocolNodeInset : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = OtherProtocol

    override val inheritable: Boolean get() = true

    override fun create(): InsetNode = InsetNode(0)

    override fun update(node: InsetNode) = Unit
}

private data object AdditiveNodeInset : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val additive: Boolean get() = true

    override val inheritable: Boolean get() = true

    override fun create(): InsetNode = InsetNode(0)

    override fun update(node: InsetNode) = Unit
}

private val OtherProtocol: ParentProtocol = parentProtocolOf("other inset parent") { it.layout is InsetLayout }

private data object AdditiveInheritableLayout : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val additive: Boolean get() = true

    override val inheritable: Boolean get() = true
}

private data object InheritableParentData : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val inheritable: Boolean get() = true

    override fun modifyParentData(parentData: Any?): Any? = parentData
}

private data class AdditiveParentData(
    val amount: Int,
) : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val additive: Boolean get() = true

    override val inheritable: Boolean get() = true

    override fun modifyParentData(parentData: Any?): Any = (parentData as? List<*>).orEmpty() + amount
}

private val MeasuringProtocol: ParentProtocol = MeasurementLayoutManager.parentProtocol("measuring parent")

private data object MeasuredAnywhere : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = MeasuringProtocol
}

private data class Weight(
    val amount: Int,
) : ParentLayoutNodeElement<WeightNode>() {
    override val parentProtocol: ParentProtocol get() = MeasuringProtocol

    override fun create(): WeightNode = WeightNode(amount)

    override fun update(node: WeightNode) {
        node.amount = amount
    }
}

private class WeightNode(
    var amount: Int,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = MeasuringProtocol
}

private val AnyParentProtocol: ParentProtocol = parentProtocolOf("any parent") { true }

private data object InheritableAnywhere : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = AnyParentProtocol

    override val inheritable: Boolean get() = true
}

private class InsetLayout : RecordingMeasurementLayout(FlowLayout())

private class OtherMeasuringLayout : RecordingMeasurementLayout(FlowLayout())

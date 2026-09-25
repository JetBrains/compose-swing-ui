package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
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

/**
 * An inheritable parent-layout declaration given as a component default: it reaches the children of a parent
 * whose protocol accepts it, and is left out under every other parent without the parent being told.
 */
class InheritableLayoutDefaultTest {
    @Test
    fun anInheritableLayoutDefaultReachesTheChildrenOfAParentThatAcceptsIt() = runComposeSwingTest {
        var provided by mutableStateOf<Int?>(1)
        val layout = InsetLayout()
        setContent {
            ProvideComponentDefaults(DefaultInset provides provided) {
                SwingNode(factory = { JPanel(layout) }) {
                    Label("inheriting")
                    ProvideComponentDefaults(DefaultInset provides null) { Label("masked") }
                }
            }
        }
        val inheriting = onNodeWithText("inheriting").fetch()
        val masked = onNodeWithText("masked").fetch()
        val node = layout.elementsOf(inheriting).filterIsInstance<InsetNode>().single()
        assertEquals(1, node.amount)
        assertSame(inheriting, node.component, "an attached node reaches the component whose modifier declares it")
        assertEquals(
            emptyList(),
            layout.elementsOf(masked).filterIsInstance<InsetNode>(),
            "a nested provides null masks the default",
        )

        provided = 3
        awaitIdle()
        assertSame(
            node,
            layout.elementsOf(inheriting).filterIsInstance<InsetNode>().single(),
            "a changed default writes the node it created",
        )
        assertEquals(3, node.amount)

        provided = null
        awaitIdle()
        assertEquals(
            emptyList(),
            layout.elementsOf(inheriting).filterIsInstance<InsetNode>(),
            "a withdrawn default leaves the parent's declaration",
        )
        assertFailsWith<IllegalStateException>("a detached node has no component") { node.component }
    }

    @Test
    fun anExplicitDeclarationAfterTheInheritedOneReplacesItByKey() = runComposeSwingTest {
        val layout = InsetLayout()
        setContent {
            ProvideComponentDefaults(DefaultInset provides 1) {
                SwingNode(factory = { JPanel(layout) }) {
                    Label("explicit", SwingModifier then Inset(5))
                }
            }
        }
        val declared = layout.elementsOf(onNodeWithText("explicit").fetch()).filterIsInstance<InsetNode>()
        assertEquals(listOf(5), declared.map { it.amount }, "one node stands, holding the explicit declaration")
    }

    @Test
    fun aParentWhoseProtocolRefusesTheDefaultIsNeverHandedIt() = runComposeSwingTest {
        var provided by mutableStateOf<Int?>(1)
        val other = OtherMeasuringLayout()
        setContent {
            ProvideComponentDefaults(DefaultInset provides provided) {
                Panel(PanelLayout.Border()) {
                    Label("north", SwingModifier.north())
                    Label("center")
                }
                Panel(PanelLayout.Box()) { Label("box") }
                SwingNode(factory = { JPanel(other) }) { Label("measured elsewhere") }
                ScrollPane { Label("viewport", SwingModifier.viewport()) }
                SplitPane {
                    Label("first", SwingModifier.first())
                    Label("second", SwingModifier.second())
                }
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                    Label("tab", SwingModifier.tab("tab"))
                }
                Layer(onMouseEvent = {}) { Label("view", SwingModifier.view()) }
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
    fun aMeasuringParentThatRefusesTheDefaultIsLeftAloneWhenTheDefaultChanges() = runComposeSwingTest {
        var provided by mutableStateOf<Int?>(1)
        val other = OtherMeasuringLayout()
        setContent {
            ProvideComponentDefaults(DefaultInset provides provided) {
                SwingNode(factory = { JPanel(other) }) {
                    Label("alone")
                    Label("beside", SwingModifier then MeasuredAnywhere)
                }
            }
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

    @Test
    fun aParentReceivingALayoutNodeIsLeftAloneWhenALeftOutOneJoinsOrLeaves() = runComposeSwingTest {
        var inset by mutableStateOf<Int?>(null)
        val other = OtherMeasuringLayout()
        setContent {
            SwingNode(factory = { JPanel(other) }) {
                Label("beside", SwingModifier then Weight(1) then (inset?.let { Inset(it) } ?: SwingModifier))
            }
        }
        val declarations = other.declarations.size
        val invalidations = other.invalidations.size

        for (next in listOf(1, null)) {
            inset = next
            awaitIdle()

            assertEquals(declarations, other.declarations.size, "$next: the parent is not declared to again")
            assertEquals(invalidations, other.invalidations.size, "$next: the parent's layout is not invalidated")
        }
    }

    @Test
    fun aLayoutNodeWrittenWhileALeftOutOneJoinsHasItsParentDeclaredToAgain() = runComposeSwingTest {
        var amount by mutableStateOf(1)
        val other = OtherMeasuringLayout()
        setContent {
            SwingNode(factory = { JPanel(other) }) {
                Label("beside", SwingModifier then Weight(amount) then (if (amount > 1) Inset(1) else SwingModifier))
            }
        }
        val declarations = other.declarations.size
        val invalidations = other.invalidations.size

        amount = 2
        awaitIdle()

        assertEquals(declarations + 1, other.declarations.size, "the parent is declared to again")
        val declaredNode =
            other.declarations
                .last()
                .elements
                .single() as WeightNode
        assertEquals(2, declaredNode.amount, "the parent holds the node written")
        assertTrue(other.invalidations.size > invalidations, "the parent's layout is invalidated")
    }

    @Test
    fun aComponentMovedUnderAParentThatRefusesTheDefaultKeepsItsNode() = runComposeSwingTest {
        var accepted by mutableStateOf(true)
        val layout = InsetLayout()
        val label = movableContentOf { Label("moved") }
        setContent {
            ProvideComponentDefaults(DefaultInset provides 1) {
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
    fun aLeftOutLayoutNodeWrittenBesideAnotherChangeToTheModifierLeavesItsParentAlone() = runComposeSwingTest {
        var amount by mutableStateOf(1)
        val other = OtherMeasuringLayout()
        setContent {
            SwingNode(factory = { JPanel(other) }) {
                // The property declared first changes with the inset, so the pass diffs the modifier.
                Label("beside", SwingModifier.opaque(amount > 1) then Weight(1) then Inset(amount))
            }
        }
        val declarations = other.declarations.size
        val invalidations = other.invalidations.size

        amount = 2
        awaitIdle()

        assertTrue(onNodeWithText("beside").fetch().isOpaque, "the pass applies the modifier")
        assertEquals(declarations, other.declarations.size, "the parent is not declared to again")
        assertEquals(invalidations, other.invalidations.size, "the parent's layout is not invalidated")
    }

    @Test
    fun aLayoutNodeWrittenAheadOfALeftOutOneInOnePassHasItsParentDeclaredToOnce() = runComposeSwingTest {
        var amount by mutableStateOf(1)
        val other = OtherMeasuringLayout()
        setContent {
            SwingNode(factory = { JPanel(other) }) {
                // The property declared first changes with both layout elements, so the pass diffs the modifier.
                Label("beside", SwingModifier.opaque(amount > 1) then Weight(amount) then Inset(amount))
            }
        }
        val declarations = other.declarations.size
        val invalidations = other.invalidations.size

        amount = 2
        awaitIdle()

        assertEquals(declarations + 1, other.declarations.size, "the parent is declared to once")
        val declaredNode =
            other.declarations
                .last()
                .elements
                .single() as WeightNode
        assertEquals(2, declaredNode.amount, "the parent holds the node written")
        assertTrue(other.invalidations.size > invalidations, "the parent's layout is invalidated")
    }

    @Test
    fun aParentRefusingTheDefaultReceivesTheElementsDeclaredBesideIt() = runComposeSwingTest {
        val other = OtherMeasuringLayout()
        setContent {
            ProvideComponentDefaults(DefaultInset provides 1) {
                SwingNode(factory = { JPanel(other) }) {
                    Label("beside", SwingModifier then MeasuredAnywhere)
                }
            }
        }
        assertEquals(listOf<ParentLayoutElement>(MeasuredAnywhere), other.declarations.last().elements)
    }

    @Test
    fun anInheritableElementAcceptedByAParentThatCannotMeasureIsRefused() {
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

    /** A changed value takes the adoption path, and a value provided or withdrawn the full modifier diff. */
    @Test
    fun aCardDeckKeepsItsShownCardAndCardOrderWhenTheDefaultChanges() = runComposeSwingTest {
        var provided by mutableStateOf<Int?>(1)
        setContent {
            ProvideComponentDefaults(DefaultInset provides provided) {
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

    @Test
    fun aDefaultRefusesALayoutElementThatIsNotInheritableOrFoldsParentData() {
        val kinds: List<Pair<String, ParentLayoutElement>> =
            listOf(
                "non-inheritable element" to NonInheritableLayout,
                "parent data" to InheritableParentData,
            )
        for ((kind, element) in kinds) {
            val key = componentDefaultKeyOf<Unit>(kind) { this then element }
            val failure =
                assertFailsWith<IllegalArgumentException>("A $kind must be refused as a default") {
                    runComposeSwingTest {
                        setContent { ProvideComponentDefaults(key provides Unit) { Label("refused") } }
                    }
                }
            assertTrue(
                failure.message.orEmpty().startsWith("ComponentDefaultKey '$kind' cannot declare $kind: "),
                "A $kind must be named in the refusal, but the provider said: ${failure.message}",
            )
        }
    }

    @Test
    fun anInheritableAdditiveElementIsRefusedDeclaredOnAComponentOrAsADefault() {
        for (element in listOf(AdditiveNodeInset, AdditiveInheritableLayout)) {
            val key = componentDefaultKeyOf<Unit>("additive") { this then element }
            val declarations: List<@Composable () -> Unit> =
                listOf(
                    { Label("refused", SwingModifier then element) },
                    { ProvideComponentDefaults(key provides Unit) { Label("refused") } },
                )
            for (declaration in declarations) {
                val failure =
                    assertFailsWith<IllegalArgumentException>("$element must be refused") {
                        runComposeSwingTest {
                            setContent { SwingNode(factory = { JPanel(InsetLayout()) }) { declaration() } }
                        }
                    }
                val message = failure.message.orEmpty()
                assertTrue(
                    message.startsWith(element.javaClass.name) &&
                        message.endsWith("an inheritable element is not additive."),
                    "The refusal must name the element and the rule, but said: $message",
                )
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
        val failure =
            assertFailsWith<IllegalArgumentException> {
                runComposeSwingTest {
                    setContent {
                        ScrollPane { Label("refused", SwingModifier.viewport() then InheritableParentData) }
                    }
                }
            }
        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("SwingModifier.${InheritableParentData.name}() asks for") &&
                message.contains("this modifier declares both"),
            "The refusal must name the parent data and say why, but said: $message",
        )
    }

    @Test
    fun aLayoutNodeDeclaringOtherFactsThanItsElementIsRefused() {
        val mismatches: List<Pair<String, ParentLayoutNodeElement<*>>> =
            listOf("inheritable" to NotInheritableNodeInset, "parentProtocol" to OtherProtocolNodeInset)
        for ((fact, element) in mismatches) {
            val failure =
                assertFailsWith<IllegalStateException>("A node differing in $fact must be refused") {
                    runComposeSwingTest {
                        setContent {
                            SwingNode(factory = { JPanel(InsetLayout()) }) {
                                Label("refused", SwingModifier then element)
                            }
                        }
                    }
                }
            val message = failure.message.orEmpty()
            assertTrue(
                message.contains(InsetNode::class.java.name) && message.contains(element.javaClass.name),
                "The refusal must name the node and its element, but said: $message",
            )
        }
    }
}

private val DefaultInset: ComponentDefaultKey<Int> = componentDefaultKeyOf("inset") { this then Inset(it) }

/** Accepts only an [InsetLayout] parent. */
private val InsetProtocol: ParentProtocol = parentProtocolOf("inset parent") { it.layout is InsetLayout }

/** An inheritable node-backed declaration, keyed by its class. */
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

/** Declares the node inheritable, which [InsetNode] is, but is not itself. */
private data object NotInheritableNodeInset : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override fun create(): InsetNode = InsetNode(0)

    override fun update(node: InsetNode) = Unit
}

/** Creates an [InsetNode] under a protocol other than the node's. */
private data object OtherProtocolNodeInset : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = OtherProtocol

    override val inheritable: Boolean get() = true

    override fun create(): InsetNode = InsetNode(0)

    override fun update(node: InsetNode) = Unit
}

/** Creates an [InsetNode], inheritable as the node is, and stands beside every other declaration. */
private data object AdditiveNodeInset : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val additive: Boolean get() = true

    override val inheritable: Boolean get() = true

    override fun create(): InsetNode = InsetNode(0)

    override fun update(node: InsetNode) = Unit
}

/** Accepts the parents [InsetProtocol] accepts, as a protocol of its own. */
private val OtherProtocol: ParentProtocol = parentProtocolOf("other inset parent") { it.layout is InsetLayout }

private data object NonInheritableLayout : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = InsetProtocol
}

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

/** Parent data both inheritable and additive, folding to the list of every [amount] declared. */
private data class AdditiveParentData(
    val amount: Int,
) : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val additive: Boolean get() = true

    override val inheritable: Boolean get() = true

    override fun modifyParentData(parentData: Any?): Any = (parentData as? List<*>).orEmpty() + amount
}

/** Accepts every measuring parent. */
private val MeasuringProtocol: ParentProtocol = MeasurementLayoutManager.parentProtocol("measuring parent")

/** A declaration every measuring parent interprets. */
private data object MeasuredAnywhere : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = MeasuringProtocol
}

/** A node-backed declaration every measuring parent interprets. */
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

/** Accepts every parent. */
private val AnyParentProtocol: ParentProtocol = parentProtocolOf("any parent") { true }

/** An inheritable declaration no parent's protocol refuses. */
private data object InheritableAnywhere : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = AnyParentProtocol

    override val inheritable: Boolean get() = true
}

/** The measuring manager [InsetProtocol] accepts. */
private class InsetLayout : RecordingMeasurementLayout(FlowLayout())

/** A measuring manager [InsetProtocol] refuses. */
private class OtherMeasuringLayout : RecordingMeasurementLayout(FlowLayout())

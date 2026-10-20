package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.key
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class StyleParentLayoutRemovalTest {
    /** Cleanup follows retained records when the first update failed after attachment. */
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun failedFirstUpdateReleasesParentAndListenerDeclarations(dispose: Boolean) = runComposeSwingTest {
        var phase by mutableStateOf(0)
        val events = ArrayList<String>()
        val failure = IllegalArgumentException("First layout update failed")
        val property = stylePropertyOf<Unit> { FirstUpdateFailingLayout(events, failure) }
        val label = LayoutListeningLabel()
        val layout = InsetLayout()
        val host = JPanel(layout)
        setContent { SwingNode(factory = { host }) }
        val handle =
            host.setContent {
                SwingNode(
                    factory = { label },
                    modifier = SwingModifier.styleable({ if (phase == 1) property.provide(Unit) }, PlainProbe),
                )
            }
        try {
            awaitIdle()
            phase = 1
            assertSame(failure, assertFailsWith<IllegalArgumentException> { awaitIdle() })
            assertEquals(1, layout.elementsOf(label).size)

            if (dispose) handle.dispose() else phase = 2
            awaitIdle()

            assertEquals(listOf("attach", "update", "detach"), events)
            assertEquals(emptyList(), layout.elementsOf(label))
            assertEquals(emptyList(), label.declaredNodes)
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aLayoutWhoseFirstUpdateFailsIsDetachedWhenTheStyleDropsIt() = runComposeSwingTest {
        var phase by mutableStateOf(0)
        val events = ArrayList<String>()
        val failure = IllegalArgumentException("First layout update failed")
        val property = stylePropertyOf<Unit> { FirstUpdateFailingLayout(events, failure) }
        val style = SwingStyle<ProbeScope> { if (phase == 1) property.provide(Unit) }
        setContent {
            SwingNode(factory = { JPanel(InsetLayout()) }) {
                SwingNode(
                    factory = { JLabel("styled") },
                    modifier = SwingModifier.styleable(style, PlainProbe),
                )
            }
        }

        phase = 1
        assertSame(failure, assertFailsWith<IllegalArgumentException> { awaitIdle() })
        assertEquals(listOf("attach", "update"), events)

        phase = 2
        awaitIdle()

        assertEquals(listOf("attach", "update", "detach"), events)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun layoutRemovalSurvivesSiblingFailure(failsOnAttach: Boolean) = runComposeSwingTest {
        var changed by mutableStateOf(false)
        val failure = IllegalArgumentException("Layout failed")
        val layout = InsetLayout()
        val first = stylePropertyOf<Unit> { FailingLayoutElement("A", 1) }
        val second =
            stylePropertyOf<Boolean> { fails ->
                FailingLayoutElement(
                    key = if (fails && failsOnAttach) "C" else "B",
                    amount = 2,
                    failure = if (fails) failure else null,
                    failsOnAttach = failsOnAttach,
                )
            }
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                SwingNode(
                    factory = { JLabel("styled") },
                    modifier =
                        SwingModifier.styleable(
                            {
                                if (!changed) first.provide(Unit)
                                second.provide(changed)
                            },
                            PlainProbe,
                        ),
                )
            }
        }
        onNodeWithText("styled").assertProperty(listOf(1, 2)) {
            layout.elementsOf(this).mapNotNull { (it as? FailureLayoutNode)?.amount }
        }

        changed = true
        assertSame(failure, assertFailsWith<IllegalArgumentException> { awaitIdle() })

        onNodeWithText("styled").assertProperty(if (failsOnAttach) emptyList() else listOf(2)) {
            layout.elementsOf(this).mapNotNull { (it as? FailureLayoutNode)?.amount }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removals")
    fun removedLayoutLeavesParent(declare: (present: () -> Boolean) -> SwingModifier) = runComposeSwingTest {
        var present by mutableStateOf(true)
        val layout = InsetLayout()
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                SwingNode(factory = { JLabel("styled") }, modifier = declare { present })
            }
        }
        var node: InsetNode? = null
        onNodeWithText("styled").assertProperty(1) { layout.insetOf(this).also { node = it }.amount }

        present = false
        awaitIdle()

        onNodeWithText("styled")
            .assertProperty(emptyList<ParentLayoutElement>(), "the parent is declared to without the element") {
                layout.elementsOf(this)
            }
        assertFailsWith<IllegalStateException>("a detached node has no component") { checkNotNull(node).component }

        present = true
        awaitIdle()

        onNodeWithText("styled").assertProperty(1, "it declares again") { layout.insetOf(this).amount }
    }

    @Test
    fun removedStyleClearsPlainLayout() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        val layout = InsetLayout()
        val property = stylePropertyOf<Unit> { MeasuredAnywhere }
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                SwingNode(
                    factory = { JLabel("plain") },
                    modifier =
                        if (styled) {
                            SwingModifier.styleable({ property.provide(Unit) }, PlainProbe)
                        } else {
                            SwingModifier
                        },
                )
            }
        }
        onNodeWithText("plain").assertProperty(listOf(MeasuredAnywhere)) { layout.elementsOf(this) }

        styled = false
        awaitIdle()

        onNodeWithText("plain").assertProperty(emptyList<ParentLayoutElement>()) { layout.elementsOf(this) }
    }

    companion object {
        private fun case(
            name: String,
            declare: (present: () -> Boolean) -> SwingModifier,
        ): Arguments = Arguments.of(Named.of(name, declare))

        @JvmStatic
        fun removals(): List<Arguments> = listOf(
            case("the style stops setting it") { present ->
                SwingModifier.styleable({ if (present()) InsetProperty.provide(1) }, PlainProbe)
            },
            case("the style leaves the modifier") { present ->
                if (present()) SwingModifier.styleable({ InsetProperty.provide(1) }, PlainProbe) else SwingModifier
            },
            case("the style leaves as the modifier's keys change") { present ->
                SwingModifier.key(present()).then(
                    if (present()) SwingModifier.styleable({ InsetProperty.provide(1) }, PlainProbe) else SwingModifier,
                )
            },
        )
    }
}

private data class FailingLayoutElement(
    override val key: String,
    val amount: Int,
    val failure: IllegalArgumentException? = null,
    val failsOnAttach: Boolean = false,
) : ParentLayoutNodeElement<FailureLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = MeasuringParent

    override fun create(): FailureLayoutNode = FailureLayoutNode(amount, failure.takeIf { failsOnAttach })

    override fun update(node: FailureLayoutNode) {
        if (!failsOnAttach) failure?.let { throw it }
    }
}

private class FailureLayoutNode(
    val amount: Int,
    private val attachFailure: IllegalArgumentException? = null,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = MeasuringParent

    override fun onAttach() {
        attachFailure?.let { throw it }
    }
}

private class FirstUpdateFailingLayout(
    private val events: MutableList<String>,
    private val failure: IllegalArgumentException,
) : ParentLayoutNodeElement<FirstUpdateFailingLayout.Node>() {
    override val parentProtocol: ParentProtocol get() = MeasuringParent

    override fun create(): Node = Node(events)

    override fun update(node: Node) {
        events += "update"
        throw failure
    }

    override fun equals(other: Any?): Boolean = other is FirstUpdateFailingLayout && events === other.events

    override fun hashCode(): Int = System.identityHashCode(events)

    class Node(
        private val events: MutableList<String>,
    ) : ParentLayoutNode() {
        override val parentProtocol: ParentProtocol get() = MeasuringParent

        override fun onAttach() {
            events += "attach"
        }

        override fun onDetach() {
            events += "detach"
        }
    }
}

/** Retains the nodes last handed to the component through the public listener contract. */
private class LayoutListeningLabel :
    JLabel(),
    DeclaredNodesListener {
    /** The attached declaration last published by the modifier. */
    var declaredNodes: List<SwingModifier.Node> = emptyList()
        private set

    /** Keeps the listener's current declaration for cleanup assertions. */
    override fun onDeclaredNodesChanged(
        componentNode: SwingComponentNode<*>,
        nodes: List<SwingModifier.Node>,
    ) {
        declaredNodes = nodes
    }
}

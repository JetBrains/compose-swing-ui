package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.SwingModifier
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
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A parent-layout element a style sets is declared to the component's parent as if it stood last in the
 * component's modifier.
 */
class StyleParentLayoutTest {
    @Test
    fun nestedLayoutStyleReachesParent() = runComposeSwingTest {
        val layout = InsetLayout()
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier =
                    SwingModifier.styleable(
                        { provideNestedStyle(Probes) { InsetProperty.provide(2) } },
                        PlainProbeContainer,
                    ),
            ) {
                SwingNode(factory = { JPanel(layout) }) {
                    SwingNode(
                        factory = { JLabel("nested") },
                        modifier = SwingModifier.styleable(SwingStyle, NestedProbe),
                    )
                }
            }
        }
        onNodeWithText("nested").assertProperty(2) { layout.insetOf(this).amount }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun movedLayoutWritesOnlyNewParent(next: Int) = runComposeSwingTest {
        var inFirst by mutableStateOf(true)
        lateinit var context: CompositionContext
        val writes = mutableListOf<Int>()
        val onWrite: (Int) -> Unit = { writes += it }
        val property = stylePropertyOf<Int> { NamedInset("recorded", it, onWrite) }
        val leaf = JLabel("moved")
        val moved =
            movableContentOf {
                SwingNode(factory = { leaf }, modifier = SwingModifier.styleable(SwingStyle, NestedProbe))
            }
        val firstLayout = InsetLayout()
        val secondLayout = InsetLayout()
        setContent { context = rememberCompositionContext() }
        val first =
            JPanel().setContent(parent = context) {
                SwingNode(
                    factory = { JPanel(firstLayout) },
                    modifier =
                        SwingModifier.styleable(
                            { provideNestedStyle(Probes) { property.provide(1) } },
                            PlainProbeContainer,
                        ),
                ) {
                    if (inFirst) moved()
                }
            }
        val second =
            JPanel().setContent(parent = context) {
                SwingNode(
                    factory = { JPanel(secondLayout) },
                    modifier =
                        SwingModifier.styleable(
                            { provideNestedStyle(Probes) { property.provide(next) } },
                            PlainProbeContainer,
                        ),
                ) {
                    if (!inFirst) moved()
                }
            }
        try {
            awaitIdle()
            assertEquals(1, firstLayout.insetOf(leaf).amount)
            writes.clear()

            inFirst = false
            awaitIdle()

            assertEquals(next, secondLayout.insetOf(leaf).amount)
            assertEquals(listOf(next), writes)
        } finally {
            second.dispose()
            first.dispose()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changedAmounts")
    fun refusedProtocolReceivesNoDeclarations(next: Int?) = runComposeSwingTest {
        var amount by mutableStateOf<Int?>(1)
        val other = OtherMeasuringLayout()
        setContent {
            Panel(PanelLayout.Border()) { StyledLabel("under a border", amount) }
            SwingNode(factory = { JPanel(other) }) { StyledLabel("measured elsewhere", amount) }
        }
        val declarations = other.declarations.size
        val invalidations = other.invalidations.size

        amount = next
        awaitIdle()

        onNodeWithText("under a border").assertProperty(true) {
            (parent.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER) === this
        }
        assertTrue(other.declarations.all { it.elements.isEmpty() }, "the parent receives nothing")
        assertEquals(declarations, other.declarations.size, "the parent is not declared to again")
        assertEquals(invalidations, other.invalidations.size, "the parent is not invalidated")
    }

    @Test
    fun layoutValueUpdatesExistingNode() = runComposeSwingTest {
        var amount by mutableStateOf<Int?>(1)
        val layout = InsetLayout()
        setContent {
            SwingNode(factory = { JPanel(layout) }) { StyledLabel("styled", amount) }
        }
        var declared: InsetNode? = null
        onNodeWithText("styled").assertProperty(1) { layout.insetOf(this).also { declared = it }.amount }
        val declarations = layout.declarations.size

        amount = 3
        awaitIdle()

        var rewritten: InsetNode? = null
        onNodeWithText("styled").assertProperty(3) { layout.insetOf(this).also { rewritten = it }.amount }
        assertSame(declared, rewritten, "the node is kept")
        assertEquals(declarations + 1, layout.declarations.size, "the parent is declared to again")
    }

    @Test
    fun styleLayoutOverridesModifier() = runComposeSwingTest {
        var amount by mutableStateOf<Int?>(7)
        val layout = InsetLayout()
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                SwingNode(
                    factory = { JLabel("styled") },
                    modifier =
                        SwingModifier then Inset(5) then MeasuredAnywhere then
                            SwingModifier.styleable({ amount?.let { InsetProperty.provide(it) } }, PlainProbe),
                )
            }
        }
        onNodeWithText("styled")
            .assertProperty(2) { layout.elementsOf(this).size }
            .assertProperty(MeasuredAnywhere) { layout.elementsOf(this).first() }
            .assertProperty(7) { layout.insetOf(this).amount }

        amount = null
        awaitIdle()

        onNodeWithText("styled")
            .assertProperty(5) { layout.insetOf(this).amount }
            .assertProperty(MeasuredAnywhere) { layout.elementsOf(this).last() }
    }

    @Test
    fun reorderedLayoutNodesReachComponent() = runComposeSwingTest {
        var reversed by mutableStateOf(false)
        val label = DeclaredLabel()
        val first = stylePropertyOf<Int> { NamedInset("first", it) }
        val second = stylePropertyOf<Int> { NamedInset("second", it) }
        setContent {
            SwingNode(factory = { JPanel(InsetLayout()) }) {
                SwingNode(
                    factory = { label },
                    modifier =
                        SwingModifier.styleable({
                            if (reversed) {
                                second.provide(2)
                                first.provide(1)
                            } else {
                                first.provide(1)
                                second.provide(2)
                            }
                        }, PlainProbe),
                )
            }
        }
        val original = label.declared.toList()
        assertEquals(listOf(1, 2), original.map { (it as InsetNode).amount })

        reversed = true
        awaitIdle()

        assertEquals(original.reversed(), label.declared)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun removedLayoutStyleClearsDeclarations(removeModifier: Boolean) = runComposeSwingTest {
        var declares by mutableStateOf(true)
        val label = DeclaredLabel()
        setContent {
            SwingNode(factory = { JPanel(InsetLayout()) }) {
                SwingNode(
                    factory = { label },
                    modifier =
                        if (removeModifier && !declares) {
                            SwingModifier
                        } else {
                            SwingModifier.styleable({ if (declares) InsetProperty.provide(1) }, PlainProbe)
                        },
                )
            }
        }
        assertEquals(1, label.declared.size)
        declares = false
        awaitIdle()
        assertEquals(emptyList(), label.declared)
    }

    companion object {
        @JvmStatic
        fun changedAmounts(): List<Arguments> = listOf(
            Arguments.of(Named.of("a changed amount", 2)),
            Arguments.of(Named.of("a removed element", null)),
        )
    }
}

@Composable
private fun StyledLabel(
    text: String,
    amount: Int?,
) {
    SwingNode(
        factory = { JLabel(text) },
        modifier = SwingModifier.styleable({ amount?.let { InsetProperty.provide(it) } }, PlainProbe),
    )
}

/** A declaration every measuring parent interprets. */
internal data object MeasuredAnywhere : ParentLayoutElement {
    override val parentProtocol: ParentProtocol = MeasuringParent
}

/** A measuring manager [InsetProperty]'s protocol refuses. */
private class OtherMeasuringLayout : RecordingMeasurementLayout(FlowLayout())

private class DeclaredLabel :
    JLabel(),
    DeclaredNodesListener {
    var declared: List<SwingModifier.Node> = emptyList()

    override fun onDeclaredNodesChanged(
        componentNode: SwingComponentNode<*>,
        nodes: List<SwingModifier.Node>,
    ) {
        declared = nodes
    }
}

private data class NamedInset(
    override val key: String,
    val amount: Int,
    val onWrite: (Int) -> Unit = {},
) : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = Inset(amount).parentProtocol

    override val inheritable: Boolean get() = true

    override fun create(): InsetNode = InsetNode(amount)

    override fun update(node: InsetNode) {
        node.amount = amount
        onWrite(amount)
    }
}

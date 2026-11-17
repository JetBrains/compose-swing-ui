// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.key
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.BorderLayout
import java.awt.LayoutManager
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
                    SwingModifier.styleable(PlainProbeContainer) {
                        provideNestedStyle(Probes) { InsetProperty.provide(2) }
                    },
            ) {
                SwingNode(factory = { JPanel(layout) }) {
                    SwingNode(
                        factory = { JLabel("nested") },
                        modifier = SwingModifier.styleable(NestedProbe, SwingStyle),
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
        val writes = mutableListOf<Int>()
        val onWrite: (Int) -> Unit = { writes += it }
        val property = stylePropertyOf<Int> { Inset(it, "recorded", onWrite) }
        val moved =
            movableContentOf {
                SwingNode(factory = { JLabel("moved") }, modifier = SwingModifier.styleable(NestedProbe, SwingStyle))
            }
        val firstLayout = InsetLayout()
        val secondLayout = InsetLayout()
        setContent {
            val context = rememberCompositionContext()
            NestedContentHost(parent = context) {
                SwingNode(
                    factory = { JPanel(firstLayout) },
                    modifier =
                        SwingModifier.styleable(PlainProbeContainer) {
                            provideNestedStyle(Probes) { property.provide(1) }
                        },
                ) {
                    if (inFirst) moved()
                }
            }
            NestedContentHost(parent = context) {
                SwingNode(
                    factory = { JPanel(secondLayout) },
                    modifier =
                        SwingModifier.styleable(PlainProbeContainer) {
                            provideNestedStyle(Probes) { property.provide(next) }
                        },
                ) {
                    if (!inFirst) moved()
                }
            }
        }
        awaitIdle()
        onNodeWithText("moved").assertProperty(1) { firstLayout.insetOf(this).amount }
        writes.clear()

        inFirst = false
        awaitIdle()

        onNodeWithText("moved").assertProperty(next) { secondLayout.insetOf(this).amount }
        assertEquals(listOf(next), writes)
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
    fun aFailedStyleLayoutUpdateStillRedeclaresItsMutatedNodeToTheParent() = runComposeSwingTest {
        var amount by mutableStateOf(1)
        val failure = IllegalArgumentException("layout update failed after mutation")
        val property =
            stylePropertyOf<Int> { value ->
                Inset(value) { written -> if (written == 3) throw failure }
            }
        val layout = InsetLayout()
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                SwingNode(
                    factory = { JLabel("styled") },
                    modifier = SwingModifier.styleable(PlainProbe) { property.provide(amount) },
                )
            }
        }
        val label = onNodeWithText("styled").fetch<JLabel>()
        val declared = layout.insetOf(label)
        assertEquals(1, declared.amount)
        val declarations = layout.declarations.size

        amount = 3
        assertSame(failure, assertFailsWith<IllegalArgumentException> { awaitIdle() })

        val current = layout.insetOf(label)
        assertSame(declared, current)
        assertEquals(3, current.amount)
        assertEquals(declarations + 1, layout.declarations.size, "the parent is declared after the failed write")
    }

    @Test
    fun multipleStylesComposeParentLayoutKeysAndRestoreRemainingNodes() = runComposeSwingTest {
        var includeFirst by mutableStateOf(true)
        var includeLater by mutableStateOf(false)
        val layout = InsetLayout()
        val firstOnly = stylePropertyOf<Int> { Inset(it, key = "first-only") }
        val laterOnly = stylePropertyOf<Int> { Inset(it, key = "later-only") }
        val first =
            SwingStyle<ProbeScope> {
                InsetProperty.provide(1)
                firstOnly.provide(10)
            }
        val later =
            SwingStyle<ProbeScope> {
                InsetProperty.provide(2)
                laterOnly.provide(20)
            }

        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                val modifier =
                    SwingModifier
                        .then(if (includeFirst) SwingModifier.styleable(PlainProbe, first) else SwingModifier)
                        .then(if (includeLater) SwingModifier.styleable(PlainProbe, later) else SwingModifier)
                SwingNode(factory = { JLabel("styled") }, modifier = modifier)
            }
        }
        val label = onNodeWithText("styled").fetch<JLabel>()

        fun nodes() = layout.elementsOf(label).filterIsInstance<InsetNode>()

        val originalSameKeyNode = nodes().single { it.amount == 1 }
        val firstOnlyNode = nodes().single { it.amount == 10 }
        assertEquals(listOf(1, 10), nodes().map { it.amount }.sorted())

        includeLater = true
        awaitIdle()
        assertEquals(listOf(2, 10, 20), nodes().map { it.amount }.sorted())
        assertSame(firstOnlyNode, nodes().single { it.amount == 10 })

        includeLater = false
        awaitIdle()
        assertEquals(listOf(1, 10), nodes().map { it.amount }.sorted())
        assertSame(originalSameKeyNode, nodes().single { it.amount == 1 })

        includeLater = true
        awaitIdle()
        includeFirst = false
        awaitIdle()
        assertEquals(listOf(2, 20), nodes().map { it.amount }.sorted())
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
                            SwingModifier.styleable(PlainProbe) { amount?.let { InsetProperty.provide(it) } },
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
        val first = stylePropertyOf<Int> { Inset(it, "first") }
        val second = stylePropertyOf<Int> { Inset(it, "second") }
        setContent {
            SwingNode(factory = { JPanel(InsetLayout()) }) {
                SwingNode(
                    factory = { DeclaredLayoutLabel() },
                    modifier =
                        SwingModifier.styleable(PlainProbe) {
                            if (reversed) {
                                second.provide(2)
                                first.provide(1)
                            } else {
                                first.provide(1)
                                second.provide(2)
                            }
                        },
                )
            }
        }
        val label = onNodeOfType<DeclaredLayoutLabel>().fetch()
        val original = label.declared.toList()
        assertEquals(listOf(1, 2), original.map { (it as InsetNode).amount })

        reversed = true
        awaitIdle()

        assertEquals(original.reversed(), label.declared)
    }

    /** Cleanup follows retained records when the first update failed after attachment. */
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun failedFirstUpdateReleasesParentAndListenerDeclarations(dispose: Boolean) = runComposeSwingTest {
        var phase by mutableStateOf(0)
        val events = ArrayList<String>()
        val failure = IllegalArgumentException("First layout update failed")
        val property = stylePropertyOf<Unit> { FirstUpdateFailingLayout(events, failure) }
        val layout = InsetLayout()
        var mounted by mutableStateOf(true)
        setContent {
            ParentlessMountHost(layout = layout, mounted = mounted) {
                SwingNode(
                    factory = { DeclaredLayoutLabel() },
                    modifier = SwingModifier.styleable(PlainProbe) { if (phase == 1) property.provide(Unit) },
                )
            }
        }
        awaitIdle()
        val label = onNodeOfType<DeclaredLayoutLabel>().fetch()
        phase = 1
        assertSame(failure, assertFailsWith<IllegalArgumentException> { awaitIdle() })
        assertEquals(1, layout.elementsOf(label).size)

        if (dispose) mounted = false else phase = 2
        awaitIdle()

        assertEquals(listOf("attach", "update", "detach"), events)
        assertEquals(emptyList(), layout.elementsOf(label))
        assertEquals(emptyList(), label.declared)
    }

    @ParameterizedTest
    @MethodSource("siblingFailures")
    fun layoutRemovalSurvivesSiblingFailure(
        replacementKey: String,
        attachFailure: IllegalArgumentException?,
        updateFailure: IllegalArgumentException?,
        remainingAmounts: List<Int>,
    ) = runComposeSwingTest {
        var changed by mutableStateOf(false)
        val failure = checkNotNull(attachFailure ?: updateFailure)
        val layout = InsetLayout()
        val first = stylePropertyOf<Unit> { FailingLayoutElement("A", 1) }
        val second =
            stylePropertyOf<Boolean> { fails ->
                FailingLayoutElement(
                    key = if (fails) replacementKey else "B",
                    amount = 2,
                    attachFailure = if (fails) attachFailure else null,
                    updateFailure = if (fails) updateFailure else null,
                )
            }
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                SwingNode(
                    factory = { JLabel("styled") },
                    modifier =
                        SwingModifier.styleable(PlainProbe) {
                            if (!changed) first.provide(Unit)
                            second.provide(changed)
                        },
                )
            }
        }
        onNodeWithText("styled").assertProperty(listOf(1, 2)) {
            layout.elementsOf(this).mapNotNull { (it as? FailureLayoutNode)?.amount }
        }

        changed = true
        assertSame(failure, assertFailsWith<IllegalArgumentException> { awaitIdle() })

        onNodeWithText("styled").assertProperty(remainingAmounts) {
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
                SwingNode(factory = { DeclaredLayoutLabel("styled") }, modifier = declare { present })
            }
        }
        var node: InsetNode? = null
        onNodeWithText("styled").assertProperty(1) { layout.insetOf(this).also { node = it }.amount }
        onNodeOfType<DeclaredLayoutLabel>().assertProperty(1) { declared.size }

        present = false
        awaitIdle()

        onNodeWithText("styled")
            .assertProperty(emptyList<ParentLayoutElement>(), "the parent is declared to without the element") {
                layout.elementsOf(this)
            }
        onNodeOfType<DeclaredLayoutLabel>().assertProperty(emptyList()) { declared }
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
                            SwingModifier.styleable(PlainProbe) { property.provide(Unit) }
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
        @JvmStatic
        fun siblingFailures(): List<Arguments> {
            val failure = IllegalArgumentException("Layout failed")
            return listOf(
                Arguments.of("C", failure, null, emptyList<Int>()),
                Arguments.of("B", null, failure, listOf(2)),
            )
        }

        private fun case(
            name: String,
            declare: (present: () -> Boolean) -> SwingModifier,
        ): Arguments = Arguments.of(Named.of(name, declare))

        @JvmStatic
        fun removals(): List<Arguments> = listOf(
            case("the style stops setting it") { present ->
                SwingModifier.styleable(PlainProbe) { if (present()) InsetProperty.provide(1) }
            },
            case("the style leaves the modifier") { present ->
                if (present()) SwingModifier.styleable(PlainProbe) { InsetProperty.provide(1) } else SwingModifier
            },
            case("the style leaves as the modifier's keys change") { present ->
                SwingModifier.key(present()).then(
                    if (present()) SwingModifier.styleable(PlainProbe) { InsetProperty.provide(1) } else SwingModifier,
                )
            },
        )

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
        factory = { JLabel() },
        modifier = SwingModifier.styleable(PlainProbe) { amount?.let { InsetProperty.provide(it) } },
        update = { set(text) { this.text = it } },
    )
}

@Composable
private fun ParentlessMountHost(
    layout: LayoutManager,
    mounted: Boolean,
    content: @Composable () -> Unit,
) {
    val current = rememberUpdatedState(content)
    SwingNode(
        factory = { ParentlessMountPanel() },
        update = {
            set(layout) { this.layout = it }
            set(mounted) { if (it) mount(current) else dispose() }
        },
        onRelease = { dispose() },
    )
}

private class ParentlessMountPanel :
    JPanel(),
    DisposableHandle {
    private var mount: DisposableHandle? = null

    fun mount(content: State<@Composable () -> Unit>) {
        if (mount == null) mount = setContent { content.value() }
    }

    override fun dispose() {
        mount?.dispose()
        mount = null
    }
}

private data class FailingLayoutElement(
    override val key: String,
    val amount: Int,
    val attachFailure: IllegalArgumentException? = null,
    val updateFailure: IllegalArgumentException? = null,
) : ParentLayoutNodeElement<FailureLayoutNode>() {
    override val parentProtocol: ParentProtocol<*> get() = MeasuringParent

    override fun create(): FailureLayoutNode = FailureLayoutNode(amount, attachFailure)

    override fun update(node: FailureLayoutNode) {
        updateFailure?.let { throw it }
    }
}

private class FailureLayoutNode(
    val amount: Int,
    private val attachFailure: IllegalArgumentException? = null,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol<*> get() = MeasuringParent

    override fun onAttach() {
        attachFailure?.let { throw it }
    }
}

private class FirstUpdateFailingLayout(
    private val events: MutableList<String>,
    private val failure: IllegalArgumentException,
) : ParentLayoutNodeElement<FirstUpdateFailingLayout.Node>() {
    override val parentProtocol: ParentProtocol<*> get() = MeasuringParent

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
        override val parentProtocol: ParentProtocol<*> get() = MeasuringParent

        override fun onAttach() {
            events += "attach"
        }

        override fun onDetach() {
            events += "detach"
        }
    }
}

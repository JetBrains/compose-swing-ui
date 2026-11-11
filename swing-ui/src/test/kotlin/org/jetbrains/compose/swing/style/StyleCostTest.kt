// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Buttons
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class StyleCostTest {
    @Test
    fun emptyContainerAddsNoWrites() = runComposeSwingTest {
        val writes = WriteCounter()
        setContent {
            Label("bare", style = { writes.counted.provide("own") })
            Panel(style = {}) {
                Label("under an empty style", style = { writes.counted.provide("own") })
            }
        }
        assertEquals(2, writes.count, "each label writes its own property once, with or without the container")
        onNodeWithText("bare").assertIsOfType<JComponent>().assertProperty("own") { toolTipText }
        onNodeWithText("under an empty style").assertIsOfType<JComponent>().assertProperty("own") { toolTipText }
    }

    @Test
    fun styleAndModifierChangeWritesOnce() = runComposeSwingTest {
        var dark by mutableStateOf(false)
        val writes = WriteCounter()
        setContent {
            Panel(style = { provideNestedStyle(Labels) { background(if (dark) Color.BLACK else Color.WHITE) } }) {
                Label(
                    "label",
                    modifier = SwingModifier.property(writes.descriptor, if (dark) "dark" else "light"),
                )
            }
        }
        assertEquals(1, writes.count)
        onNodeWithText("label").assertIsOfType<JComponent>().assertProperty("light") { toolTipText }

        dark = true
        awaitIdle()
        assertEquals(2, writes.count, "a pass changing the provided style and the modifier writes the property once")
        onNodeWithText("label").assertIsOfType<JComponent>().assertProperty("dark") { toolTipText }
    }

    @Test
    fun styleChangedByStateAloneDoesNotPartitionTheUnchangedModifierAgain() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        val keyAsks = mockk<Runnable>(relaxUnitFun = true)
        val element = KeyAskCountingElement(keyAsks)
        val onCheckedChange: (Boolean) -> Unit = {}
        setContent {
            CheckBox("box", checked, onCheckedChange, modifier = SwingModifier.then(element))
        }
        clearMocks(keyAsks, answers = false)

        checked = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(true) { isSelected }
        verify(exactly = 0) { keyAsks.run() }
    }

    @Test
    fun unchangedPassDoesNoWork() = runComposeSwingTest {
        var pass by mutableIntStateOf(0)
        val writes = WriteCounter()
        val nestedRuns = mockk<Runnable>(relaxUnitFun = true)
        val ownRuns = mockk<Runnable>(relaxUnitFun = true)
        setContent {
            pass.let { }
            Panel(
                style = {
                    background(Color.GRAY)
                    provideNestedStyle(Labels) {
                        nestedRuns.run()
                        foreground(Color.BLUE)
                    }
                },
            ) {
                Label(
                    "label",
                    style = {
                        ownRuns.run()
                        writes.counted.provide("stable")
                    },
                )
            }
        }
        verify(exactly = 1) {
            nestedRuns.run()
            ownRuns.run()
        }
        assertEquals(1, writes.count)

        repeat(2) {
            pass++
            awaitIdle()
        }
        verify(exactly = 1) {
            nestedRuns.run()
            ownRuns.run()
        }
        assertEquals(1, writes.count, "equal styles write nothing")
    }

    @Test
    fun unchangedPassWithMultipleStyleDeclarationsDoesNoWork() = runComposeSwingTest {
        var pass by mutableIntStateOf(0)
        val target = StyleProbe("target")
        val firstRuns = mockk<Runnable>(relaxUnitFun = true)
        val secondRuns = mockk<Runnable>(relaxUnitFun = true)
        val first =
            SwingStyle<ProbeScope> {
                firstRuns.run()
                ProbeFirst.provide("first")
                ProbeSecond.provide("second")
            }
        val second =
            SwingStyle<ProbeScope> {
                secondRuns.run()
                ProbeThird.provide("third")
                ProbeFourth.provide("fourth")
            }
        setContent {
            pass.let { }
            SwingNode(
                factory = { target },
                modifier = SwingModifier.styleable(PlainProbe, first).then(SwingModifier.styleable(PlainProbe, second)),
            )
        }
        verify(exactly = 1) {
            firstRuns.run()
            secondRuns.run()
        }
        val attachedWrites = target.writes.toList()
        assertEquals(4, attachedWrites.size)

        repeat(2) {
            pass++
            awaitIdle()
        }
        verify(exactly = 1) {
            firstRuns.run()
            secondRuns.run()
        }
        assertEquals(attachedWrites, target.writes, "an unchanged pass does not rerun or rewrite either style")
    }

    @Test
    fun publisherPresenceRestylesActiveStylesOnOneComponent() = runComposeSwingTest {
        var providing by mutableStateOf(false)
        val parent = StyleProbe("parent")
        val target = StyleProbe("target")
        val runs = List(3) { mockk<Runnable>(relaxUnitFun = true) }
        val writes = ArrayList<String>()
        val unrelatedKey = NestedStyleKey<ProbeScope>("Unrelated")
        val parentStyle =
            SwingStyle<ProbeContainerScope> {
                provideNestedStyle(unrelatedKey) { ProbeFourth.provide("unrelated") }
            }
        val firstStyle =
            countedProbeStyle("first", runs[0], writes, StyleProbe::first) {
                "first"
            }
        val secondStyle =
            countedProbeStyle("second", runs[1], writes, StyleProbe::second) {
                "second"
            }
        val thirdStyle =
            countedProbeStyle("third", runs[2], writes, StyleProbe::third) {
                "third"
            }
        setContent {
            ProbeContainer(parent, if (providing) parentStyle else SwingStyle) {
                SwingNode(
                    factory = { target },
                    modifier =
                        SwingModifier
                            .styleable(PlainProbe, firstStyle)
                            .then(SwingModifier.styleable(PlainProbe, secondStyle))
                            .then(SwingModifier.styleable(PlainProbe, thirdStyle)),
                )
            }
        }
        assertEquals(listOf("first=first", "second=second", "third=third"), writes)
        verify(exactly = 1) {
            runs[0].run()
            runs[1].run()
            runs[2].run()
        }

        providing = true
        awaitIdle()
        assertEquals(
            listOf(
                "first=first",
                "second=second",
                "third=third",
                "second=second",
                "third=third",
            ),
            writes,
        )
        verify(exactly = 2) {
            runs[0].run()
            runs[1].run()
            runs[2].run()
        }
    }

    @Test
    fun threeStylesReadingOneSnapshotInputEachRunAndWriteOnce() = runComposeSwingTest {
        var shade by mutableStateOf("light")
        val target = StyleProbe("target")
        val firstRuns = mockk<Runnable>(relaxUnitFun = true)
        val secondRuns = mockk<Runnable>(relaxUnitFun = true)
        val thirdRuns = mockk<Runnable>(relaxUnitFun = true)
        val firstStyle =
            SwingStyle<ProbeScope> {
                firstRuns.run()
                ProbeFirst.provide(shade)
            }
        val secondStyle =
            SwingStyle<ProbeScope> {
                secondRuns.run()
                ProbeSecond.provide(shade)
            }
        val thirdStyle =
            SwingStyle<ProbeScope> {
                thirdRuns.run()
                ProbeThird.provide(shade)
            }
        setContent {
            SwingNode(
                factory = { target },
                modifier =
                    SwingModifier
                        .styleable(PlainProbe, firstStyle)
                        .then(SwingModifier.styleable(PlainProbe, secondStyle))
                        .then(SwingModifier.styleable(PlainProbe, thirdStyle)),
            )
        }
        verify(exactly = 1) {
            firstRuns.run()
            secondRuns.run()
            thirdRuns.run()
        }
        val attachedWrites = target.writes.toList()
        assertEquals(listOf("first=light", "second=light", "third=light"), attachedWrites)

        shade = "dark"
        awaitIdle()
        verify(exactly = 2) {
            firstRuns.run()
            secondRuns.run()
            thirdRuns.run()
        }
        assertEquals(
            attachedWrites + listOf("first=dark", "second=dark", "third=dark"),
            target.writes,
        )
    }

    @Test
    fun changingOneStyleInputKeepsOtherStyleReadsSelective() = runComposeSwingTest {
        val firstValue = mutableStateOf("first")
        var middleValue by mutableStateOf("middle")
        val lastValue = mutableStateOf("last")
        val target = StyleProbe("target")
        val writes = ArrayList<String>()
        val runs = List(3) { mockk<Runnable>(relaxUnitFun = true) }
        val firstStyle =
            countedProbeStyle("first", runs[0], writes, StyleProbe::first) {
                firstValue.value
            }
        val middleStyle =
            countedProbeStyle("second", runs[1], writes, StyleProbe::second) {
                middleValue
            }
        val lastStyle =
            countedProbeStyle("third", runs[2], writes, StyleProbe::third) {
                lastValue.value
            }
        setContent {
            SwingNode(
                factory = { target },
                modifier =
                    SwingModifier
                        .styleable(PlainProbe, firstStyle)
                        .then(SwingModifier.styleable(PlainProbe, middleStyle))
                        .then(SwingModifier.styleable(PlainProbe, lastStyle)),
            )
        }
        verify(exactly = 1) {
            runs[0].run()
            runs[1].run()
            runs[2].run()
        }
        assertEquals(listOf("first=first", "second=middle", "third=last"), writes)

        middleValue = "changed"
        awaitIdle()
        verify(exactly = 1) {
            runs[0].run()
            runs[2].run()
        }
        verify(exactly = 2) { runs[1].run() }
        assertEquals("second=changed", writes[3])
        assertEquals(listOf("second=changed", "third=last").take(writes.size - 3), writes.drop(3))
        assertEquals("first", target.first)
        assertEquals("changed", target.second)
        assertEquals("last", target.third)
    }

    @Test
    fun changedBlocksResolveOnlyTheirConsumers() = runComposeSwingTest {
        var labelColor by mutableStateOf(Color.RED)
        var buttonColor by mutableStateOf(Color.GRAY)
        val labelRuns = mockk<Runnable>(relaxUnitFun = true)
        val buttonRuns = mockk<Runnable>(relaxUnitFun = true)
        val writes = WriteCounter()
        val overridingWrites = WriteCounter()
        setContent {
            Panel(style = {
                provideNestedStyle(Labels) {
                    labelRuns.run()
                    background(labelColor)
                    foreground(Color.BLACK)
                }
                provideNestedStyle(Buttons) {
                    buttonRuns.run()
                    background(buttonColor)
                }
            }) {
                Label("first", style = { writes.counted.provide("label") })
                Label("second")
                Label("overriding", style = {
                    background(Color.BLUE)
                    overridingWrites.counted.provide("explicit")
                })
                Button("button", onClick = {})
            }
        }
        verify(exactly = 3) { labelRuns.run() }
        verify(exactly = 1) { buttonRuns.run() }
        assertEquals(1, overridingWrites.count)

        labelColor = Color.GREEN
        awaitIdle()
        verify(exactly = 6) { labelRuns.run() }
        verify(exactly = 1) { buttonRuns.run() }
        onNodeWithText("first").assertProperty(Color.GREEN) { background }
        onNodeWithText("second").assertProperty(Color.GREEN) { background }
        onNodeWithText("overriding").assertProperty(Color.BLUE) { background }
        assertEquals(1, overridingWrites.count, "the winning style is not written again")
        val attached = listOf(writes.count, overridingWrites.count)

        buttonColor = Color.GREEN
        awaitIdle()
        onNodeWithText("button").assertProperty(Color.GREEN) { background }
        verify(exactly = 6) { labelRuns.run() }
        assertEquals(attached, listOf(writes.count, overridingWrites.count))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unchangedStyles")
    fun unchangedStylesDoNotResolve(case: UnchangedStyleCase) = runComposeSwingTest {
        var pass by mutableIntStateOf(0)
        val container = StyleProbe()
        val leaf = StyleProbe("leaf")
        var containerRuns = 0
        var leafRuns = 0
        var nestedRuns = 0
        val containerStyle =
            SwingStyle<ProbeContainerScope> {
                containerRuns++
                InheritedFirst.provide("inherited")
                provideNestedStyle(Probes) {
                    nestedRuns++
                    ProbeSecond.provide("nested")
                }
            }
        val leafStyle =
            SwingStyle<ProbeScope> {
                leafRuns++
                case.first?.let { ProbeFirst.provide(it) }
                ProbeThird.provide("caller")
            }
        setContent {
            readPass(pass)
            ProbeContainer(container, containerStyle) {
                SwingNode(
                    factory = { leaf },
                    modifier =
                        case
                            .modifier(pass)
                            .styleable(NestedProbe, case.style(leafStyle)),
                )
            }
        }
        onProbe("leaf")
            .assertProperty(case.expectedFirst) { first }
            .assertProperty("nested") { second }
            .assertProperty("caller") { third }
        val attachedRuns = listOf(containerRuns, leafRuns, nestedRuns)
        val attachedWrites = container.writes + leaf.writes
        repeat(3) {
            pass++
            awaitIdle()
        }
        assertEquals(attachedRuns, listOf(containerRuns, leafRuns, nestedRuns))
        case.additionalWrites?.let { assertEquals(attachedWrites + it, container.writes + leaf.writes) }
        onProbe("leaf")
            .assertProperty(case.expectedFirst) { first }
            .assertProperty("nested") { second }
    }

    @Test
    fun movedChildResolvesWithoutItsSibling() = runComposeSwingTest {
        var first by mutableStateOf(true)
        var containerRuns = 0
        var leafRuns = 0
        var nestedRuns = 0
        val containerStyle =
            SwingStyle<ProbeContainerScope> {
                containerRuns++
                InheritedFirst.provide("inherited")
                provideNestedStyle(Probes) {
                    nestedRuns++
                    ProbeSecond.provide("nested")
                }
            }
        val leafStyle = SwingStyle<ProbeScope> { leafRuns++ }
        val moved = StyleProbe("moved")
        setContent {
            val content = remember { movableContentOf { ProbeLeaf(moved, leafStyle, NestedProbe) } }
            ProbeContainer(StyleProbe(), containerStyle) {
                if (first) content()
                ProbeLeaf(StyleProbe("fixed"), leafStyle, NestedProbe)
                if (!first) content()
            }
        }
        val attachedContainerRuns = containerRuns
        val attachedLeafRuns = leafRuns
        val attachedNestedRuns = nestedRuns
        val panel = assertIs<JPanel>(moved.parent)

        first = false
        awaitIdle()
        assertSame(moved, panel.getComponent(1), "the child moved to the other place under the same parent")
        assertEquals(attachedLeafRuns + 1, leafRuns, "the moved child resolves once more, and its sibling does not")
        assertEquals(attachedContainerRuns, containerRuns, "the moved child does not replay its provider")
        assertEquals(attachedNestedRuns + 1, nestedRuns, "the moved child's resolve applies the nested style once")
        onProbe("moved")
            .assertProperty("inherited") { this.first }
            .assertProperty("nested") { second }
    }

    @Test
    fun restyleReadsDoNotGrowWithDepth() {
        assertEquals(
            statesReadByARestyleBelowAPublisher(plainPanels = 1),
            statesReadByARestyleBelowAPublisher(plainPanels = 32),
            "the states a label's restyle reads do not grow with the panels between it and the publisher",
        )
    }

    @Test
    fun styleStateUpdatesWithoutRecomposition() = runComposeSwingTest {
        val probe = StyleProbe()
        var tint by mutableStateOf("light")
        var resolves = 0
        var compositions = 0
        val kind = probeKind { resolves++ }
        val style =
            SwingStyle<ProbeScope> {
                ProbeFirst.provide("fixed")
                ProbeSecond.provide(tint)
            }
        setContent {
            SideEffect { compositions++ }
            ProbeLeaf(probe, style, kind)
        }
        assertEquals(listOf("first=fixed", "second=light"), probe.writes)
        val composed = compositions
        val resolved = resolves
        probe.writes.clear()

        tint = "dark"
        awaitIdle()
        assertEquals(listOf("second=dark"), probe.writes, "only the changed property is written")
        assertEquals(resolved + 1, resolves, "the state change resolves the style once")
        assertEquals(composed, compositions, "the state change recomposes nothing")
    }

    @Test
    fun changedProviderResolvesOnlyItsConsumers() = runComposeSwingTest {
        val consumer = StyleProbe()
        val bystander = StyleProbe()
        var dark by mutableStateOf(false)
        var consumerResolves = 0
        var bystanderResolves = 0
        val light = SwingStyle<ProbeContainerScope> { provideNestedStyle(Probes) { ProbeFirst.provide("light") } }
        val darkStyle = SwingStyle<ProbeContainerScope> { provideNestedStyle(Probes) { ProbeFirst.provide("dark") } }
        val consumerKind =
            probeKind {
                consumerResolves++
                applyNestedStyle(Probes)
            }
        val bystanderKind =
            probeKind {
                bystanderResolves++
                applyNestedStyle(OtherProbes)
            }
        val bystanderStyle = SwingStyle<ProbeScope> { ProbeSecond.provide("own style") }
        setContent {
            ProbeContainer(StyleProbe(), if (dark) darkStyle else light) {
                ProbeLeaf(consumer, SwingStyle, consumerKind)
                ProbeLeaf(bystander, bystanderStyle, bystanderKind)
            }
        }
        assertEquals("light", consumer.first)
        val consumerResolved = consumerResolves
        val bystanderResolved = bystanderResolves
        consumer.writes.clear()
        bystander.writes.clear()

        dark = true
        awaitIdle()
        assertEquals(listOf("first=dark"), consumer.writes)
        assertEquals(consumerResolved + 1, consumerResolves, "the consumer of the key resolves once")
        assertEquals(emptyList(), bystander.writes, "a component applying another key writes nothing")
        assertEquals(bystanderResolved, bystanderResolves, "a component applying another key does not resolve")
    }

    @ParameterizedTest
    @MethodSource("firstFrames")
    fun firstFrameResolvesOnce(case: FirstFrameCase) = runComposeSwingTest {
        val leaf = StyleProbe()
        var resolves = 0
        val kind =
            probeKind {
                resolves++
                applyNestedStyle(Probes)
            }
        setContent {
            ProbeContainer(StyleProbe(), case.provider) { ProbeLeaf(leaf, SwingStyle, kind) }
        }
        assertEquals(listOf(case.expectedWrite), leaf.writes)
        assertEquals(1, resolves, "the container's first publish does not resolve the descendant again")
    }

    @Test
    fun localMapChangeResolvesOnlyReaders() = runComposeSwingTest {
        var tone by mutableStateOf("light")
        var readerResolves = 0
        var bystanderResolves = 0
        val readerKind =
            probeKind {
                readerResolves++
                ProbeFirst.provide(LocalTone.currentValue)
            }
        val bystanderKind =
            probeKind {
                bystanderResolves++
                ProbeFirst.provide("own style")
            }
        setContent {
            CompositionLocalProvider(LocalTone provides tone) {
                ProbeLeaf(StyleProbe("reader"), SwingStyle, readerKind)
                ProbeLeaf(StyleProbe(), SwingStyle, bystanderKind)
            }
        }
        onProbe("reader").assertProperty("light") { first }
        val readerResolved = readerResolves
        val bystanderResolved = bystanderResolves

        tone = "dark"
        awaitIdle()
        onProbe("reader").assertProperty("dark") { first }
        assertEquals(readerResolved + 1, readerResolves, "a style that read the local resolves once")
        assertEquals(bystanderResolved, bystanderResolves, "a style that read no local does not resolve")
    }

    sealed class FirstFrameCase(
        val provider: SwingStyle<ProbeContainerScope>,
        val expectedWrite: String,
    ) {
        data object Inherited : FirstFrameCase({ InheritedFirst.provide("inherited") }, "first=inherited")

        data object Nested : FirstFrameCase(
            { provideNestedStyle(Probes) { ProbeFirst.provide("nested") } },
            "first=nested",
        )
    }

    sealed class UnchangedStyleCase(
        val first: String?,
        val modifier: (Int) -> SwingModifier,
        val expectedFirst: String,
        val additionalWrites: List<String>?,
        val style: (SwingStyle<ProbeScope>) -> SwingStyle<ProbeScope> = { it },
    ) {
        data object Inherited : UnchangedStyleCase(null, { SwingModifier }, "inherited", emptyList())

        /** The leaf's style is a combination built again on every pass from the same parts. */
        data object RebuiltCombination : UnchangedStyleCase(
            null,
            { SwingModifier },
            "inherited",
            emptyList(),
            { it then it },
        )

        data object KeyedWrite : UnchangedStyleCase(
            "styled",
            { SwingModifier.property(FirstDescriptor, "chain $it") },
            "styled",
            null,
        )
    }

    companion object {
        @JvmStatic
        fun unchangedStyles(): List<UnchangedStyleCase> = listOf(
            UnchangedStyleCase.Inherited,
            UnchangedStyleCase.KeyedWrite,
            UnchangedStyleCase.RebuiltCombination,
        )

        @JvmStatic
        fun firstFrames(): List<FirstFrameCase> = listOf(FirstFrameCase.Inherited, FirstFrameCase.Nested)
    }
}

/** A modifier entry that counts each time its key is asked, which partitioning the modifier does per entry. */
private class KeyAskCountingElement(
    private val asks: Runnable,
) : SwingModifier.NodeElement<JComponent, SwingModifier.ComponentNode<JComponent>>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val key: Any
        get() {
            asks.run()
            return javaClass
        }

    override fun create(): SwingModifier.ComponentNode<JComponent> = SwingModifier.ComponentNode()

    override fun update(node: SwingModifier.ComponentNode<JComponent>) = Unit

    override fun equals(other: Any?): Boolean = other is KeyAskCountingElement && asks === other.asks

    override fun hashCode(): Int = System.identityHashCode(asks)
}

private class WriteCounter {
    var count = 0

    val descriptor =
        ComponentPropertyDescriptor<JComponent, String?>(
            name = "countedToolTipText",
            read = { it.toolTipText },
            write = { component, value ->
                count++
                component.toolTipText = value
            },
        )

    val counted = stylePropertyOf(descriptor)
}

private fun countedProbeStyle(
    name: String,
    runs: Runnable,
    writes: MutableList<String>,
    property: KMutableProperty1<StyleProbe, String?>,
    value: () -> String?,
): SwingStyle<ProbeScope> {
    val styledProperty =
        stylePropertyOf(
            ComponentPropertyDescriptor<StyleProbe, String?>(
                name,
                property::get,
                { component, written ->
                    writes += "$name=$written"
                    property.set(component, written)
                },
            ),
        )
    return SwingStyle {
        runs.run()
        styledProperty.provide(value())
    }
}

private fun statesReadByARestyleBelowAPublisher(plainPanels: Int): Int {
    var read = 0
    runComposeSwingTest {
        val shade = mutableStateOf(Color.RED)
        setContent {
            Panel(style = { provideNestedStyle(Labels) { foreground(Color.GREEN) } }) {
                Plain(plainPanels) { Label("label", style = { background(shade.value) }) }
            }
        }
        awaitIdle()
        val states = HashSet<Any>()
        Snapshot.observe(readObserver = { states += it }) {
            Snapshot.withMutableSnapshot { shade.value = Color.BLUE }
        }
        awaitIdle()
        assertTrue(shade in states, "the restyle reads the label's own state at $plainPanels panels")
        read = states.size
    }
    return read
}

@Composable
private fun Plain(
    depth: Int,
    content: @Composable () -> Unit,
) {
    if (depth == 0) content() else Panel { Plain(depth - 1, content) }
}

private val LocalTone = staticCompositionLocalOf { "plain" }

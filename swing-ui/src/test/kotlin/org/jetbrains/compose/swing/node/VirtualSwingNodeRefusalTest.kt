// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.OnDemandComposition
import org.jetbrains.compose.swing.RecordingLayout
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.captureParentContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.modifier.listener.containerListener
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.BorderLayout
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unsupported modifiers, hosts, and claimed children are refused; refused declarations preserve accepted state and
 * allow subsequent valid updates; relocation validates and attaches children in composition order across their hosts.
 */
class VirtualSwingNodeRefusalTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedModifiers")
    fun aGroupModifierIsRefused(
        modifier: SwingModifier,
        failure: KClass<out Throwable>,
    ) {
        val host = MeasuringHost()
        assertFailsWith(failure) {
            runComposeSwingTest {
                setContent { SwingNode(factory = { host }) { VirtualSwingNode(modifier) { Label("a") } } }
                awaitIdle()
            }
        }
        assertEquals(emptyList(), host.components.toList())
        assertEquals(emptyList(), host.recording.declarations)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("regionHosts")
    fun aGroupUnderARegionHostIsRefused(
        factory: (RecordingMeasurementLayout) -> Container,
        compose: suspend ComposeSwingTest.(Container) -> Unit,
    ) {
        val layout = RecordingMeasurementLayout()
        val host = factory(layout)
        val components = host.components.toList()
        assertComposingFails<IllegalStateException> {
            try {
                compose(host)
            } finally {
                assertEquals(components, host.components.toList())
                assertEquals(emptyList(), layout.declarations)
            }
        }
    }

    @Test
    fun aGroupUnderAMenuCompositionIsRefusedByTheRuntime() = runComposeSwingTest {
        var inserted by mutableStateOf(false)
        val menu = composeMenu { if (inserted) VirtualSwingNode {} }
        val layout = RecordingMeasurementLayout()
        menu.layout = layout
        assertEquals(emptyList(), menu.components.toList())

        assertFailsWith<IllegalStateException> {
            inserted = true
            awaitIdle()
        }

        assertEquals(emptyList(), menu.components.toList())
        assertEquals(emptyList(), layout.declarations)
    }

    @ParameterizedTest(name = "declared at once: {0}")
    @MethodSource("layeredParentData")
    fun aGroupWithParentDataUnderALayeredPaneIsRefused(
        declaredAtOnce: Boolean,
        layerOfTheLabel: Int,
        componentCount: Int,
    ) = runComposeSwingTest {
        var declared by mutableStateOf(declaredAtOnce)
        val label = JLabel("a")
        val pane = JLayeredPane()
        assertFailsWith<IllegalStateException> {
            setContent {
                SwingNode(factory = { pane }) {
                    VirtualSwingNode(if (declared) SwingModifier.layoutConstraint(1) else SwingModifier) {
                        SwingNode(
                            factory = { label },
                            modifier = SwingModifier.layoutConstraint(JLayeredPane.PALETTE_LAYER),
                        )
                    }
                }
            }
            awaitIdle()
            declared = true
            awaitIdle()
        }

        assertEquals(componentCount, pane.componentCount)
        assertEquals(layerOfTheLabel, JLayeredPane.getLayer(label))
    }

    @Test
    fun anExistingSwingNodeInAGroupIsRefused() {
        val claimed = JLabel("claimed")
        val host = MeasuringHost().apply { add(claimed) }
        assertComposingFails<IllegalStateException> {
            setContent {
                SwingNode(factory = { host }) {
                    VirtualSwingNode { ExistingSwingNode<JPanel, _, JLabel>(claim = { getComponent(0) as JLabel }) }
                }
            }
            awaitIdle()
        }
        assertEquals(listOf(claimed), host.components.toList())
        assertEquals(emptyList(), host.recording.declarations)
    }

    @ParameterizedTest(name = "nested group: {0}")
    @ValueSource(booleans = [false, true])
    fun aComponentAddedToASingleChildHostDisplacesTheOneHeldThroughAGroup(nested: Boolean) = runComposeSwingTest {
        setContent {}
        val recomposer = Recomposer(Dispatchers.Swing)
        val viewport = JViewport()
        val held = JLabel("held")
        val added = JLabel("added")
        val composition =
            JPanel().setContent(recomposer) {
                SwingNode(factory = { viewport }) {
                    VirtualSwingNode {
                        if (nested) {
                            VirtualSwingNode { SwingNode(factory = { held }) }
                        } else {
                            SwingNode(factory = { held })
                        }
                    }
                    SwingNode(factory = { added })
                }
            }
        try {
            assertEquals(listOf(added), viewport.components.toList())
            assertEquals(null, held.parent)
            assertFailsWith<IllegalStateException> { awaitIdle() }
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }

    @Test
    fun aRefusedSecondGroupChildLeavesTheMeasuringHostUnchanged() {
        val host = MeasuringHost()
        var inserted by mutableStateOf(false)
        val refused = parentProtocolOf("another host") { false }
        assertComposingFails<IllegalStateException> {
            setContent {
                SwingNode(factory = { host }) {
                    Label("before")
                    if (inserted) {
                        VirtualSwingNode {
                            Label("accepted")
                            Label("refused", SwingModifier then Mark("refused", 1, false, refused))
                        }
                    }
                    Label("after")
                }
            }
            assertEquals(listOf("before", "after"), host.texts())
            host.recording.declarations.clear()

            try {
                inserted = true
                awaitIdle()
            } finally {
                assertEquals(listOf("before", "after"), host.texts())
                assertEquals(emptyList(), host.recording.declarations)
            }
        }
    }

    @ParameterizedTest(name = "accepted children before refusal: {0}")
    @MethodSource("refusalPositions")
    fun aRefusedGroupChangePublishesOnlyBeforeTheRefusedChildAndAllowsALaterChange(
        earlierChildren: Int,
        labels: List<String>,
        initial: List<List<String>>,
        beforeRefusal: List<List<String>>,
        changed: List<List<String>>,
    ) = runComposeSwingTest {
        val host = MeasuringHost("P")
        val accepted = protocolOf("P")
        val refused = protocolOf("Q")
        val parentContext = captureParentContext()
        var tag by mutableStateOf("A")
        val composition =
            OnDemandComposition(parentContext) {
                SwingNode(factory = { host }) {
                    VirtualSwingNode(
                        SwingModifier
                            .mark(tag, protocol = if (tag == "B") refused else accepted)
                            .mark(tag, slot = 2, protocol = accepted),
                    ) {
                        repeat(earlierChildren) { index ->
                            Label("earlier-$index", SwingModifier.mark("own", protocol = accepted))
                        }
                        Label("a")
                        Label("b")
                    }
                }
            }
        try {
            assertEquals(labels, host.texts())
            assertEquals(initial, host.components.map { host.recording.tagsOf(it) })

            host.recording.declarations.clear()
            assertFailsWith<IllegalStateException> { composition.recompose { tag = "B" } }
            assertEquals(labels, host.texts())
            assertEquals(beforeRefusal, host.components.map { host.recording.tagsOf(it) })
            assertEquals(beforeRefusal.take(earlierChildren), host.recording.declarations.map { it.tags })

            host.recording.declarations.clear()
            composition.recompose { tag = "C" }

            assertEquals(labels, host.texts())
            assertEquals(changed, host.components.map { host.recording.tagsOf(it) })
            assertEquals(changed, host.recording.declarations.map { it.tags })
            assertEquals(emptyList(), host.recording.removals)
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aRefusedOwnElementUnderAChangedGroupFailsThePassBeforeTheHostReceivesIt() = runComposeSwingTest {
        val host = MeasuringHost("P")
        val accepted = protocolOf("P")
        val refused = protocolOf("Q")
        val parentContext = captureParentContext()
        var group by mutableStateOf("A")
        var childRefused by mutableStateOf(false)
        val round by mutableStateOf(0)
        val composition =
            OnDemandComposition(parentContext) {
                SwingNode(factory = { host }) {
                    VirtualSwingNode(SwingModifier.mark(group, protocol = accepted)) {
                        Label(
                            "a$round",
                            if (childRefused) SwingModifier.mark("own", protocol = refused) else SwingModifier,
                        )
                        Label("b")
                    }
                }
            }
        try {
            assertEquals(listOf("a0", "b"), host.texts())

            host.recording.declarations.clear()
            assertFailsWith<IllegalStateException> {
                composition.recompose {
                    group = "B"
                    childRefused = true
                }
            }
            assertTrue(
                host.recording.declarations.none { "own" in it.tags },
                "the host never received the refused element",
            )
        } finally {
            composition.dispose()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("declarationsBeforeLayoutChange")
    fun reorderingKeyedChildrenRechecksDeclarationsAfterTheHostLayoutChanges(
        case: String,
        modifier: SwingModifier,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        val border = RecordingLayout(BorderLayout())
        var changed by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { host },
                update = { set(changed) { layout = if (it) border else host.recording } },
            ) {
                VirtualSwingNode(modifier) {
                    for (text in if (changed) listOf("b", "a") else listOf("a", "b")) {
                        key(text) { Label(text) }
                    }
                }
            }
        }
        assertEquals(listOf("a", "b"), host.texts())
        assertFailsWith<IllegalStateException> {
            changed = true
            awaitIdle()
        }

        assertEquals(border, host.layout, case)
        assertEquals(emptyList(), border.registrations, case)
    }

    @ParameterizedTest(name = "protocol conflict declared first: {0}")
    @ValueSource(booleans = [true, false])
    fun twoInvalidGroupChangesFailInDeclarationOrder(protocolConflictFirst: Boolean) {
        runComposeSwingTest {
            val conflictHost = MeasuringHost()
            val refusalHost = MeasuringHost()
            val conflictChild = JLabel("conflict")
            val refusalChild = JLabel("refusal")
            val otherMeasuringProtocol = parentProtocolOf("other measuring parent") { it is MeasuringHost }
            val refusedProtocol = parentProtocolOf("border parent") { it.layout is BorderLayout }
            var changed by mutableStateOf(false)
            setContent {
                for (conflict in if (protocolConflictFirst) listOf(true, false) else listOf(false, true)) {
                    if (conflict) {
                        SwingNode(factory = { conflictHost }) {
                            VirtualSwingNode(
                                SwingModifier then
                                    Data("group", 1, if (changed) otherMeasuringProtocol else MeasuringProtocol),
                            ) {
                                SwingNode(
                                    factory = { conflictChild },
                                    modifier = SwingModifier then Data("child", 2, MeasuringProtocol),
                                )
                            }
                        }
                    } else {
                        SwingNode(factory = { refusalHost }) {
                            VirtualSwingNode(
                                SwingModifier
                                    .mark("group", protocol = if (changed) refusedProtocol else MeasuringProtocol),
                            ) {
                                SwingNode(factory = { refusalChild })
                            }
                        }
                    }
                }
            }
            assertEquals(listOf(conflictChild), conflictHost.components.toList())
            assertEquals(listOf(refusalChild), refusalHost.components.toList())

            val failure =
                assertFails {
                    Snapshot.withMutableSnapshot { changed = true }
                    awaitIdle()
                }

            if (protocolConflictFirst) {
                assertEquals(IllegalArgumentException::class, failure::class)
            } else {
                assertEquals(IllegalStateException::class, failure::class)
            }
            assertEquals(listOf(conflictChild), conflictHost.components.toList())
            assertEquals(listOf(refusalChild), refusalHost.components.toList())
        }
    }

    @ParameterizedTest(name = "grouped: {0}")
    @ValueSource(booleans = [false, true])
    fun aRefusedDuplicateClaimLeavesTheContainerEmptyAndAllowsFollowingContent(grouped: Boolean) = runComposeSwingTest {
        val context = captureParentContext()
        val container = JPanel()
        val claimed = JLabel("claimed")
        val host = JPanel().apply { add(claimed) }
        // A refused first pass ends the recomposer it composed under, so it composes under one of its own.
        val refusing = Recomposer(Dispatchers.Swing)
        assertFailsWith<IllegalStateException> {
            container.setContent(refusing) {
                SwingNode(factory = { host }) {
                    if (grouped) {
                        VirtualSwingNode {
                            ExistingSwingNode<JPanel, _, JLabel>(claim = { claimed })
                            ExistingSwingNode<JPanel, _, JLabel>(claim = { claimed })
                        }
                    } else {
                        ExistingSwingNode<JPanel, _, JLabel>(claim = { claimed })
                        ExistingSwingNode<JPanel, _, JLabel>(claim = { claimed })
                    }
                }
            }
        }
        assertEquals(emptyList(), container.components.toList())
        assertEquals(listOf(claimed), host.components.toList())
        refusing.cancel()

        val following =
            container.setContent(context) {
                SwingNode(factory = { host }) {
                    ExistingSwingNode<JPanel, _, JLabel>(claim = { claimed })
                    if (grouped) {
                        VirtualSwingNode { Label("following") }
                    } else {
                        Label("following")
                    }
                }
            }
        try {
            awaitIdle()
            assertEquals(listOf(host), container.components.toList())
            assertEquals(listOf("following", "claimed"), host.components.texts())
        } finally {
            following.dispose()
        }
        assertEquals(emptyList(), container.components.toList())
    }

    @ParameterizedTest(name = "parent data: {0}")
    @ValueSource(booleans = [false, true])
    fun relocatingGroupsRefusesDeclarationsInCompositionOrder(parentData: Boolean) = runComposeSwingTest {
        val departing = MeasuringHost()
        val arriving = JPanel()
        var moved by mutableStateOf(false)
        setContent {
            val refused =
                remember {
                    movableContentOf {
                        VirtualSwingNode(
                            if (parentData) {
                                SwingModifier then Data("group", 1, MeasuringProtocol)
                            } else {
                                SwingModifier.mark("group")
                            },
                        ) { Label("refused") }
                    }
                }
            val accepted = remember { movableContentOf { VirtualSwingNode { Label("accepted") } } }
            SwingNode(factory = { departing }) {
                if (!moved) {
                    refused()
                    accepted()
                }
            }
            SwingNode(factory = { arriving }) {
                Label("before")
                if (moved) {
                    refused()
                    accepted()
                }
                Label("after")
            }
        }
        assertEquals(listOf("refused", "accepted"), departing.components.texts())
        assertEquals(listOf("before", "after"), arriving.components.texts())

        assertFailsWith<IllegalStateException> {
            moved = true
            awaitIdle()
        }

        assertEquals(emptyList(), departing.components.toList())
        assertEquals(listOf("before", "after"), arriving.components.texts())
    }

    @Test
    fun relocatedChildrenOfDifferentParentsAttachInCompositionOrder() = runComposeSwingTest {
        val departing = MeasuringHost("source")
        val arriving = MeasuringHost()
        val inner = MeasuringHost()
        var moved by mutableStateOf(false)
        setContent {
            val first = remember { movableContentOf { Label("first") } }
            val nested = remember { movableContentOf { Label("nested") } }
            val refused =
                remember {
                    movableContentOf {
                        Label("refused", SwingModifier.mark("refused", protocol = protocolOf("source")))
                    }
                }
            SwingNode(factory = { departing }) {
                if (!moved) {
                    first()
                    nested()
                    refused()
                }
            }
            SwingNode(factory = { arriving }) {
                if (moved) first()
                SwingNode(factory = { inner }) { if (moved) nested() }
                if (moved) refused()
            }
        }

        assertFailsWith<IllegalStateException> {
            moved = true
            awaitIdle()
        }

        assertEquals(listOf("nested"), inner.texts())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("claimRelocationOrders")
    fun relocatingAGroupWithAClaimedChildRefusesBeforeAttachingAnyMember(
        case: String,
        composeSource: @Composable (@Composable () -> Unit, @Composable () -> Unit) -> Unit,
    ) = runComposeSwingTest {
        val claimed = JLabel("claimed")
        val ordinary = JLabel("ordinary")
        val before = JLabel("before")
        val after = JLabel("after")
        val first = JPanel().apply { add(claimed) }
        val second = JPanel()
        var moved by mutableStateOf(false)
        setContent {
            val claim = remember { movableContentOf { ExistingSwingNode<JPanel, _, JLabel>(claim = { claimed }) } }
            val group =
                remember {
                    movableContentOf<@Composable () -> Unit> { content ->
                        VirtualSwingNode {
                            SwingNode(factory = { ordinary })
                            content()
                        }
                    }
                }
            SwingNode(factory = { first }) {
                if (!moved) composeSource({ group {} }, claim)
            }
            SwingNode(factory = { second }) {
                SwingNode(factory = { before })
                if (moved) group { claim() }
                SwingNode(factory = { after })
            }
        }
        assertEquals(listOf(ordinary, claimed), first.components.toList(), case)
        assertEquals(listOf(before, after), second.components.toList(), case)

        assertFailsWith<IllegalStateException> {
            moved = true
            awaitIdle()
        }

        assertEquals(listOf(claimed), first.components.toList(), case)
        assertEquals(listOf(before, after), second.components.toList(), case)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun aStyleThatReplacesTheNodesOwnRefusedElementIsAccepted(grouped: Boolean) = runComposeSwingTest {
        val host = MeasuringHost("P")
        setContent {
            StyledHost(host, "s", ReplacingMarkProperty) {
                val label: @Composable () -> Unit = { ProbedLabel(SwingModifier.mark("o", protocol = protocolOf("Q"))) }
                if (grouped) VirtualSwingNode { label() } else label()
            }
        }

        assertEquals(listOf(listOf("s")), host.components.map { host.recording.tagsOf(it) }, "grouped=$grouped")
    }

    companion object {
        @JvmStatic
        fun refusedModifiers(): List<Arguments> {
            val argument = IllegalArgumentException::class
            val state = IllegalStateException::class
            val north = with(PanelLayout.Border().contentScope) { SwingModifier.north() }
            return listOf(
                Arguments.of(Named.of("a property", SwingModifier.testTag("x")), argument),
                Arguments.of(Named.of("a listener", SwingModifier.containerListener {}), argument),
                Arguments.of(
                    Named.of("a slot", SwingModifier.slot(parentProtocolOf("slot parent") { true }, "region")),
                    argument,
                ),
                Arguments.of(Named.of("a parent-layout node", SwingModifier then NodeMark("x")), argument),
                Arguments.of(Named.of("a component node", SwingModifier.onKeyStroke("ctrl S") {}), argument),
                Arguments.of(
                    Named.of(
                        "an additive inheritable element",
                        SwingModifier then
                            object : ParentLayoutElement {
                                override val parentProtocol: ParentProtocol get() = MeasuringProtocol
                                override val additive: Boolean get() = true
                                override val inheritable: Boolean get() = true
                            },
                    ),
                    argument,
                ),
                Arguments.of(Named.of("a declaration the host refuses", north), state),
            )
        }

        private fun inSlots(
            placement: ChildPlacement,
            group: @Composable () -> Unit,
        ): suspend ComposeSwingTest.(Container) -> Unit = { host ->
            setContent { SwingNode(factory = { host }, childPlacement = placement) { group() } }
            awaitIdle()
        }

        // An on-demand composition places its top-level component in a region of its root.
        private fun atRoot(group: @Composable () -> Unit): suspend ComposeSwingTest.(Container) -> Unit = { host ->
            OnDemandComposition(
                captureParentContext(),
                host,
                SlotAttachment { parent, child, _ ->
                    parent.add(child)
                    return@SlotAttachment { parent.remove(child) }
                },
                group,
            ).dispose()
        }

        private val populated = inSlots(ChildPlacement.Slots("content")) { VirtualSwingNode { Label("a") } }
        private val empty = inSlots(ChildPlacement.Slots("content")) { VirtualSwingNode {} }
        private val ordered = inSlots(ChildPlacement.OrderedSlots("content")) { VirtualSwingNode {} }
        private val root = atRoot { VirtualSwingNode { Label("a") } }
        private val emptyRoot = atRoot { VirtualSwingNode {} }

        private val inserted: suspend ComposeSwingTest.(Container) -> Unit = { host ->
            var present by mutableStateOf(false)
            setContent {
                SwingNode(factory = { host }, childPlacement = ChildPlacement.Slots("content")) {
                    if (present) VirtualSwingNode {}
                }
            }
            present = true
            awaitIdle()
        }

        private val relocated: suspend ComposeSwingTest.(Container) -> Unit = { host ->
            var moved by mutableStateOf(false)
            setContent {
                val group = remember { movableContentOf { VirtualSwingNode {} } }
                Panel { if (!moved) group() }
                SwingNode(factory = { host }, childPlacement = ChildPlacement.Slots("content")) {
                    if (moved) group()
                }
            }
            moved = true
            awaitIdle()
        }

        @JvmStatic
        fun regionHosts(): List<Arguments> {
            val scrollPane: (RecordingMeasurementLayout) -> Container = { JScrollPane() }
            val panel: (RecordingMeasurementLayout) -> Container = { JPanel(it) }
            return listOf(
                Arguments.of(scrollPane, Named.of("JScrollPane", populated)),
                Arguments.of(panel, Named.of("Container", root)),
                Arguments.of(scrollPane, Named.of("initial empty group in a scroll pane", empty)),
                Arguments.of(scrollPane, Named.of("empty group inserted into a scroll pane", inserted)),
                Arguments.of(scrollPane, Named.of("empty group relocated into a scroll pane", relocated)),
                Arguments.of(panel, Named.of("empty group in named slots", empty)),
                Arguments.of(panel, Named.of("empty group in ordered slots", ordered)),
                Arguments.of(panel, Named.of("empty group at a composition root", emptyRoot)),
            )
        }

        @JvmStatic
        fun layeredParentData(): List<Arguments> = listOf(Arguments.of(true, 0, 0), Arguments.of(false, 100, 1))

        @JvmStatic
        fun refusalPositions(): List<Arguments> = listOf(
            Arguments.of(
                0,
                listOf("a", "b"),
                listOf(listOf("A", "A"), listOf("A", "A")),
                listOf(listOf("A", "A"), listOf("A", "A")),
                listOf(listOf("C", "C"), listOf("C", "C")),
            ),
            Arguments.of(
                1,
                listOf("earlier-0", "a", "b"),
                listOf(listOf("A", "own"), listOf("A", "A"), listOf("A", "A")),
                listOf(listOf("B", "own"), listOf("A", "A"), listOf("A", "A")),
                listOf(listOf("C", "own"), listOf("C", "C"), listOf("C", "C")),
            ),
            Arguments.of(
                2,
                listOf("earlier-0", "earlier-1", "a", "b"),
                listOf(listOf("A", "own"), listOf("A", "own"), listOf("A", "A"), listOf("A", "A")),
                listOf(listOf("B", "own"), listOf("B", "own"), listOf("A", "A"), listOf("A", "A")),
                listOf(listOf("C", "own"), listOf("C", "own"), listOf("C", "C"), listOf("C", "C")),
            ),
        )

        @JvmStatic
        fun declarationsBeforeLayoutChange(): List<Arguments> = listOf(
            Arguments.of("layout element", SwingModifier.mark("G")),
            Arguments.of("parent data only", SwingModifier then Data("G", 1, MeasuringProtocol)),
        )

        @JvmStatic
        fun claimRelocationOrders(): List<Arguments> {
            val claimFirst: @Composable (@Composable () -> Unit, @Composable () -> Unit) -> Unit = { group, claim ->
                claim()
                group()
            }
            val groupFirst: @Composable (@Composable () -> Unit, @Composable () -> Unit) -> Unit = { group, claim ->
                group()
                claim()
            }
            return listOf(
                Arguments.of("claim record first", claimFirst),
                Arguments.of("group record first", groupFirst),
            )
        }
    }
}

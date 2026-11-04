package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.OnDemandComposition
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.captureParentContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unsupported modifiers, hosts, and claimed children are refused; refused declarations preserve accepted state and
 * allow subsequent valid updates; relocation validates and attaches children in composition order across their hosts.
 */
class VirtualSwingNodeRefusalTest {
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

    @ParameterizedTest(name = "grouped: {0}")
    @ValueSource(booleans = [false, true])
    fun aRefusedDuplicateClaimLeavesTheContainerEmptyAndAllowsFollowingContent(grouped: Boolean) = runComposeSwingTest {
        val context = captureParentContext()
        val container = JPanel()
        val claimed = JLabel("claimed")
        val host = JPanel().apply { add(claimed) }
        assertFailsWith<IllegalStateException> {
            container.setContent(context) {
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

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.desktop.LayeredPaneScope
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.PlainProbeContainer
import org.jetbrains.compose.swing.style.Probes
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Groups keep component order, parking, styles, and layer placement for the components they hold. */
class VirtualSwingNodeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("groupChangeCases")
    fun componentsOfGroupsAreInCompositionOrder(
        case: String,
        content: @Composable (Boolean) -> Unit,
        before: List<String>,
        after: List<String>,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        root.layout = RecordingMeasurementLayout(FlowLayout())
        var inserted by mutableStateOf(false)
        setContent {
            content(inserted)
            SwingNode(factory = { host }) { content(inserted) }
        }
        host.assertLabels(before, case)
        assertEquals(before, root.components.filterIsInstance<JLabel>().map { it.text }, case)

        inserted = true
        awaitIdle()

        host.assertLabels(after, case)
        assertEquals(after, root.components.filterIsInstance<JLabel>().map { it.text }, case)
    }

    @Test
    fun reorderingKeyedGroupsMovesTheirComponents() = runComposeSwingTest {
        val host = MeasuringHost()
        var reversed by mutableStateOf(false)
        setContent {
            SwingNode(factory = { host }) {
                for (id in if (reversed) listOf(2, 1) else listOf(1, 2)) {
                    key(id) {
                        VirtualSwingNode(SwingModifier.mark("G$id")) {
                            Label("g$id-a")
                            Label("g$id-b")
                        }
                    }
                }
            }
        }
        val identities = host.components.toList()

        reversed = true
        awaitIdle()

        assertEquals(listOf("g2-a", "g2-b", "g1-a", "g1-b"), host.texts())
        assertEquals(identities.slice(listOf(2, 3, 0, 1)), host.components.toList())
        assertEquals(
            listOf(listOf("G2"), listOf("G2"), listOf("G1"), listOf("G1")),
            host.components.map { host.recording.tagsOf(it) },
        )
    }

    @Test
    fun aChangeInsideAGroupSettlesTheHost() = runComposeSwingTest {
        val host = MeasuringHost()
        var more by mutableStateOf(false)
        setContent {
            SwingNode(factory = { host }, update = {
                reconcileWithChildren {
                    host.name =
                        host.components.joinToString { (it as JLabel).text }
                }
            }) {
                VirtualSwingNode(SwingModifier.mark("G")) {
                    Label("a")
                    if (more) Label("b")
                }
            }
        }
        onNodeOfType<MeasuringHost>().assertProperty("a") { name }

        more = true
        awaitIdle()

        onNodeOfType<MeasuringHost>().assertProperty("a, b") { name }
        onNodeWithText("b").assertProperty(listOf("G")) { host.recording.tagsOf(this) }
    }

    @Test
    fun inspectionSeesComponentsOfAGroupAsTheHostsChildren() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        try {
            setContent {
                SwingNode(factory = { JPanel() }, modifier = SwingModifier.testTag("host")) {
                    VirtualSwingNode { SwingNode(factory = { JLabel("a") }) }
                }
            }
            onNodeWithTag("host").assertTreeMatches(JPanel().apply { add(JLabel("a")) })
        } finally {
            isDebugInspectorInfoEnabled = false
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parkings")
    fun aParkedGroupIsNotAttachedAgain(
        case: String,
        slot: @Composable (Boolean) -> Unit,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        var parked by mutableStateOf(false)
        var inserted by mutableStateOf(false)
        var swapped by mutableStateOf(false)
        setContent {
            SwingNode(factory = { host }) {
                if (inserted) Label("n")
                for (id in if (swapped) listOf("q", "G", "p") else listOf("p", "G", "q")) {
                    key(id) { if (id == "G") slot(parked) else Label(id) }
                }
            }
        }
        host.assertLabels(listOf("p", "g1", "g2", "q"), "$case: mounted")

        parked = true
        awaitIdle()
        host.assertLabels(listOf("p", "q"), "$case: parked")

        inserted = true
        swapped = true
        awaitIdle()
        host.assertLabels(listOf("n", "q", "p"), "$case: changed while parked")

        parked = false
        awaitIdle()
        host.assertLabels(listOf("n", "q", "g1", "g2", "p"), "$case: reactivated")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("providedAroundOrInside")
    fun componentDefaultsReachComponentsInAGroupUnchanged(
        case: String,
        content: @Composable (Boolean) -> Unit,
    ) = runComposeSwingTest {
        var enabled by mutableStateOf(false)
        setContent { content(enabled) }

        onNodeOfType<JButton>().assertProperty(false, "$case: initial default") { isEnabled }
        enabled = true
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(true, "$case: changed default") { isEnabled }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nestedStyleHosts")
    fun aNestedStyleProvidedByTheHostReachesComponentsInAGroup(
        case: String,
        styledFromTheStart: Boolean,
        nested: Boolean,
        expected: List<String>,
        beforeStyle: List<String>,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        val label = JLabel("p")
        var styled by mutableStateOf(styledFromTheStart)
        setContent {
            SwingNode(
                factory = { host },
                modifier =
                    if (styled) {
                        SwingModifier.styleable(PlainProbeContainer) {
                            provideNestedStyle(Probes) { MarkProperty.provide("s") }
                        }
                    } else {
                        SwingModifier
                    },
            ) {
                VirtualSwingNode(SwingModifier.mark("g")) {
                    if (nested) {
                        VirtualSwingNode(SwingModifier.mark("h", slot = 4)) { ProbedLabel(label = label) }
                    } else {
                        ProbedLabel(label = label)
                    }
                }
            }
        }
        if (!styledFromTheStart) {
            assertEquals(beforeStyle, host.recording.tagsOf(label), "$case: before the style")
            styled = true
            awaitIdle()
        }

        assertEquals(expected, host.recording.tagsOf(label), case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("styleRelocationOrders")
    fun restyleOnMoveReachesComponentsInAGroup(
        case: String,
        destinationFirst: Boolean,
    ) = runComposeSwingTest {
        val source = MeasuringHost()
        val destination = MeasuringHost()
        val label = JLabel("p")
        var moved by mutableStateOf(false)
        setContent {
            val group =
                remember {
                    movableContentOf { VirtualSwingNode(SwingModifier.mark("g")) { ProbedLabel(label = label) } }
                }
            val fromHost: @Composable () -> Unit = { FixedStyledHost(source, "a") { if (!moved) group() } }
            val toHost: @Composable () -> Unit = {
                FixedStyledHost(destination, "b") { if (moved) group() }
            }
            if (destinationFirst) {
                toHost()
                fromHost()
            } else {
                fromHost()
                toHost()
            }
        }
        assertEquals(listOf("g", "a"), source.recording.tagsOf(label), "$case: before the move")
        source.recording.declarations.clear()

        moved = true
        awaitIdle()

        assertEquals(listOf("g", "b"), destination.recording.tagsOf(label), case)
        assertEquals(emptyList(), source.recording.declarations, case)
    }

    @Test
    fun aStyleThatReplacesARefusedGroupKeyInTheSamePassIsAccepted() = runComposeSwingTest {
        val host = MeasuringHost("P")
        val accepted = protocolOf("P")
        val refused = protocolOf("Q")
        var changed by mutableStateOf(false)
        setContent {
            StyledHost(
                host,
                "s1",
                MarkProperty,
                modifier =
                    if (changed) {
                        SwingModifier.styleable(PlainProbeContainer) {
                            provideNestedStyle(Probes) { ReplacingMarkProperty.provide(LocalMark.currentValue) }
                        }
                    } else {
                        SwingModifier
                    },
            ) {
                VirtualSwingNode(SwingModifier.mark("g", protocol = if (changed) refused else accepted)) {
                    ProbedLabel(SwingModifier.mark(if (changed) "o1" else "o0", slot = 3))
                }
            }
        }
        assertEquals(listOf(listOf("g", "o0")), host.components.map { host.recording.tagsOf(it) })

        changed = true
        awaitIdle()

        assertEquals(listOf(listOf("o1", "s1")), host.components.map { host.recording.tagsOf(it) })
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layeredGroups")
    fun groupsUnderALayeredPaneKeepTheirDepthOrder(
        case: String,
        group: @Composable LayeredPaneScope.(Boolean) -> Unit,
        before: List<List<String>>,
        after: List<List<String>>,
    ) = runComposeSwingTest {
        var changed by mutableStateOf(false)
        setContent {
            LayeredPane {
                Label("l1", SwingModifier.layer(1))
                Label("h1", SwingModifier.layer(2))
                VirtualSwingNode { group(changed) }
                Label("l4", SwingModifier.layer(1))
                Label("h4", SwingModifier.layer(2))
            }
        }
        onNodeOfType<JLayeredPane>().assertProperty(before, "$case: before") {
            listOf(getComponentsInLayer(1).texts(), getComponentsInLayer(2).texts())
        }
        changed = true
        awaitIdle()

        onNodeOfType<JLayeredPane>().assertProperty(after, "$case: after") {
            listOf(getComponentsInLayer(1).texts(), getComponentsInLayer(2).texts())
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupRelocationLayers")
    fun relocatingNestedGroupsCountsOnlyCompletedAttachmentsOnEachLayer(
        case: String,
        initiallyBefore: Boolean,
        initial: List<String>,
        relocated: List<String>,
    ) = runComposeSwingTest {
        val pane = JLayeredPane()
        var before by mutableStateOf(initiallyBefore)
        setContent {
            val group =
                remember {
                    movableContentOf {
                        VirtualSwingNode {
                            VirtualSwingNode {
                                Label("first", SwingModifier.layoutConstraint(1))
                                Label("high", SwingModifier.layoutConstraint(2))
                                Label("second", SwingModifier.layoutConstraint(1))
                            }
                        }
                    }
                }
            SwingNode(factory = { pane }) {
                if (before) group()
                Label("before", SwingModifier.layoutConstraint(1))
                if (!before) group()
                Label("after", SwingModifier.layoutConstraint(1))
                Label("high-after", SwingModifier.layoutConstraint(2))
            }
        }
        assertEquals(initial, pane.getComponentsInLayer(1).texts(), case)
        assertEquals(listOf("high", "high-after"), pane.getComponentsInLayer(2).texts(), case)

        before = !initiallyBefore
        awaitIdle()
        assertEquals(relocated, pane.getComponentsInLayer(1).texts(), case)
        assertEquals(listOf("high", "high-after"), pane.getComponentsInLayer(2).texts(), case)
        assertEquals(6, pane.componentCount, case)

        before = initiallyBefore
        awaitIdle()
        assertEquals(initial, pane.getComponentsInLayer(1).texts(), case)
        assertEquals(listOf("high", "high-after"), pane.getComponentsInLayer(2).texts(), case)
        assertEquals(6, pane.componentCount, case)
    }

    companion object {
        @JvmStatic
        fun groupChangeCases(): List<Arguments> {
            val removedGroup: @Composable (Boolean) -> Unit = { removed ->
                Label("p")
                if (!removed) {
                    VirtualSwingNode(SwingModifier.mark("G")) {
                        Label("g1")
                        Label("g2")
                    }
                }
                Label("q")
            }
            return insertionCases() +
                Arguments.of("removing a group", removedGroup, listOf("p", "g1", "g2", "q"), listOf("p", "q"))
        }

        private fun insertionCases(): List<Arguments> {
            val middle: @Composable (Boolean) -> Unit = { inserted ->
                Label("p")
                VirtualSwingNode(SwingModifier.mark("G")) {
                    Label("g1")
                    if (inserted) Label("g2")
                    Label("g3")
                }
                Label("q")
            }
            val emptyGroup: @Composable (Boolean) -> Unit = { inserted ->
                Label("p")
                VirtualSwingNode(SwingModifier.mark("G")) { if (inserted) Label("g1") }
                Label("q")
            }
            val groupEnd: @Composable (Boolean) -> Unit = { inserted ->
                VirtualSwingNode(SwingModifier.mark("G")) {
                    Label("g1")
                    if (inserted) Label("g2")
                }
                Label("q")
            }
            val nested: @Composable (Boolean) -> Unit = { inserted ->
                Label("p")
                VirtualSwingNode(SwingModifier.mark("G")) {
                    Label("g0")
                    VirtualSwingNode(SwingModifier.mark("G")) {
                        Label("g1")
                        if (inserted) Label("g2")
                    }
                    Label("g3")
                }
            }
            val newGroup: @Composable (Boolean) -> Unit = { inserted ->
                Label("p")
                if (inserted) VirtualSwingNode(SwingModifier.mark("G")) { Label("g1") }
                Label("q")
            }
            return listOf(
                Arguments.of(
                    "into the middle of a group",
                    middle,
                    listOf("p", "g1", "g3", "q"),
                    listOf("p", "g1", "g2", "g3", "q"),
                ),
                Arguments.of("into an empty group", emptyGroup, listOf("p", "q"), listOf("p", "g1", "q")),
                Arguments.of("at the end of a group", groupEnd, listOf("g1", "q"), listOf("g1", "g2", "q")),
                Arguments.of(
                    "under nested groups",
                    nested,
                    listOf("p", "g0", "g1", "g3"),
                    listOf("p", "g0", "g1", "g2", "g3"),
                ),
                Arguments.of("a new group after a component", newGroup, listOf("p", "q"), listOf("p", "g1", "q")),
            )
        }

        @JvmStatic
        fun parkings(): List<Arguments> {
            val inactiveHost: @Composable (Boolean) -> Unit = { parked ->
                ReusableContentHost(active = !parked) { Group("G") }
            }
            val unusedMovable: @Composable (Boolean) -> Unit = { parked ->
                val group = remember { movableContentOf { Group("G") } }
                if (!parked) group()
            }
            return listOf(
                Arguments.of("an inactive reusable host", inactiveHost),
                Arguments.of("a movable content no longer composed", unusedMovable),
            )
        }

        @JvmStatic
        fun providedAroundOrInside(): List<Arguments> {
            val around: @Composable (Boolean) -> Unit = { enabled ->
                Panel(style = { provideNestedStyle(AllComponents) { enabled(enabled) } }) {
                    VirtualSwingNode { Button("b", onClick = {}) }
                }
            }
            val inside: @Composable (Boolean) -> Unit = { enabled ->
                VirtualSwingNode {
                    Panel(style = { provideNestedStyle(AllComponents) { enabled(enabled) } }) {
                        Button("b", onClick = {})
                    }
                }
            }
            return listOf(Arguments.of("around the group", around), Arguments.of("inside the group", inside))
        }

        @JvmStatic
        fun nestedStyleHosts(): List<Arguments> = listOf(
            Arguments.of("styled from the start", true, false, listOf("g", "s"), listOf("g")),
            Arguments.of("styled later", false, false, listOf("g", "s"), listOf("g")),
            Arguments.of(
                "styled from the start, in nested groups",
                true,
                true,
                listOf("g", "h", "s"),
                listOf("g", "h"),
            ),
            Arguments.of("styled later, in nested groups", false, true, listOf("g", "h", "s"), listOf("g", "h")),
        )

        @JvmStatic
        fun styleRelocationOrders(): List<Arguments> = listOf(
            Arguments.of("destination composed first", true),
            Arguments.of("destination composed last", false),
        )

        @JvmStatic
        fun layeredGroups(): List<Arguments> {
            val insert: @Composable LayeredPaneScope.(Boolean) -> Unit = { inserted ->
                Label("l2", SwingModifier.layer(1))
                Label("h2", SwingModifier.layer(2))
                if (inserted) {
                    Label("l3", SwingModifier.layer(1))
                    Label("h3", SwingModifier.layer(2))
                }
            }
            val move: @Composable LayeredPaneScope.(Boolean) -> Unit = { reversed ->
                for (id in if (reversed) listOf(3, 2) else listOf(2, 3)) {
                    key(id) {
                        Label("l$id", SwingModifier.layer(1))
                        Label("h$id", SwingModifier.layer(2))
                    }
                }
            }
            val remove: @Composable LayeredPaneScope.(Boolean) -> Unit = { removed ->
                Label("l2", SwingModifier.layer(1))
                Label("h2", SwingModifier.layer(2))
                if (!removed) {
                    Label("l3", SwingModifier.layer(1))
                    Label("h3", SwingModifier.layer(2))
                }
            }
            return listOf(
                Arguments.of(
                    "insert",
                    insert,
                    listOf(listOf("l1", "l2", "l4"), listOf("h1", "h2", "h4")),
                    listOf(listOf("l1", "l2", "l3", "l4"), listOf("h1", "h2", "h3", "h4")),
                ),
                Arguments.of(
                    "move",
                    move,
                    listOf(listOf("l1", "l2", "l3", "l4"), listOf("h1", "h2", "h3", "h4")),
                    listOf(listOf("l1", "l3", "l2", "l4"), listOf("h1", "h3", "h2", "h4")),
                ),
                Arguments.of(
                    "removal",
                    remove,
                    listOf(listOf("l1", "l2", "l3", "l4"), listOf("h1", "h2", "h3", "h4")),
                    listOf(listOf("l1", "l2", "l4"), listOf("h1", "h2", "h4")),
                ),
            )
        }

        @JvmStatic
        fun groupRelocationLayers(): List<Arguments> = listOf(
            Arguments.of(
                "relocate before the same-layer sibling",
                false,
                listOf("before", "first", "second", "after"),
                listOf("first", "second", "before", "after"),
            ),
            Arguments.of(
                "relocate after the same-layer sibling",
                true,
                listOf("first", "second", "before", "after"),
                listOf("before", "first", "second", "after"),
            ),
        )
    }
}

/** Provides a fixed nested style without observing a composition local. */
@Composable
private fun FixedStyledHost(
    host: JPanel,
    style: String,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMark provides style) {
        SwingNode(
            factory = { JPanel() },
            modifier =
                SwingModifier.styleable(PlainProbeContainer) {
                    provideNestedStyle(Probes) { MarkProperty.provide(style) }
                },
        ) {
            SwingNode(factory = { host }, content = content)
        }
    }
}

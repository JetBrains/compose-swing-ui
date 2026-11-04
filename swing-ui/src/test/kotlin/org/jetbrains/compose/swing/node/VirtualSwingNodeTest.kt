package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.LayoutDeclaration
import org.jetbrains.compose.swing.OnDemandComposition
import org.jetbrains.compose.swing.RecordingLayout
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.assumeKeyboardFocusIsPossible
import org.jetbrains.compose.swing.assumeWindowBecomesFocused
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.desktop.LayeredPaneScope
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.defaults.DefaultEnabled
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.ProvidedComponentDefault
import org.jetbrains.compose.swing.defaults.componentDefaultKeyOf
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.FocusRequester
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.focusRequester
import org.jetbrains.compose.swing.modifier.interaction.rememberFocusRequester
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.modifier.listener.containerListener
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JViewport
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Where [VirtualSwingNode] places the components of its content: among the indexed children of the container it
 * is composed in, in composition order, through inserts, moves, removals, relocation and parking, without being
 * a component or a node the inspection and the test harness see.
 *
 * A measuring host is a [MeasuringHost]; "receives" means the elements its layout last recorded for a child.
 * The labels of a group start with `g` in the cases that mix grouped and ungrouped children.
 */
class VirtualSwingNodeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("insertionCases")
    fun componentsOfGroupsAreInCompositionOrder(
        case: String,
        content: @Composable (Boolean) -> Unit,
        before: List<String>,
        after: List<String>,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        var inserted by mutableStateOf(false)
        setContent { Host(host) { content(inserted) } }
        host.assertLabels(before, case)

        inserted = true
        awaitIdle()

        host.assertLabels(after, case)
    }

    @Test
    fun reorderingKeyedGroupsMovesTheirComponents() = runComposeSwingTest {
        val host = MeasuringHost()
        var reversed by mutableStateOf(false)
        setContent {
            Host(host) {
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
    fun removingAGroupRemovesItsComponents() = runComposeSwingTest {
        val host = MeasuringHost()
        var present by mutableStateOf(true)
        setContent {
            Host(host) {
                Label("p")
                if (present) {
                    VirtualSwingNode(SwingModifier.mark("G")) {
                        Label("g1")
                        Label("g2")
                    }
                }
                Label("q")
            }
        }
        onNodeWithText("g1").assertProperty(listOf("G")) { host.recording.tagsOf(this) }
        onNodeWithText("g2").assertProperty(listOf("G")) { host.recording.tagsOf(this) }

        present = false
        awaitIdle()

        assertEquals(listOf("p", "q"), host.texts())
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
        setContent { LayeredCase(changed, group) }
        onNodeOfType<JLayeredPane>().assertProperty(before, "$case: before") {
            listOf(getComponentsInLayer(1).texts(), getComponentsInLayer(2).texts())
        }
        changed = true
        awaitIdle()

        onNodeOfType<JLayeredPane>().assertProperty(after, "$case: after") {
            listOf(getComponentsInLayer(1).texts(), getComponentsInLayer(2).texts())
        }
    }

    @Test
    fun movableContentMovesAGroupBetweenHosts() = runComposeSwingTest {
        val first = MeasuringHost()
        val second = MeasuringHost()
        var inSecond by mutableStateOf(false)
        setContent {
            val group = remember { movableContentOf { Group("G") } }
            Host(first) {
                Label("a0")
                if (!inSecond) group()
            }
            Host(second) {
                Label("b0")
                if (inSecond) group()
                Label("b1")
            }
        }
        first.assertLabels(listOf("a0", "g1", "g2"), "before")

        inSecond = true
        awaitIdle()

        first.assertLabels(listOf("a0"), "the old host")
        second.assertLabels(listOf("b0", "g1", "g2", "b1"), "the new host")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("relocationOrders")
    fun movableContentMovesAGroupAndAComponentIntoItInOnePass(
        case: String,
        labelFirst: Boolean,
        destination: @Composable (@Composable () -> Unit) -> Unit,
        expected: List<String>,
    ) = runComposeSwingTest {
        val first = MeasuringHost()
        val second = MeasuringHost()
        var moved by mutableStateOf(false)
        setContent {
            val label = remember { movableContentOf { Label("m") } }
            val group =
                remember {
                    movableContentOf<@Composable () -> Unit> { inner ->
                        VirtualSwingNode(SwingModifier.mark("G")) {
                            destination(inner)
                        }
                    }
                }
            Host(first) {
                if (!moved) {
                    if (labelFirst) label()
                    group {}
                    if (!labelFirst) label()
                }
            }
            Host(second) { if (moved) group { label() } }
        }

        moved = true
        awaitIdle()

        first.assertLabels(emptyList(), "the old host")
        assertEquals(expected, second.texts(), case)
        assertEquals(listOf(listOf("G"), listOf("G")), second.components.map { second.recording.tagsOf(it) })
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("relocatedGroupChanges")
    fun changesInsideARelocatedGroupApplyWhenItIsPlaced(
        case: String,
        after: List<Int>,
        siblings: @Composable (Boolean) -> Unit,
        expected: List<String>,
    ) = runComposeSwingTest {
        val first = MeasuringHost()
        val second = MeasuringHost()
        var moved by mutableStateOf(false)
        var ids by mutableStateOf(listOf(1, 2, 3))
        setContent {
            val group =
                remember {
                    movableContentOf<List<Int>> { current ->
                        VirtualSwingNode(SwingModifier.mark("G")) {
                            for (id in current) key(id) { Label("g$id") }
                        }
                    }
                }
            Host(first) { if (!moved) group(ids) }
            Host(second) {
                if (moved) group(ids)
                siblings(moved)
            }
        }

        Snapshot.withMutableSnapshot {
            moved = true
            ids = after
        }
        awaitIdle()

        first.assertLabels(emptyList(), "the old host")
        second.assertLabels(expected, case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("relocatedDescendantChanges")
    fun descendantsOfARelocatedGroupAreAttachedOnlyAtTheirDestination(
        case: String,
        content: @Composable (Boolean) -> Unit,
        before: List<String>,
        after: List<String>,
        parentData: List<Any?>,
    ) = runComposeSwingTest {
        val departing = MeasuringHost("P")
        val arriving = MeasuringHost("Q")
        var moved by mutableStateOf(false)
        setContent {
            val group = remember { movableContentOf<Boolean> { changed -> content(changed) } }
            Host(departing) { if (!moved) group(false) }
            Host(arriving) { if (moved) group(true) }
        }
        assertEquals(before, departing.texts(), case)
        departing.recording.declarations.clear()
        arriving.recording.registrations.clear()

        moved = true
        awaitIdle()

        assertEquals(emptyList(), departing.texts(), case)
        assertEquals(after, arriving.texts(), case)
        assertEquals(emptyList(), departing.recording.declarations, case)
        assertEquals(after, arriving.recording.registrations.map { (it.component as JLabel).text }, case)
        assertEquals(parentData, arriving.components.map { arriving.recording.parentDataOf(it) }, case)
        assertEquals(emptyList(), arriving.recording.removals, case)
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
            Host(host) {
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

    @ParameterizedTest(name = "nested groups: {0}")
    @ValueSource(booleans = [false, true])
    fun structuralMutationsUpdateTheHostsComponentOrder(nested: Boolean) = runComposeSwingTest {
        val host = JPanel()
        var ids by mutableStateOf(listOf(1, 2))
        setContent {
            SwingNode(factory = { host }) {
                Label("before")
                VirtualSwingNode {
                    Label("prefix")
                    val children: @Composable () -> Unit = {
                        for (id in ids) key(id) { Label("g$id") }
                    }
                    if (nested) VirtualSwingNode(content = children) else children()
                }
                Label("after")
            }
        }
        assertEquals(listOf("before", "prefix", "g1", "g2", "after"), host.components.texts())

        ids = listOf(3, 2, 1)
        awaitIdle()
        assertEquals(listOf("before", "prefix", "g3", "g2", "g1", "after"), host.components.texts())

        ids = listOf(1, 3)
        awaitIdle()
        assertEquals(listOf("before", "prefix", "g1", "g3", "after"), host.components.texts())

        ids = emptyList()
        awaitIdle()
        assertEquals(listOf("before", "prefix", "after"), host.components.texts())

        ids = listOf(4, 5)
        awaitIdle()
        assertEquals(listOf("before", "prefix", "g4", "g5", "after"), host.components.texts())
    }

    @ParameterizedTest(name = "nested source group: {0}")
    @ValueSource(booleans = [false, true])
    fun removingAChildFromAGroupUpdatesItsIndexInAnArrivingGroup(nested: Boolean) = runComposeSwingTest {
        val departing = JPanel()
        val arriving = JPanel()
        var moved by mutableStateOf(false)
        setContent {
            val label = remember { movableContentOf { Label("m") } }
            val group =
                remember {
                    movableContentOf<@Composable () -> Unit> { content ->
                        VirtualSwingNode {
                            Label("prefix")
                            content()
                        }
                    }
                }
            Host(departing) { if (!moved) group {} }
            Host(arriving) {
                if (moved) group { label() }
                if (moved) Label("new")
                VirtualSwingNode {
                    val content: @Composable () -> Unit = { if (!moved) label() }
                    if (nested) VirtualSwingNode(content = content) else content()
                    Label("tail")
                }
            }
        }
        assertEquals(listOf("prefix"), departing.components.texts())
        assertEquals(listOf("m", "tail"), arriving.components.texts())

        moved = true
        awaitIdle()

        assertEquals(emptyList(), departing.components.toList())
        assertEquals(listOf("prefix", "m", "new", "tail"), arriving.components.texts())
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

    @ParameterizedTest(name = "insert inside the component host during relocation: {0}")
    @ValueSource(booleans = [false, true])
    fun groupRelocationStopsAtTheComponentHost(insert: Boolean) = runComposeSwingTest {
        val departing = JPanel()
        val arriving = JPanel()
        val childHost = JPanel()
        var moved by mutableStateOf(false)
        setContent {
            val group =
                remember {
                    movableContentOf<Boolean> { expanded ->
                        VirtualSwingNode {
                            VirtualSwingNode {
                                SwingNode(factory = { childHost }) {
                                    Label("inner-first")
                                    if (expanded) Label("inner-new")
                                    Label("inner-last")
                                }
                                Label("sibling")
                            }
                        }
                    }
                }
            SwingNode(factory = { departing }) { if (!moved) group(false) }
            SwingNode(factory = { arriving }) { if (moved) group(insert) }
        }
        assertEquals(listOf("inner-first", "inner-last"), childHost.components.texts())
        assertEquals(2, departing.componentCount)
        assertEquals(childHost, departing.getComponent(0))
        onNodeWithText("sibling").assertProperty(departing) { parent }

        moved = true
        awaitIdle()

        assertEquals(0, departing.componentCount)
        assertEquals(2, arriving.componentCount)
        assertEquals(childHost, arriving.getComponent(0))
        onNodeWithText("sibling").assertProperty(arriving) { parent }
        assertEquals(
            if (insert) listOf("inner-first", "inner-new", "inner-last") else listOf("inner-first", "inner-last"),
            childHost.components.texts(),
        )

        moved = false
        awaitIdle()
        assertEquals(0, arriving.componentCount)
        assertEquals(2, departing.componentCount)
        assertEquals(childHost, departing.getComponent(0))
        onNodeWithText("sibling").assertProperty(departing) { parent }
        assertEquals(listOf("inner-first", "inner-last"), childHost.components.texts())
    }

    companion object {
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

        @JvmStatic
        fun relocationOrders(): List<Arguments> {
            val first: @Composable (@Composable () -> Unit) -> Unit = { inner ->
                inner()
                Label("g1")
            }
            val last: @Composable (@Composable () -> Unit) -> Unit = { inner ->
                Label("g1")
                inner()
            }
            return listOf(
                Arguments.of("label record first, destination first", true, first, listOf("m", "g1")),
                Arguments.of("label record first, destination last", true, last, listOf("g1", "m")),
                Arguments.of("group record first, destination first", false, first, listOf("m", "g1")),
                Arguments.of("group record first, destination last", false, last, listOf("g1", "m")),
            )
        }

        @JvmStatic
        fun insertionCases(): List<Arguments> {
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
        fun relocatedGroupChanges(): List<Arguments> {
            val noSiblings: @Composable (Boolean) -> Unit = {}
            val insertSibling: @Composable (Boolean) -> Unit = { moved ->
                if (moved) Label("sibling")
                Label("tail")
            }
            val rebuildSibling: @Composable (Boolean) -> Unit = { moved ->
                key(moved) { Label("sibling") }
                Label("tail")
            }
            return listOf(
                Arguments.of(
                    "rebuild a sibling while relocating and changing inner keys",
                    listOf(3, 1, 4),
                    rebuildSibling,
                    listOf("g3", "g1", "g4", "sibling", "tail"),
                ),
                Arguments.of("change inner keys", listOf(3, 1, 4), noSiblings, listOf("g3", "g1", "g4")),
                Arguments.of(
                    "insert a sibling and a group child",
                    listOf(1, 2, 3, 4),
                    insertSibling,
                    listOf("g1", "g2", "g3", "g4", "sibling", "tail"),
                ),
            )
        }

        @JvmStatic
        fun relocatedDescendantChanges(): List<Arguments> {
            val insert: @Composable (Boolean) -> Unit = { changed ->
                VirtualSwingNode {
                    VirtualSwingNode {
                        Label("g1")
                        if (changed) Label("g2")
                        Label("g3")
                    }
                }
            }
            val changeProtocol: @Composable (Boolean) -> Unit = { changed ->
                VirtualSwingNode {
                    VirtualSwingNode {
                        Label(
                            "g1",
                            SwingModifier then
                                Data(
                                    if (changed) "Q" else "P",
                                    1,
                                    protocolOf(if (changed) "Q" else "P"),
                                ),
                        )
                    }
                }
            }
            return listOf(
                Arguments.of(
                    "insert into a nested group during relocation",
                    insert,
                    listOf("g1", "g3"),
                    listOf("g1", "g2", "g3"),
                    listOf(null, null, null),
                ),
                Arguments.of(
                    "change a descendant protocol during relocation",
                    changeProtocol,
                    listOf("g1"),
                    listOf("g1"),
                    listOf(listOf("Q")),
                ),
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
                ProvideComponentDefaults(DefaultEnabled provides enabled) {
                    VirtualSwingNode { Button("b", onClick = {}) }
                }
            }
            val inside: @Composable (Boolean) -> Unit = { enabled ->
                VirtualSwingNode {
                    ProvideComponentDefaults(DefaultEnabled provides enabled) { Button("b", onClick = {}) }
                }
            }
            return listOf(Arguments.of("around the group", around), Arguments.of("inside the group", inside))
        }
    }
}

/**
 * What [VirtualSwingNode] declares to its container for each component it places: its modifier ahead of the
 * component's own, again when it changes, never a superseded one, and to a raw layout manager as a constraint.
 */
class VirtualSwingNodeDeclarationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#declarationReaches")
    fun groupDeclarationsReachTheirComponents(
        case: String,
        content: @Composable () -> Unit,
        expected: Map<String, List<String>>,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        setContent { Host(host, content) }

        for ((text, tags) in expected) {
            onNodeWithText(text).assertProperty(tags, "$case: $text") { host.recording.tagsOf(this) }
        }
    }

    @Test
    fun groupDeclarationsStopAtTheFirstComponent() = runComposeSwingTest {
        val outer = MeasuringHost()
        val inner = MeasuringHost()
        setContent {
            Host(outer) { VirtualSwingNode(SwingModifier.mark("A")) { Host(inner) { Label("x") } } }
        }

        assertEquals(listOf("A"), outer.recording.tagsOf(inner))
        onNodeWithText("x").assertProperty(emptyList()) { inner.recording.tagsOf(this) }
    }

    @Test
    fun aGroupConstraintReachesARawLayoutManager2() = runComposeSwingTest {
        val layout = GridBagLayout()
        val constraints =
            GridBagConstraints().apply {
                gridx = 3
                gridy = 4
            }
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                VirtualSwingNode(SwingModifier.layoutConstraint(constraints)) {
                    Label("a")
                    Label("b")
                }
            }
        }

        for (text in listOf("a", "b")) {
            onNodeWithText(text).assertProperty(3 to 4, text) {
                layout.getConstraints(this).run { gridx to gridy }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#movableContentCases")
    fun movableContentMovesBetweenGroups(
        case: String,
        sourceProtocol: String,
        destinationProtocol: String,
    ) = runComposeSwingTest {
        val hostP = MeasuringHost(sourceProtocol)
        val hostQ = if (sourceProtocol == destinationProtocol) hostP else MeasuringHost("Q")
        var inSecond by mutableStateOf(false)
        setContent {
            val label = remember { movableContentOf { Label("m") } }
            Host(hostP) {
                VirtualSwingNode(
                    SwingModifier.mark("A", protocol = protocolOf(sourceProtocol)),
                ) { if (!inSecond) label() }
                if (hostP === hostQ) {
                    VirtualSwingNode(
                        SwingModifier.mark("B", protocol = protocolOf(destinationProtocol)),
                    ) { if (inSecond) label() }
                }
            }
            if (hostP !== hostQ) {
                Host(hostQ) {
                    VirtualSwingNode(
                        SwingModifier.mark("B", protocol = protocolOf(destinationProtocol)),
                    ) { if (inSecond) label() }
                }
            }
        }

        if (destinationProtocol == "R") {
            assertFailsWith<IllegalStateException> {
                inSecond = true
                awaitIdle()
            }
        } else {
            inSecond = true
            awaitIdle()
            if (hostP !== hostQ) assertEquals(emptyList(), hostP.texts(), case)
            assertEquals(listOf("m"), hostQ.texts(), case)
            onNodeWithText("m").assertProperty(listOf("B"), case) { hostQ.recording.tagsOf(this) }
        }
    }

    @ParameterizedTest(name = "own modifier: {0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#ownModifiers")
    fun changingTheGroupModifierRedeclaresWithoutRemovingTheComponents(
        ownTag: String?,
        expected: List<String>,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        var tag by mutableStateOf("A")
        setContent {
            Host(host) {
                VirtualSwingNode(SwingModifier.mark(tag)) {
                    Label(
                        "a",
                        if (ownTag == null) SwingModifier else SwingModifier.mark(ownTag, slot = 3),
                    )
                }
            }
        }

        tag = "B"
        awaitIdle()

        onNodeWithText("a").assertProperty(expected) { host.recording.tagsOf(this) }
        assertEquals(emptyList(), host.recording.removals)
    }

    @ExclusiveWindowSystem
    @Test
    fun changingTheGroupModifierKeepsTheFocusedChildFocused() {
        assumeKeyboardFocusIsPossible()
        runComposeSwingTest {
            val host = MeasuringHost()
            var tag by mutableStateOf("A")
            lateinit var anchor: FocusRequester
            lateinit var requester: FocusRequester
            setContent {
                Window(onCloseRequest = {}, title = "virtual-node-focus-test") {
                    anchor = rememberFocusRequester()
                    requester = rememberFocusRequester()
                    Host(host) {
                        TextField("anchor", onValueChange = {}, modifier = SwingModifier.focusRequester(anchor))
                        VirtualSwingNode(SwingModifier.mark(tag)) {
                            TextField("field", onValueChange = {}, modifier = SwingModifier.focusRequester(requester))
                        }
                    }
                }
            }
            assumeWindowBecomesFocused(SwingUtilities.getWindowAncestor(host) as JFrame)
            val anchorField = host.components[0] as JTextField
            val field = host.components[1] as JTextField
            // A window gives its only focusable component the keyboard by itself, and Swing refuses a request for the
            // current focus owner, so the field takes the keyboard from another owner.
            anchor.requestFocus()
            waitUntil { anchorField.isFocusOwner }
            assertTrue(requester.requestFocus(), "the field takes the keyboard before the change")
            waitUntil { field.isFocusOwner }

            tag = "B"
            awaitIdle()

            assertEquals(listOf("B"), host.recording.tagsOf(field))
            assertTrue(field.isFocusOwner, "the field keeps the keyboard")
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#simultaneousChanges")
    fun aGroupChangePublishesNoSupersededGroup(
        case: String,
        content: @Composable (
            JPanel,
            JPanel,
            Boolean,
            String,
            String,
            ProvidedComponentDefault<String>,
        ) -> Unit,
        change: SimultaneousChange,
    ) = runComposeSwingTest {
        val departing = MeasuringHost()
        val host = MeasuringHost()
        var changed by mutableStateOf(false)
        var group by mutableStateOf("g0")
        var default by mutableStateOf("d0")
        var own by mutableStateOf("o0")
        val markDefault =
            componentDefaultKeyOf<String>("mark default") { this then Mark(it, 2, true, MeasuringProtocol) }
        setContent {
            content(departing, host, changed, group, own, markDefault provides default)
        }
        host.recording.declarations.clear()
        departing.recording.declarations.clear()

        Snapshot.withMutableSnapshot {
            changed = true
            group = change.group
            default = change.default
            own = change.own
        }
        awaitIdle()

        assertEquals(
            change.expected,
            host.recording.declarations
                .lastOrNull()
                ?.tags,
            case,
        )
        assertTrue(
            host.recording.declarations.none { declaration ->
                declaration.tags.any { it in change.forbidden }
            },
            case,
        )
        assertEquals(emptyList(), departing.recording.declarations, case)
    }

    @Test
    fun updatingALayoutNodeToANewKeyRestoresTheGroupElement() = runComposeSwingTest {
        val host = MeasuringHost()
        val group = Mark("group", 1, false, MeasuringProtocol)
        lateinit var childNode: RecordingLayoutNode
        var key by mutableIntStateOf(1)
        setContent {
            Host(host) {
                VirtualSwingNode(SwingModifier then group) {
                    Label(
                        "a",
                        SwingModifier then
                            RecordingLayoutNodeElement(
                                "childNode-$key",
                                mutableListOf(),
                                key = key,
                                onAttach = { childNode = it },
                            ),
                    )
                }
            }
        }
        val label = host.components.single()
        assertEquals(listOf(childNode), host.recording.elementsOf(label))
        host.recording.declarations.clear()

        key = 2
        awaitIdle()

        assertEquals(listOf("a"), host.texts())
        assertEquals(listOf(group, childNode), host.recording.elementsOf(label))
        assertEquals(listOf(listOf(group, childNode)), host.recording.declarations.map { it.elements })
        assertEquals(emptyList(), host.recording.removals)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#protocolDeactivationChildren")
    fun changingAGroupProtocolWhileDeactivatingAChildAllowsReactivation(
        case: String,
        child: @Composable (ParentProtocol) -> Unit,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        val p = parentProtocolOf("P") { it is MeasuringHost }
        val q = parentProtocolOf("Q") { it is MeasuringHost }
        var protocol by mutableStateOf(p)
        var active by mutableStateOf(true)
        setContent {
            Host(host) {
                VirtualSwingNode(SwingModifier then Data("group-${protocol.description}", 1, protocol)) {
                    ReusableContentHost(active) { child(protocol) }
                }
            }
        }
        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(listOf("group-P", "child-P"), host.recording.parentDataOf(host.components.single()), case)

        Snapshot.withMutableSnapshot {
            protocol = q
            active = false
        }
        awaitIdle()

        assertEquals(emptyList(), host.components.toList(), case)

        active = true
        awaitIdle()

        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(listOf("group-Q", "child-Q"), host.recording.parentDataOf(host.components.single()), case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#refusedGroupHosts")
    fun aRefusedGroupDoesNotStopAnIndependentGroupFromReachingItsHostSettle(
        case: String,
        sharedHost: Boolean,
        expected: RefusedGroupSettles,
    ) = runComposeSwingTest {
        val hostA = MeasuringHost()
        val hostB = if (sharedHost) hostA else MeasuringHost()
        val p = parentProtocolOf("P") { it is MeasuringHost }
        val q = parentProtocolOf("Q") { it is MeasuringHost }
        var protocol by mutableStateOf(p)
        var active by mutableStateOf(true)
        var version by mutableIntStateOf(1)
        val groupA: @Composable () -> Unit = {
            VirtualSwingNode(SwingModifier then Data("A-${protocol.description}", 1, protocol)) {
                ReusableContentHost(active) {
                    Label("a", SwingModifier then Data("a-${protocol.description}", 2, protocol))
                }
            }
        }
        val groupB: @Composable () -> Unit = {
            VirtualSwingNode(SwingModifier then Data("B-$version", 1, MeasuringProtocol)) { Label("b") }
        }
        setContent {
            if (sharedHost) {
                SettlingHost(hostA) {
                    groupA()
                    groupB()
                }
            } else {
                SwingNode(factory = { JPanel() }) {
                    SettlingHost(hostA, groupA)
                    SettlingHost(hostB, groupB)
                }
            }
        }
        assertEquals(expected.mountedA, hostA.name, case)
        assertEquals(expected.mountedB, hostB.name, case)

        Snapshot.withMutableSnapshot {
            protocol = q
            active = false
            version = 2
        }
        awaitIdle()

        assertEquals(expected.changedB, hostB.name, case)
        assertEquals(expected.changedA, hostA.name, case)

        active = true
        awaitIdle()

        assertEquals(expected.reactivatedB, hostB.name, case)
        assertEquals(expected.reactivatedA, hostA.name, case)
    }
}

class VirtualSwingNodeLayoutNodeDeclarationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#layoutNodeRemovals")
    fun removingALayoutNodeRedeclaresTheGroup(
        case: String,
        groupKey: Any,
        initialGroup: String,
        currentGroup: String,
        change: LayoutNodeRemovalChange,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        var removed by mutableStateOf(false)
        setContent {
            Host(host) {
                VirtualSwingNode(
                    SwingModifier then
                        Mark(if (removed) currentGroup else initialGroup, groupKey, false, MeasuringProtocol),
                ) {
                    Label("a", if (removed) SwingModifier else SwingModifier then NodeMark("own"))
                }
            }
        }
        val label = host.components.single()
        assertEquals(change.before, host.recording.tagsOf(label), case)
        host.recording.declarations.clear()
        host.recording.removals.clear()

        removed = true
        awaitIdle()

        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(listOf(change.expected), host.components.map { host.recording.tagsOf(it) }, case)
        assertEquals(listOf(change.expected), host.recording.declarations.map { it.tags }, case)
        assertEquals(listOf(label), host.recording.declarations.map { it.component }, case)
        assertEquals(emptyList(), host.recording.removals, case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#layoutNodeTypeReplacements")
    fun replacingALayoutNodeTypePublishesOnDetachAndAfterReplacement(
        case: String,
        groupKey: Any,
        before: List<String>,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        lateinit var replacementNode: RecordingLayoutNode
        val replacement =
            RecordingLayoutNodeElement("replacement", mutableListOf(), onAttach = { replacementNode = it })
        var changed by mutableStateOf(false)
        setContent {
            Host(host) {
                VirtualSwingNode(SwingModifier then Mark("G", groupKey, false, MeasuringProtocol)) {
                    Label("a", SwingModifier then (if (changed) replacement else NodeMark("own")))
                }
            }
        }
        val label = host.components.single()
        assertEquals(before, host.recording.tagsOf(label), case)
        host.recording.declarations.clear()
        host.recording.removals.clear()

        changed = true
        awaitIdle()

        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(
            listOf(
                listOf(Mark("G", groupKey, false, MeasuringProtocol)),
                listOf(Mark("G", groupKey, false, MeasuringProtocol), replacementNode),
            ),
            host.recording.declarations.map { it.elements },
            case,
        )
        assertEquals(listOf(label, label), host.recording.declarations.map { it.component }, case)
        assertEquals(
            listOf(Mark("G", groupKey, false, MeasuringProtocol), replacementNode),
            host.recording.elementsOf(label),
            case,
        )
        assertEquals(emptyList(), host.recording.removals, case)
    }

    @Test
    fun movingAChildAcrossCompositionsPublishesItsDestinationProtocolAfterLayoutNodeTeardown() = runComposeSwingTest {
        val departing = MeasuringHost()
        val arriving = MeasuringHost()
        val p = parentProtocolOf("P") { it.layout is MeasurementLayoutManager }
        val q = parentProtocolOf("Q") { it.layout is MeasurementLayoutManager }
        var moved by mutableStateOf(false)
        lateinit var context: CompositionContext
        lateinit var content: @Composable (ParentProtocol) -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf<ParentProtocol> { protocol ->
                        Label("a", SwingModifier then NodeMark("own") then Data(protocol.description, 2, protocol))
                    }
                }
        }
        val first =
            JPanel().setContent(parent = context) {
                Host(departing) {
                    VirtualSwingNode(SwingModifier then Data("group-P", 1, p)) { if (!moved) content(p) }
                }
            }
        val second =
            JPanel().setContent(parent = context) {
                Host(arriving) {
                    VirtualSwingNode(SwingModifier then Data("group-Q", 1, q)) { if (moved) content(q) }
                }
            }
        try {
            awaitIdle()
            val label = departing.components.single()
            assertEquals(listOf("group-P", "P"), departing.recording.parentDataOf(label))
            departing.recording.declarations.clear()
            arriving.recording.declarations.clear()

            moved = true
            awaitIdle()

            assertEquals(emptyList(), departing.components.toList())
            assertEquals(listOf(label), arriving.components.toList())
            assertEquals(emptyList(), departing.recording.declarations)
            assertEquals(listOf(listOf("group-Q", "Q")), arriving.recording.declarations.map { it.parentData })
            assertEquals(listOf(listOf("own")), arriving.recording.declarations.map { it.tags })
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @Test
    fun removingALayoutNodeWithoutAGroupPreservesTheParentDataPublicationSequence() = runComposeSwingTest {
        val host = MeasuringHost()
        var modifier by mutableStateOf<SwingModifier>(
            SwingModifier then NodeMark("own") then Data("old", 1, MeasuringProtocol),
        )
        setContent {
            Host(host) { Label("a", modifier) }
        }
        val label = host.components.single()
        assertEquals(listOf("old"), host.recording.parentDataOf(label))
        assertEquals(listOf("own"), host.recording.tagsOf(label))
        host.recording.declarations.clear()

        modifier = SwingModifier then Data("new", 1, MeasuringProtocol)
        awaitIdle()

        assertEquals(listOf(listOf("old"), listOf("new")), host.recording.declarations.map { it.parentData })
        assertEquals(listOf(emptyList<String>(), emptyList<String>()), host.recording.declarations.map { it.tags })
        assertEquals(listOf("new"), host.recording.parentDataOf(label))
    }

    @Test
    fun releasingAChildLayoutNodeWithdrawsItsLastPublishedDeclaration() = runComposeSwingTest {
        val host = MeasuringHost()
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        val composition =
            OnDemandComposition(context) {
                Host(host) {
                    VirtualSwingNode(SwingModifier then Mark("G", NodeMark::class.java, false, MeasuringProtocol)) {
                        Label("a", SwingModifier then NodeMark("own"))
                    }
                }
            }
        val label = host.components.single()
        try {
            assertEquals(listOf("own"), host.recording.tagsOf(label))
            host.recording.declarations.clear()
        } finally {
            composition.dispose()
        }

        assertEquals(listOf(emptyList<String>()), host.recording.declarations.map { it.tags })
        assertEquals(listOf(label), host.recording.declarations.map { it.component })
    }

    @Test
    fun anUnchangedGroupModifierIsNotDeclaredAgain() = runComposeSwingTest {
        val host = MeasuringHost()
        var tick by mutableIntStateOf(0)
        setContent {
            Host(host) {
                tick
                VirtualSwingNode(SwingModifier.mark("A")) { Label("a") }
            }
        }
        onNodeWithText("a").assertProperty(listOf("A")) { host.recording.tagsOf(this) }
        host.recording.declarations.clear()

        repeat(3) {
            tick++
            awaitIdle()
        }

        assertEquals(emptyList(), host.recording.declarations)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(
        "org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#groupDeclarationChanges",
        "org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#reorderedGroupDeclarationChanges",
    )
    internal fun aGroupChangeDeclaresEachChangedComponentOnceAndSkipsUnchangedMerges(
        case: String,
        content: @Composable (String) -> Unit,
        expected: List<List<String>>,
        current: List<List<String>>,
        change: GroupDeclarationChange,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        var suffix by mutableStateOf("0")
        setContent { Host(host) { content(suffix) } }
        assertEquals(change.beforeTexts, host.texts(), case)
        assertEquals(change.beforeTags, host.components.map { host.recording.tagsOf(it) }, case)
        assertEquals(change.data.before, host.components.map { host.recording.parentDataOf(it) }, case)
        host.recording.declarations.clear()
        host.recording.removals.clear()

        suffix = "1"
        awaitIdle()

        assertEquals(expected, host.recording.declarations.map { it.tags }, case)
        assertEquals(change.afterTexts, host.texts(), case)
        assertEquals(change.data.after, host.components.map { host.recording.parentDataOf(it) }, case)
        assertEquals(change.data.published, host.recording.declarations.map { it.parentData }, case)
        assertEquals(current, host.components.map { host.recording.tagsOf(it) }, case)
        assertEquals(
            change.removedTexts,
            host.recording.removals
                .toTypedArray()
                .texts(),
            case,
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#parentDataReplacements")
    fun parentDataKeysAreReplacedAcrossAllGroupsBeforeProtocolValidation(
        case: String,
        content: @Composable () -> Unit,
        expected: List<String>,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        setContent { Host(host, content) }

        assertEquals(listOf("a"), host.texts(), case)
        onNodeWithText("a").assertProperty(expected, case) { host.recording.parentDataOf(this) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#layoutNodeReplacements")
    fun groupAndChildProtocolChangesPublishTogether(
        case: String,
        childModifier: (ParentProtocol) -> SwingModifier,
        change: ProtocolDeclarationChange,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        val initialProtocol = parentProtocolOf("P") { it is MeasuringHost }
        var protocol by mutableStateOf(initialProtocol)
        setContent {
            Host(host) {
                VirtualSwingNode(SwingModifier then Data("group-${protocol.description}", 1, protocol)) {
                    Label(
                        "a",
                        childModifier(protocol) then Data("child-${protocol.description}", 2, protocol),
                    )
                }
            }
        }
        val label = host.components.single()
        assertEquals(listOf("group-P", "child-P"), host.recording.parentDataOf(label), case)
        assertEquals(change.initialTags, host.recording.tagsOf(label), case)
        host.recording.declarations.clear()
        host.recording.removals.clear()

        protocol = parentProtocolOf("Q") { it is MeasuringHost }
        awaitIdle()

        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(listOf("group-Q", "child-Q"), host.recording.parentDataOf(label), case)
        assertEquals(change.expectedTags, host.recording.tagsOf(label), case)
        assertEquals(change.publications, host.recording.declarations.map { it.tags }, case)
        assertEquals(
            change.parentDataPublications,
            host.recording.declarations.map { it.parentData },
            case,
        )
        assertEquals(emptyList(), host.recording.removals, case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#childAndGroupDeclarationChanges")
    fun aChildUpdatePublishesItsChangedGroupMergeOnceBeforeTheGroupPhase(
        case: String,
        content: @Composable (String) -> Unit,
        before: List<String>,
        publications: List<List<String>>,
        after: List<String>,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        var suffix by mutableStateOf("0")
        setContent { Host(host) { content(suffix) } }
        val label = host.components.single()
        assertEquals(before, host.recording.tagsOf(label), case)
        host.recording.declarations.clear()
        host.recording.removals.clear()

        suffix = "1"
        awaitIdle()

        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(publications, host.recording.declarations.map { it.tags }, case)
        assertEquals(after, host.recording.tagsOf(label), case)
        assertEquals(emptyList(), host.recording.removals, case)
    }

    @ParameterizedTest(name = "nested groups: {0}")
    @ValueSource(booleans = [false, true])
    fun stableBatchesPreserveGroupAttachmentsAndDeclarations(nested: Boolean) = runComposeSwingTest {
        val host = MeasuringHost()
        var text by mutableStateOf("initial")
        var inserted by mutableStateOf(false)
        setContent {
            Host(host) {
                VirtualSwingNode(SwingModifier.mark("G")) {
                    val children: @Composable () -> Unit = {
                        Label(text)
                        if (inserted) Label("inserted")
                    }
                    if (nested) VirtualSwingNode(content = children) else children()
                }
            }
        }
        host.recording.registrations.clear()
        host.recording.declarations.clear()
        host.recording.removals.clear()

        text = "updated"
        awaitIdle()
        assertEquals(listOf("updated"), host.texts())
        assertEquals(listOf(listOf("G")), host.components.map { host.recording.tagsOf(it) })
        assertEquals(emptyList(), host.recording.registrations)
        assertEquals(emptyList(), host.recording.declarations)
        assertEquals(emptyList(), host.recording.removals)

        inserted = true
        awaitIdle()
        assertEquals(listOf("updated", "inserted"), host.texts())
        assertEquals(listOf(listOf("G"), listOf("G")), host.components.map { host.recording.tagsOf(it) })
        host.recording.registrations.clear()
        host.recording.declarations.clear()
        host.recording.removals.clear()

        text = "final"
        awaitIdle()
        assertEquals(listOf("final", "inserted"), host.texts())
        assertEquals(listOf(listOf("G"), listOf("G")), host.components.map { host.recording.tagsOf(it) })
        assertEquals(emptyList(), host.recording.registrations)
        assertEquals(emptyList(), host.recording.declarations)
        assertEquals(emptyList(), host.recording.removals)
    }
}

class LayoutNodeRemovalChange(
    val before: List<String>,
    val expected: List<String>,
)

class ProtocolDeclarationChange(
    val initialTags: List<String>,
    val expectedTags: List<String>,
    val publications: List<List<String>>,
    val parentDataPublications: List<List<String>>,
)

internal class GroupDeclarationChange(
    val beforeTexts: List<String>,
    val afterTexts: List<String>,
    val beforeTags: List<List<String>>,
    val data: GroupDeclarationData,
    val removedTexts: List<String>,
)

internal class GroupDeclarationData(
    val before: List<Any?>,
    val after: List<Any?>,
    val published: List<Any?>,
)

class RefusedGroupSettles(
    val mountedA: String,
    val mountedB: String,
    val changedA: String,
    val changedB: String,
    val reactivatedA: String,
    val reactivatedB: String,
)

class SimultaneousChange(
    val group: String,
    val default: String,
    val own: String,
    val expected: List<String>,
    val forbidden: List<String>,
)

private object VirtualSwingNodeDeclarationCases {
    @JvmStatic
    fun protocolDeactivationChildren(): List<Arguments> {
        val parentData: @Composable (ParentProtocol) -> Unit = { protocol ->
            Label("a", SwingModifier then Data("child-${protocol.description}", 2, protocol))
        }
        val layoutNodeAndParentData: @Composable (ParentProtocol) -> Unit = { protocol ->
            Label(
                "a",
                SwingModifier then NodeMark("own") then Data("child-${protocol.description}", 2, protocol),
            )
        }
        return listOf(
            Arguments.of("parent data", parentData),
            Arguments.of("layout node and parent data", layoutNodeAndParentData),
        )
    }

    @JvmStatic
    fun refusedGroupHosts(): List<Arguments> = listOf(
        Arguments.of(
            "groups share a host",
            true,
            RefusedGroupSettles(
                mountedA = "[A-P, a-P] [B-1]",
                mountedB = "[A-P, a-P] [B-1]",
                changedA = "[B-2]",
                changedB = "[B-2]",
                reactivatedA = "[A-Q, a-Q] [B-2]",
                reactivatedB = "[A-Q, a-Q] [B-2]",
            ),
        ),
        Arguments.of(
            "groups have their own hosts",
            false,
            RefusedGroupSettles("[A-P, a-P]", "[B-1]", "", "[B-2]", "[A-Q, a-Q]", "[B-2]"),
        ),
    )

    @JvmStatic
    fun layoutNodeRemovals(): List<Arguments> = listOf(
        Arguments.of("different group key", 1, "G", "G", LayoutNodeRemovalChange(listOf("G", "own"), listOf("G"))),
        Arguments.of(
            "same group key",
            NodeMark::class.java,
            "G",
            "G",
            LayoutNodeRemovalChange(listOf("own"), listOf("G")),
        ),
        Arguments.of(
            "group changes while its child node leaves",
            NodeMark::class.java,
            "old group",
            "new group",
            LayoutNodeRemovalChange(listOf("own"), listOf("new group")),
        ),
    )

    @JvmStatic
    fun layoutNodeTypeReplacements(): List<Arguments> = listOf(
        Arguments.of("different group key", 1, listOf("G", "own")),
        Arguments.of("same group key", NodeMark::class.java, listOf("own")),
    )

    @JvmStatic
    fun groupDeclarationChanges(): List<Arguments> {
        val overridden: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark("group$suffix")) {
                Label("a", SwingModifier.mark("own"))
                Label("b")
            }
        }
        val nested: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark("outer$suffix")) {
                Label("a")
                VirtualSwingNode(SwingModifier.mark("inner$suffix", slot = 2)) { Label("b") }
            }
        }
        return listOf(
            Arguments.of(
                "one child replaces the changed group declaration and its sibling inherits it",
                overridden,
                listOf(listOf("group1")),
                listOf(listOf("own"), listOf("group1")),
                GroupDeclarationChange(
                    listOf("a", "b"),
                    listOf("a", "b"),
                    listOf(listOf("own"), listOf("group0")),
                    GroupDeclarationData(
                        listOf(null, null),
                        listOf(null, null),
                        listOf(null),
                    ),
                    emptyList<String>(),
                ),
            ),
            Arguments.of(
                "outer and inner declarations change together",
                nested,
                listOf(listOf("outer1"), listOf("outer1", "inner1")),
                listOf(listOf("outer1"), listOf("outer1", "inner1")),
                GroupDeclarationChange(
                    listOf("a", "b"),
                    listOf("a", "b"),
                    listOf(listOf("outer0"), listOf("outer0", "inner0")),
                    GroupDeclarationData(
                        listOf(null, null),
                        listOf(null, null),
                        listOf(null, null),
                    ),
                    emptyList<String>(),
                ),
            ),
        )
    }

    @JvmStatic
    fun reorderedGroupDeclarationChanges(): List<Arguments> {
        val reordered: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark(if (suffix == "0") "A" else "B")) {
                for (id in if (suffix == "0") listOf(1, 2) else listOf(2, 1)) {
                    key(id) { Label("g$id") }
                }
            }
        }
        val retainedKeys: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier then Data("group", 1, MeasuringProtocol)) {
                Label(
                    "a",
                    SwingModifier then Data("child", if (suffix == "0") 1 else 3, MeasuringProtocol) then
                        Data("last", 2, MeasuringProtocol),
                )
            }
        }
        return listOf(
            Arguments.of(
                "reorder children while the group changes",
                reordered,
                listOf(listOf("B"), listOf("B")),
                listOf(listOf("B"), listOf("B")),
                GroupDeclarationChange(
                    listOf("g1", "g2"),
                    listOf("g2", "g1"),
                    listOf(listOf("A"), listOf("A")),
                    GroupDeclarationData(
                        listOf(null, null),
                        listOf(null, null),
                        listOf(null, null),
                    ),
                    listOf("g2"),
                ),
            ),
            Arguments.of(
                "retained keys change while own parent data stays equal",
                retainedKeys,
                listOf(emptyList<String>()),
                listOf(emptyList<String>()),
                GroupDeclarationChange(
                    listOf("a"),
                    listOf("a"),
                    listOf(emptyList<String>()),
                    GroupDeclarationData(
                        listOf(listOf("child", "last")),
                        listOf(listOf("group", "child", "last")),
                        listOf(listOf("group", "child", "last")),
                    ),
                    emptyList<String>(),
                ),
            ),
        )
    }

    @JvmStatic
    fun parentDataReplacements(): List<Arguments> {
        val protocol = parentProtocolOf("Q") { it is MeasuringHost }
        val childReplacement: @Composable () -> Unit = {
            VirtualSwingNode(
                SwingModifier then Data("group-P", 1, parentProtocolOf("P") { it is MeasuringHost }) then
                    Data("group-Q", 2, protocol),
            ) {
                Label("a", SwingModifier then Data("child-Q", 1, protocol))
            }
        }
        val innerReplacement: @Composable () -> Unit = {
            VirtualSwingNode(
                SwingModifier then Data("group-P", 1, parentProtocolOf("P") { it is MeasuringHost }) then
                    Data("group-Q", 2, protocol),
            ) {
                VirtualSwingNode(SwingModifier then Data("inner-Q", 1, protocol)) { Label("a") }
            }
        }
        return listOf(
            Arguments.of("replacement by the child", childReplacement, listOf("group-Q", "child-Q")),
            Arguments.of("replacement by an inner group", innerReplacement, listOf("group-Q", "inner-Q")),
        )
    }

    @JvmStatic
    fun layoutNodeReplacements(): List<Arguments> {
        val data: (ParentProtocol) -> SwingModifier = { SwingModifier }
        val node: (ParentProtocol) -> SwingModifier = { SwingModifier then NodeMark(it.description) }
        val removed: (ParentProtocol) -> SwingModifier = {
            if (it.description == "P") SwingModifier then NodeMark("old") else SwingModifier
        }
        val replaced: (ParentProtocol) -> SwingModifier = {
            if (it.description == "P") SwingModifier then NodeMark("old") else SwingModifier.mark("new", slot = 3)
        }
        return listOf(
            Arguments.of(
                "group and parent data",
                data,
                ProtocolDeclarationChange(
                    emptyList<String>(),
                    emptyList<String>(),
                    listOf(emptyList<String>()),
                    listOf(listOf("group-Q", "child-Q")),
                ),
            ),
            Arguments.of(
                "group and layout node",
                node,
                ProtocolDeclarationChange(
                    listOf("P"),
                    listOf("Q"),
                    listOf(listOf("Q")),
                    listOf(listOf("group-Q", "child-Q")),
                ),
            ),
            Arguments.of(
                "remove the layout node",
                removed,
                ProtocolDeclarationChange(
                    listOf("old"),
                    emptyList<String>(),
                    listOf(emptyList<String>()),
                    listOf(listOf("group-Q", "child-Q")),
                ),
            ),
            Arguments.of(
                "replace the layout node with an element",
                replaced,
                ProtocolDeclarationChange(
                    listOf("old"),
                    listOf("new"),
                    listOf(emptyList<String>(), listOf("new")),
                    listOf(listOf("group-Q", "child-Q"), listOf("group-Q", "child-Q")),
                ),
            ),
        )
    }

    @JvmStatic
    fun childAndGroupDeclarationChanges(): List<Arguments> {
        val replaced: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark("outer$suffix")) {
                VirtualSwingNode(SwingModifier.mark("inner$suffix")) {
                    Label("a", SwingModifier.mark("own$suffix"))
                }
            }
        }
        val combined: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark("outer$suffix")) {
                VirtualSwingNode(SwingModifier.mark("inner$suffix", slot = 2)) {
                    Label("a", SwingModifier.mark("own$suffix", slot = 3))
                }
            }
        }
        return listOf(
            Arguments.of(
                "the child replaces both groups",
                replaced,
                listOf("own0"),
                listOf(listOf("own1")),
                listOf("own1"),
            ),
            Arguments.of(
                "the child extends both groups",
                combined,
                listOf("outer0", "inner0", "own0"),
                listOf(listOf("outer1", "inner1", "own1")),
                listOf("outer1", "inner1", "own1"),
            ),
        )
    }

    @JvmStatic
    fun declarationReaches(): List<Arguments> {
        val eachComponent: @Composable () -> Unit = {
            VirtualSwingNode(SwingModifier.mark("A")) {
                Label("a")
                Label("b")
            }
        }
        val nested: @Composable () -> Unit = {
            VirtualSwingNode(SwingModifier.mark("A", slot = 1)) {
                VirtualSwingNode(SwingModifier.mark("B", slot = 2)) {
                    Label("c", SwingModifier.mark("C", slot = 3))
                }
            }
        }
        val replaced: @Composable () -> Unit = {
            VirtualSwingNode(SwingModifier.mark("A")) {
                Label("c", SwingModifier.mark("B"))
                Label("d")
            }
        }
        return listOf(
            Arguments.of(
                "each component of the group",
                eachComponent,
                mapOf("a" to listOf("A"), "b" to listOf("A")),
            ),
            Arguments.of(
                "outermost group first, the component's own last",
                nested,
                mapOf("c" to listOf("A", "B", "C")),
            ),
            Arguments.of(
                "a component's own declaration of the key replaces the group's",
                replaced,
                mapOf("c" to listOf("B"), "d" to listOf("A")),
            ),
        )
    }

    @JvmStatic
    fun movableContentCases(): List<Arguments> = listOf(
        Arguments.of("same host", "P", "P"),
        Arguments.of("different accepted protocol", "P", "Q"),
        Arguments.of("refused protocol", "P", "R"),
    )

    @JvmStatic
    fun ownModifiers(): List<Arguments> = listOf(
        Arguments.of(null, listOf("B")),
        Arguments.of("o", listOf("B", "o")),
    )

    @JvmStatic
    fun simultaneousChanges(): List<Arguments> = ordinaryChanges() + layoutNodeChanges() + relocatedChanges()

    private fun ordinaryChanges(): List<Arguments> {
        val ordinary: @Composable (
            JPanel,
            JPanel,
            Boolean,
            String,
            String,
            ProvidedComponentDefault<String>,
        ) -> Unit = { _, host, _, group, own, default ->
            ProvideComponentDefaults(default) {
                Host(host) {
                    VirtualSwingNode(SwingModifier.mark(group)) { Label("a", SwingModifier.mark(own, slot = 3)) }
                }
            }
        }
        return listOf(
            Arguments.of(
                "group",
                ordinary,
                SimultaneousChange("g1", "d0", "o0", listOf("g1", "d0", "o0"), listOf("g0")),
            ),
            Arguments.of(
                "default",
                ordinary,
                SimultaneousChange("g0", "d1", "o0", listOf("g0", "d1", "o0"), emptyList()),
            ),
            Arguments.of("own", ordinary, SimultaneousChange("g0", "d0", "o1", listOf("g0", "d0", "o1"), emptyList())),
            Arguments.of(
                "group and default",
                ordinary,
                SimultaneousChange("g1", "d1", "o0", listOf("g1", "d1", "o0"), listOf("g0")),
            ),
            Arguments.of(
                "group and own",
                ordinary,
                SimultaneousChange("g1", "d0", "o1", listOf("g1", "d0", "o1"), listOf("g0")),
            ),
            Arguments.of(
                "default and own",
                ordinary,
                SimultaneousChange("g0", "d1", "o1", listOf("g0", "d1", "o1"), emptyList()),
            ),
            Arguments.of(
                "group, default and own",
                ordinary,
                SimultaneousChange("g1", "d1", "o1", listOf("g1", "d1", "o1"), listOf("g0")),
            ),
        )
    }

    private fun layoutNodeChanges(): List<Arguments> {
        val layoutNode: @Composable (
            JPanel,
            JPanel,
            Boolean,
            String,
            String,
            ProvidedComponentDefault<String>,
        ) -> Unit = { _, host, _, group, own, default ->
            ProvideComponentDefaults(default) {
                Host(
                    host,
                ) { VirtualSwingNode(SwingModifier.mark(group)) { Label("a", SwingModifier then NodeMark(own)) } }
            }
        }
        return listOf(
            Arguments.of(
                "layout node",
                layoutNode,
                SimultaneousChange("g1", "d0", "o1", listOf("g1", "d0", "o1"), listOf("g0")),
            ),
        )
    }

    private fun relocatedChanges(): List<Arguments> {
        val relocated: @Composable (
            JPanel,
            JPanel,
            Boolean,
            String,
            String,
            ProvidedComponentDefault<String>,
        ) -> Unit = { departing, host, changed, group, own, default ->
            val label =
                remember {
                    movableContentOf<String> { value ->
                        Label("a", SwingModifier.mark(value, slot = 3))
                    }
                }
            Host(departing) { VirtualSwingNode(SwingModifier.mark("old")) { if (!changed) label(own) } }
            ProvideComponentDefaults(default) {
                Host(host) { VirtualSwingNode(SwingModifier.mark(group)) { if (changed) label(own) } }
            }
        }
        return listOf(
            Arguments.of(
                "relocated with own change",
                relocated,
                SimultaneousChange("g1", "d1", "o1", listOf("g1", "d1", "o1"), listOf("g0", "old")),
            ),
            Arguments.of(
                "relocated without own change",
                relocated,
                SimultaneousChange("g1", "d1", "o0", listOf("g1", "d1", "o0"), listOf("g0", "old")),
            ),
        )
    }
}

/**
 * What [VirtualSwingNode] refuses: a modifier that is not a parent-layout declaration, a host that places
 * its children in regions or has no layout manager to declare to, a claimed component, and a declaration
 * its host does not accept.
 */
class VirtualSwingNodeRefusalTest {
    @Test
    fun aRefusedSecondGroupChildLeavesTheMeasuringHostUnchanged() {
        val host = MeasuringHost()
        var inserted by mutableStateOf(false)
        val refused = parentProtocolOf("another host") { false }
        assertComposingFails<IllegalStateException> {
            setContent {
                Host(host) {
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
    fun aRefusedGroupChangeRetainsTheAcceptedDeclarationAndAllowsALaterChange(
        earlierChildren: Int,
        labels: List<String>,
        initial: List<List<String>>,
        changed: List<List<String>>,
    ) = runComposeSwingTest {
        val host = MeasuringHost("P")
        val accepted = protocolOf("P")
        val refused = protocolOf("Q")
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        var tag by mutableStateOf("A")
        var restored by mutableStateOf(false)
        val composition =
            OnDemandComposition(parentContext) {
                restored
                Host(host) {
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
            assertEquals(emptyList(), host.recording.declarations)

            assertEquals(labels, host.texts())
            assertEquals(initial, host.components.map { host.recording.tagsOf(it) })
            composition.recompose {
                tag = "A"
                restored = true
            }
            assertEquals(emptyList(), host.recording.declarations)

            composition.recompose { tag = "C" }

            assertEquals(labels, host.texts())
            assertEquals(changed, host.components.map { host.recording.tagsOf(it) })
            assertEquals(changed, host.recording.declarations.map { it.tags })
            assertEquals(emptyList(), host.recording.removals)
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
            Host(departing) {
                if (!moved) {
                    refused()
                    accepted()
                }
            }
            Host(arriving) {
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("siblingGroupChanges")
    fun aRefusedGroupChangeStillLetsAcceptedSiblingsPublish(
        order: List<String>,
        parentData: Boolean,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        val accepted = JLabel("accepted")
        val refused = JLabel("refused")
        val refusedProtocol = parentProtocolOf("border parent") { it.layout is BorderLayout }
        var changed by mutableStateOf(false)
        setContent {
            Host(host) {
                for (group in order) {
                    if (group == "accepted") {
                        VirtualSwingNode(SwingModifier.mark(if (changed) "B" else "A")) {
                            SwingNode(factory = { accepted })
                        }
                    } else {
                        VirtualSwingNode(
                            if (parentData) {
                                SwingModifier then
                                    Data("refused", 1, if (changed) refusedProtocol else MeasuringProtocol)
                            } else {
                                SwingModifier.mark(
                                    "refused",
                                    protocol = if (changed) refusedProtocol else MeasuringProtocol,
                                )
                            },
                        ) {
                            SwingNode(factory = { refused })
                        }
                    }
                }
            }
        }
        assertEquals(listOf("A"), host.recording.tagsOf(accepted))
        assertEquals(if (parentData) emptyList() else listOf("refused"), host.recording.tagsOf(refused))
        assertEquals(if (parentData) listOf("refused") else null, host.recording.parentDataOf(refused))
        host.recording.declarations.clear()
        host.recording.removals.clear()

        assertFailsWith<IllegalStateException> {
            Snapshot.withMutableSnapshot { changed = true }
            awaitIdle()
        }

        assertEquals(
            listOf(listOf("B")),
            host.recording.declarations
                .filter { it.component === accepted }
                .map { it.tags },
        )
        assertEquals(emptyList(), host.recording.declarations.filter { it.component === refused })
        assertEquals(if (parentData) emptyList() else listOf("refused"), host.recording.tagsOf(refused))
        assertEquals(if (parentData) listOf("refused") else null, host.recording.parentDataOf(refused))
        assertEquals(order, host.components.texts())
        assertEquals(emptyList(), host.recording.removals)
    }

    @ParameterizedTest(name = "protocol conflict declared first: {0}")
    @ValueSource(booleans = [true, false])
    fun twoInvalidGroupChangesFailInDeclarationOrder(protocolConflictFirst: Boolean) {
        repeat(20) {
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
                            Host(conflictHost) {
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
                            Host(refusalHost) {
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
    }

    @ParameterizedTest(name = "grouped: {0}")
    @ValueSource(booleans = [false, true])
    fun aRefusedDuplicateClaimLeavesTheContainerEmptyAndAllowsFollowingContent(grouped: Boolean) = runComposeSwingTest {
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        val container = JPanel()
        val claimed = JLabel("claimed")
        val host = JPanel().apply { add(claimed) }
        assertFailsWith<IllegalStateException> {
            container.setContent(context) {
                SwingNode(factory = { host }) {
                    ExistingSwingNode<JPanel, _, JLabel>(claim = { claimed })
                    ExistingSwingNode<JPanel, _, JLabel>(claim = { claimed })
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
    @MethodSource("refusedModifiers")
    fun aGroupModifierIsRefused(modifier: SwingModifier) {
        val host = MeasuringHost()
        assertComposingFails<IllegalArgumentException> {
            setContent { Host(host) { VirtualSwingNode(modifier) { Label("a") } } }
            awaitIdle()
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

    @Test
    fun aGroupDeclarationRefusedByTheHostFails() {
        val north = with(PanelLayout.Border().contentScope) { SwingModifier.north() }
        val host = MeasuringHost()

        assertComposingFails<IllegalStateException> {
            setContent { Host(host) { VirtualSwingNode(north) { Label("a") } } }
            awaitIdle()
        }
        assertEquals(emptyList(), host.components.toList())
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
            Host(first) {
                if (!moved) composeSource({ group {} }, claim)
            }
            Host(second) {
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

    companion object {
        @JvmStatic
        fun refusalPositions(): List<Arguments> = listOf(
            Arguments.of(
                0,
                listOf("a", "b"),
                listOf(listOf("A", "A"), listOf("A", "A")),
                listOf(listOf("C", "C"), listOf("C", "C")),
            ),
            Arguments.of(
                1,
                listOf("earlier-0", "a", "b"),
                listOf(listOf("A", "own"), listOf("A", "A"), listOf("A", "A")),
                listOf(listOf("C", "own"), listOf("C", "C"), listOf("C", "C")),
            ),
            Arguments.of(
                2,
                listOf("earlier-0", "earlier-1", "a", "b"),
                listOf(listOf("A", "own"), listOf("A", "own"), listOf("A", "A"), listOf("A", "A")),
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

        @JvmStatic
        fun siblingGroupChanges(): List<Arguments> = listOf(
            Arguments.of(listOf("accepted", "refused"), false),
            Arguments.of(listOf("refused", "accepted"), false),
            Arguments.of(listOf("accepted", "refused"), true),
            Arguments.of(listOf("refused", "accepted"), true),
        )

        @JvmStatic
        fun refusedModifiers(): List<Arguments> = listOf(
            Arguments.of(Named.of("a property", SwingModifier.enabled(false))),
            Arguments.of(Named.of("a listener", SwingModifier.containerListener {})),
            Arguments.of(Named.of("a slot", SwingModifier.slot(parentProtocolOf("slot parent") { true }, "region"))),
            Arguments.of(Named.of("a parent-layout node", SwingModifier then NodeMark("x"))),
            Arguments.of(Named.of("a component node", SwingModifier.onKeyStroke("ctrl S") {})),
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
            ),
        )

        private val populated: suspend ComposeSwingTest.(Container) -> Unit = { host ->
            setContent {
                SwingNode(factory = { host }, childPlacement = ChildPlacement.Slots("content")) {
                    VirtualSwingNode { Label("a") }
                }
            }
            awaitIdle()
        }

        private val empty: suspend ComposeSwingTest.(Container) -> Unit = { host ->
            setContent {
                SwingNode(factory = { host }, childPlacement = ChildPlacement.Slots("content")) {
                    VirtualSwingNode {}
                }
            }
            awaitIdle()
        }

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

        private val ordered: suspend ComposeSwingTest.(Container) -> Unit = { host ->
            setContent {
                SwingNode(factory = { host }, childPlacement = ChildPlacement.OrderedSlots("content")) {
                    VirtualSwingNode {}
                }
            }
            awaitIdle()
        }

        // An on-demand composition places its top-level component in a region of its root.
        private val root: suspend ComposeSwingTest.(Container) -> Unit = { host ->
            lateinit var context: CompositionContext
            setContent { context = rememberCompositionContext() }
            OnDemandComposition(
                context,
                host,
                SlotAttachment { parent, child, _ ->
                    parent.add(child)
                    return@SlotAttachment { parent.remove(child) }
                },
            ) { VirtualSwingNode { Label("a") } }.dispose()
        }

        private val emptyRoot: suspend ComposeSwingTest.(Container) -> Unit = { host ->
            lateinit var context: CompositionContext
            setContent { context = rememberCompositionContext() }
            OnDemandComposition(
                context,
                host,
                SlotAttachment { parent, child, _ ->
                    parent.add(child)
                    return@SlotAttachment { parent.remove(child) }
                },
            ) { VirtualSwingNode {} }.dispose()
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
    }
}

/** Asserts that [block] fails with [T], including test teardown. */
private inline fun <reified T : Throwable> assertComposingFails(noinline block: suspend ComposeSwingTest.() -> Unit) {
    assertFailsWith<T> { runComposeSwingTest(block = block) }
}

@Composable
private fun Host(
    host: JPanel,
    content: @Composable () -> Unit,
) {
    SwingNode(factory = { host }) { content() }
}

@Composable
private fun SettlingHost(
    host: MeasuringHost,
    content: @Composable () -> Unit,
) {
    SwingNode(factory = { host }, update = {
        reconcileWithChildren {
            host.name = host.components.joinToString(" ") { "${host.recording.parentDataOf(it)}" }
        }
    }) { content() }
}

@Composable
private fun Group(tag: String) {
    VirtualSwingNode(SwingModifier.mark(tag)) {
        Label("g1")
        Label("g2")
    }
}

@Composable
private fun LayeredCase(
    changed: Boolean,
    group: @Composable LayeredPaneScope.(Boolean) -> Unit,
) {
    LayeredPane {
        Label("l1", SwingModifier.layer(1))
        Label("h1", SwingModifier.layer(2))
        VirtualSwingNode { group(changed) }
        Label("l4", SwingModifier.layer(1))
        Label("h4", SwingModifier.layer(2))
    }
}

private fun Array<Component>.texts(): List<String> = map { (it as JLabel).text }

/** Asserts the labels of this host are [expected] in order, and only the `g` labels carry the group's mark. */
private fun MeasuringHost.assertLabels(
    expected: List<String>,
    case: String,
) {
    assertEquals(expected, texts(), case)
    for (component in components) {
        val grouped = (component as JLabel).text.startsWith("g")
        assertEquals(if (grouped) listOf("G") else emptyList(), recording.tagsOf(component), "$case: ${component.text}")
    }
}

/** A container whose layout records each child's declaration; [protocolName] names the protocol that accepts it. */
private class MeasuringHost(
    val protocolName: String = "",
    val recording: RecordingMeasurementLayout = RecordingMeasurementLayout(FlowLayout()),
) : JPanel(recording) {
    fun texts(): List<String> = components.texts()
}

private fun RecordingMeasurementLayout.tagsOf(component: Component): List<String> =
    elementsOf(component).map { (it as Tagged).tag }

private val LayoutDeclaration.tags: List<String> get() = elements.map { (it as Tagged).tag }

private interface Tagged {
    val tag: String
}

/** A declaration to the measuring parent, replaced by the next one of its [slot]. */
private data class Mark(
    override val tag: String,
    val slot: Any,
    override val inheritable: Boolean,
    val protocol: ParentProtocol,
) : ParentLayoutElement,
    Tagged {
    override val parentProtocol: ParentProtocol get() = protocol

    override val key: Any get() = slot
}

private val MeasuringProtocol: ParentProtocol = MeasurementLayoutManager.parentProtocol("measuring parent")

private fun protocolOf(owner: String): ParentProtocol =
    parentProtocolOf("$owner parent") { (it as? MeasuringHost)?.protocolName == owner }

private fun SwingModifier.mark(
    tag: String,
    slot: Int = 1,
    protocol: ParentProtocol = MeasuringProtocol,
): SwingModifier = this then Mark(tag, slot, inheritable = false, protocol)

/** Parent data of [protocol]; folds into the list of the tags declared. */
private data class Data(
    val tag: String,
    val slot: Int,
    override val parentProtocol: ParentProtocol,
) : ParentDataModifier {
    override val key: Any get() = slot

    override fun modifyParentData(parentData: Any?): Any = (parentData as? List<*>).orEmpty() + tag
}

private data class NodeMark(
    val tag: String,
) : ParentLayoutNodeElement<NodeMarkNode>() {
    override val parentProtocol: ParentProtocol get() = MeasuringProtocol

    override fun create(): NodeMarkNode = NodeMarkNode(tag)

    override fun update(node: NodeMarkNode) {
        node.tag = tag
    }
}

private class NodeMarkNode(
    override var tag: String,
) : ParentLayoutNode(),
    Tagged {
    override val parentProtocol: ParentProtocol get() = MeasuringProtocol
}

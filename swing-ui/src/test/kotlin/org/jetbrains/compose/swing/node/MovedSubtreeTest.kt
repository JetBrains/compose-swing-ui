// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.setContentAsMenuInteropHost
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a [SwingComponentNodeListener] component is told, and what its modifier nodes run, when `movableContent` moves
 * the subtree holding it.
 */
class MovedSubtreeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("crossCompositionGroupChanges")
    fun movingAGroupAcrossCompositionsAppliesInnerChangesOnlyAtTheDestination(
        case: String,
        after: List<Int>,
        expected: List<String>,
        expectedWithSiblings: List<String>,
        secondChildArrives: Boolean,
    ) = runComposeSwingTest {
        val departingLayout = RecordingMeasurementLayout()
        val arrivingLayout = RecordingMeasurementLayout()
        var moved by mutableStateOf(false)
        setMovableGroupHosts(departingLayout, arrivingLayout, after) { moved }
        awaitIdle()
        onRoot()
            .onChildAt(0)
            .onChild()
            .assertIsOfType<JPanel>()
            .assertProperty(listOf("g1", "g2"), case) { components.map { (it as JLabel).text } }
        val retained = fetchHostChild(0, 0)
        val secondChild = fetchHostChild(0, 1)
        onRoot()
            .onChildAt(1)
            .onChild()
            .assertIsOfType<JPanel>()
            .assertProperty(listOf("before", "after"), case) { components.map { (it as JLabel).text } }
        val before = onNodeWithText("before").fetch()
        val afterLabel = onNodeWithText("after").fetch()
        onRoot()
            .onChildAt(1)
            .onChild()
            .assertIsOfType<JPanel>()
            .assertProperty(listOf(before, afterLabel), case) { components.toList() }
        departingLayout.declarations.clear()
        arrivingLayout.declarations.clear()
        arrivingLayout.registrations.clear()
        arrivingLayout.removals.clear()

        moved = true
        awaitIdle()

        onRoot()
            .onChildAt(0)
            .onChild()
            .assertIsOfType<JPanel>()
            .assertProperty(emptyList<Component>(), case) { components.toList() }
        onRoot()
            .onChildAt(1)
            .onChild()
            .assertIsOfType<JPanel>()
            .assertProperty(expectedWithSiblings, case) { components.map { (it as JLabel).text } }
        val arriving = onRoot().onChildAt(1).onChild().fetch()
        val arrivedSecond = fetchHostChild(1, 1)
        assertSame(retained, arrivedSecond, case)
        assertSame(arriving, retained.parent, case)
        assertEquals(secondChildArrives, secondChild.parent === arriving, case)
        assertEquals(emptyList(), departingLayout.declarations, case)
        assertEquals(expected, arrivingLayout.registrations.map { (it.component as JLabel).text }, case)
        assertEquals(expected, arrivingLayout.declarations.map { (it.component as JLabel).text }, case)
        assertEquals(emptyList(), arrivingLayout.removals, case)
    }

    @Composable
    private fun GroupLabels(ids: List<Int>) {
        VirtualSwingNode {
            for (id in ids) key(id) { SwingNode(factory = { JLabel("g$id") }) }
        }
    }

    private fun ComposeSwingTest.fetchHostChild(
        host: Int,
        child: Int,
    ) = onRoot()
        .onChildAt(host)
        .onChild()
        .onChildAt(child)
        .fetch()

    private fun ComposeSwingTest.setMovableGroupHosts(
        departingLayout: RecordingMeasurementLayout,
        arrivingLayout: RecordingMeasurementLayout,
        after: List<Int>,
        isMoved: () -> Boolean,
    ) {
        setContent {
            val context = rememberCompositionContext()
            val content =
                remember {
                    movableContentOf<List<Int>> { ids -> GroupLabels(ids) }
                }
            NestedContentHost(parent = context) {
                SwingNode(factory = { JPanel(departingLayout) }) { if (!isMoved()) content(listOf(1, 2)) }
            }
            NestedContentHost(parent = context) {
                SwingNode(factory = { JPanel(arrivingLayout) }) {
                    SwingNode(factory = { JLabel("before") })
                    if (isMoved()) content(after)
                    SwingNode(factory = { JLabel("after") })
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupAttachFailures")
    fun aGroupAttachFailureStopsBeforeLaterChildrenEnterTheDestination(
        case: String,
        children: @Composable (SwingModifier) -> Unit,
    ) = runComposeSwingTest {
        var moved by mutableStateOf(false)
        var onAttach: () -> Unit = {}
        val modifier =
            SwingModifier then
                RecordingLayoutNodeElement(
                    "failure",
                    mutableListOf(),
                    onAttach = { onAttach() },
                )
        setContent {
            val context = rememberCompositionContext()
            val content = remember { movableContentOf { VirtualSwingNode { children(modifier) } } }
            NestedContentHost(parent = context) {
                SwingNode(factory = { JPanel(RecordingMeasurementLayout()) }) { if (!moved) content() }
            }
            NestedContentHost(parent = context) {
                SwingNode(factory = { JPanel(RecordingMeasurementLayout()) }) { if (moved) content() }
            }
        }
        awaitIdle()
        val later = onNodeWithText("later").fetch()
        onRoot()
            .onChildAt(1)
            .onChild()
            .assertIsOfType<JPanel>()
            .assertProperty(emptyList<Component>(), case) { components.toList() }
        assertEquals("later", later.name, case)

        onAttach = { throw UnsupportedOperationException() }
        moved = true
        assertFailsWith<UnsupportedOperationException> { awaitIdle() }

        onRoot()
            .onChildAt(1)
            .onChild()
            .assertIsOfType<JPanel>()
            .assertProperty(emptyList<Component>(), case) { components.toList() }
        assertNull(later.name, case)
    }

    @ParameterizedTest(name = "destination region: {0}")
    @ValueSource(strings = ["left", "right"])
    fun relocatingWithinTheSameHostRefusesANewSlotProtocol(destination: String) = runComposeSwingTest {
        val accepted = parentProtocolOf<Container>("split pane") { it is JSplitPane }
        val refused = parentProtocolOf<Container>("scroll pane") { it is JScrollPane }
        val left = regionAttachment { host, component -> (host as JSplitPane).leftComponent = component }
        val right = regionAttachment { host, component -> (host as JSplitPane).rightComponent = component }
        var moved by mutableStateOf(false)
        setContent {
            val content =
                remember {
                    movableContentOf<ParentProtocol<Container>> { protocol ->
                        SwingNode(
                            factory = { JLabel("moved") },
                            modifier =
                                SwingModifier.slot(
                                    protocol,
                                    if (protocol === accepted) "left" else destination,
                                    if (protocol === accepted || destination == "left") left else right,
                                ),
                        )
                    }
                }
            SwingNode(
                factory = {
                    JSplitPane().apply {
                        leftComponent = null
                        rightComponent = null
                    }
                },
                childPlacement = ChildPlacement.Slots("left", "right"),
            ) {
                if (!moved) content(accepted)
                if (moved) content(refused)
            }
        }
        val label = onNodeWithText("moved").fetch()
        onNodeOfType<JSplitPane>().assertProperty(label) { leftComponent }
        onNodeOfType<JSplitPane>().assertProperty(null) { rightComponent }

        assertFailsWith<IllegalStateException> {
            moved = true
            awaitIdle()
        }
    }

    @Test
    fun everyNodeOfAMenuSubtreeMovedToAnotherCompositionMovesWithIt() = runComposeSwingTest {
        var inSecond by mutableStateOf(false)
        var firstHosted by mutableStateOf(true)
        setContent {
            val context = rememberCompositionContext()
            val content =
                remember {
                    movableContentOf {
                        val events = remember { ArrayList<String>() }
                        MenuNode(factory = { JMenu() }) {
                            MenuNode(
                                factory = { ListeningMenuItem(events) },
                                modifier = SwingModifier then LifecycleElement(events),
                            )
                        }
                    }
                }
            if (firstHosted) MenuBarHost(parent = context) { if (!inSecond) content() }
            MenuBarHost(parent = context) { if (inSecond) content() }
        }
        awaitIdle()
        val node = checkNotNull(onNodeOfType<ListeningMenuItem>().fetch().node)

        inSecond = true
        awaitIdle()
        firstHosted = false
        awaitIdle()
        val state = mutableIntStateOf(0)
        val changes = ArrayList<SwingComponentNode<*>>()
        node.observeReads({ changes += it }) { state.intValue }
        state.intValue = 1
        awaitIdle()

        onNodeOfType<ListeningMenuItem>().assertProperty(
            listOf("attached", "onAttach", "detached", "onDetach", "attached", "onAttach"),
            "moving the menu detaches the item under it from one composition and attaches it to the other",
        ) { events }
        assertEquals(listOf(node), changes, "the item observes in the composition it moved to")
    }

    @Composable
    private fun MenuBarHost(
        parent: CompositionContext,
        content: @Composable () -> Unit,
    ) {
        val current = rememberUpdatedState(content)
        SwingNode(
            factory = { MenuBarHostComponent() },
            update = { set(parent) { mount(it, current) } },
            onRelease = { dispose() },
        )
    }

    private class MenuBarHostComponent :
        JMenuBar(),
        DisposableHandle {
        private var mounted: DisposableHandle? = null

        fun mount(
            parent: CompositionContext,
            content: State<@Composable () -> Unit>,
        ) {
            dispose()
            mounted = setContentAsMenuInteropHost(parent) { content.value() }
        }

        override fun dispose() {
            mounted?.dispose()
            mounted = null
        }
    }

    @Test
    fun aNodeWhoseListenerThrowsFromOnAttachedAsItMovesIsReportedAndEveryMovedNodeStaysAttached() =
        runComposeSwingTest {
            val nested =
                composeInNestedComposition {
                    ListeningNode(attachFailure = "top") { ListeningNode() }
                }
            val top = onAllNodesOfType<ListeningPanel>()[0].fetch()
            val child = onAllNodesOfType<ListeningPanel>()[1].fetch()
            val topNode = checkNotNull(top.node)
            val childNode = checkNotNull(child.node)

            nested.moveContentOut()
            val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }

            assertEquals("top", thrown.message)
            val events = listOf("attached", "onAttach", "detached", "onDetach", "attached", "onAttach")
            assertEquals(events, top.events, "the node whose listener threw is attached all the same")
            assertEquals(events, child.events, "the node under it moves and attaches")
            assertTrue(topNode.isAttached)
            assertTrue(childNode.isAttached)
            nested.dispose()
        }

    @Test
    fun aThrowFromOnAttachDuringAMoveFailsThePassAndDetachesTheThrowingNode() = runComposeSwingTest {
        val events = ArrayList<String>()
        val attachFailure = object : IllegalStateException("onAttach fails") {}
        val node = LifecycleNode(events, attachFailure, false)
        val nested =
            composeInNestedComposition {
                SwingNode(
                    factory = { JPanel() },
                    modifier = SwingModifier then LifecycleElement(events, node = node),
                ) { ListeningNode() }
            }
        val child = onNodeOfType<ListeningPanel>().fetch()

        nested.moveContentOut()
        val failure = assertFails { awaitIdle() }

        assertTrue(failure === attachFailure || failure.suppressed.any { it === attachFailure })
        assertFalse(node.isAttached)
        assertEquals(listOf("onAttach", "onDetach", "onAttach", "onDetach"), events)
        assertEquals(
            listOf("attached", "onAttach", "detached", "onDetach"),
            child.events,
            "the child detaches before the failed attach stops the pass",
        )
        nested.dispose()
    }

    @Test
    fun aModifierNodeThatThrowsFromOnDetachAsItsComponentMovesIsMovedAllTheSame() = runComposeSwingTest {
        val events = ArrayList<String>()
        val nested =
            composeInNestedComposition {
                SwingNode(
                    factory = { JPanel() },
                    modifier = SwingModifier then LifecycleElement(events, failOnDetach = true),
                ) { ListeningNode() }
            }

        nested.moveContentOut()
        val thrown = assertFailsWith<IllegalStateException>("the failure is reported") { awaitIdle() }

        assertEquals("onDetach fails", thrown.message, "the node's failure is reported")
        assertEquals(
            listOf("onAttach", "onDetach", "onAttach"),
            events,
            "the node attaches in the new composition after its detach threw",
        )
        onNodeOfType<ListeningPanel>().assertProperty(
            listOf("attached", "onAttach", "detached", "onDetach", "attached", "onAttach"),
            "the node under it moves and attaches",
        ) { this.events }
    }

    @Test
    fun aNodeWhoseListenerThrowsFromOnDetachedAsItMovesIsAttachedAgainWithTheNodesUnderIt() = runComposeSwingTest {
        val nested =
            composeInNestedComposition {
                ListeningNode(failure = "top") { ListeningNode() }
            }
        val top = onAllNodesOfType<ListeningPanel>()[0].fetch()
        val child = onAllNodesOfType<ListeningPanel>()[1].fetch()
        val topNode = checkNotNull(top.node)
        val childNode = checkNotNull(child.node)

        nested.moveContentOut()
        val thrown = assertFailsWith<IllegalStateException>("the failure is reported") { awaitIdle() }

        assertEquals("top", thrown.message, "the listener's failure is reported")
        assertEquals(
            listOf("attached", "onAttach", "detached", "onDetach", "attached", "onAttach"),
            top.events,
            "the node whose listener threw is told it attached again",
        )
        assertEquals(
            listOf("attached", "onAttach", "detached", "onDetach", "attached", "onAttach"),
            child.events,
            "the node under it moves and attaches",
        )
        assertTrue(topNode.isAttached, "the node whose listener threw is attached in the new composition")
        assertTrue(childNode.isAttached, "the node under it is attached in the new composition")
    }

    @Test
    fun aParkedNodeInASubtreeMovedToAnotherCompositionIsToldNothingAndRunsNoModifierNode() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val nested =
            composeInNestedComposition {
                SwingNode(factory = { JPanel() }) { ReusableContentHost(active) { ListeningNode() } }
            }
        val parked = onNodeOfType<ListeningPanel>().fetch()
        active = false
        awaitIdle()
        assertEquals(listOf("attached", "onAttach", "detached", "onDetach"), parked.events, "parking detaches the node")

        nested.moveContentOut()
        awaitIdle()

        assertEquals(
            listOf("attached", "onAttach", "detached", "onDetach"),
            parked.events,
            "the parked node stays detached",
        )
        nested.dispose()
    }

    @Test
    fun aSubtreeMovedWithinItsCompositionIsToldNothingAndRunsNoModifierNode() = runComposeSwingTest {
        var first by mutableStateOf(true)
        setContent {
            val content = remember { movableContentOf { ListeningNode { ListeningNode() } } }
            SwingNode(factory = { JPanel() }) { if (first) content() }
            SwingNode(factory = { JPanel() }) { if (!first) content() }
        }
        awaitIdle()
        val top = onAllNodesOfType<ListeningPanel>()[0].fetch()
        val child = onAllNodesOfType<ListeningPanel>()[1].fetch()

        first = false
        awaitIdle()

        assertEquals(listOf("attached", "onAttach"), top.events, "the moved node stays attached")
        assertEquals(listOf("attached", "onAttach"), child.events, "the node under it stays attached")
    }

    @Test
    fun removingALayoutNodeWhileRelocatingChecksTheNewDeclarationAtTheDestination() = runComposeSwingTest {
        val departingLayout = RecordingMeasurementLayout()
        val arrivingLayout = BorderLayout()
        val layoutElement = RecordingLayoutNodeElement("layout", mutableListOf())
        val north = with(PanelLayout.Border().contentScope) { SwingModifier.north() }
        var moved by mutableStateOf(false)
        setContent {
            val content =
                remember {
                    movableContentOf<Boolean> { atDestination ->
                        SwingNode(
                            factory = { JLabel("moved") },
                            modifier = if (atDestination) north else SwingModifier then layoutElement,
                        )
                    }
                }
            SwingNode(factory = { JPanel(departingLayout) }) {
                if (!moved) content(false)
            }
            SwingNode(factory = { JPanel(arrivingLayout) }) {
                if (moved) content(true)
            }
        }
        val label =
            onRoot()
                .onChildAt(0)
                .onChild()
                .assertTextEquals("moved")
                .fetch()
        assertEquals(1, departingLayout.elementsOf(label).size)
        departingLayout.declarations.clear()

        moved = true
        awaitIdle()

        onRoot().onChildAt(0).assertIsOfType<JPanel>().assertProperty(0) { componentCount }
        assertSame(label, onRoot().onChildAt(1).onChild().fetch())
        onRoot().onChildAt(1).onChild().assertLayoutConstraint(BorderLayout.NORTH)
        assertEquals(emptyList(), departingLayout.declarations)
    }

    @Test
    fun aMovedModifierNodesCallbackIsNotHeldByTheCompositionItLeft() = runComposeSwingTest {
        val watched = mutableIntStateOf(0)
        val present = mutableStateOf(true)
        var probe: ProbeNode? = null
        val nested =
            composeInNestedComposition {
                if (present.value) {
                    SwingNode(factory = { JPanel() }, modifier = SwingModifier then ProbeElement { probe = it })
                }
            }
        val callback = observeUnderAFreshCallback(checkNotNull(probe), watched)
        probe = null

        nested.moveContentOut()
        awaitIdle()
        present.value = false
        awaitIdle()

        assertTrue(isCollected(callback), "a node that left a composition leaves no read behind in it")
        nested.dispose()
    }

    @TestFactory
    fun aReturnedModifierNodeObservesNewReadsWithoutReceivingNotificationsForItsPreviousAttachment() =
        listOf(false, true).map { queued ->
            DynamicTest.dynamicTest("queued notification before round trip = $queued") {
                runComposeSwingTest { assertObservationsAcrossRoundTrip(queued) }
            }
        }

    private suspend fun ComposeSwingTest.assertObservationsAcrossRoundTrip(queued: Boolean) {
        with(composeRoundTrip()) {
            try {
                frame()
                val hostsPanel = onRoot().onChild()
                val firstHost = hostsPanel.onChildAt(0).fetch<JPanel>()
                val secondHost = hostsPanel.onChildAt(1).fetch<JPanel>()
                val original = moved
                val panel = hostsPanel.onChildAt(0).onChildAt(1).fetch<JPanel>()
                val oldState = mutableIntStateOf(0)
                val newState = mutableIntStateOf(0)
                val changes = ArrayList<String>()
                val onChanged: (ProbeNode) -> Unit = { changes += if (it === original) "moved" else "sibling" }
                moved.observeReads(onChanged) { oldState.intValue }
                sibling.observeReads(onChanged) { oldState.intValue }
                if (queued) {
                    thread {
                        Snapshot.withMutableSnapshot { oldState.intValue = 1 }
                        Snapshot.sendApplyNotifications()
                    }.join()
                }
                inSecond = true
                frame()
                assertStillOriginalIn(secondHost, panel, original, "the node moved to the second owner")
                if (!queued) {
                    oldState.intValue = 1
                    Snapshot.sendApplyNotifications()
                    assertEquals(listOf("sibling"), changes, "a write while moved out reaches only the sibling")
                    changes.clear()
                }
                inSecond = false
                frame()
                assertStillOriginalIn(firstHost, panel, original, "the node returned to its original owner")
                moved.observeReads(onChanged) { newState.intValue }
                assertEquals(emptyList(), changes, "the EDT has not delivered queued notifications")
                awaitIdle()
                assertEquals(
                    if (queued) listOf("sibling") else emptyList(),
                    changes,
                    "the returned attachment gets no callback for its previous read",
                )
                changes.clear()
                newState.intValue = 1
                awaitIdle()
                assertEquals(
                    listOf("moved"),
                    changes,
                    "a write after the return reaches the node exactly once",
                )
            } finally {
                close()
            }
        }
    }

    private fun RoundTrip.assertStillOriginalIn(
        host: JPanel,
        panel: JPanel,
        original: ProbeNode,
        message: String,
    ) {
        assertSame(host, panel.parent, message)
        assertSame(original, moved)
    }

    private fun ComposeSwingTest.composeRoundTrip(): RoundTrip {
        val roundTrip = RoundTrip()
        setContent { roundTrip.Hosts() }
        return roundTrip
    }

    private class RoundTrip(
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) {
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(Job() + dispatcher + clock)
        private val recomposer = Recomposer(scope.coroutineContext)
        private val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }
        private var frameTime = 0L
        var inSecond by mutableStateOf(false)
        lateinit var moved: ProbeNode
        lateinit var sibling: ProbeNode
        private val content =
            movableContentOf {
                SwingNode(
                    factory = { JPanel() },
                    modifier = ProbeElement { moved = it },
                )
            }

        @Composable
        fun Hosts(modifier: SwingModifier = SwingModifier) {
            Panel(modifier) {
                NestedContentHost(parent = recomposer) {
                    Panel(modifier = ProbeElement { sibling = it }) {}
                    if (!inSecond) content()
                }
                NestedContentHost(parent = recomposer) {
                    if (inSecond) content()
                }
            }
        }

        fun frame() {
            Snapshot.sendApplyNotifications()
            frameTime += 16_000_000L
            clock.sendFrame(frameTime)
        }

        fun close() {
            recomposer.cancel()
            runner.cancel()
            scope.cancel()
        }
    }

    /** Declares a [ListeningPanel] whose modifier node records its lifecycle in the panel's events. */
    @Composable
    private fun ListeningNode(
        failure: String? = null,
        attachFailure: String? = null,
        content: @Composable () -> Unit = {},
    ) {
        SwingNode(
            factory = { ListeningPanel() },
            modifier = SwingModifier then LifecycleElement(),
            update = {
                set(failure) { this.failure = it }
                set(attachFailure) { this.attachFailure = it }
            },
            content = content,
        )
    }

    /**
     * Records each change its node is told, and holds the node first attached. Where [failure] is set, it throws
     * an [IllegalStateException] with it as the message on the first detach; where [attachFailure] is set, on each
     * attach after the first.
     */
    private class ListeningPanel :
        JPanel(),
        SwingComponentNodeListener {
        val events = ArrayList<String>()
        var failure: String? = null
        var attachFailure: String? = null
        var node: SwingComponentNode<*>? = null

        override fun onAttached(node: SwingComponentNode<*>) {
            events += "attached"
            if (this.node == null) this.node = node else attachFailure?.let { error(it) }
        }

        override fun onDetached(node: SwingComponentNode<*>) {
            events += "detached"
            failure?.let {
                failure = null
                error(it)
            }
        }
    }

    /** Records each change its node is told, and holds the node first attached. */
    private class ListeningMenuItem(
        val events: MutableList<String>,
    ) : JMenuItem(),
        SwingComponentNodeListener {
        var node: SwingComponentNode<*>? = null

        override fun onAttached(node: SwingComponentNode<*>) {
            events += "attached"
            if (this.node == null) this.node = node
        }

        override fun onDetached(node: SwingComponentNode<*>) {
            events += "detached"
        }
    }

    /**
     * An element whose node records its `onAttach` and `onDetach` in [events], or in the events of the
     * [ListeningPanel] it sits on where [events] is null, and throws from the first `onDetach` where [failOnDetach]
     * is set.
     */
    private class LifecycleElement(
        private val events: MutableList<String>? = null,
        private val failOnDetach: Boolean = false,
        private val node: LifecycleNode? = null,
    ) : SwingModifier.NodeElement<Component, LifecycleNode>() {
        override val targetType: Class<Component> get() = Component::class.java

        override fun create(): LifecycleNode = node ?: LifecycleNode(events, null, failOnDetach)

        override fun update(node: LifecycleNode) = Unit

        override fun equals(other: Any?): Boolean = other is LifecycleElement && events === other.events

        override fun hashCode(): Int = System.identityHashCode(events)
    }

    private class LifecycleNode(
        private val events: MutableList<String>?,
        private val secondAttachFailure: Throwable?,
        private var failOnDetach: Boolean,
    ) : SwingModifier.ComponentNode<Component>() {
        private var attaches = 0
        private val log get() = events ?: (component as ListeningPanel).events

        override fun onAttach() {
            log += "onAttach"
            if (++attaches == 2) secondAttachFailure?.let { throw it }
        }

        override fun onDetach() {
            log += "onDetach"
            if (failOnDetach) {
                failOnDetach = false
                error("onDetach fails")
            }
        }
    }

    private fun regionAttachment(place: (Container, Component?) -> Unit) =
        SlotAttachment<Container> { host, component, _ ->
            place(host, component)
            return@SlotAttachment { place(host, null) }
        }

    companion object {
        @JvmStatic
        fun groupAttachFailures(): List<Arguments> {
            val direct: @Composable (SwingModifier) -> Unit = { modifier ->
                SwingNode(factory = { JLabel("failure") }, modifier = modifier)
                SwingNode(factory = { JLabel("later") }, modifier = SwingModifier.name("later"))
            }
            val nested: @Composable (SwingModifier) -> Unit = { modifier ->
                VirtualSwingNode {
                    SwingNode(factory = { JLabel("failure") }, modifier = modifier)
                }
                SwingNode(factory = { JLabel("later") }, modifier = SwingModifier.name("later"))
            }
            return listOf(
                Arguments.of("direct children", direct),
                Arguments.of("nested group followed by a component", nested),
            )
        }

        @JvmStatic
        fun crossCompositionGroupChanges(): List<Arguments> = listOf(
            Arguments.of(
                "unchanged children",
                listOf(1, 2),
                listOf("g1", "g2"),
                listOf("before", "g1", "g2", "after"),
                true,
            ),
            Arguments.of(
                "insert a child",
                listOf(1, 2, 3),
                listOf("g1", "g2", "g3"),
                listOf("before", "g1", "g2", "g3", "after"),
                true,
            ),
            Arguments.of(
                "change an inner key",
                listOf(1, 3),
                listOf("g1", "g3"),
                listOf("before", "g1", "g3", "after"),
                false,
            ),
        )
    }
}

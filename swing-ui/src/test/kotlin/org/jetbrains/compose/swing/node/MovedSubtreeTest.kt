// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.BroadcastFrameClock
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.setContentAsMenuInteropHost
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.BorderLayout
import java.awt.Component
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
        val departing = JPanel(departingLayout)
        val arriving = JPanel(arrivingLayout)
        val before = JLabel("before")
        val afterLabel = JLabel("after")
        var moved by mutableStateOf(false)
        lateinit var context: CompositionContext
        lateinit var content: @Composable (List<Int>) -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf<List<Int>> { ids ->
                        VirtualSwingNode {
                            for (id in ids) key(id) { SwingNode(factory = { JLabel("g$id") }) }
                        }
                    }
                }
        }
        val first =
            JPanel().setContent(parent = context) {
                SwingNode(factory = { departing }) { if (!moved) content(listOf(1, 2)) }
            }
        val second =
            JPanel().setContent(parent = context) {
                SwingNode(factory = { arriving }) {
                    SwingNode(factory = { before })
                    if (moved) content(after)
                    SwingNode(factory = { afterLabel })
                }
            }
        try {
            awaitIdle()
            assertEquals(listOf("g1", "g2"), departing.components.map { (it as JLabel).text }, case)
            val retained = departing.components.first()
            val secondChild = departing.components[1]
            assertEquals(listOf(before, afterLabel), arriving.components.toList(), case)
            departingLayout.declarations.clear()
            arrivingLayout.declarations.clear()
            arrivingLayout.registrations.clear()
            arrivingLayout.removals.clear()

            moved = true
            awaitIdle()

            assertEquals(emptyList(), departing.components.toList(), case)
            assertEquals(expectedWithSiblings, arriving.components.map { (it as JLabel).text }, case)
            assertSame(retained, arriving.components[1], case)
            assertSame(arriving, retained.parent, case)
            assertEquals(secondChildArrives, secondChild.parent === arriving, case)
            assertEquals(emptyList(), departingLayout.declarations, case)
            assertEquals(expected, arrivingLayout.registrations.map { (it.component as JLabel).text }, case)
            assertEquals(expected, arrivingLayout.declarations.map { (it.component as JLabel).text }, case)
            assertEquals(emptyList(), arrivingLayout.removals, case)
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupAttachFailures")
    fun aGroupAttachFailureStopsBeforeLaterChildrenEnterTheDestination(
        case: String,
        children: @Composable (SwingModifier, JLabel) -> Unit,
    ) = runComposeSwingTest {
        val later = JLabel("later")
        val departing = JPanel(RecordingMeasurementLayout())
        val arriving = JPanel(RecordingMeasurementLayout())
        var moved by mutableStateOf(false)
        var onAttach: () -> Unit = {}
        val modifier =
            SwingModifier then
                RecordingLayoutNodeElement(
                    "failure",
                    mutableListOf(),
                    onAttach = { onAttach() },
                )
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content = remember { movableContentOf { VirtualSwingNode { children(modifier, later) } } }
        }
        val first =
            JPanel().setContent(parent = context) {
                SwingNode(factory = { departing }) { if (!moved) content() }
            }
        val second =
            JPanel().setContent(parent = context) {
                SwingNode(factory = { arriving }) { if (moved) content() }
            }
        try {
            awaitIdle()
            assertEquals(emptyList(), arriving.components.toList(), case)
            assertEquals("later", later.name, case)

            onAttach = { throw UnsupportedOperationException() }
            moved = true
            assertFailsWith<UnsupportedOperationException> { awaitIdle() }

            assertEquals(emptyList(), arriving.components.toList(), case)
            assertNull(later.name, case)
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @ParameterizedTest(name = "destination region: {0}")
    @ValueSource(strings = ["left", "right"])
    fun relocatingWithinTheSameHostRefusesANewSlotProtocol(destination: String) = runComposeSwingTest {
        val host =
            JSplitPane().apply {
                leftComponent = null
                rightComponent = null
            }
        val label = JLabel("moved")
        val accepted = parentProtocolOf("split pane") { it === host }
        val refused = parentProtocolOf("scroll pane") { it is JScrollPane }
        val left = regionAttachment { host.leftComponent = it }
        val right = regionAttachment { host.rightComponent = it }
        var moved by mutableStateOf(false)
        setContent {
            val content =
                remember {
                    movableContentOf<ParentProtocol> { protocol ->
                        SwingNode(
                            factory = { label },
                            modifier =
                                SwingModifier.slot(
                                    protocol,
                                    if (protocol === accepted) "left" else destination,
                                    if (protocol === accepted || destination == "left") left else right,
                                ),
                        )
                    }
                }
            SwingNode(factory = { host }, childPlacement = ChildPlacement.Slots("left", "right")) {
                if (!moved) content(accepted)
                if (moved) content(refused)
            }
        }
        assertSame(label, host.leftComponent)
        assertEquals(null, host.rightComponent)

        assertFailsWith<IllegalStateException> {
            moved = true
            awaitIdle()
        }
    }

    @Test
    fun everyNodeOfAMenuSubtreeMovedToAnotherCompositionMovesWithIt() = runComposeSwingTest {
        var inSecond by mutableStateOf(false)
        val item = ListeningMenuItem()
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        MenuNode(factory = { JMenu() }) {
                            MenuNode(factory = { item }, modifier = SwingModifier then LifecycleElement(item.events))
                        }
                    }
                }
        }
        awaitIdle()
        val first = JMenuBar().setContentAsMenuInteropHost(context) { if (!inSecond) content() }
        val second = JMenuBar().setContentAsMenuInteropHost(context) { if (inSecond) content() }
        awaitIdle()
        val node = checkNotNull(item.node)

        inSecond = true
        awaitIdle()
        first.dispose()
        val state = mutableIntStateOf(0)
        val changes = ArrayList<SwingComponentNode<*>>()
        node.observeReads({ changes += it }) { state.intValue }
        state.intValue = 1
        awaitIdle()

        assertEquals(
            listOf("attached", "onAttach", "detached", "onDetach", "attached", "onAttach"),
            item.events,
            "moving the menu detaches the item under it from one composition and attaches it to the other",
        )
        assertEquals(listOf(node), changes, "the item observes in the composition it moved to")
        second.dispose()
    }

    @Test
    fun aNodeWhoseListenerThrowsFromOnAttachedAsItMovesIsReportedAndEveryMovedNodeStaysAttached() =
        runComposeSwingTest {
            val top = ListeningPanel(attachFailure = "top")
            val child = ListeningPanel()
            val nested = composeInNestedComposition { ListeningNode(top) { ListeningNode(child) } }
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
        val child = ListeningPanel()
        val nested =
            composeInNestedComposition {
                SwingNode(
                    factory = { JPanel() },
                    modifier = SwingModifier then LifecycleElement(events, node = node),
                ) { ListeningNode(child) }
            }

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
        val child = ListeningPanel()
        val nested =
            composeInNestedComposition {
                SwingNode(
                    factory = { JPanel() },
                    modifier = SwingModifier then LifecycleElement(events, failOnDetach = true),
                ) { ListeningNode(child) }
            }

        nested.moveContentOut()
        val thrown = assertFailsWith<IllegalStateException>("the failure is reported") { awaitIdle() }

        assertEquals("onDetach fails", thrown.message, "the node's failure is reported")
        assertEquals(
            listOf("onAttach", "onDetach", "onAttach"),
            events,
            "the node attaches in the new composition after its detach threw",
        )
        assertEquals(
            listOf("attached", "onAttach", "detached", "onDetach", "attached", "onAttach"),
            child.events,
            "the node under it moves and attaches",
        )
    }

    @Test
    fun aNodeWhoseListenerThrowsFromOnDetachedAsItMovesIsAttachedAgainWithTheNodesUnderIt() = runComposeSwingTest {
        val top = ListeningPanel(failure = "top")
        val child = ListeningPanel()
        val nested = composeInNestedComposition { ListeningNode(top) { ListeningNode(child) } }
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
        val parked = ListeningPanel()
        val nested =
            composeInNestedComposition {
                SwingNode(factory = { JPanel() }) { ReusableContentHost(active) { ListeningNode(parked) } }
            }
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
        val top = ListeningPanel()
        val child = ListeningPanel()
        setContent {
            val content = remember { movableContentOf { ListeningNode(top) { ListeningNode(child) } } }
            SwingNode(factory = { JPanel() }) { if (first) content() }
            SwingNode(factory = { JPanel() }) { if (!first) content() }
        }
        awaitIdle()

        first = false
        awaitIdle()

        assertEquals(listOf("attached", "onAttach"), top.events, "the moved node stays attached")
        assertEquals(listOf("attached", "onAttach"), child.events, "the node under it stays attached")
    }

    @Test
    fun removingALayoutNodeWhileRelocatingChecksTheNewDeclarationAtTheDestination() = runComposeSwingTest {
        val departingLayout = RecordingMeasurementLayout()
        val departing = JPanel(departingLayout)
        val arrivingLayout = BorderLayout()
        val arriving = JPanel(arrivingLayout)
        val label = JLabel("moved")
        val layoutElement = RecordingLayoutNodeElement("layout", mutableListOf())
        val north = with(PanelLayout.Border().contentScope) { SwingModifier.north() }
        var moved by mutableStateOf(false)
        setContent {
            val content =
                remember {
                    movableContentOf<Boolean> { atDestination ->
                        SwingNode(
                            factory = { label },
                            modifier = if (atDestination) north else SwingModifier then layoutElement,
                        )
                    }
                }
            SwingNode(factory = { departing }) { if (!moved) content(false) }
            SwingNode(factory = { arriving }) { if (moved) content(true) }
        }
        assertEquals(listOf(label), departing.components.toList())
        assertEquals(1, departingLayout.elementsOf(label).size)
        departingLayout.declarations.clear()

        moved = true
        awaitIdle()

        assertEquals(0, departing.componentCount)
        assertEquals(listOf(label), arriving.components.toList())
        assertSame(arriving, label.parent)
        assertEquals(BorderLayout.NORTH, arrivingLayout.getConstraints(label))
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
                val original = moved
                val oldState = mutableIntStateOf(0)
                val newState = mutableIntStateOf(0)
                val changes = ArrayList<String>()
                val onChanged: (ProbeNode) -> Unit = {
                    changes +=
                        if (it === original) "moved" else "sibling"
                }
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
                assertSame(secondHost, panel.parent, "the node moved to the second owner")
                assertSame(original, moved)
                if (!queued) {
                    oldState.intValue = 1
                    Snapshot.sendApplyNotifications()
                    assertEquals(
                        listOf("sibling"),
                        changes,
                        "a write while moved out reaches only the sibling",
                    )
                    changes.clear()
                }
                inSecond = false
                frame()
                assertSame(firstHost, panel.parent, "the node returned to its original owner")
                assertSame(original, moved)
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

    private fun ComposeSwingTest.composeRoundTrip(): RoundTrip {
        val firstHost = JPanel()
        val secondHost = JPanel()
        setContent {
            SwingNode(factory = { firstHost })
            SwingNode(factory = { secondHost })
        }
        return RoundTrip(firstHost, secondHost)
    }

    private class RoundTrip(
        val firstHost: JPanel,
        val secondHost: JPanel,
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
        val panel = JPanel()
        private val content =
            movableContentOf {
                SwingNode(factory = { panel }, modifier = ProbeElement { moved = it })
            }
        private val first =
            firstHost.setContent(parent = recomposer) {
                SwingNode(factory = { JPanel() }, modifier = ProbeElement { sibling = it })
                if (!inSecond) content()
            }
        private val second =
            secondHost.setContent(parent = recomposer) {
                if (inSecond) content()
            }

        fun frame() {
            Snapshot.sendApplyNotifications()
            frameTime += 16_000_000L
            clock.sendFrame(frameTime)
        }

        fun close() {
            first.dispose()
            second.dispose()
            recomposer.cancel()
            runner.cancel()
            scope.cancel()
        }
    }

    /** Declares [panel] with a modifier node that records its lifecycle in [ListeningPanel.events]. */
    @Composable
    private fun ListeningNode(
        panel: ListeningPanel,
        content: @Composable () -> Unit = {},
    ) {
        SwingNode(factory = { panel }, modifier = SwingModifier then LifecycleElement(panel.events), content = content)
    }

    /**
     * Records each change its node is told, and holds the node first attached. Where [failure] is set, it throws
     * an [IllegalStateException] with it as the message on the first detach; where [attachFailure] is set, on each
     * attach after the first.
     */
    private class ListeningPanel(
        private var failure: String? = null,
        private val attachFailure: String? = null,
    ) : JPanel(),
        SwingComponentNodeListener {
        val events = ArrayList<String>()
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
    private class ListeningMenuItem :
        JMenuItem(),
        SwingComponentNodeListener {
        val events = ArrayList<String>()
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
     * An element whose node records its `onAttach` and `onDetach` in [events] and throws from the first
     * `onDetach` where [failOnDetach] is set.
     */
    private class LifecycleElement(
        private val events: MutableList<String>,
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
        private val events: MutableList<String>,
        private val secondAttachFailure: Throwable?,
        private var failOnDetach: Boolean,
    ) : SwingModifier.ComponentNode<Component>() {
        private var attaches = 0

        override fun onAttach() {
            events += "onAttach"
            if (++attaches == 2) secondAttachFailure?.let { throw it }
        }

        override fun onDetach() {
            events += "onDetach"
            if (failOnDetach) {
                failOnDetach = false
                error("onDetach fails")
            }
        }
    }

    private fun regionAttachment(place: (Component?) -> Unit) = SlotAttachment { _, component, _ ->
        place(component)
        return@SlotAttachment { place(null) }
    }

    companion object {
        @JvmStatic
        fun groupAttachFailures(): List<Arguments> {
            val direct: @Composable (SwingModifier, JLabel) -> Unit = { modifier, later ->
                SwingNode(factory = { JLabel("failure") }, modifier = modifier)
                SwingNode(factory = { later }, modifier = SwingModifier.name("later"))
            }
            val nested: @Composable (SwingModifier, JLabel) -> Unit = { modifier, later ->
                VirtualSwingNode {
                    SwingNode(factory = { JLabel("failure") }, modifier = modifier)
                }
                SwingNode(factory = { later }, modifier = SwingModifier.name("later"))
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

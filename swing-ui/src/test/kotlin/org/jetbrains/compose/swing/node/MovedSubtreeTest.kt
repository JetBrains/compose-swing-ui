package org.jetbrains.compose.swing.node

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
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
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.setContentAsMenuInteropHost
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPanel
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a [SwingComponentNodeListener] component is told, and what its modifier nodes run, when `movableContent` moves
 * the subtree holding it.
 */
class MovedSubtreeTest {
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
                Snapshot.sendApplyNotifications()
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
}

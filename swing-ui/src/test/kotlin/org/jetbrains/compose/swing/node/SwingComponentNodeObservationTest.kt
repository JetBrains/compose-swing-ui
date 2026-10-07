package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.ReusableComposeNode
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.lang.ref.WeakReference
import javax.swing.JPanel
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What a node held by a [SwingComponentNodeListener] component and the modifier nodes on it observe and request. */
class SwingComponentNodeObservationTest {
    @Test
    fun aModifierNodeMovedToAnotherCompositionAndRemovedThereRunsNoCallbackForAReadItObservedBefore() =
        runComposeSwingTest {
            var inOuter by mutableStateOf(false)
            var shownInOuter by mutableStateOf(true)
            val host = JPanel()
            lateinit var modifierNode: SwingModifier.Node
            lateinit var context: CompositionContext
            lateinit var content: @Composable () -> Unit
            setContent {
                context = rememberCompositionContext()
                content =
                    remember {
                        movableContentOf {
                            SwingNode(factory = { ListeningPanel() }, modifier = PanelProbe { modifierNode = it })
                        }
                    }
                SwingNode(factory = { host })
                if (inOuter && shownInOuter) content()
            }
            awaitIdle()
            val handle = host.setContent(parent = context) { if (!inOuter) content() }
            awaitIdle()
            val state = mutableIntStateOf(0)
            val changes = ArrayList<SwingModifier.Node>()
            modifierNode.observeReads({ changes += it }) { state.intValue }

            inOuter = true
            awaitIdle()
            shownInOuter = false
            awaitIdle()
            state.intValue = 1
            awaitIdle()

            assertEquals(
                emptyList(),
                changes,
                "a modifier node removed after a move runs no callback for a read it observed before the move",
            )
            handle.dispose()
        }

    @Test
    fun aModifierNodeMovedToAnotherCompositionAndDeactivatedThereRunsNoCallbackForAReadItObservedBefore() =
        runComposeSwingTest {
            var inOuter by mutableStateOf(false)
            var active by mutableStateOf(true)
            val host = JPanel()
            lateinit var modifierNode: SwingModifier.Node
            lateinit var context: CompositionContext
            lateinit var content: @Composable () -> Unit
            setContent {
                context = rememberCompositionContext()
                content =
                    remember {
                        movableContentOf {
                            SwingNode(factory = { ListeningPanel() }, modifier = PanelProbe { modifierNode = it })
                        }
                    }
                SwingNode(factory = { host })
                if (inOuter) ReusableContentHost(active) { content() }
            }
            awaitIdle()
            val handle = host.setContent(parent = context) { if (!inOuter) content() }
            awaitIdle()
            val state = mutableIntStateOf(0)
            val changes = ArrayList<SwingModifier.Node>()
            modifierNode.observeReads({ changes += it }) { state.intValue }

            inOuter = true
            awaitIdle()
            active = false
            awaitIdle()
            state.intValue = 1
            awaitIdle()

            assertEquals(
                emptyList(),
                changes,
                "a modifier node deactivated after a move runs no callback for a read it observed before the move",
            )
            handle.dispose()
        }

    @Test
    fun aNestedNodeObservesInItsNewCompositionAfterTheSourceCompositionIsDisposed() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        val child = ListeningPanel()
        val grandchild = ListeningPanel()
        val sibling = ListeningPanel()
        val panels = listOf(child, grandchild, sibling)
        val host = JPanel()
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        SwingNode(factory = { JPanel() }) {
                            SwingNode(factory = { child }) {
                                SwingNode(factory = { grandchild })
                            }
                            SwingNode(factory = { sibling })
                        }
                    }
                }
            SwingNode(factory = { host })
            if (inOuter) content()
        }
        awaitIdle()
        val handle = host.setContent(parent = context) { if (!inOuter) content() }
        awaitIdle()
        val nodes = panels.map { it.events.single().node }
        val state = mutableIntStateOf(0)
        val changes = ArrayList<SwingComponentNode<*>>()
        val onChanged: (SwingComponentNode<*>) -> Unit = { changes += it }
        nodes.forEach { node -> node.observeReads(onChanged) { state.intValue } }

        inOuter = true
        awaitIdle()
        handle.dispose()
        awaitIdle()
        nodes.forEach { node -> node.observeReads(onChanged) { state.intValue } }

        state.intValue = 1
        Snapshot.sendApplyNotifications()
        awaitIdle()

        assertEquals(nodes.toSet(), changes.toSet(), "each unchanged descendant observes through its new composition")
        assertEquals(nodes.size, changes.size, "each descendant is notified once")
        panels.zip(nodes).forEach { (panel, node) ->
            assertEquals(listOf("attached", "detached", "attached"), panel.events.map { it.kind })
            panel.events.forEach { assertSame(node, it.node, "moving the subtree preserves every descendant node") }
        }
    }

    @Test
    fun aMovedModifierReplacesItsPreviousCompositionsReads() = assertMovedModifierReplacesReads(nested = false)

    @Test
    fun aNestedMovedModifierReplacesItsPreviousCompositionsReads() = assertMovedModifierReplacesReads(nested = true)

    private fun assertMovedModifierReplacesReads(nested: Boolean) = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        val host = JPanel()
        lateinit var modifierNode: SwingModifier.Node
        val modifier = PanelProbe { modifierNode = it }
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        if (nested) {
                            SwingNode(factory = { JPanel() }) {
                                SwingNode(factory = { ListeningPanel() }, modifier = modifier)
                            }
                        } else {
                            SwingNode(factory = { ListeningPanel() }, modifier = modifier)
                        }
                    }
                }
            SwingNode(factory = { host })
            if (inOuter) content()
        }
        awaitIdle()
        val handle = host.setContent(parent = context) { if (!inOuter) content() }
        try {
            awaitIdle()
            val node = modifierNode
            val first = mutableIntStateOf(0)
            val second = mutableIntStateOf(0)
            val changes = ArrayList<SwingModifier.Node>()
            val onChanged: (SwingModifier.Node) -> Unit = { changes += it }
            node.observeReads(onChanged) { first.intValue }

            inOuter = true
            awaitIdle()
            assertSame(node, modifierNode, "moving preserves the modifier node")
            node.observeReads(onChanged) { second.intValue }
            changes.clear()

            first.intValue = 1
            Snapshot.sendApplyNotifications()
            awaitIdle()
            assertEquals(emptyList(), changes, "reobserving drops the former composition's reads")

            second.intValue = 1
            Snapshot.sendApplyNotifications()
            awaitIdle()
            assertEquals(listOf(node), changes, "the replacement reads stay observed")
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun pendingComponentAndModifierActionsAreCanceledWhenTheirNodeMoves() =
        assertMovedActionsAreCanceled(nested = false)

    @Test
    fun pendingComponentAndModifierActionsAreCanceledWhenTheirParentMoves() =
        assertMovedActionsAreCanceled(nested = true)

    private fun assertMovedActionsAreCanceled(nested: Boolean) = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        val child = ListeningPanel()
        val host = JPanel()
        lateinit var modifierNode: SwingModifier.Node
        val modifier = PanelProbe { modifierNode = it }
        var componentCalls = 0
        var modifierCalls = 0
        val componentAction: (SwingComponentNode<*>) -> Unit = { componentCalls++ }
        val modifierAction: (SwingModifier.Node) -> Unit = { modifierCalls++ }
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        if (nested) {
                            SwingNode(factory = { JPanel() }) {
                                SwingNode(factory = { child }, modifier = modifier)
                            }
                        } else {
                            SwingNode(factory = { child }, modifier = modifier)
                        }
                    }
                }
            SwingNode(factory = { host })
            if (inOuter) {
                synchronized(child.treeLock) {
                    child.events
                        .first()
                        .node
                        .requestAfterValidation(componentAction)
                    modifierNode.requestAfterValidation(modifierAction)
                }
                content()
            }
        }
        awaitIdle()
        val source = host.setContent(parent = context) { if (!inOuter) content() }
        try {
            awaitIdle()
            inOuter = true
            awaitIdle()

            assertEquals(0, componentCalls, "a component action queued before its owner changes never runs")
            assertEquals(0, modifierCalls, "a modifier action queued before its owner changes never runs")

            synchronized(child.treeLock) {
                child.events
                    .first()
                    .node
                    .requestAfterValidation(componentAction)
                modifierNode.requestAfterValidation(modifierAction)
            }
            awaitIdle()
            assertEquals(1, componentCalls, "the component accepts requests under its new owner")
            assertEquals(1, modifierCalls, "the modifier accepts requests under its new owner")
        } finally {
            source.dispose()
        }
    }

    @Test
    fun aDeactivatedDescendantIsNotReattachedWhenItsParentMoves() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var active by mutableStateOf(true)
        val child = ListeningPanel()
        val host = JPanel()
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        SwingNode(factory = { JPanel() }) {
                            ReusableContentHost(active) { SwingNode(factory = { child }) }
                        }
                    }
                }
            SwingNode(factory = { host })
            if (inOuter) content()
        }
        awaitIdle()
        val source = host.setContent(parent = context) { if (!inOuter) content() }
        try {
            awaitIdle()
            val node = child.events.single().node
            active = false
            awaitIdle()
            assertFailsWith<IllegalStateException> { node.observeReads({}) {} }
            assertFailsWith<IllegalStateException> { node.requestAfterValidation {} }
            val events = child.events.toList()

            inOuter = true
            awaitIdle()

            assertEquals(events, child.events, "moving a parent does not attach its deactivated descendant")
            assertFailsWith<IllegalStateException> { node.observeReads({}) {} }
            assertFailsWith<IllegalStateException> { node.requestAfterValidation {} }
        } finally {
            source.dispose()
        }
    }

    @Test
    fun aReadObservedByTheReleaseBlockIsDroppedWithItsNode() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val state = mutableIntStateOf(0)
        var reference: WeakReference<Any>? = null
        setContent {
            if (shown) {
                SwingNode(
                    factory = { ListeningPanel() },
                    onRelease = { reference = observeUnderAFreshCallback(events.first().node, state) },
                )
            }
        }
        awaitIdle()

        shown = false
        awaitIdle()

        assertTrue(
            isCollected(checkNotNull(reference)),
            "a read the release block observes is not held after the release",
        )
    }

    @Test
    fun aReadObservedAsTheModifierTearsDownIsDroppedWithItsNode() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val state = mutableIntStateOf(0)
        var reference: WeakReference<Any>? = null
        val modifier = SwingModifier then ObserveOnDetach { reference = observeUnderAFreshCallback(it, state) }
        setContent { if (shown) SwingNode(factory = { ListeningPanel() }, modifier = modifier) }
        awaitIdle()

        shown = false
        awaitIdle()

        assertTrue(
            isCollected(checkNotNull(reference)),
            "a read observed as the modifier tears down is not held after the release",
        )
    }

    @Test
    fun aReadObservedAsTheModifierTearsDownForAReuseRunsNoCallbackAfterIt() = runComposeSwingTest {
        var key by mutableIntStateOf(0)
        val component = ListeningPanel()
        val state = mutableIntStateOf(0)
        val changes = ArrayList<SwingComponentNode<*>>()
        val modifier =
            SwingModifier then ObserveOnDetach { node -> node.observeReads({ changes += it }) { state.intValue } }
        setContent {
            ReusableContent(key) {
                ReusableComposeNode<SwingNodeHolder<ListeningPanel>, SwingApplier>(
                    factory = { CreatedNodeHolder(component) },
                    update = { SwingNodeUpdater(this).applyModifier(CompositionLocalMap.Empty, modifier) },
                )
            }
        }
        awaitIdle()

        key = 1
        awaitIdle()
        state.intValue = 1
        awaitIdle()

        assertEquals(
            emptyList(),
            changes,
            "a read observed while the modifier tore down for the reuse runs no callback after it",
        )
    }

    @Test
    fun aNodeRefusesToObserveOffTheEventDispatchThread() = runComposeSwingTest {
        val component = ListeningPanel()
        setContent { SwingNode(factory = { component }) }
        awaitIdle()
        val node = component.events.single().node
        var refused: Throwable? = null

        thread { refused = runCatching { node.observeReads({}) {} }.exceptionOrNull() }.join()

        val thrown = assertIs<IllegalStateException>(refused, "a call off the Event Dispatch Thread is refused")
        assertTrue("Event Dispatch Thread" in thrown.message.orEmpty(), "the refusal names the thread rule")
    }

    @Test
    fun aNodeRefusesARequestOffTheEventDispatchThread() = runComposeSwingTest {
        val component = ListeningPanel()
        setContent { SwingNode(factory = { component }) }
        awaitIdle()
        val node = component.events.single().node
        var refused: Throwable? = null

        thread { refused = runCatching { node.requestAfterValidation {} }.exceptionOrNull() }.join()

        val thrown = assertIs<IllegalStateException>(refused, "a call off the Event Dispatch Thread is refused")
        assertTrue("Event Dispatch Thread" in thrown.message.orEmpty(), "the refusal names the thread rule")
    }

    @Test
    fun aNodeRefusesToInvalidateItsLayoutOffTheEventDispatchThread() = runComposeSwingTest {
        val component = ListeningPanel()
        setContent { SwingNode(factory = { component }) }
        awaitIdle()
        val node = component.events.single().node
        var refused: Throwable? = null

        thread { refused = runCatching { node.invalidateLayout() }.exceptionOrNull() }.join()

        val thrown = assertIs<IllegalStateException>(refused, "a call off the Event Dispatch Thread is refused")
        assertTrue("Event Dispatch Thread" in thrown.message.orEmpty(), "the refusal names the thread rule")
    }

    @Test
    fun aRequestPendingWhenItsNodeMovesToAnotherCompositionNeverRuns() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        val moved = ListeningPanel()
        val host = JPanel()
        var calls = 0
        val action: (SwingComponentNode<*>) -> Unit = { calls++ }
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content = remember { movableContentOf { SwingNode(factory = { moved }) } }
            SwingNode(factory = { host })
            if (inOuter) {
                // Queued in the composition the node stands in, so the move this pass applies comes before the drain.
                synchronized(moved.treeLock) {
                    moved.events
                        .first()
                        .node
                        .requestAfterValidation(action)
                }
                content()
            }
        }
        awaitIdle()
        val handle = host.setContent(parent = context) { if (!inOuter) content() }
        awaitIdle()
        val node = moved.events.single().node

        inOuter = true
        awaitIdle()

        assertEquals(0, calls, "a request pending when its node moves to another composition never runs")

        synchronized(moved.treeLock) { node.requestAfterValidation(action) }
        awaitIdle()

        assertEquals(1, calls, "the moved node takes requests in its new composition")
        handle.dispose()
    }

    @Test
    fun requestsWithDifferentCallbacksUnderTheLockEachRunOnce() = runComposeSwingTest {
        val component = ListeningPanel()
        setContent { SwingNode(factory = { component }) }
        awaitIdle()
        val node = component.events.single().node
        var aCalls = 0
        var bCalls = 0
        val a: (SwingComponentNode<*>) -> Unit = { aCalls++ }
        val b: (SwingComponentNode<*>) -> Unit = { bCalls++ }

        synchronized(component.treeLock) {
            node.requestAfterValidation(a)
            node.requestAfterValidation(b)
            node.requestAfterValidation(a)
        }
        awaitIdle()

        assertEquals(1, aCalls, "a callback requested twice runs once")
        assertEquals(1, bCalls, "a different callback requested on the same node runs as well")
    }

    @Test
    fun aRequestMadeWithoutTheLockRunsAtOnceAndCancelsTheQueuedOne() = runComposeSwingTest {
        val component = ListeningPanel()
        setContent { SwingNode(factory = { component }) }
        awaitIdle()
        val node = component.events.single().node
        var calls = 0
        val action: (SwingComponentNode<*>) -> Unit = { calls++ }

        synchronized(component.treeLock) { node.requestAfterValidation(action) }
        node.requestAfterValidation(action)

        assertEquals(1, calls, "a request made without the lock runs at once")

        awaitIdle()

        assertEquals(1, calls, "it cancelled the queued request for the same callback")
    }

    @Test
    fun readsObservedUnderANodeAreGroupedByCallbackAndReobservingReplacesOnlyThatCallbacksReads() =
        runComposeSwingTest {
            val component = ListeningPanel()
            setContent { SwingNode(factory = { component }) }
            awaitIdle()
            val node = component.events.single().node
            val first = mutableIntStateOf(0)
            val second = mutableIntStateOf(0)
            val third = mutableIntStateOf(0)
            val unrelated = mutableIntStateOf(0)
            val calls = ArrayList<String>()
            val a: (SwingComponentNode<*>) -> Unit = { calls += "a" }
            val b: (SwingComponentNode<*>) -> Unit = { calls += "b" }
            node.observeReads(a) { first.intValue }
            node.observeReads(b) { second.intValue }

            // Moves the global snapshot forward, so the next observeReads replaces reads recorded under an earlier
            // snapshot even when nothing else applied one in between.
            unrelated.intValue = 1
            Snapshot.sendApplyNotifications()
            node.observeReads(a) { third.intValue }

            first.intValue = 1
            awaitIdle()
            second.intValue = 1
            awaitIdle()
            third.intValue = 1
            awaitIdle()

            assertEquals(
                listOf("b", "a"),
                calls,
                "observing again under a callback drops its earlier reads, and no other callback's",
            )
        }

    @Test
    fun aDetachedComponentNodesCallbackIsNotHeldByTheComposition() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val component = ListeningPanel()
        val state = mutableIntStateOf(0)
        setContent { if (shown) SwingNode(factory = { component }) }
        awaitIdle()
        val callback = observeUnderAFreshCallback(component.events.single().node, state)

        shown = false
        awaitIdle()

        assertTrue(isCollected(callback), "a detached node's callback must not stay reachable from the composition")
    }

    /** Declares an [ObserveOnDetachNode]. */
    private class ObserveOnDetach(
        private val observe: (SwingComponentNode<*>) -> Unit,
    ) : SwingModifier.NodeElement<ListeningPanel, ObserveOnDetachNode>() {
        override val targetType: Class<ListeningPanel> get() = ListeningPanel::class.java

        override val name: String get() = "observeOnDetach"

        override fun create(): ObserveOnDetachNode = ObserveOnDetachNode(observe)

        override fun update(node: ObserveOnDetachNode) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /** Hands the component node holding its component to [observe] as it detaches. */
    private class ObserveOnDetachNode(
        private val observe: (SwingComponentNode<*>) -> Unit,
    ) : SwingModifier.ComponentNode<ListeningPanel>() {
        override fun onDetach() = observe(component.events.first().node)
    }
}

/** Observes [watched] on [node] under a callback no one else holds, returning a weak reference to it. */
private fun observeUnderAFreshCallback(
    node: SwingComponentNode<*>,
    watched: MutableIntState,
): WeakReference<Any> {
    val onChanged: (SwingComponentNode<*>) -> Unit = { watched.intValue }
    node.observeReads(onChanged) { watched.intValue }
    return WeakReference(onChanged)
}

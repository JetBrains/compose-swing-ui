package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
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
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import javax.swing.JMenuBar
import javax.swing.JPanel
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** When a [SwingComponentNodeListener] component is told the node holding it, and what that node observes. */
class SwingComponentNodeListenerTest {
    @Test
    fun aDeclaredComponentIsToldItsNodeAttachedAndDetachedOnRemoval() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val component = ListeningPanel()
        setContent { if (shown) SwingNode(factory = { component }) }
        awaitIdle()

        val node = component.events.single().node
        assertSame(component, node.component, "the component is told the node that holds it")
        assertEquals(listOf(Event("attached", node)), component.events, "the node attaches once")

        shown = false
        awaitIdle()

        assertEquals(
            listOf(Event("attached", node), Event("detached", node)),
            component.events,
            "removing the component detaches its node",
        )
    }

    @Test
    fun aClaimedComponentIsToldItsNodeAttachedAndDetachedWhileItStaysInItsParent() = runComposeSwingTest {
        var claimed by mutableStateOf(true)
        setContent {
            SwingNode(factory = { ListeningParent() }) {
                if (claimed) ExistingSwingNode(claim = ListeningParent::part)
            }
        }
        awaitIdle()

        val parent = onNodeOfType<ListeningParent>().fetch()
        val part = parent.part
        val node = part.events.single().node
        assertSame(part, node.component, "the claimed component is told the node that claims it")
        assertEquals(listOf(Event("attached", node)), part.events, "the claim attaches its node once")

        claimed = false
        awaitIdle()

        assertEquals(
            listOf(Event("attached", node), Event("detached", node)),
            part.events,
            "ending the claim detaches its node",
        )
        assertSame(parent, part.parent, "the claimed component stays in its parent")
    }

    @Test
    fun aComponentIsToldItsNodeDetachedBeforeTheModifierTearsDown() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val component = ListeningPanel()
        setContent { if (shown) SwingNode(factory = { component }, modifier = DetachRecorder) }
        awaitIdle()

        shown = false
        awaitIdle()

        assertEquals(
            listOf("attached", "detached", "modifier detached"),
            component.events.map { it.kind },
            "the component is told before the modifier's node detaches",
        )
    }

    @Test
    fun aSetContentHostIsToldItsRootNodeAttachedAndDetachedOnDispose() = runComposeSwingTest {
        val host = ListeningPanel()
        setContent { SwingNode(factory = { JPanel(BorderLayout()).apply { add(host) } }) }
        awaitIdle()

        val handle = host.setContent {}
        awaitIdle()
        val node = host.events.single().node
        assertSame(host, node.component, "the host is told the root node of its content")

        handle.dispose()

        assertEquals(
            listOf(Event("attached", node), Event("detached", node)),
            host.events,
            "disposing the content detaches its root node",
        )
    }

    @Test
    fun aReusableContentKeyChangeDetachesTheOldNodeAndAttachesTheNewOne() = runComposeSwingTest {
        var key by mutableIntStateOf(0)
        val built = ArrayList<ListeningPanel>()
        setContent {
            ReusableContent(key) { SwingNode(factory = { ListeningPanel().also { built += it } }) }
        }
        awaitIdle()

        key = 1
        awaitIdle()

        val (first, second) = built
        val firstNode = first.events.first().node
        assertEquals(
            listOf(Event("attached", firstNode), Event("detached", firstNode)),
            first.events,
            "the old key's node detaches",
        )
        assertEquals(listOf("attached"), second.events.map { it.kind }, "the new key's node attaches")
    }

    /** No public node is reusable, so the holder is declared directly to reach its reuse callback. */
    @Test
    fun theHolderReuseCallbackReattachesAndDropsWhatItObservedAndRequestedBefore() = runComposeSwingTest {
        var key by mutableIntStateOf(0)
        val component = ListeningPanel()
        var calls = 0
        val action: (SwingComponentNode<*>) -> Unit = { calls++ }
        setContent {
            ReusableContent(key) {
                // Queued in the composition, so the reuse this pass applies comes before the queue drains.
                if (key == 1) {
                    val node = component.events.single().node
                    synchronized(component.treeLock) { node.requestAfterValidation(action) }
                }
                ReusableComposeNode<SwingNodeHolder<ListeningPanel>, SwingApplier>(
                    factory = { CreatedNodeHolder(component) },
                    update = {},
                )
            }
        }
        awaitIdle()
        val node = component.events.single().node
        val state = mutableIntStateOf(0)
        val changes = ArrayList<SwingComponentNode<*>>()
        node.observeReads({ changes += it }) { state.intValue }

        key = 1
        awaitIdle()
        state.intValue = 1
        awaitIdle()

        assertEquals(
            listOf(Event("attached", node), Event("detached", node), Event("attached", node)),
            component.events,
            "reusing the node detaches and attaches it again",
        )
        assertEquals(emptyList(), changes, "a read observed before the reuse runs no callback after it")
        assertEquals(0, calls, "a request made before the reuse never runs")
    }

    @Test
    fun aListenerThatThrowsWhenItsNodeIsReusedStillObservesChanges() = runComposeSwingTest {
        var key by mutableIntStateOf(0)
        val listener = FailingOnAttach().apply { failure = null }
        val component = FailingPanel(listener)
        setContent {
            ReusableContent(key) {
                ReusableComposeNode<SwingNodeHolder<FailingPanel>, SwingApplier>(
                    factory = { CreatedNodeHolder(component) },
                    update = {},
                )
            }
        }
        awaitIdle()
        val node = listener.events.single().node
        listener.failure = object : IllegalStateException("The listener failed on reuse") {}

        key = 1
        val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }

        assertSame(listener.failure, thrown, "the listener's original failure reaches the caller")
        assertEquals(
            listOf("attached", "detached", "attached"),
            listener.events.map { it.kind },
            "the reused node stays attached",
        )
        awaitIdle()
        listener.watched.intValue = 1
        awaitIdle()
        assertEquals(listOf(node), listener.changes, "the reused node still observes changes")
    }

    @Test
    fun aDeactivatedNodeDetachesOnceThroughItsRelease() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var shown by mutableStateOf(true)
        val built = ArrayList<ListeningPanel>()
        setContent {
            if (shown) {
                ReusableContentHost(active) { SwingNode(factory = { ListeningPanel().also { built += it } }) }
            }
        }
        awaitIdle()

        active = false
        awaitIdle()
        shown = false
        awaitIdle()

        assertEquals(
            listOf("attached", "detached"),
            built.single().events.map { it.kind },
            "the deactivation detaches the node, and its release does not detach it again",
        )
    }

    @Test
    fun readsObservedUnderANodeRunTheirCallbackWhileItIsAttachedAndNeverAfter() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val component = ListeningPanel()
        setContent { if (shown) SwingNode(factory = { component }) }
        awaitIdle()
        val node = component.events.single().node
        val state = mutableIntStateOf(0)
        val changes = ArrayList<SwingComponentNode<*>>()
        val onChanged: (SwingComponentNode<*>) -> Unit = { changes += it }
        node.observeReads(onChanged) { state.intValue }

        state.intValue = 1
        awaitIdle()

        assertEquals(listOf(node), changes, "a changed read runs the callback with the node")

        node.observeReads(onChanged) { state.intValue }
        shown = false
        awaitIdle()
        state.intValue = 2
        awaitIdle()

        assertEquals(listOf(node), changes, "a read changed after the node detaches runs no callback")
        assertFailsWith<IllegalStateException>("a detached node refuses to observe") {
            node.observeReads(onChanged) { state.intValue }
        }
    }

    @Test
    fun oneCallbackObservingUnderAModifierNodeAndAComponentNodeIsRunForBoth() = runComposeSwingTest {
        val component = ListeningPanel()
        lateinit var modifierNode: SwingModifier.Node
        setContent { SwingNode(factory = { component }, modifier = PanelProbe { modifierNode = it }) }
        awaitIdle()
        val componentNode = component.events.single().node
        val state = mutableIntStateOf(0)
        val changes = ArrayList<Any>()
        val onChanged: (Any) -> Unit = { changes += it }
        componentNode.observeReads(onChanged) { state.intValue }
        modifierNode.observeReads(onChanged) { state.intValue }

        state.intValue = 1
        awaitIdle()

        assertEquals(2, changes.size, "each node is told once")
        assertEquals(
            setOf(componentNode, modifierNode),
            changes.toSet(),
            "a callback shared by a component node and a modifier node is run with each",
        )
    }

    @Test
    fun aListenerTypesItsNodeWithAsNodeOfWhichRefusesAnotherComponent() = runComposeSwingTest {
        val component = TypedListeningPanel()
        setContent { SwingNode(factory = { component }) }
        awaitIdle()
        val node = component.nodes.single()
        val state = mutableIntStateOf(0)
        val heard = ArrayList<TypedListeningPanel>()
        val onChanged: (SwingComponentNode<TypedListeningPanel>) -> Unit = { heard += it.component }
        node.observeReads(onChanged) { state.intValue }

        state.intValue = 1
        awaitIdle()

        assertEquals(listOf(component), heard, "the callback receives the component without a cast")
        assertFailsWith<IllegalArgumentException>("a node refuses to be typed by a component it does not hold") {
            node.asNodeOf(JPanel())
        }
    }

    @Test
    fun aChangeQueuedBeforeTheNodeDetachesRunsNoCallback() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val removed = ListeningPanel()
        val kept = ListeningPanel()
        val state = mutableIntStateOf(0)
        setContent {
            if (shown) {
                SwingNode(factory = { removed })
            } else {
                // Published off the event dispatch thread, the change is posted to it, after this pass removes
                // the node.
                thread {
                    state.intValue = 1
                    Snapshot.sendApplyNotifications()
                }.join()
            }
            SwingNode(factory = { kept })
        }
        awaitIdle()
        val removedNode = removed.events.single().node
        val keptNode = kept.events.single().node
        val changes = ArrayList<SwingComponentNode<*>>()
        val onChanged: (SwingComponentNode<*>) -> Unit = { changes += it }
        removedNode.observeReads(onChanged) { state.intValue }
        keptNode.observeReads(onChanged) { state.intValue }

        shown = false
        awaitIdle()

        assertEquals(
            listOf(keptNode),
            changes,
            "a change reported before a node detached runs the callback only for the node still attached",
        )
    }

    @Test
    fun aDeactivatedNodeRefusesToObserveAndToRequest() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val built = ArrayList<ListeningPanel>()
        setContent {
            ReusableContentHost(active) { SwingNode(factory = { ListeningPanel().also { built += it } }) }
        }
        awaitIdle()
        val node = built.single().let { it.events.single().node }

        active = false
        awaitIdle()

        assertFailsWith<IllegalStateException>("a deactivated node refuses to observe") { node.observeReads({}) {} }
        assertFailsWith<IllegalStateException>("a deactivated node refuses a request") {
            node.requestAfterValidation {}
        }
    }

    @Test
    fun aComponentThatThrowsOnDetachStillHasItsModifierTornDownOnRemoval() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val component = ListeningPanel(failsOnDetach = true)
        var text by mutableStateOf("before")
        val kept = JPanel()
        setContent {
            if (shown) SwingNode(factory = { component }, modifier = DetachRecorder)
            SwingNode(factory = { kept }, update = { set(text) { name = it } })
        }
        awaitIdle()

        shown = false
        val thrown =
            assertFailsWith<IllegalStateException>("the component's failure reaches the caller") { awaitIdle() }
        assertEquals("The listener failed on detach", thrown.message, "the component's own failure reaches the caller")

        assertEquals(
            listOf("attached", "detached", "modifier detached"),
            component.events.map { it.kind },
            "the modifier's node detaches all the same",
        )
        awaitIdle()
        text = "after"
        awaitIdle()
        assertEquals("after", kept.name, "the remaining composition still updates")
    }

    @Test
    fun aDeclaredComponentThatHostsContentIsToldEachOfItsNodes() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val component = ListeningPanel()
        setContent { if (shown) SwingNode(factory = { component }) }
        awaitIdle()
        val declared = component.events.single().node

        val handle = component.setContent {}
        awaitIdle()
        val root = component.events.last().node

        assertEquals(
            listOf(Event("attached", declared), Event("attached", root)),
            component.events,
            "the component is told the node declaring it and the root node of its content",
        )

        handle.dispose()
        shown = false
        awaitIdle()

        assertEquals(
            listOf(
                Event("attached", declared),
                Event("attached", root),
                Event("detached", root),
                Event("detached", declared),
            ),
            component.events,
            "each node detaches on its own",
        )
    }

    @Test
    fun aNodeMovedToAnotherCompositionIsToldItDetachedAndAttachedAndDropsTheReadsItObservedThere() =
        runComposeSwingTest {
            var inOuter by mutableStateOf(false)
            val moved = ListeningPanel()
            val kept = ListeningPanel()
            val host = JPanel()
            val state = mutableIntStateOf(0)
            lateinit var context: CompositionContext
            lateinit var content: @Composable () -> Unit
            setContent {
                context = rememberCompositionContext()
                content = remember { movableContentOf { SwingNode(factory = { moved }) } }
                SwingNode(factory = { host })
                if (inOuter) {
                    // Published off the event dispatch thread, the change is posted to it, after this pass moves the
                    // node.
                    thread {
                        state.intValue = 1
                        Snapshot.sendApplyNotifications()
                    }.join()
                    content()
                }
            }
            awaitIdle()
            val handle =
                host.setContent(parent = context) {
                    if (!inOuter) content()
                    SwingNode(factory = { kept })
                }
            awaitIdle()
            val movedNode = moved.events.single().node
            val keptNode = kept.events.single().node
            val changes = ArrayList<SwingComponentNode<*>>()
            val onChanged: (SwingComponentNode<*>) -> Unit = { changes += it }
            movedNode.observeReads(onChanged) { state.intValue }
            keptNode.observeReads(onChanged) { state.intValue }

            inOuter = true
            awaitIdle()

            assertEquals(
                listOf(Event("attached", movedNode), Event("detached", movedNode), Event("attached", movedNode)),
                moved.events,
                "the node moved to the other composition",
            )
            assertEquals(
                listOf(keptNode),
                changes,
                "a change the old composition reported before the move runs the callback only for the node still there",
            )

            state.intValue = 2
            awaitIdle()

            assertEquals(
                listOf(keptNode, keptNode),
                changes,
                "a change after the move runs the callback only for the node still there",
            )
            handle.dispose()
        }

    @Test
    fun anAfterValidationRequestRunsOnceAndNeverAfterItsNodeDetaches() = runComposeSwingTest {
        val host = ListeningPanel()
        setContent { SwingNode(factory = { JPanel(BorderLayout()).apply { add(host) } }) }
        awaitIdle()
        val handle = host.setContent {}
        awaitIdle()
        val node = host.events.single().node
        var calls = 0
        val action: (SwingComponentNode<*>) -> Unit = { calls++ }

        synchronized(host.treeLock) {
            node.requestAfterValidation(action)
            node.requestAfterValidation(action)
        }
        assertEquals(0, calls, "a request made under the tree lock waits")
        awaitIdle()

        assertEquals(1, calls, "two requests with the same callback run once")

        synchronized(host.treeLock) { node.requestAfterValidation(action) }
        handle.dispose()
        awaitIdle()

        assertEquals(1, calls, "a request pending when the node detaches never runs")
        assertFailsWith<IllegalStateException>("a detached node refuses a request") {
            node.requestAfterValidation(action)
        }
    }

    @Test
    fun disposingContentWhoseHostThrowsOnDetachStillDetachesItsRoot() = runComposeSwingTest {
        val host = ListeningPanel(failsOnDetach = true)
        setContent { SwingNode(factory = { JPanel(BorderLayout()).apply { add(host) } }) }
        awaitIdle()
        val handle = host.setContent {}
        awaitIdle()
        val node = host.events.single().node

        val thrown =
            assertFailsWith<IllegalStateException>("the host's failure reaches the caller") {
                handle.dispose()
                awaitIdle()
            }
        assertEquals("The listener failed on detach", thrown.message, "the host's own failure reaches the caller")

        assertFailsWith<IllegalStateException>("the root node is detached all the same") { node.observeReads({}) {} }
    }

    @Test
    fun aHostThatThrowsAsContentUnderANamedParentAttachesItsRootLeavesItsCompositionUsable() = runComposeSwingTest {
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        awaitIdle()
        val listener = FailingOnAttach()
        val host = FailingPanel(listener)

        assertAReportedAttachLeavesTheNodeUsable(listener) { host.setContent(parent = context) {} }
    }

    @Test
    fun aHostThatThrowsAsItsPlaceInTheTreeAttachesItsContentRootLeavesItsCompositionUsable() = runComposeSwingTest {
        val listener = FailingOnAttach()
        val host = FailingPanel(listener)
        setContent { SwingNode(factory = { JPanel(BorderLayout()).apply { add(host) } }) }
        awaitIdle()

        assertAReportedAttachLeavesTheNodeUsable(listener) { host.setContent {} }
    }

    @Test
    fun aMenuBarThatThrowsAsItsContentRootAttachesLeavesItsCompositionUsable() = runComposeSwingTest {
        val listener = FailingOnAttach()
        val bar = FailingMenuBar(listener)
        setContent { SwingNode(factory = { JPanel(BorderLayout()).apply { add(bar) } }) }
        awaitIdle()

        assertAReportedAttachLeavesTheNodeUsable(listener) { bar.setContent {} }
    }

    @Test
    fun aDeclaredComponentThatThrowsAsItsNodeAttachesOnTheFirstPassLeavesItsCompositionUsable() = runComposeSwingTest {
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        awaitIdle()
        val listener = FailingOnAttach()
        val component = FailingPanel(listener)

        assertAReportedAttachLeavesTheNodeUsable(listener) {
            JPanel().setContent(parent = context) { SwingNode(factory = { component }) }
        }
    }

    @Test
    fun aListenerThatThrowsWhenItsNodeIsReusedIsDetachedOnceOnRelease() = runComposeSwingTest {
        var key by mutableIntStateOf(0)
        val listener = FailingOnAttach().apply { failure = null }
        val component = FailingPanel(listener)
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        awaitIdle()
        val handle =
            JPanel().setContent(parent = context) {
                ReusableContent(key) {
                    ReusableComposeNode<SwingNodeHolder<FailingPanel>, SwingApplier>(
                        factory = { CreatedNodeHolder(component) },
                        update = {},
                    )
                }
            }
        awaitIdle()
        listener.failure = object : IllegalStateException("The listener failed on reuse") {}

        key = 1
        val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
        awaitIdle()
        listener.watched.intValue = 1
        awaitIdle()
        assertEquals(listOf(listener.events.first().node), listener.changes, "the reused node still observes changes")
        handle.dispose()

        assertSame(listener.failure, thrown, "the listener's original failure reaches the caller")
        assertEquals(
            listOf("attached", "detached", "attached", "detached"),
            listener.events.map { it.kind },
            "release pairs the completed reuse attach with one detach",
        )
    }

    private suspend fun ComposeSwingTest.assertAReportedAttachLeavesTheNodeUsable(
        listener: FailingOnAttach,
        mount: () -> DisposableHandle,
    ) {
        val failure = listener.failure
        val handle = mount()
        val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
        assertSame(failure, thrown, "the listener's original failure reaches the caller")
        val node = listener.events.single().node
        awaitIdle()
        listener.watched.intValue = 1
        awaitIdle()
        assertEquals(listOf(node), listener.changes, "the attached node still observes changes")
        assertEquals(listOf(Event("attached", node)), listener.events, "the node stays attached")
        handle.dispose()
        awaitIdle()
        listener.watched.intValue = 2
        awaitIdle()
        assertEquals(listOf(node), listener.changes, "disposing drops the node's reads")
        assertEquals(
            listOf(Event("attached", node), Event("detached", node)),
            listener.events,
            "dispose pairs the reported attach with one detach",
        )
    }

    /** Observes [watched] under each node it is told attached, then throws [failure] while one is set. */
    private class FailingOnAttach : SwingComponentNodeListener {
        val watched = mutableIntStateOf(0)
        val events = ArrayList<Event>()
        val changes = ArrayList<SwingComponentNode<*>>()
        var failure: IllegalStateException? = object : IllegalStateException("The listener failed on attach") {}

        override fun onAttached(node: SwingComponentNode<*>) {
            events += Event("attached", node)
            node.observeReads({ changes += it }) { watched.intValue }
            failure?.let { throw it }
        }

        override fun onDetached(node: SwingComponentNode<*>) {
            events += Event("detached", node)
        }
    }

    private class FailingPanel(
        listener: FailingOnAttach,
    ) : JPanel(),
        SwingComponentNodeListener by listener

    private class FailingMenuBar(
        listener: FailingOnAttach,
    ) : JMenuBar(),
        SwingComponentNodeListener by listener

    /** Declares a [DetachRecorderNode]. */
    private object DetachRecorder : SwingModifier.NodeElement<ListeningPanel, DetachRecorderNode>() {
        override val targetType: Class<ListeningPanel> get() = ListeningPanel::class.java

        override val name: String get() = "detachRecorder"

        override fun create(): DetachRecorderNode = DetachRecorderNode()

        override fun update(node: DetachRecorderNode) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /** Records "modifier detached" on its component as it detaches. */
    private class DetachRecorderNode : SwingModifier.ComponentNode<ListeningPanel>() {
        override fun onDetach() {
            component.events += Event("modifier detached", component.events.first().node)
        }
    }

    /** Holds a [ListeningPanel] that [ExistingSwingNode] claims. */
    private class ListeningParent : JPanel() {
        val part = ListeningPanel().also { add(it) }
    }

    /** Types each node it is told as the node of this very panel. */
    private class TypedListeningPanel :
        JPanel(),
        SwingComponentNodeListener {
        val nodes = ArrayList<SwingComponentNode<TypedListeningPanel>>()

        override fun onAttached(node: SwingComponentNode<*>) {
            nodes += node.asNodeOf(this)
        }

        override fun onDetached(node: SwingComponentNode<*>) {
            nodes -= node.asNodeOf(this)
        }
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableComposeNode
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
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
        setContent { if (shown) SwingNode(factory = { ListeningPanel() }) }
        awaitIdle()

        val component = onNodeOfType<ListeningPanel>().fetch()
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
        setContent { if (shown) SwingNode(factory = { ListeningPanel() }, modifier = DetachRecorder) }
        awaitIdle()
        val component = onNodeOfType<ListeningPanel>().fetch()

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
        var mounted by mutableStateOf(false)
        setContent { HostedListener(mounted) }
        awaitIdle()

        mounted = true
        awaitIdle()
        val host = onNodeOfType<ListeningHost>().fetch()
        val node = host.events.single().node
        assertSame(host, node.component, "the host is told the root node of its content")

        mounted = false
        awaitIdle()

        assertEquals(
            listOf(Event("attached", node), Event("detached", node)),
            host.events,
            "disposing the content detaches its root node",
        )
    }

    @Test
    fun aReusableContentKeyChangeDetachesTheOldNodeAndAttachesTheNewOne() = runComposeSwingTest {
        var key by mutableIntStateOf(0)
        setContent {
            ReusableContent(key) { SwingNode(factory = { ListeningPanel() }) }
        }
        awaitIdle()
        val first = onNodeOfType<ListeningPanel>().fetch()

        key = 1
        awaitIdle()

        val firstNode = first.events.first().node
        assertEquals(
            listOf(Event("attached", firstNode), Event("detached", firstNode)),
            first.events,
            "the old key's node detaches",
        )
        onNodeOfType<ListeningPanel>().assertProperty(listOf("attached"), "the new key's node attaches") {
            events.map { it.kind }
        }
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
    fun aDeactivatedNodeDetachesOnceThroughItsRelease() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var shown by mutableStateOf(true)
        var constructions = 0
        setContent {
            if (shown) {
                ReusableContentHost(active) {
                    SwingNode(factory = { ListeningPanel().also { constructions++ } })
                }
            }
        }
        awaitIdle()
        val component = onNodeOfType<ListeningPanel>().fetch()

        active = false
        awaitIdle()
        shown = false
        awaitIdle()

        assertEquals(
            listOf("attached", "detached"),
            component.events.map { it.kind },
            "the deactivation detaches the node, and its release does not detach it again",
        )
        assertEquals(1, constructions, "the scenario builds exactly one component")
    }

    @Test
    fun readsObservedUnderANodeRunTheirCallbackWhileItIsAttachedAndNeverAfter() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        setContent { if (shown) SwingNode(factory = { ListeningPanel() }) }
        awaitIdle()
        val node =
            onNodeOfType<ListeningPanel>()
                .fetch()
                .events
                .single()
                .node
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
        lateinit var modifierNode: SwingModifier.Node
        setContent { SwingNode(factory = { ListeningPanel() }, modifier = PanelProbe { modifierNode = it }) }
        awaitIdle()
        val componentNode =
            onNodeOfType<ListeningPanel>()
                .fetch()
                .events
                .single()
                .node
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
        setContent { SwingNode(factory = { TypedListeningPanel() }) }
        awaitIdle()
        val component = onNodeOfType<TypedListeningPanel>().fetch()
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
        val state = mutableIntStateOf(0)
        setContent {
            if (shown) {
                SwingNode(factory = { ListeningPanel() })
            } else {
                // Published off the event dispatch thread, the change is posted to it, after this pass removes
                // the node.
                thread {
                    state.intValue = 1
                    Snapshot.sendApplyNotifications()
                }.join()
            }
            SwingNode(factory = { ListeningPanel() })
        }
        awaitIdle()
        val removedNode =
            onRoot()
                .onChildAt(0)
                .assertIsOfType<ListeningPanel>()
                .fetch()
                .events
                .single()
                .node
        val keptNode =
            onRoot()
                .onChildAt(1)
                .assertIsOfType<ListeningPanel>()
                .fetch()
                .events
                .single()
                .node
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
        setContent {
            ReusableContentHost(active) { SwingNode(factory = { ListeningPanel() }) }
        }
        awaitIdle()
        val node =
            onNodeOfType<ListeningPanel>()
                .fetch()
                .events
                .single()
                .node

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
        var text by mutableStateOf("before")
        setContent {
            if (shown) SwingNode(factory = { ListeningPanel(failsOnDetach = true) }, modifier = DetachRecorder)
            SwingNode(
                factory = { JPanel() },
                update = { set(text) { name = it } },
            )
        }
        awaitIdle()
        val component = onNodeOfType<ListeningPanel>().fetch()

        shown = false
        val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
        assertSame(component.detachFailure, thrown, "the component's own failure is reported")

        assertEquals(
            listOf("attached", "detached", "modifier detached"),
            component.events.map { it.kind },
            "the modifier's node detaches all the same",
        )
        awaitIdle()
        text = "after"
        awaitIdle()
        onRoot().onChild().assertProperty("after", "the remaining composition still updates") { name }
    }

    @Test
    fun aDeclaredComponentThatHostsContentIsToldEachOfItsNodes() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        var mounted by mutableStateOf(false)
        setContent { if (shown) DeclaredHost(mounted) }
        awaitIdle()
        val component = onNodeOfType<ListeningHost>().fetch()
        val declared = component.events.single().node

        mounted = true
        awaitIdle()
        val root = component.events.last().node

        assertEquals(
            listOf(Event("attached", declared), Event("attached", root)),
            component.events,
            "the component is told the node declaring it and the root node of its content",
        )

        mounted = false
        awaitIdle()
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
            val state = mutableIntStateOf(0)
            setContent {
                val content =
                    remember {
                        movableContentOf {
                            SwingNode(factory = { ListeningPanel() }, modifier = SwingModifier.testTag("moved"))
                        }
                    }
                NestedContentHost {
                    if (!inOuter) content()
                    SwingNode(factory = { ListeningPanel() }, modifier = SwingModifier.testTag("kept"))
                }
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
            val movedNode =
                onNodeWithTag("moved")
                    .assertIsOfType<ListeningPanel>()
                    .fetch()
                    .events
                    .single()
                    .node
            val keptNode =
                onNodeWithTag("kept")
                    .assertIsOfType<ListeningPanel>()
                    .fetch()
                    .events
                    .single()
                    .node
            val changes = ArrayList<SwingComponentNode<*>>()
            val onChanged: (SwingComponentNode<*>) -> Unit = { changes += it }
            movedNode.observeReads(onChanged) { state.intValue }
            keptNode.observeReads(onChanged) { state.intValue }

            inOuter = true
            awaitIdle()

            onNodeWithTag("moved").assertIsOfType<ListeningPanel>().assertProperty(
                listOf(Event("attached", movedNode), Event("detached", movedNode), Event("attached", movedNode)),
                "the node moved to the other composition",
            ) { events }
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
        }

    @Test
    fun aNodeBelowAMovedNodeIsToldItDetachedAndAttachedAndDropsTheReadsItObservedThere() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        setContent {
            val content =
                remember {
                    movableContentOf {
                        SwingNode(factory = { JPanel() }) {
                            SwingNode(factory = { ListeningPanel() })
                        }
                    }
                }
            NestedContentHost { if (!inOuter) content() }
            if (inOuter) content()
        }
        awaitIdle()
        val node =
            onNodeOfType<ListeningPanel>()
                .assertIsOfType<ListeningPanel>()
                .fetch()
                .events
                .single()
                .node
        val state = mutableIntStateOf(0)
        val changes = ArrayList<SwingComponentNode<*>>()
        node.observeReads({ changes += it }) { state.intValue }

        inOuter = true
        awaitIdle()
        state.intValue = 1
        awaitIdle()

        onNodeOfType<ListeningPanel>().assertIsOfType<ListeningPanel>().assertProperty(
            listOf(Event("attached", node), Event("detached", node), Event("attached", node)),
            "moving a node moves the nodes below it too",
        ) { events }
        assertEquals(emptyList(), changes, "a read observed in the old composition runs no callback")
    }

    @Test
    fun anAfterValidationRequestRunsOnceAndNeverAfterItsNodeDetaches() = runComposeSwingTest {
        setContent { HostedListener() }
        awaitIdle()
        val host = onNodeOfType<ListeningHost>().fetch()
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
        host.unmount()
        awaitIdle()

        assertEquals(1, calls, "a request pending when the node detaches never runs")
        assertFailsWith<IllegalStateException>("a detached node refuses a request") {
            node.requestAfterValidation(action)
        }
    }

    @Test
    fun disposingContentWhoseHostThrowsOnDetachStillDetachesItsRoot() = runComposeSwingTest {
        setContent { HostedListener(failsOnDetach = true) }
        awaitIdle()
        val host = onNodeOfType<ListeningHost>().fetch()
        val node = host.events.single().node

        host.unmount()
        val thrown =
            assertFailsWith<IllegalStateException>("the host's failure is reported") { awaitIdle() }
        assertSame(host.detachFailure, thrown, "the host's own failure is reported")

        assertFailsWith<IllegalStateException>("the root node is detached all the same") { node.observeReads({}) {} }
    }

    @Composable
    private fun HostedListener(
        mounted: Boolean = true,
        failsOnDetach: Boolean = false,
    ) {
        SwingNode(
            factory = { HostingPanel() },
            update = {
                set(failsOnDetach) { hostFailsOnDetach(it) }
                set(mounted) { mount(it) }
            },
            onRelease = { unmount() },
        )
    }

    @Composable
    private fun DeclaredHost(mounted: Boolean) {
        SwingNode(
            factory = { ListeningHost() },
            update = { set(mounted) { mount(it) } },
            onRelease = { unmount() },
        )
    }

    /** Holds a [ListeningHost] that no node declares. */
    private class HostingPanel : JPanel(BorderLayout()) {
        private val host = ListeningHost().also { add(it) }

        fun hostFailsOnDetach(value: Boolean) {
            host.failsOnDetach = value
        }

        fun mount(mounted: Boolean) = host.mount(mounted)

        fun unmount() = host.unmount()
    }

    /** A [ListeningPanel] that also mounts empty content on itself and records the nodes it is told. */
    private class ListeningHost :
        JPanel(),
        SwingComponentNodeListener {
        var failsOnDetach = false
        val events = ArrayList<Event>()
        val detachFailure = IllegalStateException("The listener failed on detach")
        private var content: DisposableHandle? = null

        fun mount(mounted: Boolean) {
            unmount()
            if (mounted) content = setContent {}
        }

        fun unmount() {
            val handle = content
            content = null
            handle?.dispose()
        }

        override fun onAttached(node: SwingComponentNode<*>) {
            events += Event("attached", node)
        }

        override fun onDetached(node: SwingComponentNode<*>) {
            events += Event("detached", node)
            if (failsOnDetach) throw detachFailure
        }
    }

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

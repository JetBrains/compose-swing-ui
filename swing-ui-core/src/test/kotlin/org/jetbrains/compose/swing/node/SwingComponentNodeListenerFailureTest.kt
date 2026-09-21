// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.ReusableComposeNode
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.BorderLayout
import javax.swing.JMenuBar
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** When a [SwingComponentNodeListener] throws on attach, its failure is reported and its node stays attached. */
class SwingComponentNodeListenerFailureTest {
    @Test
    fun aListenerThatThrowsWhenItsNodeIsReusedIsReportedAndLeftAttached() = runComposeSwingTest {
        var key by mutableIntStateOf(0)
        var shown by mutableStateOf(true)
        val listener = FailingOnAttach().apply { failure = null }
        setContent {
            if (shown) {
                ReusableContent(key) {
                    ReusableComposeNode<SwingNodeHolder<FailingPanel>, SwingApplier>(
                        factory = { CreatedNodeHolder(FailingPanel(listener)) },
                        update = {},
                    )
                }
            }
        }
        awaitIdle()
        val node = listener.events.single().node
        val failure = IllegalStateException("The listener failed on reuse")
        listener.failure = failure

        key = 1
        val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }

        assertSame(failure, thrown, "the listener's own failure is reported")
        assertEquals(
            listOf("attached", "detached", "attached"),
            listener.events.map { it.kind },
            "the reused node is attached all the same",
        )
        assertTrue(node.isAttached, "the reused node stays attached to observe reads")
        listener.watched.intValue = 1
        awaitIdle()
        assertEquals(listOf(node), listener.changes, "the reused node still observes changes")

        listener.failure = null
        key = 2
        awaitIdle()

        assertEquals(
            listOf("attached", "detached", "attached", "detached", "attached"),
            listener.events.map { it.kind },
            "the composition keeps updating",
        )

        shown = false
        awaitIdle()

        assertEquals(
            listOf("attached", "detached", "attached", "detached", "attached", "detached"),
            listener.events.map { it.kind },
            "release pairs the reused node's last attach with one detach",
        )
    }

    @TestFactory
    fun aListenerThatThrowsOnAttachIsReportedAndLeftAttached(): List<DynamicTest> = listOf(
        failingOnAttach("a host's content root under a named parent") { listener ->
            val context = namedParent()
            FailingPanel(listener).setContent(parent = context) {}
        },
        failingOnAttach("a host's content root once it is in the tree") { listener ->
            var mounted by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { FailingPanelHost(listener) },
                    update = { set(mounted) { mount(it) } },
                    onRelease = { unmount() },
                )
            }
            awaitIdle()
            mounted = true
            DisposableHandle { mounted = false }
        },
        failingOnAttach("a menu bar's content root") { listener ->
            var mounted by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { FailingMenuBarHost(listener) },
                    update = { set(mounted) { mount(it) } },
                    onRelease = { unmount() },
                )
            }
            awaitIdle()
            mounted = true
            DisposableHandle { mounted = false }
        },
        failingOnAttach("a declared component on the first pass") { listener ->
            val context = namedParent()
            JPanel().setContent(parent = context) { SwingNode(factory = { FailingPanel(listener) }) }
        },
    )

    private suspend fun ComposeSwingTest.namedParent(): CompositionContext {
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        awaitIdle()
        return context
    }

    /**
     * A case named [name]: [mount] declares a component whose listener throws on attach and returns the handle that
     * disposes it. The failure is the listener's own and reaches the thread's uncaught-exception handler, the node the
     * listener observed under stays attached and its reads keep running, and disposing tells the node it detached
     * once and stops its reads.
     */
    private fun failingOnAttach(
        name: String,
        mount: suspend ComposeSwingTest.(FailingOnAttach) -> DisposableHandle,
    ): DynamicTest = DynamicTest.dynamicTest(name) {
        runComposeSwingTest {
            val listener = FailingOnAttach()
            val failure = listener.failure
            val handle = mount(listener)
            val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
            assertSame(failure, thrown, "the listener's own failure is reported")
            val node = listener.events.single().node

            listener.failure = null
            listener.watched.intValue = 1
            awaitIdle()

            assertEquals(listOf(node), listener.changes, "a read observed before the failure still runs its callback")

            handle.dispose()
            awaitIdle()
            listener.watched.intValue = 2
            awaitIdle()

            assertEquals(
                listOf(Event("attached", node), Event("detached", node)),
                listener.events,
                "the node is told it detached once",
            )
            assertEquals(listOf(node), listener.changes, "disposing drops the node's reads")
        }
    }

    /** Observes [watched] under each node it is told attached, then throws [failure] while one is set. */
    private class FailingOnAttach : SwingComponentNodeListener {
        val watched = mutableIntStateOf(0)
        val events = ArrayList<Event>()
        val changes = ArrayList<SwingComponentNode<*>>()
        var failure: IllegalStateException? = IllegalStateException("The listener failed on attach")

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

    /** Holds a [FailingPanel] that no node declares and mounts empty content on it. */
    private class FailingPanelHost(
        listener: FailingOnAttach,
    ) : JPanel(BorderLayout()) {
        private val failing = FailingPanel(listener).also { add(it) }
        private var content: DisposableHandle? = null

        fun mount(mounted: Boolean) {
            unmount()
            if (mounted) content = failing.setContent {}
        }

        fun unmount() {
            val handle = content
            content = null
            handle?.dispose()
        }
    }

    private class FailingMenuBar(
        listener: FailingOnAttach,
    ) : JMenuBar(),
        SwingComponentNodeListener by listener

    /** Holds a [FailingMenuBar] that no node declares and mounts empty content on it. */
    private class FailingMenuBarHost(
        listener: FailingOnAttach,
    ) : JPanel(BorderLayout()) {
        private val failing = FailingMenuBar(listener).also { add(it) }
        private var content: DisposableHandle? = null

        fun mount(mounted: Boolean) {
            unmount()
            if (mounted) content = failing.setContent {}
        }

        fun unmount() {
            val handle = content
            content = null
            handle?.dispose()
        }
    }
}

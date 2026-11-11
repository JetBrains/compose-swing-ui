// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.ObserverModifierNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.node.requestAfterValidation
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.setContentAsInteropHost
import org.jetbrains.compose.swing.setContentAsMenuInteropHost
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the thread-confinement contract of the composition entry points, both ways: every `setContent`
 * overload must run on the Swing Event Dispatch Thread, and so must disposing what it returns - a
 * disposal empties the caller's container, which is as much a Swing mutation as mounting into it. A
 * call from any other thread fails immediately with an [IllegalStateException] naming the offending
 * thread - instead of mutating Swing state off the EDT.
 *
 * Each case performs the call on a named worker thread and joins it, so the assertions run on the
 * test thread against the failure the worker actually observed.
 */
class EventDispatchThreadContractTest {
    @TestFactory
    fun setContentOffTheEventDispatchThreadFailsFast(): List<DynamicTest> = listOf<Pair<String, () -> Unit>>(
        "container" to { JPanel().setContent { } },
        "menu bar" to { JMenuBar().setContent { } },
    ).map { (name, setContent) -> dynamicTest(name) { assertRejectedOffEdt(setContent) } } +
        dynamicTest("popup") {
            runTest {
                withRecomposer { recomposer ->
                    assertRejectedOffEdt { JPopupMenu().setContent(recomposer.compositionContext) { } }
                }
            }
        }

    @Test
    fun windowSetContentOffTheEventDispatchThreadFailsFast() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = JFrame()
        try {
            assertRejectedOffEdt { frame.setContent { } }
        } finally {
            frame.dispose()
        }
    }

    @TestFactory
    fun contentDisposalOffTheEventDispatchThreadFailsFast(): List<DynamicTest> =
        listOf<Pair<String, (CompositionContext) -> MountedContent>>(
            "container" to ::mountedContainer,
            "interop host" to ::mountedInteropHost,
            "menu interop host" to ::mountedMenuInteropHost,
            "popup" to ::mountedPopup,
        ).map { (name, mount) -> dynamicTest(name) { runTest { assertDisposalRejectedOffEdt(mount) } } }

    @TestFactory
    fun contentDisposalOffTheEventDispatchThreadKeepsRefusingASecondSetContent(): List<DynamicTest> =
        listOf<Pair<String, (CompositionContext) -> OccupiedHost>>(
            "container" to { parent ->
                val panel = JPanel()
                OccupiedHost(panel.setContent(parent) { }) { panel.setContent(parent) { } }
            },
            "popup" to { parent ->
                val popup = JPopupMenu()
                OccupiedHost(popup.setContent(parent) { }) { popup.setContent(parent) { } }
            },
        ).map { (name, mount) ->
            dynamicTest(name) {
                runTest {
                    withRecomposer { recomposer ->
                        val mounted = onEventDispatchThread { mount(recomposer.compositionContext) }
                        try {
                            assertRejectedOffEdt { mounted.handle.dispose() }

                            onEventDispatchThread {
                                assertFailsWith<IllegalStateException> { mounted.setContentAgain() }
                            }
                        } finally {
                            onEventDispatchThread { mounted.handle.dispose() }
                        }
                    }
                }
            }
        }

    @Test
    fun interopHostDisposalOffTheEventDispatchThreadKeepsItsPublishedContext() = runTest {
        val panel = JPanel()
        withRecomposer { recomposer ->
            val handle =
                onEventDispatchThread { panel.setContentAsInteropHost(recomposer.compositionContext) { } }
            try {
                assertRejectedOffEdt { handle.dispose() }

                assertSame(
                    recomposer.compositionContext,
                    onEventDispatchThread { panel.findParentCompositionContext() },
                    "a disposal rejected off the Event Dispatch Thread must leave the interop host's " +
                        "published context in place",
                )
            } finally {
                onEventDispatchThread { handle.dispose() }
            }
        }
    }

    @Test
    fun recomposerDisposalOffTheEventDispatchThreadFailsFast() {
        val panel = JPanel()
        lateinit var recomposer: SwingRecomposer
        SwingUtilities.invokeAndWait { recomposer = SwingRecomposer.create(panel) }
        try {
            assertRejectedOffEdt { recomposer.dispose() }
        } finally {
            SwingUtilities.invokeAndWait { recomposer.dispose() }
        }
    }

    @Test
    fun runSwingTestRunsItsBodyAndResumesOnTheEventDispatchThread() = runSwingTest {
        assertTrue(SwingUtilities.isEventDispatchThread())

        yield()

        assertTrue(SwingUtilities.isEventDispatchThread())
    }

    @Test
    fun runSwingTestPropagatesTheBodyFailureByIdentity() {
        val expected = object : IllegalStateException("body failure") {}

        val thrown =
            assertFailsWith<IllegalStateException> {
                runSwingTest { throw expected }
            }

        assertSame(expected, thrown)
    }

    @Test
    fun runSwingTestPropagatesAChildCoroutineFailureByIdentity() {
        val expected = object : IllegalStateException("child failure") {}

        val thrown =
            assertFailsWith<IllegalStateException> {
                runSwingTest {
                    launch { throw expected }
                    yield()
                }
            }

        assertSame(expected, thrown)
    }

    @Test
    fun runSwingTestTimeoutCancelsAnAwaitingBodyAndRunsCleanupOnTheEventDispatchThread() {
        var bodyStarted = false
        var cleanupOnEdt = false

        assertFailsWith<AssertionError> {
            runSwingTest(timeout = 1.seconds) {
                bodyStarted = true
                try {
                    awaitCancellation()
                } finally {
                    cleanupOnEdt = SwingUtilities.isEventDispatchThread()
                }
            }
        }

        assertTrue(bodyStarted)
        assertTrue(cleanupOnEdt)
    }

    @Test
    fun modifierNodeObservationOffTheEventDispatchThreadFailsFast() {
        val panel = JPanel()
        val watched = mutableIntStateOf(0)
        lateinit var recomposer: SwingRecomposer
        var created: ObservingProbeNode? = null
        SwingUtilities.invokeAndWait {
            recomposer = SwingRecomposer.create(panel)
            panel.setContent(parent = recomposer.compositionContext) {
                SwingNode(
                    factory = { JPanel() },
                    modifier = SwingModifier then ObservingProbe { created = it },
                )
            }
        }
        try {
            val node = assertNotNull(created, "the modifier node must be created and attached")
            assertRejectedOffEdt { node.observeReads { watched.intValue } }
            assertRejectedOffEdt { node.observeReads<ObservingProbeNode>({ it.changes++ }) { watched.intValue } }
            assertRejectedOffEdt { node.requestAfterValidation<ObservingProbeNode> { } }

            SwingUtilities.invokeAndWait {
                watched.intValue = 1
                Snapshot.sendApplyNotifications()
            }
            SwingUtilities.invokeAndWait { }
            assertEquals(0, node.changes, "a call rejected off the Event Dispatch Thread must record no read")
        } finally {
            SwingUtilities.invokeAndWait { recomposer.dispose() }
        }
    }

    /**
     * Runs [call] on a worker thread named [OFF_EDT_THREAD_NAME] and asserts it was rejected with the
     * thread-confinement failure, which names the calling thread so the author can locate the call.
     */
    private fun assertRejectedOffEdt(call: () -> Unit) {
        var failure: Throwable? = null
        val worker =
            Thread({ failure = runCatching(call).exceptionOrNull() }, OFF_EDT_THREAD_NAME)
        worker.start()
        worker.join(JOIN_TIMEOUT.inWholeMilliseconds)

        val thrown = assertNotNull(failure, "the call off the Event Dispatch Thread must fail")
        assertIs<IllegalStateException>(thrown, "the off-EDT call must be rejected as illegal state")
        val message = assertNotNull(thrown.message, "the failure must carry a diagnostic message")
        assertTrue(
            "Event Dispatch Thread" in message,
            "the failure must state the Event Dispatch Thread requirement, but was: $message",
        )
        assertTrue(
            OFF_EDT_THREAD_NAME in message,
            "the failure must name the calling thread, but was: $message",
        )
    }

    private fun mountedContainer(parent: CompositionContext): MountedContent {
        val panel = JPanel()
        return MountedContent(
            panel.setContent(parent) { Label("inner") },
            items = { panel.components.toList() },
        )
    }

    private fun mountedInteropHost(parent: CompositionContext): MountedContent {
        val panel = JPanel()
        return MountedContent(
            panel.setContentAsInteropHost(parent) { Label("inner") },
            items = { panel.components.toList() },
        )
    }

    private fun mountedMenuInteropHost(parent: CompositionContext): MountedContent {
        val menuBar = JMenuBar()
        return MountedContent(
            menuBar.setContentAsMenuInteropHost(parent) { Menu("File") },
            items = { menuBar.components.toList() },
        )
    }

    private fun mountedPopup(parent: CompositionContext): MountedContent {
        val popup = JPopupMenu()
        return MountedContent(
            popup.setContent(parent) { MenuItem(text = "Cut", onClick = {}) },
            items = { popup.components.toList() },
        )
    }

    private suspend fun assertDisposalRejectedOffEdt(mount: (CompositionContext) -> MountedContent) {
        withRecomposer { recomposer ->
            val mounted = onEventDispatchThread { mount(recomposer.compositionContext) }
            try {
                val itemsBefore = onEventDispatchThread { mounted.items() }
                assertTrue(itemsBefore.isNotEmpty(), "the mounted content must have composed a component")

                assertRejectedOffEdt { mounted.handle.dispose() }

                assertEquals(
                    itemsBefore,
                    onEventDispatchThread { mounted.items() },
                    "a disposal rejected off the Event Dispatch Thread must leave the composed components",
                )
            } finally {
                onEventDispatchThread { mounted.handle.dispose() }
            }
        }
    }

    private suspend fun withRecomposer(body: suspend (SwingRecomposer) -> Unit) {
        val recomposer = onEventDispatchThread { SwingRecomposer.create(JPanel()) }
        try {
            body(recomposer)
        } finally {
            onEventDispatchThread { recomposer.dispose() }
        }
    }

    private suspend fun <T> onEventDispatchThread(read: () -> T): T = withContext(Dispatchers.Swing) { read() }

    /**
     * Content mounted by a case of the disposal matrix: its [handle] and the [items] it composed into its
     * container, which a rejected disposal must not change. [items] is read on the Event Dispatch Thread.
     */
    private class MountedContent(
        val handle: DisposableHandle,
        val items: () -> List<Component>,
    )

    /**
     * Content a host takes one at a time: its [handle] and [setContentAgain], which sets further content
     * on the same host and is refused while [handle] is undisposed.
     */
    private class OccupiedHost(
        val handle: DisposableHandle,
        val setContentAgain: () -> Unit,
    )

    private class ObservingProbe(
        private val onCreate: (ObservingProbeNode) -> Unit,
    ) : SwingModifier.NodeElement<JPanel, ObservingProbeNode>() {
        override val targetType: Class<JPanel> get() = JPanel::class.java
        override val additive: Boolean get() = true

        override fun create(): ObservingProbeNode = ObservingProbeNode().also(onCreate)

        override fun update(node: ObservingProbeNode) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private class ObservingProbeNode :
        SwingModifier.ComponentNode<JPanel>(),
        ObserverModifierNode {
        var changes = 0

        override fun onObservedReadsChanged() {
            changes++
        }
    }

    private companion object {
        const val OFF_EDT_THREAD_NAME: String = "off-edt-caller"
        val JOIN_TIMEOUT = 10.seconds
    }
}

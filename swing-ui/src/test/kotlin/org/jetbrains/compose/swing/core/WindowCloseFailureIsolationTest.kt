package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.currentCompositionLocalContext
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.window.LocalWindow
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.GraphicsEnvironment
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import java.beans.PropertyChangeListener
import javax.swing.JFrame
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.awt.Window as AwtWindow

class WindowCloseFailureIsolationTest {
    @TestFactory
    fun closingTheWindowDisposesBothRootsAndRetiresItsOwnerWhateverFails(): List<DynamicTest> {
        val rootFailures = List(2) { IllegalStateException("root-$it disposal failed") }
        val leftRootFailures = List(2) { IllegalStateException("left root-$it disposal failed") }
        val listenerFailure = IllegalStateException("property listener failed")
        val watchFailure = IllegalStateException("watch removal failed")
        val failing =
            PropertyChangeListener { event ->
                if (event.oldValue != null && event.newValue == null) throw listenerFailure
            }
        return listOf(
            closes("with no failure", failures = emptyList()),
            closes("when the first root's disposal throws", failures = rootFailures.take(1)) {
                it.mount(firstFailure = rootFailures[0])
            },
            closes(
                "when several roots fail to dispose",
                failures = rootFailures,
                newFixture = { WindowFixture(rootFailures.map { FailingRemovalPanel(listenerRemoval = it) }) },
            ),
            closes(
                "when a listener fails as the recomposer ends",
                failures = listOf(listenerFailure),
                afterClose = { it.window.rootPane.removePropertyChangeListener(failing) },
            ) {
                it.mount()
                it.window.rootPane.addPropertyChangeListener(failing)
            },
            // A root out of the window's tree is reached only when the window's recomposer ends.
            closes(
                "when roots out of the window's tree fail to dispose and so does the recomposer's watch",
                failures = listOf(watchFailure) + leftRootFailures,
                newFixture = {
                    WindowFixture(
                        leftRootFailures.map { FailingRemovalPanel(listenerRemoval = it) },
                        newWindow = { FailingRemovalFrame(propertyListenerRemoval = watchFailure) },
                    )
                },
            ) {
                it.mount()
                it.panels.forEach(it.window.contentPane::remove)
            },
        )
    }

    @TestFactory
    fun aFailingContentDisposalWithdrawsItsWindowAndLeavesTheWindowsRecomposerLive(): List<DynamicTest> {
        val effectFailure = IllegalStateException("effect disposal failed")
        val removalFailure = IllegalStateException("listener removal failed")
        return listOf(
            withdraws("when an effect's disposal throws", effectFailure) { it.mount(firstFailure = effectFailure) },
            withdraws(
                "when a hierarchy listener's removal throws",
                removalFailure,
                panels = listOf(FailingRemovalPanel(listenerRemoval = removalFailure), FailingRemovalPanel()),
            ),
        )
    }

    private fun withdraws(
        name: String,
        failure: Throwable,
        panels: List<FailingRemovalPanel> = List(2) { FailingRemovalPanel() },
        arrange: (WindowFixture) -> Unit = { it.mount() },
    ) = DynamicTest.dynamicTest(name) {
        runSwingTest {
            val fixture = WindowFixture(panels)
            val probe = FailingRemovalPanel()
            var observing: DisposableHandle? = null
            try {
                arrange(fixture)
                val recomposer = assertNotNull(fixture.window.findRecomposer())
                var observed: AwtWindow? = null
                observing =
                    probe.setContent(parent = recomposer) {
                        CompositionLocalProvider(fixture.locals) { observed = LocalWindow.current }
                    }
                assertSame(fixture.window, observed, "mounted content must be given its owning window")

                assertSame(failure, assertFailsWith<IllegalStateException> { fixture.disposeFirst() })
                awaitUntil("disposed content withdraws its window from the locals it captured") { observed == null }
                assertTrue(recomposer.isLive)
                assertSame(recomposer, fixture.window.findRecomposer())
                assertEquals("root-1", (panels[1].components.single() as JLabel).text)
                assertEquals(1, panels[0].detachments)
                fixture.disposeFirst()
                assertEquals(listOf(1, 0), fixture.disposals)
            } finally {
                observing?.dispose()
                fixture.cleanup()
            }
        }
    }

    private fun closes(
        name: String,
        failures: List<Throwable>,
        newFixture: () -> WindowFixture = { WindowFixture() },
        afterClose: (WindowFixture) -> Unit = {},
        arrange: (WindowFixture) -> Unit = { it.mount() },
    ) = DynamicTest.dynamicTest(name) {
        runSwingTest {
            val fixture = newFixture()
            try {
                arrange(fixture)
                awaitUntil("the roots' effects start") { fixture.effects.size == fixture.panels.size }
                var thrown: Throwable? = null
                val reported = reportedUncaught { thrown = runCatching { fixture.close() }.exceptionOrNull() }
                afterClose(fixture)
                assertSame(failures.firstOrNull(), thrown, "the first failure reaches the caller")
                assertEquals(failures.drop(1), thrown?.suppressed?.toList().orEmpty(), "later failures ride on it")
                assertEquals(emptyList(), reported, "a failure is thrown, not reported")
                assertEquals(listOf(1, 1), fixture.disposals)
                fixture.assertRetired()
                awaitUntil("the roots' running effects are cancelled") { fixture.effects.all(Job::isCancelled) }
                fixture.close()
                assertEquals(listOf(1, 1), fixture.disposals)

                val (reused) = fixture.panels
                fixture.panels.forEach { it.listenerRemoval = null }
                reused.setContent { Label("replacement") }.dispose()
            } finally {
                fixture.cleanup()
            }
        }
    }

    private class WindowFixture(
        val panels: List<FailingRemovalPanel> = List(2) { FailingRemovalPanel() },
        newWindow: () -> JFrame = { JFrame() },
    ) {
        val window =
            run {
                assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
                newWindow()
            }
        private val handles = mutableListOf<DisposableHandle>()
        val disposals = mutableListOf(0, 0)
        val effects = mutableListOf<Job>()
        lateinit var locals: CompositionLocalContext
        private var gainedListeners = emptyList<WindowListener>()
        private lateinit var recomposer: Recomposer
        private val clocksBefore = EventDispatchHook.subscriberCount(window.toolkit)

        init {
            panels.forEach(window.contentPane::add)
        }

        fun mount(firstFailure: Throwable? = null) {
            panels.forEachIndexed { index, panel ->
                val content: @Composable () -> Unit = {
                    if (index == 0) locals = currentCompositionLocalContext
                    LaunchedEffect(Unit) {
                        effects += checkNotNull(coroutineContext[Job])
                        awaitCancellation()
                    }
                    DisposableEffect(Unit) {
                        onDispose {
                            disposals[index]++
                            if (index == 0) firstFailure?.let { throw it }
                        }
                    }
                    Label(text = "root-$index")
                }
                handles += panel.setContent(content = content)
                if (index == 0) {
                    // The window holds no listener until its content gives it a recomposer.
                    gainedListeners = window.windowListeners.toList()
                    assertTrue(gainedListeners.isNotEmpty())
                    recomposer = assertNotNull(window.findRecomposer())
                }
                assertEquals("root-$index", (panel.components.single() as JLabel).text)
                assertSame(recomposer, panel.findRecomposer())
            }
            assertEquals(listOf(0, 0), disposals)
            assertTrue(recomposer.isLive)
        }

        fun disposeFirst() {
            handles.first().dispose()
        }

        fun close() {
            window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSED))
        }

        fun assertRetired() {
            assertFalse(recomposer.isLive, "window close must end its recomposer")
            assertNull(window.findRecomposer())
            assertTrue(window.windowListeners.none { it in gainedListeners }, "window close must remove its reaper")
            val clocksAfter = EventDispatchHook.subscriberCount(window.toolkit)
            assertEquals(clocksBefore, clocksAfter, "window close must retire its clock")
            panels.forEach { assertNull(it.findRecomposer()) }
        }

        fun cleanup() {
            handles.forEach { runCatching { it.dispose() } }
            window.dispose()
        }
    }
}

private val Recomposer.isLive: Boolean get() = currentState.value > Recomposer.State.ShuttingDown

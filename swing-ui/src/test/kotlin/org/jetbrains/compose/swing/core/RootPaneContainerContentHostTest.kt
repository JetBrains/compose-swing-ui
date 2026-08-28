package org.jetbrains.compose.swing.core

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.util.set
import java.awt.Container
import java.awt.Rectangle
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

/**
 * `Container.setContent` accepts any container, a root-pane container such as a [JInternalFrame]
 * included. Such a container forwards `add` to its content pane, so the composition's children live on
 * the content pane and every structural change - the last of them being the dispose - has to address
 * them there. What the container holds itself is its own affair, the root pane and whatever else the
 * installed look and feel gave it, and the composition neither adds to it nor takes from it.
 *
 * The frame here is stamped with the test's recomposer, which is how a window publishes its own, so this
 * exercises the real self-first resolution off-screen and needs no display.
 */
class RootPaneContainerContentHostTest {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(Dispatchers.Swing + Job() + clock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val handles = mutableListOf<DisposableHandle>()
    private var frameTimeNanos = 0L

    init {
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    @AfterTest
    fun tearDown() {
        onEdt { handles.forEach { it.dispose() } }
        recomposer.cancel()
        scope.cancel()
    }

    private fun hostFrame(): JInternalFrame = onEdt {
        JInternalFrame("host").apply {
            bounds = Rectangle(0, 0, SIZE, SIZE)
            this[COMPOSITION_KEY] = recomposer
        }
    }

    @Test
    fun aRootPaneContainerHostsTheCompositionOnItsContentPane() {
        val host = hostFrame()
        val ownChildren = onEdt { host.components.toList() }
        var text by mutableStateOf("first")
        onEdt { handles += host.setContent { Label(text = text) } }
        waitForIdle()

        assertEquals(
            ownChildren,
            onEdt { host.components.toList() },
            "the host itself should hold nothing beyond the children it came with",
        )
        assertEquals("first", labelText(host), "the content should be composed onto the host's content pane")

        onEdt { text = "second" }
        waitForIdle()
        assertEquals("second", labelText(host), "the hosted content should recompose in place")
    }

    @Test
    fun disposingTheCompositionRemovesItsChildrenAndLeavesTheRootPaneInstalled() {
        val host = hostFrame()
        val ownChildren = onEdt { host.components.toList() }
        val handle = onEdt { host.setContent { Label(text = "body") } }
        handles += handle
        waitForIdle()

        val contentPane = onEdt { host.contentPane }
        assertEquals(1, onEdt { contentPane.componentCount }, "the composed child should be on the content pane")

        onEdt { handle.dispose() }

        assertEquals(
            ownChildren,
            onEdt { host.components.toList() },
            "disposing the composition must leave the host with the children it came with",
        )
        assertSame(contentPane, onEdt { host.contentPane }, "the host should keep the content pane it had")
        assertEquals(
            0,
            onEdt { contentPane.componentCount },
            "disposing the composition must remove the children it put on the content pane",
        )
    }

    /**
     * The text of the single composed label. Read off the content pane rather than the frame, because a
     * frame's own title pane carries a label of its own.
     */
    private fun labelText(frame: JInternalFrame): String = onEdt {
        val labels = mutableListOf<JLabel>()

        fun visit(component: Container) {
            for (child in component.components) {
                if (child is JLabel) labels += child
                if (child is Container) visit(child)
            }
        }
        visit(frame.contentPane)
        labels.single().text
    }

    private fun waitForIdle() {
        var iterations = 0
        while (true) {
            onEdt { Snapshot.sendApplyNotifications() }
            frameTimeNanos += FRAME_INTERVAL_NANOS
            clock.sendFrame(frameTimeNanos)
            SwingUtilities.invokeAndWait { }
            if (!recomposer.hasPendingWork && !Snapshot.current.hasPendingChanges()) return
            if (++iterations >= MAX_IDLE_FRAMES) {
                throw AssertionError(
                    "waitForIdle did not settle after $MAX_IDLE_FRAMES frames " +
                        "(hasPendingWork=${recomposer.hasPendingWork}, " +
                        "hasPendingChanges=${Snapshot.current.hasPendingChanges()}).",
                )
            }
        }
    }

    private fun <T> onEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        var outcome: Result<T>? = null
        SwingUtilities.invokeAndWait { outcome = runCatching(action) }
        return checkNotNull(outcome) { "EDT action did not run." }.getOrThrow()
    }

    private companion object {
        val FRAME_INTERVAL_NANOS: Long = (1.seconds / 60).inWholeNanoseconds
        const val MAX_IDLE_FRAMES: Int = 10_000
        const val SIZE: Int = 200
    }
}

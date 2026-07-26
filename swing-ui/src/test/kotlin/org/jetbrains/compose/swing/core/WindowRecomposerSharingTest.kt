package org.jetbrains.compose.swing.core

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Behavioral tests for the per-window recomposer resolution model.
 *
 * Nothing here attaches to a real top-level [java.awt.Window], so these tests run with or without a
 * display; they exercise the *resolution mechanism* the window path is built on, which is fully
 * observable off-screen:
 *  - a window publishes ONE [Recomposer] as the [COMPOSITION_KEY] context on a [JComponent] ancestor
 *    of its content (its root pane), and
 *  - every island under that ancestor resolves to it via the self-first
 *    [findParentCompositionContext] walk.
 *
 * So a single ancestor stamped with one recomposer stands in for a window root pane, and two sibling
 * islands beneath it model two in-window islands. Driven on a controllable [BroadcastFrameClock] (no
 * sleeps, bounded frames).
 */
class WindowRecomposerSharingTest {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(Dispatchers.Swing + Job() + clock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val handles = mutableListOf<kotlinx.coroutines.DisposableHandle>()
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

    @Test
    fun twoIslandsUnderOneStampedAncestorShareItsRecomposer() {
        // The ancestor stands in for a window root pane: stamped with exactly ONE recomposer context,
        // exactly as getOrCreateWindowRecomposer() publishes it.
        val windowRoot = onEdt { JPanel().apply { size = Dimension(SIZE, SIZE) } }
        val islandA = onEdt { JPanel().also { windowRoot.add(it) } }
        val islandB = onEdt { JPanel().also { windowRoot.add(it) } }
        onEdt { windowRoot.putClientProperty(COMPOSITION_KEY, recomposer) }

        // Both islands read ONE shared state. If they share one recomposer, mutating it once
        // recomposes BOTH; if each had spun up its own recomposer this single clock would not drive
        // the other.
        var shared by mutableStateOf("v0")
        onEdt {
            handles += islandA.setContent { Label(text = "a=$shared") }
            handles += islandB.setContent { Label(text = "b=$shared") }
        }
        waitForIdle()

        assertEquals("a=v0", labelText(islandA), "island A should render the initial shared state")
        assertEquals("b=v0", labelText(islandB), "island B should render the initial shared state")

        // Both content panes resolved to the SAME published recomposer context.
        assertSame(
            recomposer,
            onEdt { islandA.findParentCompositionContext() },
            "island A did not resolve to the shared window recomposer",
        )
        assertSame(
            recomposer,
            onEdt { islandB.findParentCompositionContext() },
            "island B did not resolve to the shared window recomposer",
        )

        onEdt { shared = "v1" }
        waitForIdle()
        assertEquals("a=v1", labelText(islandA), "island A did not recompose on the shared recomposer")
        assertEquals("b=v1", labelText(islandB), "island B did not recompose on the shared recomposer")
    }

    @Test
    fun selfFirstWalkLetsAStampedContainerHostItsOwnContent() {
        // A container stamped with a context is discovered by a setContent call on that very
        // container (self-first), not only by descendants. This is the findViewTreeCompositionContext
        // semantics the window root pane relies on.
        val host = onEdt { JPanel().apply { size = Dimension(SIZE, SIZE) } }
        onEdt { host.putClientProperty(COMPOSITION_KEY, recomposer) }

        var value by mutableStateOf("seed")
        onEdt { handles += host.setContent { Label(text = "self=$value") } }
        waitForIdle()

        assertEquals("self=seed", labelText(host), "the self-stamped host should render its initial content")
        assertSame(
            recomposer,
            onEdt { host.findParentCompositionContext() },
            "the self-stamped host should resolve to its own recomposer",
        )

        onEdt { value = "next" }
        waitForIdle()
        assertEquals("self=next", labelText(host), "self-stamped host did not recompose on its own recomposer")
    }

    @Test
    fun detachedContainerWithoutWindowAncestorDefersInsteadOfThrowing() {
        // No window ancestor, no stamped context, no injected recomposer: the mount is deferred until
        // the container is attached to a window, and a usable handle comes back now.
        //
        // The deferral is observable headless: setContent installs exactly ONE HierarchyListener that
        // is waiting for the attach event, and mounts nothing yet (no child components appear).
        val orphan = onEdt { JPanel().apply { size = Dimension(SIZE, SIZE) } }

        val handle = onEdt { orphan.setContent { Label(text = "never") } }
        handles += handle

        assertEquals(
            1,
            onEdt { orphan.hierarchyListeners.size },
            "a detached setContent must install exactly one HierarchyListener to await attachment",
        )
        assertEquals(
            0,
            onEdt { orphan.componentCount },
            "a deferred mount must not add any child components before the container is attached",
        )

        // Disposing a never-attached handle removes the pending listener cleanly: no mount ever
        // happened, and no listener is left leaking on the container.
        onEdt { handle.dispose() }
        assertEquals(
            0,
            onEdt { orphan.hierarchyListeners.size },
            "disposing a never-attached handle must remove the pending HierarchyListener (no leak)",
        )
        assertEquals(
            0,
            onEdt { orphan.componentCount },
            "disposing before attach must mount nothing",
        )

        // Idempotent: double-dispose is safe and leaves no listener behind.
        onEdt { handle.dispose() }
        assertEquals(0, onEdt { orphan.hierarchyListeners.size }, "a double-dispose must leave no listener behind")
    }

    @Test
    fun disposingBeforeAttachUnregistersTheHierarchyListener() {
        // Focused guarantee for the deferred state machine: the HierarchyListener installed by a
        // detached setContent is registered while the handle is live and unregistered on dispose,
        // observable purely through container.getHierarchyListeners().
        val orphan = onEdt { JPanel().apply { size = Dimension(SIZE, SIZE) } }
        val before = onEdt { orphan.hierarchyListeners.size }

        val handle = onEdt { orphan.setContent { Label(text = "never") } }
        assertEquals(
            before + 1,
            onEdt { orphan.hierarchyListeners.size },
            "setContent on a detached container must register exactly one HierarchyListener",
        )

        onEdt { handle.dispose() }
        assertEquals(
            before,
            onEdt { orphan.hierarchyListeners.size },
            "disposing the deferred handle must unregister the HierarchyListener it added",
        )
    }

    private fun labelText(container: Container): String = onEdt {
        val labels = mutableListOf<JLabel>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) labels += child
                if (child is Container) visit(child)
            }
        }
        visit(container)
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
        const val FRAME_INTERVAL_NANOS: Long = 16_666_667L
        const val MAX_IDLE_FRAMES: Int = 10_000
        const val SIZE: Int = 200
    }
}

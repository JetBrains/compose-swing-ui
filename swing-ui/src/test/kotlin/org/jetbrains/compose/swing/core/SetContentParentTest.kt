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
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for `setContent` composing under a caller-named parent, on the half that needs no
 * window.
 *
 * A named parent is the whole answer to what the content joins, so every case here runs on a container
 * that hangs off no window at all - the shape of a container built to be read rather than shown. The
 * parent is a [Recomposer] the test owns and drives on a controllable [BroadcastFrameClock] (no sleeps,
 * bounded frames), which is exactly what a caller passing a context of their own has.
 */
class SetContentParentTest {
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

    @Test
    fun namedParentContentComposesOnTheCall() {
        // This container hangs off no window and no stamped ancestor, so nothing but the named parent
        // answers what it composes under; the content is there the moment the call returns.
        val detached = onEdt { JPanel().apply { size = Dimension(SIZE, SIZE) } }

        val textsOnReturn =
            onEdt {
                handles += detached.setContent(parent = recomposer) { Label(text = "on-call") }
                // Read inside the same EDT action as the call, before any event-queue turn or frame.
                labelTexts(detached)
            }

        assertEquals(
            listOf("on-call"),
            textsOnReturn,
            "content under a named parent must be composed by the time setContent returns",
        )
    }

    @Test
    fun disposingACompositionLeavesItsParentDrivingTheNextOne() {
        val first = onEdt { JPanel().apply { size = Dimension(SIZE, SIZE) } }
        val second = onEdt { JPanel().apply { size = Dimension(SIZE, SIZE) } }
        var shared by mutableStateOf("v0")

        val firstHandle = onEdt { first.setContent(parent = recomposer) { Label(text = "first=$shared") } }
        handles += firstHandle
        waitForIdle()
        assertEquals(
            listOf("first=v0"),
            labelTexts(first),
            "the first content composition should render its initial state",
        )

        // The handle owns this content composition and nothing else, so disposing it takes only that
        // composition's content down and leaves the parent the caller owns running.
        onEdt { firstHandle.dispose() }
        assertEquals(
            emptyList(),
            labelTexts(first),
            "disposing the handle must take the composed content with it",
        )

        onEdt { handles += second.setContent(parent = recomposer) { Label(text = "second=$shared") } }
        assertEquals(
            listOf("second=v0"),
            labelTexts(second),
            "a second content composition must compose under a parent that outlived the first",
        )

        onEdt { shared = "v1" }
        waitForIdle()
        assertEquals(
            listOf("second=v1"),
            labelTexts(second),
            "the parent must keep driving recomposition after one of its content compositions was disposed",
        )
        assertEquals(
            emptyList(),
            labelTexts(first),
            "a disposed content composition must not compose again when the state it read changes",
        )
    }

    @Test
    fun aNamedParentMountWatchesTheContainersPlaceAndGivesItUpOnDispose() {
        // Two things watch the container's place in the Swing tree, each with its own HierarchyListener:
        // the mount phase (so a container that ends up in a window joins that window's composition) and
        // the content's lifecycle owner (which reads whether the content is shown off that same place).
        // Disposing the handle gives both listeners back.
        val detached = onEdt { JPanel().apply { size = Dimension(SIZE, SIZE) } }
        val before = onEdt { detached.hierarchyListeners.size }

        val handle = onEdt { detached.setContent(parent = recomposer) { Label(text = "watched") } }
        assertEquals(
            before + 2,
            onEdt { detached.hierarchyListeners.size },
            "content mounted under a named parent must watch the container's place with one " +
                "HierarchyListener for the mount phase and one for the content's lifecycle",
        )

        onEdt { handle.dispose() }
        assertEquals(
            before,
            onEdt { detached.hierarchyListeners.size },
            "disposing the handle must unregister every HierarchyListener setContent added",
        )
        assertEquals(emptyList(), labelTexts(detached), "disposing the handle must take the content down")

        onEdt { handle.dispose() }
        assertEquals(
            before,
            onEdt { detached.hierarchyListeners.size },
            "a double-dispose must leave no listener behind",
        )
    }

    @Test
    fun aMoveThatResolvesToNoParentLeavesTheCompositionComposingAndDisposableCleanly() {
        // Moved into another container that is itself in no window, the container's place still resolves
        // to no parent of its own, so the content composition keeps composing under the parent it was
        // given.
        val detached = onEdt { JPanel().apply { size = Dimension(SIZE, SIZE) } }
        val newHost = onEdt { JPanel().apply { size = Dimension(SIZE, SIZE) } }
        var shared by mutableStateOf("v0")

        val handle = onEdt { detached.setContent(parent = recomposer) { Label(text = "moved=$shared") } }
        handles += handle
        waitForIdle()

        onEdt { newHost.add(detached) }
        onEdt { shared = "v1" }
        waitForIdle()
        assertEquals(
            listOf("moved=v1"),
            labelTexts(detached),
            "a container moved where no parent resolves must keep composing under the parent it was given",
        )
        assertEquals(
            2,
            onEdt { detached.hierarchyListeners.size },
            "a live content composition and the lifecycle of its content must both keep watching the " +
                "container's place after a move",
        )

        onEdt { handle.dispose() }
        assertEquals(
            0,
            onEdt { detached.hierarchyListeners.size },
            "disposing after a move must unregister every HierarchyListener setContent added",
        )
        assertEquals(emptyList(), labelTexts(detached), "disposing after a move must take the content down")
    }

    /** The text of every [JLabel] in [container]'s subtree, in tree order. */
    private fun labelTexts(container: Container): List<String> = onEdt {
        val texts = mutableListOf<String>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) texts += child.text
                if (child is Container) visit(child)
            }
        }
        visit(container)
        texts
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

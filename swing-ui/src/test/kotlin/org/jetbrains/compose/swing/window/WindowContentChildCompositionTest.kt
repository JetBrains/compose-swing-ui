package org.jetbrains.compose.swing.window

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
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
import org.jetbrains.compose.swing.setContentAsInteropHost
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
 * Canary for the app->window context flow (design fork F1, "preserve/hybrid"): content mounted
 * through [setContentAsInteropHost] with the enclosing [rememberCompositionContext] becomes a CHILD
 * of the application composition, so a CompositionLocal (and the snapshot state backing it) provided
 * in the application scope is observed inside that DETACHED content, and an application-scope update
 * recomposes it.
 *
 * This reproduces what `Window` does without constructing a real `JFrame` (so it needs no display
 * and runs with or without one): capture the parent context in the composable
 * body, then mount a detached top-level peer's content pane via `setContentAsInteropHost(parent)`.
 * The host [JPanel] is deliberately NOT attached to the application's Swing tree, exactly like a
 * `JFrame` content pane, so the production [findParentCompositionContext] tree-walk would find only
 * the stamp this call itself installs.
 *
 * If `Window` mounted such detached content as an independent root, the content would see only the
 * CompositionLocal default and never recompose on application-scope state changes; this test fails
 * against that behavior and passes once the content is threaded through the parent context.
 *
 * Driven on a controllable [BroadcastFrameClock] (no sleeps, bounded frames).
 */
class WindowContentChildCompositionTest {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(Dispatchers.Swing + Job() + clock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private var composition: Composition? = null

    // Stands in for a JFrame's content pane: a real container that is NOT part of the application's
    // Swing tree, so the upward COMPOSITION_KEY walk from it finds nothing.
    private val detachedHost: JPanel = onEdt { JPanel().apply { size = Dimension(HOST_SIZE, HOST_SIZE) } }
    private var frameTimeNanos = 0L

    init {
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    @AfterTest
    fun tearDown() {
        onEdt { composition?.dispose() }
        recomposer.cancel()
        scope.cancel()
    }

    @Test
    fun applicationStateAndLocalReachDetachedContentAndUpdate() {
        var provided by mutableStateOf("from-app")

        onEdt {
            // The "application" composition driven by the injected recomposer/clock, mirroring
            // awaitApplication's wiring. Its applier has no Swing root of its own.
            composition =
                Composition(NoOpApplier(), recomposer).apply {
                    setContent {
                        CompositionLocalProvider(LocalAppValue provides provided) {
                            HostDetachedContent(detachedHost) {
                                // Resolves the application-scope CompositionLocal only if this
                                // detached content is a CHILD of the application composition.
                                Label(text = "local=${LocalAppValue.current}")
                            }
                        }
                    }
                }
        }
        waitForIdle()

        assertEquals(
            "local=from-app",
            detachedLabelText(),
            "Application CompositionLocal did not reach detached window content",
        )

        onEdt { provided = "updated" }
        waitForIdle()
        assertEquals(
            "local=updated",
            detachedLabelText(),
            "Detached window content did not recompose on application-state change",
        )
    }

    /**
     * The window wiring under test: capture the enclosing context in the composable body (not inside
     * the effect), then mount [content] into the detached [host] as a child of that context.
     * Identical in shape to `Window`'s body.
     */
    @androidx.compose.runtime.Composable
    private fun HostDetachedContent(
        host: Container,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val parentContext = rememberCompositionContext()
        DisposableEffect(Unit) {
            val handle: DisposableHandle = host.setContentAsInteropHost(parentContext) { content() }
            onDispose { handle.dispose() }
        }
    }

    private fun detachedLabelText(): String = onEdt {
        val labels = mutableListOf<JLabel>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) labels += child
                if (child is Container) visit(child)
            }
        }

        visit(detachedHost)
        labels.single().text
    }

    // Deterministic idle gate: pump one frame per iteration, flush the EDT, stop when neither the
    // recomposer nor the snapshot system has outstanding work. Bounded so a non-settling composition
    // fails fast instead of hanging. No sleeping.
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
        const val HOST_SIZE: Int = 200
    }

    /** A no-op applier, matching how the application composition has no Swing root of its own. */
    private class NoOpApplier : androidx.compose.runtime.Applier<Any> {
        override val current: Any = Unit

        override fun down(node: Any) = Unit

        override fun up() = Unit

        override fun insertTopDown(
            index: Int,
            instance: Any,
        ) = Unit

        override fun insertBottomUp(
            index: Int,
            instance: Any,
        ) = Unit

        override fun remove(
            index: Int,
            count: Int,
        ) = Unit

        override fun move(
            from: Int,
            to: Int,
            count: Int,
        ) = Unit

        override fun clear() = Unit
    }
}

/**
 * An application-scope [androidx.compose.runtime.CompositionLocal] used to prove propagation into
 * detached window content. Declared top-level so it carries the `Local` prefix expected of
 * CompositionLocals while remaining file-private to this test.
 */
private val LocalAppValue = compositionLocalOf { "default" }

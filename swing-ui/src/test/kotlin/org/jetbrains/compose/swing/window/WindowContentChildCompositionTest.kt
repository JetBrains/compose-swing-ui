package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.TestRecomposer
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContentAsInteropHost
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

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
 * the context this call itself publishes.
 *
 * If `Window` mounted such detached content as an independent root, the content would see only the
 * CompositionLocal default and never recompose on application-scope state changes; this test fails
 * against that behavior and passes once the content is threaded through the parent context.
 *
 * Driven on a controllable frame clock (no sleeps, bounded frames).
 */
class WindowContentChildCompositionTest {
    @Test
    fun applicationStateAndLocalReachDetachedContentAndUpdate() = runSwingTest {
        val test = TestRecomposer(this)
        // Stands in for a JFrame's content pane: a real container that is NOT part of the
        // application's Swing tree, so the upward COMPOSITION_KEY walk from it finds nothing.
        val detachedHost = JPanel().apply { size = Dimension(HOST_SIZE, HOST_SIZE) }
        var composition: Composition? = null
        try {
            var provided by mutableStateOf("from-app")

            // The "application" composition driven by the injected recomposer/clock, mirroring
            // awaitApplication's wiring. Its applier has no Swing root of its own.
            composition =
                Composition(NoOpApplier(), test.recomposer).apply {
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
            test.awaitIdle()

            assertEquals(
                "local=from-app",
                detachedLabelText(detachedHost),
                "Application CompositionLocal did not reach detached window content",
            )

            provided = "updated"
            test.awaitIdle()
            assertEquals(
                "local=updated",
                detachedLabelText(detachedHost),
                "Detached window content did not recompose on application-state change",
            )
        } finally {
            composition?.dispose()
            test.cancel()
        }
    }

    /**
     * The window wiring under test: capture the enclosing context in the composable body (not inside
     * the effect), then mount [content] into the detached [host] as a child of that context.
     * Identical in shape to `Window`'s body.
     */
    @Composable
    private fun HostDetachedContent(
        host: Container,
        content: @Composable () -> Unit,
    ) {
        val parentContext = rememberCompositionContext()
        DisposableEffect(Unit) {
            val handle: DisposableHandle = host.setContentAsInteropHost(parentContext) { content() }
            onDispose { handle.dispose() }
        }
    }

    private fun detachedLabelText(detachedHost: Container): String {
        val labels = mutableListOf<JLabel>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) labels += child
                if (child is Container) visit(child)
            }
        }

        visit(detachedHost)
        return labels.single().text
    }

    private companion object {
        const val HOST_SIZE: Int = 200
    }

    /** A no-op applier, matching how the application composition has no Swing root of its own. */
    private class NoOpApplier : Applier<Any> {
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

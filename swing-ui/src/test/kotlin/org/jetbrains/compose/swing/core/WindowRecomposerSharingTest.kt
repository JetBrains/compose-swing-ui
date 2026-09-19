package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.TestRecomposer
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.util.set
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Behavioral tests for the per-window recomposer resolution model.
 *
 * Nothing here attaches to a real top-level [java.awt.Window], so these tests run with or without a
 * display; they exercise the resolution mechanism the window path is built on:
 *  - a window publishes ONE [androidx.compose.runtime.Recomposer] as the [COMPOSITION_KEY] context on a
 *    [javax.swing.JComponent] ancestor of its content (its root pane), and
 *  - every content composition under that ancestor resolves to it via the self-first
 *    [findParentCompositionContext] walk.
 *
 * A single ancestor published with one recomposer stands in for a window root pane, and two sibling
 * content compositions beneath it model two content compositions of one window. Driven on a
 * controllable frame clock (no sleeps, bounded frames).
 */
class WindowRecomposerSharingTest {
    @Test
    fun twoCompositionsUnderOnePublishedAncestorShareItsRecomposer() = runSwingTest {
        val test = TestRecomposer(this)
        val handles = mutableListOf<DisposableHandle>()
        try {
            // The ancestor stands in for a window root pane: published with exactly ONE recomposer
            // context, exactly as getOrCreateRecomposer() publishes it.
            val windowRoot = JPanel().apply { size = Dimension(SIZE, SIZE) }
            val compositionA = JPanel().also { windowRoot.add(it) }
            val compositionB = JPanel().also { windowRoot.add(it) }
            windowRoot[COMPOSITION_KEY] = test.recomposer

            // Both compositions read ONE shared state: mutating it recomposes BOTH only if they
            // share one recomposer, since a recomposer of their own would not be driven by this clock.
            var shared by mutableStateOf("v0")
            handles += compositionA.setContent { Label(text = "a=$shared") }
            handles += compositionB.setContent { Label(text = "b=$shared") }
            test.awaitIdle()

            assertEquals("a=v0", labelText(compositionA), "composition A should render the initial shared state")
            assertEquals("b=v0", labelText(compositionB), "composition B should render the initial shared state")

            // Both content panes resolved to the SAME published recomposer context.
            assertSame(
                test.recomposer,
                compositionA.findParentCompositionContext(),
                "composition A did not resolve to the shared window recomposer",
            )
            assertSame(
                test.recomposer,
                compositionB.findParentCompositionContext(),
                "composition B did not resolve to the shared window recomposer",
            )

            shared = "v1"
            test.awaitIdle()
            assertEquals(
                "a=v1",
                labelText(compositionA),
                "composition A did not recompose on the shared recomposer",
            )
            assertEquals(
                "b=v1",
                labelText(compositionB),
                "composition B did not recompose on the shared recomposer",
            )
        } finally {
            handles.forEach { it.dispose() }
            test.cancel()
        }
    }

    @Test
    fun selfFirstWalkLetsAPublishedContainerHostItsOwnContent() = runSwingTest {
        val test = TestRecomposer(this)
        val handles = mutableListOf<DisposableHandle>()
        try {
            // A container published with a context is discovered by a setContent call on that very
            // container (self-first), not only by descendants - the semantics the window root pane
            // relies on.
            val host = JPanel().apply { size = Dimension(SIZE, SIZE) }
            host[COMPOSITION_KEY] = test.recomposer

            var value by mutableStateOf("seed")
            handles += host.setContent { Label(text = "self=$value") }
            test.awaitIdle()

            assertEquals("self=seed", labelText(host), "the self-published host should render its initial content")
            assertSame(
                test.recomposer,
                host.findParentCompositionContext(),
                "the self-published host should resolve to its own recomposer",
            )

            value = "next"
            test.awaitIdle()
            assertEquals("self=next", labelText(host), "self-published host did not recompose on its own recomposer")
        } finally {
            handles.forEach { it.dispose() }
            test.cancel()
        }
    }

    @Test
    fun detachedContainerWithoutWindowAncestorDefersInsteadOfThrowing() = runSwingTest {
        val handles = mutableListOf<DisposableHandle>()
        try {
            // No window ancestor, no published context, no injected recomposer: the content mounts only
            // once the container is attached to a window, and a usable handle comes back now.
            val orphan = JPanel().apply { size = Dimension(SIZE, SIZE) }

            val handle = orphan.setContent { Label(text = "never") }
            handles += handle

            assertEquals(
                1,
                orphan.hierarchyListeners.size,
                "a detached setContent must install exactly one HierarchyListener to await attachment",
            )
            assertEquals(
                0,
                orphan.componentCount,
                "a deferred setContent must add no child components before the container is attached",
            )

            handle.dispose()
            assertEquals(
                0,
                orphan.hierarchyListeners.size,
                "disposing a never-attached handle must remove the pending HierarchyListener (no leak)",
            )
            assertEquals(
                0,
                orphan.componentCount,
                "disposing before attach must mount nothing",
            )

            handle.dispose()
            assertEquals(0, orphan.hierarchyListeners.size, "a double-dispose must leave no listener behind")
        } finally {
            handles.forEach { it.dispose() }
        }
    }

    @Test
    fun disposingBeforeAttachUnregistersTheHierarchyListener() = runSwingTest {
        val handles = mutableListOf<DisposableHandle>()
        try {
            // The HierarchyListener installed by a detached setContent is registered while the handle
            // is live and unregistered on dispose.
            val orphan = JPanel().apply { size = Dimension(SIZE, SIZE) }
            val before = orphan.hierarchyListeners.size

            val handle = orphan.setContent { Label(text = "never") }
            handles += handle
            assertEquals(
                before + 1,
                orphan.hierarchyListeners.size,
                "setContent on a detached container must register exactly one HierarchyListener",
            )

            handle.dispose()
            assertEquals(
                before,
                orphan.hierarchyListeners.size,
                "disposing the deferred handle must unregister the HierarchyListener it added",
            )
        } finally {
            handles.forEach { it.dispose() }
        }
    }

    private fun labelText(container: Container): String {
        val labels = mutableListOf<JLabel>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) labels += child
                if (child is Container) visit(child)
            }
        }
        visit(container)
        return labels.single().text
    }

    private companion object {
        const val SIZE: Int = 200
    }
}

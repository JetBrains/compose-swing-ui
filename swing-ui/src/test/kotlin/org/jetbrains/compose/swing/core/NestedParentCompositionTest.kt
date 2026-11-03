// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Container
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for the composition a `setContent` nested inside another composition joins.
 *
 * A content composition mounted under a caller's own [androidx.compose.runtime.CompositionContext]
 * governs everything mounted inside it: a container that hangs under such a composition composes on the
 * recomposer that composition was given, not on the one its window happens to own - whether the content
 * is mounted during the enclosing composition's own first pass, once that pass has put the container in
 * place, or before a later recomposition inserts it. Where no enclosing composition stands in the way, a
 * container joins the composition its window shares, which is what naming no parent resolves to.
 *
 * Which recomposer content joined is read off what it does: the recomposer the enclosing composition was
 * given is disposed, and content that joined it stops recomposing while a sibling on the window's own
 * recomposer goes on.
 *
 * Each case realizes a real top-level peer, so each skips on a headless environment.
 */
class NestedParentCompositionTest {
    @Test
    fun contentMountedDuringTheCompositionsFirstPassJoinsTheRecomposerTheCompositionWasGiven() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        val recomposer = SwingRecomposer.create(JPanel())
        val composition = JPanel().also { frame.contentPane.add(it) }
        val windowComposition = JPanel().also { frame.contentPane.add(it) }
        var text by mutableStateOf("v0")
        var compositionHandle: DisposableHandle? = null
        var windowHandle: DisposableHandle? = null
        try {
            compositionHandle =
                composition.setContent(parent = recomposer.compositionContext) {
                    ParentlessHost { Label(text = text) }
                }
            windowHandle = windowComposition.setContent { Label(text = text) }
            awaitUntil("both contents render") {
                composition.componentCount == 1 && labelTexts(composition) == listOf("v0") &&
                    labelTexts(windowComposition) == listOf("v0")
            }
            val nested = composition.components.single()
            assertSame(
                composition,
                nested.parent,
                "the enclosing composition's first pass composed the nested container into place",
            )

            recomposer.dispose()
            text = "v1"
            awaitUntil("the window's own content composition recomposes") {
                labelTexts(windowComposition) == listOf("v1")
            }
            delay(QUIET_PERIOD)
            assertEquals(
                listOf("v0"),
                labelTexts(nested as Container),
                "content mounted during the enclosing composition's first pass must recompose on the " +
                    "recomposer that composition was given, not on the window's",
            )
        } finally {
            compositionHandle?.dispose()
            windowHandle?.dispose()
            recomposer.dispose()
            frame.dispose()
        }
    }

    @Test
    fun contentMountedWithNoCompositionAboveItJoinsItsWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        val composition = JPanel().also { frame.contentPane.add(it) }
        var compositionHandle: DisposableHandle? = null
        try {
            compositionHandle = composition.setContent { ParentlessHost { Label(text = "nested") } }
            awaitUntil("the nested content renders") { labelTexts(composition) == listOf("nested") }

            assertSame(
                frame.compositionContext(),
                composition.components.single().findParentCompositionContext(),
                "content mounted under a composition that named no parent must join the window's shared " +
                    "composition",
            )
        } finally {
            compositionHandle?.dispose()
            frame.dispose()
        }
    }

    @Test
    fun contentInsertedByALaterRecompositionJoinsTheRecomposerTheCompositionWasGiven() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        val recomposer = SwingRecomposer.create(JPanel())
        val composition = JPanel().also { frame.contentPane.add(it) }
        val windowComposition = JPanel().also { frame.contentPane.add(it) }
        var showNested by mutableStateOf(false)
        var text by mutableStateOf("v0")
        var compositionHandle: DisposableHandle? = null
        var windowHandle: DisposableHandle? = null
        try {
            compositionHandle =
                composition.setContent(parent = recomposer.compositionContext) {
                    Label(text = "outer")
                    if (showNested) ParentlessHost(mountBeforeAttach = true) { Label(text = text) }
                }
            windowHandle = windowComposition.setContent { Label(text = text) }
            awaitUntil("the outer composition composes without the nested container") {
                labelTexts(composition) == listOf("outer")
            }

            showNested = true

            awaitUntil("a later recomposition inserts the nested container") { composition.componentCount == 2 }
            awaitUntil("both contents render") {
                labelTexts(composition) == listOf("outer", "v0") && labelTexts(windowComposition) == listOf("v0")
            }
            val nested = composition.components[1] as Container
            assertSame(composition, nested.parent, "a later recomposition inserted the nested container")
            assertEquals(
                listOf("outer", "v0"),
                labelTexts(composition),
                "the outer composition holds the content of both",
            )

            recomposer.dispose()
            text = "v1"
            awaitUntil("the window's own content composition recomposes") {
                labelTexts(windowComposition) == listOf("v1")
            }
            delay(QUIET_PERIOD)
            assertEquals(
                listOf("v0"),
                labelTexts(nested),
                "content a later recomposition places inside another composition must recompose on the " +
                    "recomposer that composition was given, not on the window's",
            )
        } finally {
            windowHandle?.dispose()
            compositionHandle?.dispose()
            recomposer.dispose()
            frame.dispose()
        }
    }

    @Test
    fun nestedContentRecomposesOnTheRecomposerTheCompositionWasGiven() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        val recomposer = SwingRecomposer.create(JPanel())
        val composition = JPanel().also { frame.contentPane.add(it) }
        val windowComposition = JPanel().also { frame.contentPane.add(it) }
        var text by mutableStateOf("v0")
        var mounted by mutableStateOf(false)
        var compositionHandle: DisposableHandle? = null
        var windowHandle: DisposableHandle? = null
        try {
            compositionHandle =
                composition.setContent(parent = recomposer.compositionContext) {
                    ParentlessHost(mounted = mounted) { Label(text = text) }
                }
            windowHandle = windowComposition.setContent { Label(text = text) }
            awaitUntil("the content composition composes its nested container into place") {
                composition.componentCount == 1
            }

            // The content is mounted by a pass after the one that attached its host.
            mounted = true
            awaitUntil("both contents render") {
                labelTexts(composition) == listOf("v0") && labelTexts(windowComposition) == listOf("v0")
            }

            // Only the recomposer the enclosing composition was given is disposed. Content that joined it
            // stops recomposing; content that joined the window goes on.
            recomposer.dispose()
            text = "v1"
            awaitUntil("the window's own content composition recomposes") {
                labelTexts(windowComposition) == listOf("v1")
            }
            delay(QUIET_PERIOD)
            assertEquals(
                listOf("v0"),
                labelTexts(composition),
                "nested content must recompose on the recomposer its enclosing composition was given, not " +
                    "on the window's",
            )
        } finally {
            windowHandle?.dispose()
            compositionHandle?.dispose()
            recomposer.dispose()
            frame.dispose()
        }
    }

    @Test
    fun aNestedContentCompositionFollowsItsContainersMoveToAnotherWindowAndOutlivesTheOneItLeft() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val first = realizedFrame()
        val second = realizedFrame()
        val outer = JPanel().also { first.contentPane.add(it) }
        var text by mutableStateOf("v0")
        var outerHandle: DisposableHandle? = null
        try {
            outerHandle = outer.setContent { ParentlessHost { Label(text = text) } }
            awaitUntil("the nested content renders") { labelTexts(outer) == listOf("v0") }

            second.contentPane.add(outer)
            second.pack()
            awaitUntil("the outer container is in the second window") {
                SwingUtilities.getWindowAncestor(outer) === second
            }

            // The window the nested content started in owns the recomposer it composed on; disposing that
            // window must not silence a container that has moved on to another one.
            first.dispose()
            text = "v1"
            awaitUntil("the nested content recomposes in the window it moved to") {
                labelTexts(outer) == listOf("v1")
            }
        } finally {
            outerHandle?.dispose()
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun contentMountedStaysOnItsCompositionsRecomposerWhenItsContainerMovesToAnotherWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val first = realizedFrame()
        val second = realizedFrame()
        val recomposer = SwingRecomposer.create(JPanel())
        val outer = JPanel().also { first.contentPane.add(it) }
        val windowComposition = JPanel().also { first.contentPane.add(it) }
        var text by mutableStateOf("v0")
        var outerHandle: DisposableHandle? = null
        var windowHandle: DisposableHandle? = null
        try {
            outerHandle =
                outer.setContent(parent = recomposer.compositionContext) {
                    ParentlessHost { Label(text = text) }
                }
            windowHandle = windowComposition.setContent { Label(text = text) }
            awaitUntil("both contents render") {
                labelTexts(outer) == listOf("v0") && labelTexts(windowComposition) == listOf("v0")
            }

            second.contentPane.add(outer)
            second.pack()
            awaitUntil("the outer container is in the second window") {
                SwingUtilities.getWindowAncestor(outer) === second
            }
            // The nested content's rejoin is queued behind the reparenting event rather than run inline,
            // so a quiet period here lets it settle before the container moves again.
            delay(QUIET_PERIOD)

            // Moved a second time, back to the window it started in: the nested content is asked to
            // resolve its parent again, so this is what tells a caller's recomposer read fresh on every
            // move from one that only ever happened to be read on the move that settled first.
            first.contentPane.add(outer)
            awaitUntil("the outer container is back in the first window") {
                SwingUtilities.getWindowAncestor(outer) === first
            }
            delay(QUIET_PERIOD)

            // Only the recomposer the outer composition was given is disposed. The nested content that
            // joined it must stop recomposing, while a sibling on the window's own recomposer goes on.
            recomposer.dispose()
            text = "v1"
            awaitUntil("the window's own content composition recomposes") {
                labelTexts(windowComposition) == listOf("v1")
            }
            delay(QUIET_PERIOD)
            assertEquals(
                listOf("v0"),
                labelTexts(outer),
                "nested content must stay on the recomposer the enclosing composition was given after that " +
                    "composition's container moves to another window",
            )
        } finally {
            windowHandle?.dispose()
            outerHandle?.dispose()
            recomposer.dispose()
            second.dispose()
            first.dispose()
        }
    }

    /**
     * A panel that owns a nested composition mounted with no parent, so discovery picks the parent. The
     * content is mounted once [mounted] holds, in the pass that composed the host or a later one.
     */
    @Composable
    private fun ParentlessHost(
        mountBeforeAttach: Boolean = false,
        mounted: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val current = rememberUpdatedState(content)
        SwingNode(
            factory = { ParentlessPanel().also { if (mountBeforeAttach) it.mount(current) } },
            update = { set(mountBeforeAttach || !mounted) { if (!it) mount(current) } },
            onRelease = { dispose() },
        )
    }

    private class ParentlessPanel :
        JPanel(),
        DisposableHandle {
        private var mounted: DisposableHandle? = null

        fun mount(content: State<@Composable () -> Unit>) {
            if (mounted == null) mounted = setContent { content.value() }
        }

        override fun dispose() {
            mounted?.dispose()
            mounted = null
        }
    }

    /**
     * A realized, off-screen [JFrame] with a live peer. Packing realizes the peer without showing the
     * frame. Must be called on the EDT.
     */
    private fun realizedFrame(): JFrame = JFrame().apply {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        pack()
    }

    /** The text of every [JLabel] in [container]'s subtree, in tree order. Must be called on the EDT. */
    private fun labelTexts(container: Container): List<String> {
        val texts = mutableListOf<String>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) texts += child.text
                if (child is Container) visit(child)
            }
        }
        visit(container)
        return texts
    }

    /**
     * Suspends on the EDT until [condition] holds, yielding the EDT back between checks so a window's
     * real frame-clock timer can fire and its recomposer can mount and recompose content. A condition
     * that never holds fails the test at the deadline, naming [description], instead of hanging.
     */
    private suspend fun awaitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        try {
            withTimeout(SETTLE_TIMEOUT) {
                while (!condition()) {
                    yield()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after $SETTLE_TIMEOUT waiting until $description", timedOut)
        }
    }

    private companion object {
        val SETTLE_TIMEOUT = 10.seconds

        /** Spans many frame intervals, so a recomposer still running would have applied a change well inside it. */
        val QUIET_PERIOD = 300.milliseconds
    }
}

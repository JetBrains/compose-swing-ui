package org.jetbrains.compose.swing.core

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
 * Behavioral tests for the composition a `setContent` nested inside an island joins.
 *
 * An island mounted under a caller's own [androidx.compose.runtime.CompositionContext] governs
 * everything mounted inside it: a container that hangs under such an island composes on the runtime the
 * island was given, not on the one its window happens to own - whether the mount is made during the
 * island's own first pass, once that pass has put the container in place, or before a later
 * recomposition inserts it. Where no island stands in the way, a container joins the composition its
 * window shares, which is what every parentless mount resolves to.
 *
 * Which runtime a mount joined is read off what it does: the runtime the island was given is disposed,
 * and content that joined it stops recomposing while a sibling on the window's own runtime goes on.
 *
 * Each case realizes a real top-level peer, so each skips on a headless environment.
 */
class NestedMountParentTest {
    @Test
    fun aNestedMountMadeDuringTheIslandsFirstPassJoinsTheRuntimeItsIslandWasGiven() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        val runtime = SwingRecomposer.create(JPanel())
        val island = JPanel().also { frame.contentPane.add(it) }
        val windowIsland = JPanel().also { frame.contentPane.add(it) }
        val nested = JPanel()
        var text by mutableStateOf("v0")
        var islandHandle: DisposableHandle? = null
        var windowHandle: DisposableHandle? = null
        try {
            islandHandle =
                island.setContent(parent = runtime.compositionContext) {
                    SwingNode(factory = { nested })
                    // The nested mount is made from an effect of the island's own first pass, before that
                    // pass has returned, so it resolves its parent while the island is still composing.
                    // The island's composition owns it, and ends it.
                    DisposableEffect(Unit) {
                        val handle = nested.setContent { Label(text = text) }
                        onDispose { handle.dispose() }
                    }
                }
            windowHandle = windowIsland.setContent { Label(text = text) }
            awaitUntil("both contents render") {
                labelTexts(nested) == listOf("v0") && labelTexts(windowIsland) == listOf("v0")
            }
            assertSame(island, nested.parent, "the island's first pass composed the nested container into place")

            runtime.dispose()
            text = "v1"
            awaitUntil("the window's own island recomposes") { labelTexts(windowIsland) == listOf("v1") }
            delay(QUIET_PERIOD)
            assertEquals(
                listOf("v0"),
                labelTexts(nested),
                "content mounted during the island's first pass must recompose on the runtime that island " +
                    "was given, not on the window's",
            )
        } finally {
            islandHandle?.dispose()
            windowHandle?.dispose()
            runtime.dispose()
            frame.dispose()
        }
    }

    @Test
    fun aNestedMountWithNoIslandAboveItJoinsItsWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        val island = JPanel().also { frame.contentPane.add(it) }
        val nested = JPanel()
        var islandHandle: DisposableHandle? = null
        var nestedHandle: DisposableHandle? = null
        try {
            islandHandle = island.setContent { SwingNode(factory = { nested }) }
            awaitUntil("the island composes its nested container into place") { nested.parent === island }

            nestedHandle = nested.setContent { Label(text = "nested") }
            awaitUntil("the nested content renders") { labelTexts(nested) == listOf("nested") }

            assertSame(
                frame.compositionContext(),
                nested.findParentCompositionContext(),
                "a mount under an island that named no parent must join the window's shared composition",
            )
        } finally {
            nestedHandle?.dispose()
            islandHandle?.dispose()
            frame.dispose()
        }
    }

    @Test
    fun aNestedMountInsertedByALaterRecompositionJoinsTheRuntimeItsIslandWasGiven() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        val runtime = SwingRecomposer.create(JPanel())
        val island = JPanel().also { frame.contentPane.add(it) }
        val windowIsland = JPanel().also { frame.contentPane.add(it) }
        val nested = JPanel()
        var showNested by mutableStateOf(false)
        var text by mutableStateOf("v0")
        var islandHandle: DisposableHandle? = null
        var nestedHandle: DisposableHandle? = null
        var windowHandle: DisposableHandle? = null
        try {
            islandHandle =
                island.setContent(parent = runtime.compositionContext) {
                    Label(text = "island")
                    if (showNested) SwingNode(factory = { nested })
                }
            windowHandle = windowIsland.setContent { Label(text = text) }
            awaitUntil("the island composes without the nested container") { labelTexts(island) == listOf("island") }

            // The mount is made while the container hangs nowhere, so it waits for a place; the place it
            // then takes is one a later recomposition gives it.
            nestedHandle = nested.setContent { Label(text = text) }
            showNested = true

            awaitUntil("a later recomposition inserts the nested container") { nested.parent === island }
            awaitUntil("both contents render") {
                labelTexts(nested) == listOf("v0") && labelTexts(windowIsland) == listOf("v0")
            }
            assertEquals(listOf("island", "v0"), labelTexts(island), "the island holds what both mounts composed")

            runtime.dispose()
            text = "v1"
            awaitUntil("the window's own island recomposes") { labelTexts(windowIsland) == listOf("v1") }
            delay(QUIET_PERIOD)
            assertEquals(
                listOf("v0"),
                labelTexts(nested),
                "content a later recomposition places inside an island must recompose on the runtime that " +
                    "island was given, not on the window's",
            )
        } finally {
            nestedHandle?.dispose()
            windowHandle?.dispose()
            islandHandle?.dispose()
            runtime.dispose()
            frame.dispose()
        }
    }

    @Test
    fun nestedContentRecomposesOnTheRuntimeItsIslandWasGiven() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        val runtime = SwingRecomposer.create(JPanel())
        val island = JPanel().also { frame.contentPane.add(it) }
        val windowIsland = JPanel().also { frame.contentPane.add(it) }
        val nested = JPanel()
        var text by mutableStateOf("v0")
        var islandHandle: DisposableHandle? = null
        var nestedHandle: DisposableHandle? = null
        var windowHandle: DisposableHandle? = null
        try {
            islandHandle = island.setContent(parent = runtime.compositionContext) { SwingNode(factory = { nested }) }
            windowHandle = windowIsland.setContent { Label(text = text) }
            awaitUntil("the island composes its nested container into place") { nested.parent === island }

            nestedHandle = nested.setContent { Label(text = text) }
            awaitUntil("both contents render") {
                labelTexts(nested) == listOf("v0") && labelTexts(windowIsland) == listOf("v0")
            }

            // Only the runtime the island was given is disposed. Content that joined it stops recomposing;
            // content that joined the window goes on.
            runtime.dispose()
            text = "v1"
            awaitUntil("the window's own island recomposes") { labelTexts(windowIsland) == listOf("v1") }
            delay(QUIET_PERIOD)
            assertEquals(
                listOf("v0"),
                labelTexts(nested),
                "nested content must recompose on the runtime its island was given, not on the window's",
            )
        } finally {
            nestedHandle?.dispose()
            windowHandle?.dispose()
            islandHandle?.dispose()
            runtime.dispose()
            frame.dispose()
        }
    }

    @Test
    fun aNestedMountFollowsItsContainersMoveToAnotherWindowAndOutlivesTheOneItLeft() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val first = realizedFrame()
        val second = realizedFrame()
        val outer = JPanel().also { first.contentPane.add(it) }
        val inner = JPanel()
        var text by mutableStateOf("v0")
        var outerHandle: DisposableHandle? = null
        var innerHandle: DisposableHandle? = null
        try {
            outerHandle = outer.setContent { SwingNode(factory = { inner }) }
            awaitUntil("the outer island composes the nested container into place") { inner.parent === outer }

            innerHandle = inner.setContent { Label(text = text) }
            awaitUntil("the nested content renders") { labelTexts(inner) == listOf("v0") }

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
                labelTexts(inner) == listOf("v1")
            }
        } finally {
            innerHandle?.dispose()
            outerHandle?.dispose()
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun aNestedMountStaysOnItsIslandsRuntimeWhenItsContainerMovesToAnotherWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val first = realizedFrame()
        val second = realizedFrame()
        val runtime = SwingRecomposer.create(JPanel())
        val outer = JPanel().also { first.contentPane.add(it) }
        val windowIsland = JPanel().also { first.contentPane.add(it) }
        val inner = JPanel()
        var text by mutableStateOf("v0")
        var outerHandle: DisposableHandle? = null
        var innerHandle: DisposableHandle? = null
        var windowHandle: DisposableHandle? = null
        try {
            outerHandle = outer.setContent(parent = runtime.compositionContext) { SwingNode(factory = { inner }) }
            windowHandle = windowIsland.setContent { Label(text = text) }
            awaitUntil("the outer island composes the nested container into place") { inner.parent === outer }

            innerHandle = inner.setContent { Label(text = text) }
            awaitUntil("both contents render") {
                labelTexts(inner) == listOf("v0") && labelTexts(windowIsland) == listOf("v0")
            }

            second.contentPane.add(outer)
            second.pack()
            awaitUntil("the outer container is in the second window") {
                SwingUtilities.getWindowAncestor(outer) === second
            }
            // The nested mount's rejoin is queued behind the reparenting event rather than run inline, so
            // a quiet period here lets it settle before the container moves again.
            delay(QUIET_PERIOD)

            // Moved a second time, back to the window it started in: the nested mount is asked to resolve
            // its parent again, so this is what tells a caller's runtime read fresh on every move from one
            // that only ever happened to be read on the move that settled first.
            first.contentPane.add(outer)
            awaitUntil("the outer container is back in the first window") {
                SwingUtilities.getWindowAncestor(outer) === first
            }
            delay(QUIET_PERIOD)

            // Only the runtime the outer island was given is disposed. The nested content that joined it
            // must stop recomposing, while a sibling on the window's own runtime goes on.
            runtime.dispose()
            text = "v1"
            awaitUntil("the window's own island recomposes") { labelTexts(windowIsland) == listOf("v1") }
            delay(QUIET_PERIOD)
            assertEquals(
                listOf("v0"),
                labelTexts(inner),
                "a nested mount must stay on the runtime its island was given after the island's container " +
                    "moves to another window",
            )
        } finally {
            innerHandle?.dispose()
            windowHandle?.dispose()
            outerHandle?.dispose()
            runtime.dispose()
            second.dispose()
            first.dispose()
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

        /** Spans many frame intervals, so a runtime still running would have applied a change well inside it. */
        val QUIET_PERIOD = 300.milliseconds
    }
}

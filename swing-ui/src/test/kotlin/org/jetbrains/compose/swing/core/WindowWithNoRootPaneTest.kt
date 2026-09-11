package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.BorderLayout
import java.awt.Frame
import java.awt.GraphicsEnvironment
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for content composed in a window that has no root pane - a bare [java.awt.Frame]
 * holding Swing components. Such a window has nowhere to keep a recomposer, so it shares none: every
 * content composition in one drives its own, and what reaches a second one is the content already
 * standing there rather than anything the window answers with.
 *
 * Each case skips (reports SKIPPED) on a headless environment, since it needs a real top-level peer,
 * and disposes its frame on every exit path so no peer leaks.
 */
class WindowWithNoRootPaneTest {
    @Test
    fun aWindowWithNoRootPaneGivesEachContentCompositionItsOwnRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // A bare Frame is a legal host for a JComponent and has no root pane, so there is nowhere to keep
        // a recomposer for the window to share. Each content composition drives one of its own, and the
        // window answers for none - closing it still ends them all, through the walk over its tree.
        val frame = Frame().apply { pack() }
        try {
            val west = JPanel().also { frame.add(it, BorderLayout.WEST) }
            val east = JPanel().also { frame.add(it, BorderLayout.EAST) }
            west.setContent { Label(text = "west") }
            east.setContent { Label(text = "east") }
            awaitUntil("both content compositions render") {
                labelTexts(west).singleOrNull() == "west" && labelTexts(east).singleOrNull() == "east"
            }

            val westRecomposer = assertNotNull(west.findRecomposer(), "content composed in a bare frame is driven")
            val eastRecomposer = assertNotNull(east.findRecomposer(), "content composed in a bare frame is driven")
            assertNotSame(westRecomposer, eastRecomposer, "a window with nowhere to share a recomposer shares none")
            assertNull(frame.findRecomposer(), "and the window itself answers for no content")

            frame.dispose()
            awaitUntil("closing the window ends both recomposers") {
                westRecomposer.currentState.value == Recomposer.State.ShutDown &&
                    eastRecomposer.currentState.value == Recomposer.State.ShutDown
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aWindowWithNoRootPaneIsRefusedAContextItCouldNotHandOutTwice() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // Nowhere to keep a recomposer means a fresh one on every call, so a caller holding what they
        // were handed would compose under a context the next call no longer names.
        val frame = Frame().apply { pack() }
        try {
            val refusal = assertFailsWith<IllegalArgumentException> { frame.compositionContext() }
            assertTrue(
                "RootPaneContainer" in refusal.message.orEmpty(),
                "the refusal must name what a shared context needs, but was: ${refusal.message}",
            )
            assertNull(frame.swingRecomposerOrNull(), "and it must leave the window driving nothing")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun contentNestedInContentInAWindowWithNoRootPaneIsEndedWithThatWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // A bare Frame shares no recomposer, so the only one a nested content composition can register
        // with is the one driving the content it nests into. Registering with none would strand this
        // composition the moment its container leaves the tree: no walk over the window reaches it, and
        // nothing ends it.
        val frame = Frame().apply { pack() }
        try {
            val outer = JPanel().also { frame.add(it, BorderLayout.CENTER) }
            outer.setContent { Label(text = "outer") }
            val inner = JPanel().also { outer.add(it) }
            var innerComposed = false
            var innerEnded = false
            inner.setContent {
                DisposableEffect(Unit) {
                    innerComposed = true
                    onDispose { innerEnded = true }
                }
            }
            awaitUntil("the nested content composition mounts") { innerComposed }
            val driving = assertNotNull(outer.findRecomposer(), "content composed in a bare frame is driven")
            assertSame(driving, inner.findRecomposer(), "and content nested in it composes under the same one")

            // Out of the tree, so the registration is all that is left to reach it.
            outer.remove(inner)
            frame.dispose()

            awaitUntil("closing the window ends the content nested in its content") { innerEnded }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun contentNamedTheRecomposerDrivingItsNeighborInAWindowWithNoRootPaneJoinsIt() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // A bare Frame shares no recomposer, so the one driving the content already standing in it is
        // what Component.findRecomposer() answers with - and naming it is how a second container joins
        // that content. It is this library's own, ending itself once nothing is registered with it, so
        // content that named it has to register: taking it for a recomposer of the caller's own would
        // leave this content on one reaped the moment its neighbor is disposed.
        val frame = Frame().apply { pack() }
        try {
            val west = JPanel().also { frame.add(it, BorderLayout.WEST) }
            val east = JPanel().also { frame.add(it, BorderLayout.EAST) }
            val westHandle = west.setContent { Label(text = "west") }
            awaitUntil("the first content composes") { labelTexts(west).singleOrNull() == "west" }
            val driving = assertNotNull(west.findRecomposer(), "content composed in a bare frame is driven")

            var caption by mutableStateOf("east")
            east.setContent(parent = driving) { Label(text = caption) }
            awaitUntil("the content naming it composes") { labelTexts(east).singleOrNull() == "east" }

            westHandle.dispose()
            caption = "changed"
            awaitUntil("the content that named it goes on recomposing after its neighbor is disposed") {
                labelTexts(east).singleOrNull() == "changed"
            }
            assertNotSame(
                Recomposer.State.ShutDown,
                driving.currentState.value,
                "a recomposer with content still registered must not reap itself",
            )
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun contentNamingAHostContextInAWindowWithNoRootPaneIsEndedWithThatWindow() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // The same hole as the nested case above, reached by naming the host's context instead of letting
        // the container's place resolve it: the window shares no recomposer to fall back on, so the only
        // one left is the one driving the content this container hangs inside.
        val frame = Frame().apply { pack() }
        try {
            val outer = JPanel().also { frame.add(it, BorderLayout.CENTER) }
            var host: CompositionContext? = null
            outer.setContent {
                host = rememberCompositionContext()
                Label(text = "outer")
            }
            awaitUntil("the host composition publishes its composition context") { host != null }

            val inner = JPanel().also { outer.add(it) }
            var innerComposed = false
            var innerEnded = false
            inner.setContent(parent = host ?: error("no host context")) {
                DisposableEffect(Unit) {
                    innerComposed = true
                    onDispose { innerEnded = true }
                }
            }
            awaitUntil("the nested content composition mounts") { innerComposed }

            // Out of the tree, so the registration is all that is left to reach it.
            outer.remove(inner)
            frame.dispose()

            awaitUntil("closing the window ends the content that named its content's context") { innerEnded }
        } finally {
            frame.dispose()
        }
    }
}

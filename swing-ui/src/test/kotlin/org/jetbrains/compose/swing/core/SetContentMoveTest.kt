package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Frame
import java.awt.GraphicsEnvironment
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for what a `setContent` content composition does when its container moves. It watches
 * the window its container is in: it composes the content again when the container ends up in another
 * window, and leaves it exactly where it is for every other move - a container joining the window its
 * parent composition is already in, one in transit between two windows, one going back where it came
 * from.
 *
 * Every case realizes a real top-level peer, so each skips (reports SKIPPED) on a headless environment.
 * Frames are packed rather than shown - that realizes the peer without flashing a window on screen - and
 * disposed on every exit path so no peer leaks. Content driven by a window's own real Swing frame-clock
 * timer is awaited by yielding the EDT back between checks, until a bounded deadline.
 */
class SetContentMoveTest {
    @Test
    fun aCompositionUnderAHostContextJoinsTheOtherWindowItsContainerIsMovedTo() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            // The same content composition, moved out of the window its host is in and into another
            // one. Where it hangs in the Swing tree is what the second window can account for, so it is
            // composed again under that window and reads it.
            val host = hostIn(first)
            val composition = JPanel().also { first.contentPane.add(it) }
            val recorder = CompositionRecorder()
            val handle =
                composition.setContent(parent = host.context) {
                    recorder.Read()
                    Label(text = "content")
                }
            val composedOnce = recorder.remembered
            assertSame(
                first,
                recorder.windows.last(),
                "the content composition must start out under the window its host is in",
            )

            second.contentPane.add(composition)
            second.pack()

            awaitUntil("the content composition composes under the window it was moved to") {
                recorder.windows.last() === second
            }
            assertSame(
                second,
                recorder.windows.last(),
                "a container moved to another window must read the window it is then in",
            )
            assertNotSame(
                composedOnce,
                recorder.remembered,
                "joining another window's composition means composing again there",
            )
            assertEquals(
                listOf("content"),
                labelTexts(composition),
                "the content composition composed again must hold its content",
            )

            handle.dispose()
            host.handle.dispose()
        } finally {
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun aContainerAddedToAnotherWindowComposesUnderTheWindowItIsThenIn() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            // Mounted under one window's context, the container is then taken out of that window and
            // added to another one, passing through a place that belongs to no window on the way. Its
            // content is composed again under the window it is now in, and keeps composing through the
            // move.
            val panel = JPanel().also { first.contentPane.add(it) }
            val recorder = CompositionRecorder()
            val handle =
                panel.setContent(parent = first.compositionContext()) {
                    recorder.Read()
                    Label(text = "moved")
                }
            assertSame(
                first,
                recorder.windows.first(),
                "the content composition must start out under the window whose context was named",
            )

            second.contentPane.add(panel)
            second.pack()

            awaitUntil("the content composition composes under the window it was added to") {
                recorder.windows.last() === second
            }
            assertSame(
                second,
                recorder.windows.last(),
                "a container added to another window must compose under that window",
            )
            assertEquals(
                listOf("moved"),
                labelTexts(panel),
                "the content composition composed again must hold its content",
            )

            handle.dispose()
            assertEquals(0, panel.hierarchyListeners.size, "disposing after a move must leave no listener behind")
            assertEquals(emptyList(), labelTexts(panel), "disposing after a move must take the content down")
        } finally {
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun aCompositionWithNoParentNamedFollowsItsContainerToAnotherWindowAndOutlivesTheOneItLeft() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            // A container with no parent named composes under the window it is in, and every content
            // composition of one window recomposes with the rest of it - which has to hold after a
            // move, or the content left behind on the first window's recomposer goes quiet the moment
            // that window is disposed.
            val panel = JPanel().also { first.contentPane.add(it) }
            val recorder = CompositionRecorder()
            var text by mutableStateOf("v0")
            val handle =
                panel.setContent {
                    recorder.Read()
                    Label(text = text)
                }
            awaitUntil("the content composition composes under the window it was mounted in") {
                recorder.windows.isNotEmpty()
            }
            assertSame(
                first,
                recorder.windows.last(),
                "the content composition must start out under the window it is in",
            )

            // Out of the first window and in no window at all: nothing to join yet, so the content
            // composition keeps composing where it is rather than being torn down on the way.
            first.contentPane.remove(panel)
            text = "in-transit"
            awaitUntil("the content composition in no window goes on recomposing") {
                labelTexts(panel) == listOf("in-transit")
            }
            assertSame(
                first,
                recorder.windows.last(),
                "a container in no window must keep composing under the window it came from",
            )

            second.contentPane.add(panel)
            second.pack()

            awaitUntil("the content composition composes under the window it arrived in") {
                recorder.windows.last() === second
            }
            text = "arrived"
            awaitUntil("the content composition recomposes with the window it arrived in") {
                labelTexts(panel) == listOf("arrived")
            }

            // The window it left owns the recomposer it was mounted on; disposing that window cancels
            // it. Content that had joined the second window is driven by that one and carries on.
            first.dispose()
            awaitUntil("the window it left releases its recomposer") { first.swingRecomposerOrNull() == null }

            text = "outlived"
            awaitUntil("the content composition keeps recomposing once the window it left is disposed") {
                labelTexts(panel) == listOf("outlived")
            }

            handle.dispose()
        } finally {
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun aCompositionTakenOutOfItsWindowKeepsComposingAndJoinsNothingWhenItGoesBack() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // Taken out of the window and put back into it, the container has been in exactly one window
            // throughout. Nothing it passed through is another window, so the composition it belongs to is
            // never given up - not while it hangs off nothing, and not when it arrives back.
            val host = hostIn(frame)
            val composition = JPanel().also { frame.contentPane.add(it) }
            val recorder = CompositionRecorder()
            var text by mutableStateOf("v0")
            val handle =
                composition.setContent(parent = host.context) {
                    recorder.Read()
                    Label(text = text)
                }
            val composedOnce = recorder.remembered
            frame.pack()

            frame.contentPane.remove(composition)
            text = "in-transit"
            awaitUntil("the content composition in no window goes on recomposing") {
                labelTexts(composition) == listOf("in-transit")
            }
            assertSame(
                composedOnce,
                recorder.remembered,
                "a container in no window must keep the composition it has",
            )
            assertEquals(
                "from-host",
                recorder.greeting,
                "a container in no window must go on reading the locals its host provides",
            )

            frame.contentPane.add(composition)
            text = "back"
            awaitUntil("the content composition back in its window goes on recomposing") {
                labelTexts(composition) == listOf("back")
            }
            assertSame(
                composedOnce,
                recorder.remembered,
                "arriving back in the window it was in is no move between windows, so the composition stays",
            )
            assertSame(
                frame,
                recorder.windows.last(),
                "a content composition that has been in one window throughout must read that window",
            )

            handle.dispose()
            host.handle.dispose()
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aCompositionGivenItsOwnRecomposerKeepsItAcrossAWindowMove() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        val recomposer = SwingRecomposer.create(JPanel())
        val composition = JPanel().also { first.contentPane.add(it) }
        val windowComposition = JPanel().also { first.contentPane.add(it) }
        var text by mutableStateOf("v0")
        var compositionHandle: DisposableHandle? = null
        var windowHandle: DisposableHandle? = null
        try {
            // A recomposer of its own belongs to no window, so the move a container carrying it makes
            // between windows is nothing the composition it belongs to has to account for.
            compositionHandle = composition.setContent(parent = recomposer.compositionContext) { Label(text = text) }
            windowHandle = windowComposition.setContent { Label(text = text) }
            awaitUntil("both content compositions render") {
                labelTexts(composition) == listOf("v0") && labelTexts(windowComposition) == listOf("v0")
            }

            second.contentPane.add(composition)
            second.pack()
            awaitUntil("the content composition is in the second window") {
                SwingUtilities.getWindowAncestor(composition) === second
            }

            // Only the caller's recomposer is disposed. A content composition that kept it must stop
            // recomposing; the window's own content composition goes on.
            recomposer.dispose()
            text = "v1"
            awaitUntil("the window's own content composition recomposes") {
                labelTexts(windowComposition) == listOf("v1")
            }
            delay(QUIET_PERIOD)
            assertEquals(
                listOf("v0"),
                labelTexts(composition),
                "a content composition given its own recomposer must keep it across a window move, " +
                    "not join the window",
            )
        } finally {
            compositionHandle?.dispose()
            windowHandle?.dispose()
            recomposer.dispose()
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun aCompositionUnderAHostGoesOnReadingItsHostsWindowWhenItsContainerJoinsAnother() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val hostWindow = realizedFrame()
        val otherWindow = realizedFrame()
        try {
            // The content composition composes under a host in one window while hanging off nothing, and
            // its container is then given a place in another. It reads the window of the composition it
            // joined: where a container hangs answers only where that composition names no window at all.
            val host = hostIn(hostWindow)
            val composition = JPanel()
            val recorder = CompositionRecorder()
            val handle = composition.setContent(parent = host.context) { recorder.Read() }

            awaitUntil("the content composition composes under its host") { recorder.windows.isNotEmpty() }
            assertSame(
                hostWindow,
                recorder.windows.last(),
                "a content composition must read the window of the host it joined",
            )

            otherWindow.contentPane.add(composition)
            otherWindow.pack()

            assertSame(
                hostWindow,
                recorder.windows.last(),
                "a container joining a window of its own must not displace the window its composition inherited",
            )
            assertTrue(
                recorder.windows.none { it === otherWindow },
                "the window a content composition's own container hangs in must never be read where one " +
                    "was inherited",
            )

            handle.dispose()
            host.handle.dispose()
        } finally {
            otherWindow.dispose()
            hostWindow.dispose()
        }
    }

    @Test
    fun aCompositionWhoseMoveIsUndoneBeforeRejoinRunsKeepsItsContextPublished() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            val composition = JPanel().also { first.contentPane.add(it) }
            val handle = composition.setContent { Label(text = "rooted") }
            awaitUntil("the content composition renders in its first window") {
                labelTexts(composition) == listOf("rooted")
            }

            val published = composition.contentCompositionOrNull()?.publishedContext
            assertNotNull(published, "a content composition must publish the context its content composes under")

            // Both within one EDT turn, so the content composition enters the second window and leaves
            // it again before the rejoin queued for the move can run: the move is undone before it is
            // served.
            second.contentPane.add(composition)
            second.contentPane.remove(composition)
            repeat(4) { yield() }

            assertSame(
                published,
                composition.contentCompositionOrNull()?.publishedContext,
                "a content composition whose move is undone must publish the context it published before it",
            )

            handle.dispose()
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @Test
    fun aContainerPutBackBeforeItsRejoinRunsKeepsTheRecomposerItWasOn() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // The window the content starts in has no root pane, so it shares no recomposer and answering
        // the rejoin by asking that window would answer nothing: the registration the move withdrew
        // would stay withdrawn, and the recomposer would be reaped as unused under live content. The
        // content composition's own record of what it composes under is what puts the registration back.
        val bare = Frame().apply { pack() }
        val other = realizedFrame()
        try {
            val composition = JPanel().also { bare.add(it) }
            var caption by mutableStateOf("first")
            val handle = composition.setContent { Label(text = caption) }
            awaitUntil("the content composition renders in the window it starts in") {
                labelTexts(composition) == listOf("first")
            }

            // Both within one EDT turn, so the container leaves the bare frame and is back in it before
            // the rejoin queued for the move can run.
            other.contentPane.add(composition)
            bare.add(composition)
            repeat(4) { yield() }

            caption = "second"
            awaitUntil("the content put back where it was goes on recomposing") {
                labelTexts(composition) == listOf("second")
            }

            handle.dispose()
        } finally {
            other.dispose()
            bare.dispose()
        }
    }

    @Test
    fun aHierarchyEventWhileContentIsPendingButParentStillUnresolvableChangesNothing() = runSwingTest {
        // A setContent on a detached container is pending. Moving it into another detached container
        // fires a hierarchy event but the parent is still unresolvable: the Pending → resolve null
        // branch of placeChanged() runs and the composition remains pending.
        val outer = JPanel()
        val inner = JPanel()
        val handle = inner.setContent { Label(text = "pending") }
        try {
            // inner is detached; setContent is pending. Adding inner into outer fires a hierarchy
            // event on inner - both are still detached so resolveParentOrNull returns null and
            // nothing composes yet.
            outer.add(inner)
            repeat(4) { yield() }

            assertEquals(0, inner.componentCount, "content must still be pending - no window to compose in")
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aRejoinScheduledBeforeDisposeRunsCleanly() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // Schedule a rejoin (move to second window), then dispose before the rejoin fires. The rejoin
        // runs with phase == Disposed: parentToRejoin returns null (moved == false), and neither branch
        // of rejoin() executes. Nothing must throw or leave state behind.
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            val composition = JPanel().also { first.contentPane.add(it) }
            val handle = composition.setContent { Label(text = "content") }
            awaitUntil("content composes in first window") {
                labelTexts(composition) == listOf("content")
            }

            // Move queues a rejoin. Dispose before that queued event can run — both in the same turn.
            second.contentPane.add(composition)
            handle.dispose()

            // Let the queued rejoin fire and settle. With phase == Disposed it must be a no-op.
            repeat(8) { yield() }

            assertEquals(0, composition.componentCount, "disposed content leaves the container empty")
            assertEquals(0, composition.hierarchyListeners.size, "disposed content leaves no listener behind")
        } finally {
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun aContainerRemovedFromAllWindowsBeforeItsRejoinFiresComposeNothing() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // Move queues a rejoin. Remove the container from all windows before the rejoin fires.
        // parentToRejoin sees arrivedIn == null so moved == false, returns null, and rejoin's
        // else-if branch (phase == Mounted) restores the registration rather than composing again.
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            val composition = JPanel().also { first.contentPane.add(it) }
            var caption by mutableStateOf("first")
            val handle = composition.setContent { Label(text = caption) }
            awaitUntil("content composes in first window") {
                labelTexts(composition) == listOf("first")
            }

            // Move to second (queues rejoin), then immediately detach from all windows in the same turn.
            second.contentPane.add(composition)
            second.contentPane.remove(composition)

            // Let the rejoin fire. With arrivedIn == null, parentToRejoin returns null and
            // the else-if branch restores context + registration without composing again.
            repeat(8) { yield() }

            caption = "second"
            // The composition should still be live (it went back to no-window state, context restored).
            awaitUntil("recomposition fires after the no-op rejoin") {
                labelTexts(composition) == listOf("second")
            }

            handle.dispose()
        } finally {
            second.dispose()
            first.dispose()
        }
    }
}

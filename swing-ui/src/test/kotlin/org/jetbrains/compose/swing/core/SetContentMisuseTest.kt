package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What `setContent` refuses, and what it must go on accepting.
 *
 * Two things make a call unanswerable rather than merely unusual. A container that already holds a live
 * island is asked once for the composition its contents nest into, and cannot give two answers. A named
 * parent whose runtime has ended would never recompose what is composed under it. Both fail on the
 * call.
 *
 * Each case that refuses is paired with the nearest legal call, because a check that fires on a legal
 * transition is worse than the misuse it guards against.
 */
class SetContentMisuseTest {
    @Test
    fun aContainerAlreadyHoldingALiveIslandRefusesMoreContent() = runSwingTest {
        val panel = JPanel()
        val runtime = SwingRecomposer.create(panel)
        try {
            panel.setContent(parent = runtime.compositionContext) { Label(text = "first") }

            val refused =
                assertFailsWith<IllegalStateException> {
                    panel.setContent(parent = runtime.compositionContext) { Label(text = "second") }
                }
            assertTrue(
                "two islands cannot both be" in refused.message.orEmpty(),
                "the refusal must say why a container answers for one island only, and said: ${refused.message}",
            )
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun aContainerWhoseIslandWasDisposedTakesContentAgain() = runSwingTest {
        val panel = JPanel()
        val runtime = SwingRecomposer.create(panel)
        try {
            val first = panel.setContent(parent = runtime.compositionContext) { Label(text = "first") }
            first.dispose()

            // Only a live island stands in the way, so the container answers for this one now.
            panel.setContent(parent = runtime.compositionContext) { Label(text = "second") }

            assertEquals(1, panel.componentCount, "the container should hold the content mounted second")
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun aRuntimeThatHasBeenDisposedIsRefusedAsAParent() = runSwingTest {
        val panel = JPanel()
        val runtime = SwingRecomposer.create(panel)
        val ended = runtime.compositionContext
        runtime.dispose()

        val refused =
            assertFailsWith<IllegalStateException> {
                panel.setContent(parent = ended) { Label(text = "never composes") }
            }
        assertTrue(
            "would never recompose" in refused.message.orEmpty(),
            "the refusal must say what composing under an ended runtime would cost, and said: ${refused.message}",
        )
        assertEquals(0, panel.componentCount, "a refused mount must leave the container empty")
        assertEquals(0, panel.hierarchyListeners.size, "a refused mount must leave no listener behind")
    }

    @Test
    fun aRuntimeThatIsStillLiveIsAcceptedAsAParent() = runSwingTest {
        val panel = JPanel()
        val runtime = SwingRecomposer.create(panel)
        try {
            // A runtime stands at Inactive between being created and its coroutine running, which is not
            // an ended one. Naming it on the call after create() is the transition the check must pass.
            panel.setContent(parent = runtime.compositionContext) { Label(text = "composes") }

            assertEquals(1, panel.componentCount, "a live runtime must compose the content it was named for")
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun aRuntimeCancelledBeforeItEverRecomposedIsRefusedAsAParent() = runSwingTest {
        // A runtime cancelled before it ever recomposed reaches an ended state through its effect job
        // completing rather than through the cancel itself, so this pins that a runtime which never ran
        // is refused too - not only one that ran and was then stopped.
        val neverRan = Recomposer(Dispatchers.Swing)
        neverRan.cancel()

        val panel = JPanel()
        val refused =
            assertFailsWith<IllegalStateException> {
                panel.setContent(parent = neverRan) { Label(text = "never composes") }
            }
        assertTrue(
            "would never recompose" in refused.message.orEmpty(),
            "a cancelled runtime must be refused whatever state it reports, and said: ${refused.message}",
        )
    }

    @Test
    fun theContextOfAClosedWindowIsRefusedAsAParent() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = JFrame().apply { pack() }
        val ended = frame.compositionContext()
        val listenersHeld = frame.windowListeners.size

        // Disposing the window ends the runtime it owns and takes the listener naming that runtime off
        // the window, which is what would otherwise leave this context looking like a runtime standing on
        // its own. dispose() posts that event rather than delivering it, so the queue is drained first:
        // until it is, the runtime is still live and still findable, and refusing the mount would be
        // wrong.
        frame.dispose()
        var cycles = 0
        while (frame.windowListeners.size == listenersHeld && cycles++ < CLOSE_CYCLES) yield()
        assertTrue(
            frame.windowListeners.size < listenersHeld,
            "the window must finish closing before this asserts what a closed window's context does",
        )

        val panel = JPanel()
        val refused =
            assertFailsWith<IllegalStateException> {
                panel.setContent(parent = ended) { Label(text = "never composes") }
            }
        assertTrue(
            "would never recompose" in refused.message.orEmpty(),
            "a closed window's context must be refused as an ended runtime, and said: ${refused.message}",
        )
    }

    private companion object {
        /** Event-dispatch cycles a window's own teardown may take after `dispose()` posts it. */
        const val CLOSE_CYCLES: Int = 50
    }
}

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What `setContent` refuses, and what it must go on accepting.
 *
 * Two things make a call unanswerable rather than merely unusual. A container that already holds a live
 * content composition - composed, or still pending until the container reaches a window - is asked once
 * for the composition its contents nest into, and cannot give two answers. A named parent whose
 * recomposer has ended would never recompose what is composed under it. Both fail on the call.
 *
 * Each case that refuses is paired with the nearest legal call, because a check that fires on a legal
 * transition is worse than the misuse it guards against.
 */
class SetContentMisuseTest {
    @Test
    fun aContainerAlreadyHoldingALiveCompositionRefusesMoreContent() = runSwingTest {
        val panel = JPanel()
        val recomposer = SwingRecomposer.create(panel)
        try {
            panel.setContent(parent = recomposer.compositionContext) { Label(text = "first") }

            val refused =
                assertFailsWith<IllegalStateException> {
                    panel.setContent(parent = recomposer.compositionContext) { Label(text = "second") }
                }
            assertTrue(
                "two content compositions cannot both be" in refused.message.orEmpty(),
                "the refusal must say why a container answers for one content composition only, " +
                    "and said: ${refused.message}",
            )
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun aContainerWhoseMountIsStillPendingRefusesMoreContent() = runSwingTest {
        // In no window and under no host, so the mount phase defers and publishes no context yet. The
        // pending content will compose the moment the panel reaches a window, so it stands in the way
        // exactly as a composed one does.
        val panel = JPanel()
        val pending = panel.setContent { Label(text = "first") }

        val refused =
            assertFailsWith<IllegalStateException> {
                panel.setContent { Label(text = "second") }
            }
        assertTrue(
            "two content compositions cannot both be" in refused.message.orEmpty(),
            "the refusal must say why a container answers for one content composition only, " +
                "and said: ${refused.message}",
        )

        // Disposing the pending handle frees the container, so the next call is accepted and defers as
        // the first one did.
        pending.dispose()
        val accepted = panel.setContent { Label(text = "third") }
        assertEquals(0, panel.componentCount, "accepted content on a detached container stays pending")
        accepted.dispose()
    }

    @Test
    fun aContainerWhoseCompositionWasDisposedTakesContentAgain() = runSwingTest {
        val panel = JPanel()
        val recomposer = SwingRecomposer.create(panel)
        try {
            val first = panel.setContent(parent = recomposer.compositionContext) { Label(text = "first") }
            first.dispose()

            // Only a live content composition stands in the way, so the container answers for this one now.
            panel.setContent(parent = recomposer.compositionContext) { Label(text = "second") }

            assertEquals(1, panel.componentCount, "the container should hold the content mounted second")
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun aRecomposerThatHasBeenDisposedIsRefusedAsAParent() = runSwingTest {
        val panel = JPanel()
        val recomposer = SwingRecomposer.create(panel)
        val ended = recomposer.compositionContext
        recomposer.dispose()

        val refused =
            assertFailsWith<IllegalStateException> {
                panel.setContent(parent = ended) { Label(text = "never composes") }
            }
        assertTrue(
            "would never recompose" in refused.message.orEmpty(),
            "the refusal must say what composing under an ended recomposer would cost, and said: ${refused.message}",
        )
        assertEquals(0, panel.componentCount, "a refused call must leave the container empty")
        assertEquals(0, panel.hierarchyListeners.size, "a refused call must leave no listener behind")
    }

    @Test
    fun aRecomposerThatIsStillLiveIsAcceptedAsAParent() = runSwingTest {
        val panel = JPanel()
        val recomposer = SwingRecomposer.create(panel)
        try {
            // A recomposer stands at Inactive between being created and its coroutine running, which is
            // not an ended one. Naming it on the call after create() is the transition the check must pass.
            panel.setContent(parent = recomposer.compositionContext) { Label(text = "composes") }

            assertEquals(1, panel.componentCount, "a live recomposer must compose the content it was named for")
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun aRecomposerCancelledBeforeItEverRecomposedIsRefusedAsAParent() = runSwingTest {
        // A recomposer cancelled before it ever recomposed reaches an ended state through its effect
        // job completing rather than through the cancel itself, so this pins that a recomposer which
        // never ran is refused too - not only one that ran and was then stopped.
        val neverRan = Recomposer(Dispatchers.Swing)
        neverRan.cancel()

        val panel = JPanel()
        val refused =
            assertFailsWith<IllegalStateException> {
                panel.setContent(parent = neverRan) { Label(text = "never composes") }
            }
        assertTrue(
            "would never recompose" in refused.message.orEmpty(),
            "a cancelled recomposer must be refused whatever state it reports, and said: ${refused.message}",
        )
    }

    @Test
    fun theContextOfAClosedWindowIsRefusedAsAParent() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = JFrame().apply { pack() }
        val ended = frame.compositionContext()

        // Disposing the window ends the recomposer it owns, which is what would otherwise leave this
        // context looking like a recomposer standing on its own. dispose() posts that event rather than
        // delivering it, so the queue is drained first: until it is, the recomposer is still live and
        // still findable, and refusing the call would be wrong.
        frame.dispose()
        var cycles = 0
        while (frame.swingRecomposerOrNull() != null && cycles++ < CLOSE_CYCLES) yield()
        assertNull(
            frame.swingRecomposerOrNull(),
            "the window must finish closing before this asserts what a closed window's context does",
        )

        val panel = JPanel()
        val refused =
            assertFailsWith<IllegalStateException> {
                panel.setContent(parent = ended) { Label(text = "never composes") }
            }
        assertTrue(
            "would never recompose" in refused.message.orEmpty(),
            "a closed window's context must be refused as an ended recomposer, and said: ${refused.message}",
        )
    }

    private companion object {
        /** Event-dispatch cycles a window's own teardown may take after `dispose()` posts it. */
        const val CLOSE_CYCLES: Int = 50
    }
}

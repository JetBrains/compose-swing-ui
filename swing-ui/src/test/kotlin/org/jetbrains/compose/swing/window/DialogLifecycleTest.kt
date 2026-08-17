package org.jetbrains.compose.swing.window

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests asserting that a [Dialog] leaving the composition fully releases its realized
 * [JDialog].
 *
 * A visibility change is applied on a fresh event-dispatch tick (a modal show blocks inside a nested
 * event loop, so it can never run inline in an effect), so a dialog can leave the composition while a
 * show it requested is still pending on the event queue. Setting `isVisible` on a disposed window
 * realizes a fresh peer, which would resurrect the dialog with no content, no listeners, and no way
 * to close it from the application; a pending show must therefore settle as a no-op once the dialog
 * has left the composition. The dialog is composed modeless so showing it never blocks the driving
 * thread. Skipped in headless environments where no real peer can be realized.
 *
 * The interleaving under test - two recompositions landing on one event-dispatch turn, ahead of the
 * deferred show that the first of them queued - requires recomposition to be driven synchronously.
 * The shared harness advances recomposition through queued event-dispatch turns, which lets every
 * already-queued runnable (including the deferred show) run between frames, so this test drives a
 * minimal immediate-dispatch recomposer of its own.
 */
class DialogLifecycleTest {
    @Test
    fun pendingShowDoesNotRealizeADialogThatLeftTheComposition() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        runSwingTest {
            val clock = BroadcastFrameClock()
            val recomposer = Recomposer(coroutineContext + clock)
            // The immediate dispatcher resumes the recomposer inline, so a snapshot flush moves it to
            // its frame await and a frame sent to [clock] recomposes before control returns - letting
            // [recomposeNow] complete a whole recomposition ahead of runnables an earlier
            // recomposition queued on the event queue.
            val runner = launch(Dispatchers.Swing.immediate + clock) { recomposer.runRecomposeAndApplyChanges() }
            val visible = mutableStateOf(false)
            val composed = mutableStateOf(true)
            val handle =
                JPanel().setContent(recomposer) {
                    if (composed.value) {
                        Dialog(onCloseRequest = {}, title = DIALOG_TITLE, visible = visible.value) {}
                    }
                }
            try {
                withTimeout(TIMEOUT) {
                    settle(recomposer, clock)
                    val dialog = assertNotNull(realizedDialog(), "Dialog did not realize a JDialog")
                    // Request the show and remove the dialog from the composition on the same
                    // event-dispatch turn: the deferred show is still queued when the dialog is
                    // disposed.
                    visible.value = true
                    recomposeNow(recomposer, clock)
                    composed.value = false
                    recomposeNow(recomposer, clock)
                    assertFalse(dialog.isDisplayable, "leaving the composition must release the dialog's peer")
                    // Yield to the event queue so the deferred show (and anything it triggers) settles.
                    settle(recomposer, clock)
                    assertFalse(
                        dialog.isVisible,
                        "a show requested before the dialog left the composition must not show the disposed dialog",
                    )
                    assertFalse(
                        dialog.isDisplayable,
                        "a show requested before the dialog left the composition must not re-realize the disposed peer",
                    )
                }
            } finally {
                handle.dispose()
                recomposer.close()
                runner.cancel()
            }
        }
    }
}

private const val DIALOG_TITLE = "dialog-pending-show-test"
private val TIMEOUT = 15.seconds
private const val MAX_SYNCHRONOUS_FRAMES = 100

/**
 * Flushes pending snapshot writes and recomposes synchronously on the current event-dispatch turn.
 * Never yields, so runnables the recomposition queues on the event queue (such as a deferred dialog
 * show) are still pending when this returns; the caller decides what runs before them. Bounded so a
 * composition that never settles fails the test instead of hanging the event dispatch thread, which
 * no coroutine timeout could interrupt.
 */
private fun recomposeNow(
    recomposer: Recomposer,
    clock: BroadcastFrameClock,
) {
    Snapshot.sendApplyNotifications()
    var frames = 0
    while (recomposer.hasPendingWork) {
        check(frames++ < MAX_SYNCHRONOUS_FRAMES) {
            "Recomposition did not settle within $MAX_SYNCHRONOUS_FRAMES synchronous frames"
        }
        clock.sendFrame(System.nanoTime())
    }
}

/**
 * Flushes pending snapshot writes and pumps recomposition frames, yielding between them so runnables
 * queued on the event queue run, until the recomposer drains.
 */
private suspend fun settle(
    recomposer: Recomposer,
    clock: BroadcastFrameClock,
) {
    Snapshot.sendApplyNotifications()
    while (recomposer.hasPendingWork) {
        clock.sendFrame(System.nanoTime())
        yield()
    }
    yield()
}

private fun realizedDialog(): JDialog? = java.awt.Window
    .getWindows()
    .filterIsInstance<JDialog>()
    .firstOrNull { it.title == DIALOG_TITLE }

package org.jetbrains.compose.swing.core

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.singleWidget
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral test for a change reported from content standing on a recomposer of the caller's own.
 *
 * A frame taken out of turn is this library's own clock running one, and a caller's recomposer names no
 * such clock. A change reported there must reach the caller and be put back on the cadence the caller
 * drives, rather than fail for a frame it cannot take.
 */
class SettleOnACallersRecomposerTest {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(Dispatchers.Swing + Job() + clock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private var mounted: DisposableHandle? = null
    private var frameTimeNanos = 0L

    init {
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    @AfterTest
    fun tearDown() {
        SwingUtilities.invokeAndWait { mounted?.dispose() }
        recomposer.cancel()
        scope.cancel()
    }

    @Test
    fun aReportedChangeIsPutBackOnTheCallersOwnCadence() = runSwingTest {
        val composition = JPanel()
        var reported = false
        mounted =
            composition.setContent(parent = recomposer) {
                CheckBox(text = "box", checked = false, onCheckedChange = { reported = true })
            }
        val box = singleWidget(composition, JCheckBox::class.java)

        box.doClick()

        assertTrue(reported, "the change must reach the caller from a composition on a recomposer of their own")
        assertTrue(
            box.isSelected,
            "the caller adopted nothing and drove no frame, so nothing can have put the declaration back yet",
        )

        driveCallersCadence()

        assertFalse(
            box.isSelected,
            "the declaration must be put back on the cadence the caller drives",
        )
    }

    /**
     * Publishes what the event queue has written, sends a frame, and lets the event dispatch thread turn
     * so the pass it carries applies. Returns once the recomposer is idle, or after [MAX_FRAMES] frames.
     */
    private suspend fun driveCallersCadence() {
        repeat(MAX_FRAMES) {
            Snapshot.sendApplyNotifications()
            frameTimeNanos += FRAME_INTERVAL_NANOS
            clock.sendFrame(frameTimeNanos)
            yield()
            if (!recomposer.hasPendingWork && !Snapshot.current.hasPendingChanges()) return
        }
    }

    private companion object {
        val FRAME_INTERVAL_NANOS: Long = (1.seconds / 60).inWholeNanoseconds

        /** An upper bound on the frames a caller's cadence takes to carry one change through. */
        const val MAX_FRAMES: Int = 100
    }
}

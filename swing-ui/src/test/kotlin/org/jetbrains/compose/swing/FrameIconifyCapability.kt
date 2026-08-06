package org.jetbrains.compose.swing

import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowListener

/**
 * Asks the window system to minimize [frame] and suspends until it has, skipping the test as a failed
 * assumption rather than failing it if the window system has not acted within [timeoutMillis].
 *
 * Minimizing is a request, not a state a process assigns. `Frame.setExtendedState` records what was
 * asked for and hands it to the window system, which posts [WindowEvent.WINDOW_ICONIFIED] once it has
 * acted - and a host that declines to manage this process's windows, a background agent's among them,
 * never acts and never posts it. So the wait is on the notification and not on the frame's own
 * extended state, which answers with what was asked for whether or not anything came of it.
 *
 * Minimizing a window at all is a capability a host may withhold, not a defect: a frame the host never minimizes skips
 * here; a minimized frame whose content did not follow it into the matching lifecycle state fails below.
 *
 * Call this inside `runComposeSwingTest`: waiting here suspends and hands the event dispatch thread
 * back, which is what lets the notification arrive.
 */
internal suspend fun assumeFrameIconifies(
    frame: Frame,
    timeoutMillis: Long = ICONIFY_TIMEOUT_MILLIS,
) {
    assumeTrue(
        frame.awaitStateChange(WindowEvent.WINDOW_ICONIFIED, timeoutMillis) {
            frame.extendedState = Frame.ICONIFIED
        },
        "requires a window system that minimizes this process's windows: '${frame.title}' was still " +
            "not iconified after ${timeoutMillis}ms",
    )
}

/**
 * Asks the window system to restore [frame] from minimization and suspends until it has, skipping the
 * test as a failed assumption if it has not within [timeoutMillis]. The counterpart of
 * [assumeFrameIconifies], and withheld by a host on the same terms.
 */
internal suspend fun assumeFrameDeiconifies(
    frame: Frame,
    timeoutMillis: Long = ICONIFY_TIMEOUT_MILLIS,
) {
    assumeTrue(
        frame.awaitStateChange(WindowEvent.WINDOW_DEICONIFIED, timeoutMillis) {
            frame.extendedState = Frame.NORMAL
        },
        "requires a window system that restores this process's minimized windows: '${frame.title}' was " +
            "still not restored after ${timeoutMillis}ms",
    )
}

/**
 * Runs [request] with a listener already installed for [eventId] and reports whether the window system
 * posted it before [timeoutMillis] ran out. The listener goes on first so a window system that acts
 * immediately cannot post the notification into the gap before anything was listening.
 */
private suspend fun Frame.awaitStateChange(
    eventId: Int,
    timeoutMillis: Long,
    request: () -> Unit,
): Boolean {
    val arrived = BooleanArray(1)
    val listener: WindowListener =
        object : WindowAdapter() {
            override fun windowIconified(e: WindowEvent) {
                if (eventId == WindowEvent.WINDOW_ICONIFIED) arrived[0] = true
            }

            override fun windowDeiconified(e: WindowEvent) {
                if (eventId == WindowEvent.WINDOW_DEICONIFIED) arrived[0] = true
            }
        }
    addWindowListener(listener)
    return try {
        request()
        val deadline = System.nanoTime() + timeoutMillis * NANOS_PER_MILLI
        while (!arrived[0]) {
            if (System.nanoTime() >= deadline) return false
            delay(POLL_MILLIS)
        }
        true
    } finally {
        removeWindowListener(listener)
    }
}

/**
 * Generous for the same reason the keyboard-focus gate's deadline is, with minimization standing in for
 * activation as the capability being waited on.
 */
private const val ICONIFY_TIMEOUT_MILLIS = 5_000L
private const val POLL_MILLIS = 25L
private const val NANOS_PER_MILLI = 1_000_000L

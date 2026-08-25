package org.jetbrains.compose.swing.util

import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/**
 * Runs [action] on the next turn of the event queue, once for however many calls to [schedule] arrive
 * before that turn.
 *
 * The run is always deferred, including for a call already on the event dispatch thread: a caller takes
 * the turn from this, not only the thread.
 *
 * [schedule] may be called from any thread.
 */
internal class DeferredAction(
    private val action: () -> Unit,
) {
    private val scheduled = AtomicBoolean(false)

    /** A call from [action] schedules the turn after it: the run clears the request before it starts. */
    fun schedule() {
        if (scheduled.getAndSet(true)) return
        SwingUtilities.invokeLater {
            scheduled.set(false)
            action()
        }
    }
}

package org.jetbrains.compose.swing.window

import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Point
import java.awt.Window
import java.awt.event.ComponentEvent
import kotlin.time.Duration.Companion.seconds

/**
 * Places this window at [location] and notifies the move, the way a window system placing a window
 * notifies it.
 *
 * Notifying it here rather than leaving it to the peer keeps the test off native timing: a window
 * system performs a placement and reports it back on a schedule of its own, and a window that is not
 * on screen may have its placement reported late, or not at all. What the composition does with a move
 * it is told about is the subject either way, and this is what tells it about one.
 */
internal fun Window.placeAt(location: Point) {
    setLocation(location.x, location.y)
    notifyPlacement()
}

/**
 * Notifies where this window stands without moving it, the way a window system reports a placement it
 * has performed.
 *
 * The coordinates a placement is reported on are the ones the window stands on when the report is
 * delivered, so a report of the placement a window system is still performing is this over a window
 * that has not been moved to the coordinates asked of it in the meantime.
 */
internal fun Window.notifyPlacement() {
    dispatchEvent(ComponentEvent(this, ComponentEvent.COMPONENT_MOVED))
}

/**
 * Notifies this window as being on screen, the way a window system finishing putting it there notifies
 * it.
 *
 * A composition hears a move out as a user's only once the window has reported itself on screen.
 * Notifying it here rather than showing the window keeps the test off native timing the way [placeAt]
 * does: a window the window system is showing takes placements of its own, and every placement here is
 * meant to be the test's.
 */
internal fun Window.notifyOnScreen() {
    dispatchEvent(ComponentEvent(this, ComponentEvent.COMPONENT_SHOWN))
}

/**
 * Settles the composition over a placement the window system has not performed, leaving [window]
 * standing on the coordinates it stood on beforehand.
 *
 * A window system busy with an earlier placement answers a later one late or not at all: it neither
 * takes the window to the coordinates asked for nor reports them, so the placement stays outstanding
 * and the window goes on standing where it was. AWT answers for itself instead - it moves the window
 * the moment it is asked and notifies the move on the next dispatch, which would report the placement
 * performed and settle it. Withholding that notification for the length of the settle, and putting
 * back the coordinates the window stood on, is what leaves the placement outstanding here.
 *
 * Expects a settled composition, so that the notification withheld is the one this placement produces
 * and no other.
 */
internal suspend fun ComposeSwingTest.settleAPlacementTheWindowSystemHasNotPerformed(window: Window) {
    val standingAt = window.location
    val listeners = window.componentListeners
    listeners.forEach(window::removeComponentListener)
    try {
        awaitIdle()
        window.setLocation(standingAt.x, standingAt.y)
    } finally {
        listeners.forEach(window::addComponentListener)
    }
}

/**
 * Leaves [window] standing on [target] the way a user's drag leaves it, and returns once [settled]
 * holds.
 *
 * A window a user can drag is one the window system has finished putting on screen. Asked to place a
 * window it is still putting there, a window system may decline outright - the window keeps the
 * coordinates it had, and there is no move for the composition to respond to. A user goes on holding
 * the window where they want it, so the placement is repeated until it has taken and been seen.
 *
 * The wait fails the test if [settled] never holds, which is what keeps the caller's assertion
 * load-bearing: a move the composition never responds to runs the deadline down and fails here.
 */
internal suspend fun ComposeSwingTest.moveWindowLikeAUser(
    window: Window,
    target: Point,
    settled: () -> Boolean,
) {
    waitUntil(timeout = USER_MOVE_TIMEOUT) {
        window.placeAt(target)
        settled()
    }
}

/**
 * Wall-clock deadline for a user's move to take and reach the composition, generous because how long a
 * window system takes to put a window on screen is its own affair and varies from run to run. The wait
 * ends on its next check once the move has landed, so its length costs a quicker run nothing.
 */
private val USER_MOVE_TIMEOUT = 10.seconds

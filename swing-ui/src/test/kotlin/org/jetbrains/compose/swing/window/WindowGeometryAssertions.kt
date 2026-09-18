package org.jetbrains.compose.swing.window

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Point
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Waits for [read] to answer [expected] and asserts that it does.
 *
 * A window system performs a placement or a resize on a schedule of its own and reports it back on a
 * later dispatch, so a realized window carries what a composition declared only once the toolkit has
 * finished delivering it. Reading it as soon as the composition has settled reads whatever stood
 * beforehand.
 */
internal suspend fun <T> ComposeSwingTest.assertReaches(
    expected: T,
    message: String? = null,
    read: () -> T,
) {
    waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { read() == expected }
    assertEquals(expected, read(), message)
}

/**
 * Waits for [read] to answer what [expected] answers and asserts that it does.
 *
 * Waits for the reason the value form does, and reads [expected] on every check: a window's preferred
 * size counts in the insets its decorations take, which the window system reports on the same schedule
 * as the resize itself.
 */
internal suspend fun <T> ComposeSwingTest.assertReaches(
    message: String,
    expected: () -> T,
    read: () -> T,
) {
    waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { read() == expected() }
    assertEquals(expected(), read(), message)
}

/**
 * Waits for [read] to answer [expected] up to [POSITION_TOLERANCE_PIXELS] and asserts that it does,
 * absorbing the pixel or two a window manager may shave off a placement it honors.
 *
 * Waits for the reason [assertReaches] does. [expected] is read on every check too, so a placement is
 * compared against where the window it is measured from stands at that moment rather than where it
 * stood when the wait began.
 */
internal suspend fun ComposeSwingTest.assertReachesNear(
    message: String,
    expected: () -> Point,
    read: () -> Point,
) {
    waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { isNear(expected(), read()) }
    val target = expected()
    val actual = read()
    assertTrue(isNear(target, actual), "$message (expected around $target, was $actual)")
}

/** Whether [actual] is [expected] up to [POSITION_TOLERANCE_PIXELS]. */
private fun isNear(
    expected: Point,
    actual: Point,
): Boolean = abs(actual.x - expected.x) <= POSITION_TOLERANCE_PIXELS &&
    abs(actual.y - expected.y) <= POSITION_TOLERANCE_PIXELS

/**
 * Waits until [window] has reported no move and no resize for [WINDOW_QUIET_PERIOD].
 *
 * A window system keeps reshaping a window after the composition has settled. A window lays out again as
 * its menu bar arrives, and a decorated window sized before the window system reported the insets its
 * decorations take is reshaped once it does. A window system still performing one placement may also
 * answer the next late or not at all. A case that reads a window's settled size, moves or resizes a shown
 * window, or opens a menu a look and feel cancels on a reshape, waits here first.
 */
internal suspend fun ComposeSwingTest.awaitWindowStandsStill(window: Window) {
    var lastReshape = System.nanoTime()
    val listener =
        object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                lastReshape = System.nanoTime()
            }

            override fun componentMoved(event: ComponentEvent) {
                lastReshape = System.nanoTime()
            }
        }
    window.addComponentListener(listener)
    try {
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) {
            System.nanoTime() - lastReshape >= WINDOW_QUIET_PERIOD.inWholeNanoseconds
        }
    } finally {
        window.removeComponentListener(listener)
    }
}

/**
 * Runs [block] and answers the values [read] took while it ran, in order, starting with the one it had on
 * entry. [read] is sampled after each write the global snapshot reports to
 * Snapshot.registerGlobalWriteObserver, which reports at least the first write to each state object
 * between apply notifications and none made inside a nested mutable snapshot.
 */
internal suspend fun <T> valuesTakenDuring(
    read: () -> T,
    block: suspend () -> Unit,
): List<T> {
    val values = mutableListOf(read())
    val handle =
        Snapshot.registerGlobalWriteObserver {
            val value = read()
            synchronized(values) { if (value != values.last()) values += value }
        }
    try {
        block()
    } finally {
        handle.dispose()
    }
    return synchronized(values) { values.toList() }
}

/**
 * Wall-clock deadline for a condition gated on a native move, resize or maximize, which the window
 * manager reports with real latency - its animations included.
 */
internal val NATIVE_EVENT_TIMEOUT = 10.seconds

/** Slack allowed on a realized placement, in pixels. */
internal const val POSITION_TOLERANCE_PIXELS = 4

/**
 * How long a window must report no move and no resize to count as standing still. Raise this if a case
 * still sees the window reshape after waiting for it.
 */
private val WINDOW_QUIET_PERIOD = 250.milliseconds

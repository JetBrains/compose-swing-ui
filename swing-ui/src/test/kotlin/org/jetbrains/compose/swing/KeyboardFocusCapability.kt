package org.jetbrains.compose.swing

import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Window
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Declares that a test needs the window system to grant keyboard focus: a window this process realizes
 * has to become the focused one, which is what carries a focus request through to a component and what
 * makes a component report itself as the focus owner.
 *
 * A display is not that capability. A process the window system declines to activate - one running as a
 * background agent, say - realizes, shows and lays out windows normally while none of them ever becomes
 * focused, so the capability is measured on a probe window rather than inferred from the graphics
 * environment. Tests that only need the focus request to be *routed* need none of this: a request
 * records its component as its window's most recent focus owner whether or not the window is focused.
 *
 * This is the cheap half of the gate, and it rules the capability out rather than in: it answers for a
 * probe window of its own, while being focused is a fact about one window at one moment. A test that
 * needs the window it opened focused waits for that window with [assumeWindowBecomesFocused].
 *
 * Call this on the test method, before `runComposeSwingTest`: the probe waits for a window-system
 * notification, which cannot arrive while the event dispatch thread is blocked inside the test body.
 */
internal fun assumeKeyboardFocusIsPossible() {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
    assumeTrue(someWindowCanBeFocused, "requires a window system that focuses this process's windows")
}

/**
 * Suspends until [window] is the focused window and, if it has not become focused within
 * [timeoutMillis], skips the test as a failed assumption rather than failing it.
 *
 * This is the authoritative half of the gate, and it observes one thing only: whether the window system
 * made *this* window the focused one. That is the capability a host may withhold, and its absence is
 * nothing a test can report as a defect of the library. Which component of a focused window holds the
 * keyboard is the library's own doing, so each test asserts that for itself below this gate, with plain
 * assertions: a window the system never focuses skips, and a focused window whose keyboard went to the
 * wrong component fails.
 *
 * Call this inside `runComposeSwingTest`: waiting here suspends and hands the event dispatch thread back,
 * which is what lets the activation the window system posts arrive.
 *
 * The wait only delays. Handing the event dispatch thread back is all it does for the condition: it
 * sends no frame, no snapshot apply notification and no layout pass, so the only thing it can wait for is
 * something the window system reaches on its own. A condition gated on a recomposition or a frame effect
 * never becomes true under this wait, and the deadline expiring then reports a withheld capability - a
 * skip - rather than the stall it is. Driving the composition is the harness's `waitUntil`, which each
 * caller uses for its own assertion once this gate has passed.
 */
internal suspend fun assumeWindowBecomesFocused(
    window: Window,
    timeoutMillis: Long = FOCUS_TIMEOUT_MILLIS,
) {
    assumeTrue(
        awaitFocused(window, timeoutMillis),
        "requires a window system that focuses this process's windows: ${window.describe()} was still " +
            "not the focused window after ${timeoutMillis}ms",
    )
}

/**
 * Measured once per JVM and read only as a reason to skip: the probe shows a window of its own and waits
 * for the window system to focus it. A host that focuses nothing answers `false` here once and every
 * gated test then skips immediately instead of paying a deadline of its own; a host that focuses the
 * probe has said nothing about any other window, which is why this is a pre-check and not the gate.
 */
private val someWindowCanBeFocused: Boolean by lazy { probeKeyboardFocus() }

private fun probeKeyboardFocus(): Boolean {
    if (GraphicsEnvironment.isHeadless()) return false
    val cell = arrayOfNulls<JFrame>(1)
    onEventDispatchThread {
        cell[0] =
            JFrame("keyboard focus capability probe").apply {
                setSize(PROBE_SIDE, PROBE_SIDE)
                isVisible = true
                toFront()
                requestFocus()
            }
    }
    val probe = checkNotNull(cell[0]) { "the probe window must have been created" }
    return try {
        awaitFocusedBlocking(probe)
    } finally {
        onEventDispatchThread { probe.dispose() }
    }
}

private suspend fun awaitFocused(
    window: Window,
    timeoutMillis: Long,
): Boolean {
    val deadline = System.nanoTime() + timeoutMillis * NANOS_PER_MILLI
    while (!window.isFocused) {
        if (System.nanoTime() >= deadline) return false
        delay(POLL_MILLIS)
    }
    return true
}

/**
 * Blocking counterpart of [awaitFocused] for the probe, which runs on the test's own thread with the
 * event dispatch thread free to deliver the activation.
 */
private fun awaitFocusedBlocking(probe: JFrame): Boolean {
    val deadline = System.currentTimeMillis() + FOCUS_TIMEOUT_MILLIS
    while (System.currentTimeMillis() < deadline) {
        if (readOnEventDispatchThread { probe.isFocused }) return true
        Thread.sleep(POLL_MILLIS)
    }
    return false
}

/**
 * Names the window in the skip message, so which window went unfocused is readable from the report. A
 * frame's title is what identifies it to a reader; a window without one falls back to its component name,
 * which AWT generates from the class's own prefix and a counter when none was set.
 */
private fun Window.describe(): String {
    val label = if (this is Frame && !title.isNullOrEmpty()) title else name
    return "window '$label'"
}

private fun onEventDispatchThread(block: () -> Unit): Unit = SwingUtilities.invokeAndWait(block)

private fun readOnEventDispatchThread(read: () -> Boolean): Boolean {
    val value = BooleanArray(1)
    SwingUtilities.invokeAndWait { value[0] = read() }
    return value[0]
}

/**
 * Generous, because the two ways of getting this wrong are not symmetric. A deadline that expires before
 * the window system has acted turns a capability the host does grant into a skipped test, and that
 * coverage is simply lost; an over-long one is only ever run down where the window never becomes focused
 * at all, and the outcome there is a skip either way. How long activation takes where it does happen is
 * the host's own affair and varies from run to run, so the deadline is set for the slowest of them; a
 * wait ends on its next check once the window is focused, so its length costs a quicker run nothing.
 */
private const val FOCUS_TIMEOUT_MILLIS = 5_000L
private const val POLL_MILLIS = 25L
private const val PROBE_SIDE = 64
private const val NANOS_PER_MILLI = 1_000_000L

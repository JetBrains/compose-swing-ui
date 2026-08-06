package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import javax.swing.JFrame
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Behavioural tests for [WindowPosition.CenteredOn], the position that names the window to center on:
 * the declared window is what the placement resolves against on the first pass, a recomposition naming
 * another window re-centers on that one, the placement resolved is written back into the driving state
 * as concrete coordinates, and a window leaving the composition is released while the window it was
 * centered on is left standing.
 *
 * Centering resolves against the bounds the named window holds as the position is applied, so the
 * window named here is realized and shown by the test itself and released once the assertions are done.
 * Every centered peer is composed with an explicit size, so the geometry apply reaches it before it is
 * shown: the placement is computed by AWT alone rather than settling asynchronously behind a native
 * resize.
 *
 * Skipped in headless environments, where there is no window to center on.
 */
class WindowPositionCenteredOnTest {
    @Test
    fun aCenteredOnPositionStandsForTheWindowItNames() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = JFrame("centered-on-identity-first")
        val second = JFrame("centered-on-identity-second")
        try {
            assertEquals(
                WindowPosition.CenteredOn(first),
                WindowPosition.CenteredOn(first),
                "two positions naming the same window are equal, so a position is told apart by the " +
                    "window it names",
            )
            assertEquals(
                WindowPosition.CenteredOn(first).hashCode(),
                WindowPosition.CenteredOn(first).hashCode(),
                "positions that are equal must hash alike",
            )
            assertNotEquals(
                WindowPosition.CenteredOn(first),
                WindowPosition.CenteredOn(second),
                "positions naming different windows ask for different placements",
            )
            assertTrue(
                WindowPosition.CenteredOn(first).toString().startsWith("CenteredOn(window="),
                "the position should print its name and the window it names",
            )
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @Test
    fun aCenteredOnPositionPlacesTheWindowOnTheWindowItNames() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        // The named window sits away from the screen's center on purpose: a window centered on the
        // screen instead of on the window it names lands elsewhere and fails the assertion.
        val named = shownFrame("centered-on-reference", Rectangle(screen.x + 40, screen.y + 40, 480, 360))
        try {
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { named.isShowing }
            val state = WindowState(position = WindowPosition.CenteredOn(named), size = Dimension(240, 160))
            setContent { Window(onCloseRequest = {}, state = state, title = "centered-on-follower") {} }

            val follower = onWindowWithTitle("centered-on-follower").fetch<JFrame>()
            assertCenteredOn(
                named,
                follower,
                "a CenteredOn position must center the window on the window it names",
            )
        } finally {
            named.dispose()
        }
    }

    @Test
    fun aCenteredOnPositionNamingAnotherWindowRecentersOnThatOne() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        // The two named windows stand far enough apart that a window centered on the wrong one lands
        // outside the tolerance of the other's center.
        val first = shownFrame("centered-on-first", Rectangle(screen.x + 40, screen.y + 40, 480, 360))
        val second = shownFrame("centered-on-second", Rectangle(screen.x + 560, screen.y + 40, 480, 360))
        try {
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { first.isShowing && second.isShowing }
            val state = WindowState(position = WindowPosition.CenteredOn(first), size = Dimension(240, 160))
            setContent { Window(onCloseRequest = {}, state = state, title = "centered-on-reowned-follower") {} }

            val follower = onWindowWithTitle("centered-on-reowned-follower").fetch<JFrame>()
            assertCenteredOn(first, follower, "the window must start centered on the window first named")

            // The resolved placement travels back into the state, so the next declaration is made over
            // the coordinates the first one settled on rather than racing them.
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { state.position is WindowPosition.Absolute }
            state.position = WindowPosition.CenteredOn(second)
            awaitIdle()
            assertCenteredOn(
                second,
                follower,
                "a position naming another window must re-center the window on that window",
            )
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @Test
    fun theResolvedCenteringIsWrittenBackAsConcreteCoordinates() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val named = shownFrame("centered-on-write-back-reference", Rectangle(screen.x + 40, screen.y + 40, 480, 360))
        try {
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { named.isShowing }
            val state = WindowState(position = WindowPosition.CenteredOn(named), size = Dimension(240, 160))
            setContent { Window(onCloseRequest = {}, state = state, title = "centered-on-write-back") {} }

            val follower = onWindowWithTitle("centered-on-write-back").fetch<JFrame>()
            // The placement travels back on a component-moved event, delivered on a later dispatch.
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { state.position is WindowPosition.Absolute }
            assertEquals(
                WindowPosition.Absolute(follower.x, follower.y),
                state.position,
                "the resolved centering must replace the request with the window's realized coordinates",
            )
        } finally {
            named.dispose()
        }
    }

    @Test
    fun aCenteredWindowStandsStillUntilThePositionIsDeclaredAgain() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val named = shownFrame("centered-on-moving-reference", Rectangle(screen.x + 40, screen.y + 40, 480, 360))
        try {
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { named.isShowing }
            val state = WindowState(position = WindowPosition.CenteredOn(named), size = Dimension(240, 160))
            setContent { Window(onCloseRequest = {}, state = state, title = "centered-on-standing-still") {} }

            val follower = onWindowWithTitle("centered-on-standing-still").fetch<JFrame>()
            // Wait for the resolved placement to reach the state: only then does declaring the centering
            // again ask for something the state does not already hold.
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { state.position is WindowPosition.Absolute }
            val placed = follower.location
            val standingOn = named.locationOnScreen

            named.setLocation(standingOn.x + 200, standingOn.y + 120)
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { named.locationOnScreen != standingOn }
            awaitIdle()
            assertEquals(
                placed,
                follower.location,
                "a centered window stands where it was put once the window it was centered on moves on",
            )

            state.position = WindowPosition.CenteredOn(named)
            awaitIdle()
            assertCenteredOn(
                named,
                follower,
                "declaring the centering again must re-center on the bounds the named window holds then",
            )
        } finally {
            named.dispose()
        }
    }

    @Test
    fun aCenteredWindowLeavingTheCompositionLeavesTheWindowItWasCenteredOn() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val named = shownFrame("centered-on-outliving-reference", Rectangle(screen.x + 40, screen.y + 40, 480, 360))
        try {
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { named.isShowing }
            val state = WindowState(position = WindowPosition.CenteredOn(named), size = Dimension(240, 160))
            var composed by mutableStateOf(true)
            setContent {
                if (composed) {
                    Window(onCloseRequest = {}, state = state, title = "centered-on-transient") {}
                }
            }

            val follower = onWindowWithTitle("centered-on-transient").fetch<JFrame>()
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { state.position is WindowPosition.Absolute }
            val resolved = state.position

            composed = false
            awaitIdle()

            onWindowWithTitle("centered-on-transient").assertDoesNotExist()
            assertFalse(follower.isDisplayable, "a window leaving the composition must be released")
            assertTrue(
                named.isDisplayable,
                "a window a caller owns and named to center on must outlive the window that named it",
            )
            assertEquals(
                resolved,
                state.position,
                "the coordinates the centering resolved to must outlive the window that resolved them",
            )
        } finally {
            named.dispose()
        }
    }
}

/** A realized, shown [JFrame] standing on [standingOn], for a centering position to resolve against. */
private fun shownFrame(
    title: String,
    standingOn: Rectangle,
): JFrame = JFrame(title).apply {
    bounds = standingOn
    isVisible = true
}

/**
 * Asserts that [centered] stands centered on [reference]'s current bounds, up to
 * [CENTERING_TOLERANCE_PIXELS].
 */
private fun assertCenteredOn(
    reference: Window,
    centered: Window,
    message: String,
) {
    val referenceLocation = reference.locationOnScreen
    assertNearCenter(
        Point(
            referenceLocation.x + (reference.width - centered.width) / 2,
            referenceLocation.y + (reference.height - centered.height) / 2,
        ),
        centered.location,
        message,
    )
}

/**
 * Asserts that [actual] is [expected] up to [CENTERING_TOLERANCE_PIXELS], absorbing the pixel or two a
 * window manager may shave off a placement it honors.
 */
private fun assertNearCenter(
    expected: Point,
    actual: Point,
    message: String,
) {
    assertTrue(
        abs(actual.x - expected.x) <= CENTERING_TOLERANCE_PIXELS &&
            abs(actual.y - expected.y) <= CENTERING_TOLERANCE_PIXELS,
        "$message (expected around $expected, was $actual)",
    )
}

/**
 * Wall-clock deadline for conditions gated on native window-system notifications (moves, shows), which
 * arrive with real latency - including window-manager animations.
 */
private const val NATIVE_EVENT_TIMEOUT_MILLIS = 10_000L

/** Slack allowed on a realized placement, in pixels. */
private const val CENTERING_TOLERANCE_PIXELS = 4

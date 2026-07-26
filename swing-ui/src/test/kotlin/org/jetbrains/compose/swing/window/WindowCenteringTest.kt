package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural tests asserting that a centering [WindowPosition] places a realized peer:
 * [WindowPosition.CenteredOnScreen] centers it on the screen, [WindowPosition.CenteredOnOwner] on the
 * window that owns it, and the resolved placement is written back into the driving state as concrete
 * coordinates.
 *
 * Every peer is composed with an explicit size, so the geometry apply reaches it before it is shown:
 * the placement is computed by AWT alone rather than settling asynchronously behind a native resize.
 * The tests that realize a peer are skipped in headless environments; the string form of the
 * centering positions holds with or without a display.
 */
class WindowCenteringTest {
    @Test
    fun centeringPositionsPrintTheirDeclaredName() {
        assertEquals("CenteredOnScreen", WindowPosition.CenteredOnScreen.toString())
        assertEquals("CenteredOnOwner", WindowPosition.CenteredOnOwner.toString())
    }

    @Test
    fun centeredOnScreenPlacesTheFrameAtTheScreenCenter() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(position = WindowPosition.CenteredOnScreen, size = Dimension(320, 240))
        setContent {
            Window(onCloseRequest = {}, state = state, title = "window-centered-on-screen") {}
        }
        val frame = onWindow().fetch<JFrame>()
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        assertNear(
            Point(
                screen.x + screen.width / 2 - frame.width / 2,
                screen.y + screen.height / 2 - frame.height / 2,
            ),
            frame.location,
            "a CenteredOnScreen position must center the frame on the screen's usable area",
        )
    }

    @Test
    fun centeredOnOwnerPlacesTheDialogOnItsOwningWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        // The owner sits away from the screen's center on purpose: a dialog centered on the screen
        // instead of on its owner lands elsewhere and fails the assertion.
        val ownerState =
            WindowState(position = WindowPosition.Absolute(screen.x + 40, screen.y + 40), size = Dimension(480, 360))
        val dialogState = DialogState(position = WindowPosition.CenteredOnOwner, size = Dimension(240, 160))
        var dialogComposed by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, state = ownerState, title = "centering-owner") {
                if (dialogComposed) {
                    Dialog(onCloseRequest = {}, state = dialogState, title = "centered-on-owner") {}
                }
            }
        }
        val owner = onWindowWithTitle("centering-owner").fetch<JFrame>()
        // A window is centered on its owner's bounds only while the owner is on screen; before that
        // the owner contributes nothing but its screen. Compose the dialog once the owner is showing.
        waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { owner.isShowing }
        dialogComposed = true
        awaitIdle()
        val dialog = onWindowWithTitle("centered-on-owner").fetch<JDialog>()
        val ownerLocation = owner.locationOnScreen
        assertNear(
            Point(
                ownerLocation.x + (owner.width - dialog.width) / 2,
                ownerLocation.y + (owner.height - dialog.height) / 2,
            ),
            dialog.location,
            "a CenteredOnOwner position must center the dialog on its owning window",
        )
    }

    @Test
    fun centeredOnScreenPlacesAnOwnedDialogOnTheScreenAndNotOnItsOwner() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        // The owner sits away from the screen's center on purpose: a dialog centered on its owner
        // instead of on the screen lands elsewhere and fails the assertion.
        val ownerState =
            WindowState(position = WindowPosition.Absolute(screen.x + 40, screen.y + 40), size = Dimension(480, 360))
        val dialogState = DialogState(position = WindowPosition.CenteredOnScreen, size = Dimension(240, 160))
        var dialogComposed by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, state = ownerState, title = "screen-centering-owner") {
                if (dialogComposed) {
                    Dialog(onCloseRequest = {}, state = dialogState, title = "owned-centered-on-screen") {}
                }
            }
        }
        val owner = onWindowWithTitle("screen-centering-owner").fetch<JFrame>()
        waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { owner.isShowing }
        dialogComposed = true
        awaitIdle()
        val dialog = onWindowWithTitle("owned-centered-on-screen").fetch<JDialog>()
        assertNear(
            Point(
                screen.x + screen.width / 2 - dialog.width / 2,
                screen.y + screen.height / 2 - dialog.height / 2,
            ),
            dialog.location,
            "a CenteredOnScreen position must center a dialog on the screen even where it has an owner",
        )
    }

    @Test
    fun centeringIsWrittenBackAsConcreteCoordinates() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(position = WindowPosition.CenteredOnScreen, size = Dimension(320, 240))
        setContent {
            Window(onCloseRequest = {}, state = state, title = "window-centering-write-back") {}
        }
        val frame = onWindow().fetch<JFrame>()
        // The placement travels back on a component-moved event, delivered on a later dispatch.
        waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { state.position is WindowPosition.Absolute }
        assertEquals(
            WindowPosition.Absolute(frame.x, frame.y),
            state.position,
            "the resolved centering must replace the request with the frame's realized coordinates",
        )
    }
}

/**
 * Asserts that [actual] is [expected] up to [POSITION_TOLERANCE_PIXELS], absorbing the pixel or two a
 * window manager may shave off a placement it honors.
 */
private fun assertNear(
    expected: Point,
    actual: Point,
    message: String,
) {
    assertTrue(
        abs(actual.x - expected.x) <= POSITION_TOLERANCE_PIXELS &&
            abs(actual.y - expected.y) <= POSITION_TOLERANCE_PIXELS,
        "$message (expected around $expected, was $actual)",
    )
}

/**
 * Wall-clock deadline for conditions gated on native window-system notifications (moves, resizes,
 * maximize transitions), which arrive with real latency - including window-manager animations.
 */
private const val NATIVE_EVENT_TIMEOUT_MILLIS = 10_000L

/** Slack allowed on a realized placement, in pixels. */
private const val POSITION_TOLERANCE_PIXELS = 4

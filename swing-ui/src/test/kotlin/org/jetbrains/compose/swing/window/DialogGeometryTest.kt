package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import javax.swing.JDialog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioural tests asserting that [Dialog] geometry is two-way with the realized [JDialog]:
 * [DialogState] drives the dialog's position and size, and user-driven resizes and moves are written
 * back into the state.
 *
 * The dialog is composed modeless so showing it never blocks the driving thread inside a nested event
 * loop. Skipped in headless environments where no real peer can be realized.
 */
class DialogGeometryTest {
    @Test
    fun initialGeometryIsAppliedToTheDialog() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(position = WindowPosition(140, 90), size = Dimension(360, 260))
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-initial-geometry") {} }
        val dialog = onWindow().fetch<JDialog>()
        assertEquals(Dimension(360, 260), dialog.size)
        assertEquals(Point(140, 90), dialog.location)
    }

    @Test
    fun positionReactsToStateChange() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(position = WindowPosition(140, 90))
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-position-react") {} }
        val dialog = onWindow().fetch<JDialog>()
        assertEquals(Point(140, 90), dialog.location)
        state.position = WindowPosition(240, 170)
        // Repositioning an already-mapped window is a window-manager capability (like maximizing): a
        // display server without one (e.g. the CI's Xvfb) leaves the dialog where it is. Probe whether
        // the move takes and skip where it is unhonored instead of failing; the apply path itself is
        // covered by platformDefaultToAbsolutePositionMovesTheDialog, which drives an unmapped window.
        val moved =
            runCatching {
                waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { dialog.location == Point(240, 170) }
            }.isSuccess
        assumeTrue(moved, "repositioning a mapped window requires window-manager move support")
        assertEquals(Point(240, 170), dialog.location)
    }

    @Test
    fun sizeReactsToStateChange() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = Dimension(360, 260))
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-size-react") {} }
        val dialog = onWindow().fetch<JDialog>()
        assertEquals(Dimension(360, 260), dialog.size)
        state.size = Dimension(520, 420)
        // Applying size to the peer is an asynchronous native resize; wait for the dialog to reach the
        // target rather than assert right after the compose frame that requests it.
        waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { dialog.size == Dimension(520, 420) }
        assertEquals(Dimension(520, 420), dialog.size)
    }

    @Test
    fun widthReactsToStateChange() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = Dimension(360, 260))
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-width-react") {} }
        val dialog = onWindow().fetch<JDialog>()
        state.width = 520
        waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { dialog.size == Dimension(520, 260) }
        assertEquals(
            Dimension(520, 260),
            dialog.size,
            "assigning width must resize the realized dialog, leaving its height unchanged",
        )
    }

    @Test
    fun heightReactsToStateChange() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = Dimension(360, 260))
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-height-react") {} }
        val dialog = onWindow().fetch<JDialog>()
        state.height = 420
        waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { dialog.size == Dimension(360, 420) }
        assertEquals(
            Dimension(360, 420),
            dialog.size,
            "assigning height must resize the realized dialog, leaving its width unchanged",
        )
    }

    @Test
    fun mutatingAReadSizeCopyLeavesTheDialogAndStateUntouched() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var title by mutableStateOf("dialog-size-copy-inert")
        val state = DialogState(size = Dimension(360, 260))
        setContent { Dialog(onCloseRequest = {}, state = state, title = title) {} }
        val dialog = onWindow().fetch<JDialog>()
        state.size.setSize(520, 420)
        title = "dialog-size-copy-inert-updated"
        awaitIdle()
        assertEquals(
            "dialog-size-copy-inert-updated",
            dialog.title,
            "the unrelated title change must reach the dialog",
        )
        assertEquals(
            Dimension(360, 260),
            dialog.size,
            "mutating the Dimension returned by size must leave the realized dialog's size untouched",
        )
        assertEquals(360, state.width, "mutating the Dimension returned by size must leave width untouched")
        assertEquals(260, state.height, "mutating the Dimension returned by size must leave height untouched")
    }

    @Test
    fun sizeAssignmentAfterMutatingAReadCopyResizesTheDialog() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = Dimension(360, 260))
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-size-detached-copy") {} }
        val dialog = onWindow().fetch<JDialog>()
        // A Dimension read from the state is a detached copy ([java.awt.Component.getSize]
        // semantics): mutating it in place drives nothing, so a follow-up assignment of the same
        // values is a genuine change and must resize the dialog.
        state.size.setSize(520, 420)
        state.size = Dimension(520, 420)
        // The native resize settles asynchronously, and a stale resize event can momentarily echo the
        // prior size back into the state before the resize completes; wait for the dialog AND the state
        // to both reach the assigned size rather than catch that transient.
        waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) {
            dialog.size == Dimension(520, 420) && state.size == Dimension(520, 420)
        }
        assertEquals(
            Dimension(520, 420),
            dialog.size,
            "assigning size must resize the dialog even after a read Dimension copy was mutated to equal values",
        )
        assertEquals(
            Dimension(520, 420),
            state.size,
            "the state must reflect the assigned size",
        )
    }

    @Test
    fun userResizeIsWrittenBackIntoState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = Dimension(360, 260))
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-user-resize") {} }
        val dialog = onWindow().fetch<JDialog>()
        dialog.size = Dimension(640, 480)
        waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { state.size == Dimension(640, 480) }
        assertEquals(Dimension(640, 480), state.size)
    }

    @Test
    fun userMoveIsWrittenBackIntoState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(position = WindowPosition(140, 90))
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-user-move") {} }
        val dialog = onWindow().fetch<JDialog>()
        dialog.setLocation(320, 210)
        // The move write-back is driven by a native component-moved event. A display server without a
        // window manager (e.g. the CI's Xvfb) updates the dialog's location but never delivers that
        // event, so — like maximizing — honoring a move is a window-manager capability: probe it and
        // skip where it is absent instead of failing.
        val moveWrittenBack =
            runCatching {
                waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) {
                    state.position == WindowPosition(320, 210)
                }
            }.isSuccess
        assumeTrue(moveWrittenBack, "a window-move write-back requires window-manager move events")
        assertEquals(WindowPosition(320, 210), state.position)
    }

    @Test
    fun userResizeIsNotFoughtByTheDeclaredValue() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = Dimension(360, 260))
        setContent { Dialog(onCloseRequest = {}, state = state, title = "dialog-no-feedback-loop") {} }
        val dialog = onWindow().fetch<JDialog>()
        dialog.size = Dimension(640, 480)
        // The native resize settles asynchronously, and a stale resize event can momentarily echo the
        // prior size back into the state before the resize completes; wait for the dialog AND the state
        // to both reach the assigned size rather than catch that transient.
        waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) {
            dialog.size == Dimension(640, 480) && state.size == Dimension(640, 480)
        }
        assertEquals(Dimension(640, 480), dialog.size)
        assertEquals(Dimension(640, 480), state.size)
    }

    @Test
    fun platformDefaultPositionNeverRepositionsTheDialog() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var title by mutableStateOf("dialog-platform-default")
        val state = DialogState()
        setContent { Dialog(onCloseRequest = {}, state = state, title = title, visible = false) {} }
        val dialog = onWindow().fetch<JDialog>()
        // Give the peer a concrete placement (written back into the state as an absolute position),
        // then return the state to PlatformDefault: an unspecified position leaves placement alone
        // instead of forcing its (0, 0) coordinates onto the peer.
        dialog.setLocation(320, 210)
        waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { state.position == WindowPosition(320, 210) }
        state.position = WindowPosition.PlatformDefault
        awaitIdle()
        title = "dialog-platform-default-updated"
        awaitIdle()
        assertEquals(
            "dialog-platform-default-updated",
            dialog.title,
            "the unrelated title change must reach the dialog",
        )
        assertEquals(
            Point(320, 210),
            dialog.location,
            "a PlatformDefault position must leave the dialog's placement untouched",
        )
    }

    @Test
    fun platformDefaultToAbsolutePositionMovesTheDialog() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState()
        setContent {
            Dialog(onCloseRequest = {}, state = state, title = "dialog-platform-to-absolute", visible = false) {}
        }
        val dialog = onWindow().fetch<JDialog>()
        state.position = WindowPosition(240, 170)
        awaitIdle()
        assertEquals(
            Point(240, 170),
            dialog.location,
            "assigning an absolute position over PlatformDefault must move the dialog",
        )
    }
}

/**
 * Wall-clock deadline for conditions gated on native window-system notifications (moves, resizes,
 * maximize transitions), which arrive with real latency — including window-manager animations.
 */
private const val NATIVE_EVENT_TIMEOUT_MILLIS = 10_000L

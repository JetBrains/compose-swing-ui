package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioural tests asserting that [Window] arguments are reactive: mutating Compose state that
 * feeds a [Window] argument is reflected on the realized [JFrame] once the change is applied, and
 * that [WindowState] geometry is two-way with the realized frame. Skipped in headless environments
 * where no real peer can be realized.
 */
class WindowReactivityTest {
    @Test
    fun titleReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var title by mutableStateOf("title-test")
        setContent { Window(onCloseRequest = {}, title = title) {} }
        val frame = onWindow().fetch<JFrame>()
        assertEquals("title-test", frame.title, "the frame must realize with its declared title")
        title = "title-test-updated"
        awaitIdle()
        assertEquals("title-test-updated", frame.title, "the frame title must follow the recomposed value")
        title = "title-test"
        awaitIdle()
        assertEquals("title-test", frame.title, "the frame title must follow the recomposed value back")
    }

    @Test
    fun visibleReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var visible by mutableStateOf(false)
        setContent { Window(onCloseRequest = {}, title = "visible-test", visible = visible) {} }
        onWindow().assertIsNotVisible()
        visible = true
        awaitIdle()
        onWindow().assertIsVisible()
        visible = false
        awaitIdle()
        onWindow().assertIsNotVisible()
        visible = true
        awaitIdle()
        onWindow().assertIsVisible()
    }

    @Test
    fun resizableReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var resizable by mutableStateOf(true)
        setContent { Window(onCloseRequest = {}, title = "resizable-test", resizable = resizable) {} }
        val frame = onWindow().fetch<JFrame>()
        assertTrue(frame.isResizable, "the frame must realize resizable while resizable is declared true")
        resizable = false
        awaitIdle()
        assertFalse(frame.isResizable, "the frame must stop being resizable once resizable recomposes to false")
        resizable = true
        awaitIdle()
        assertTrue(frame.isResizable, "the frame must become resizable again once resizable recomposes back to true")
    }

    @Test
    fun alwaysOnTopReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeTrue(
            Toolkit.getDefaultToolkit().isAlwaysOnTopSupported,
            "keeping a window above the others requires toolkit support for always-on-top",
        )
        var alwaysOnTop by mutableStateOf(true)
        setContent {
            Window(onCloseRequest = {}, title = "always-on-top-test", visible = false, alwaysOnTop = alwaysOnTop) {}
        }
        val frame = onWindow().fetch<JFrame>()
        assertTrue(frame.isAlwaysOnTop, "the frame must realize always on top while alwaysOnTop is declared true")
        alwaysOnTop = false
        awaitIdle()
        assertFalse(
            frame.isAlwaysOnTop,
            "the frame must stop being always on top once alwaysOnTop recomposes to false",
        )
        alwaysOnTop = true
        awaitIdle()
        assertTrue(
            frame.isAlwaysOnTop,
            "the frame must go back above the others once alwaysOnTop recomposes back to true",
        )
    }

    @Test
    fun iconImageReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val icon: Image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val replacementIcon: Image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        var iconImage by mutableStateOf<Image?>(icon)
        setContent {
            Window(onCloseRequest = {}, title = "icon-image-test", visible = false, iconImage = iconImage) {}
        }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(icon, frame.iconImage, "the declared image must become the frame's icon")
        iconImage = replacementIcon
        awaitIdle()
        assertEquals(replacementIcon, frame.iconImage, "the frame's icon must follow the recomposed image")
        iconImage = null
        awaitIdle()
        assertTrue(
            frame.iconImages.isEmpty(),
            "a null iconImage must clear the frame's icon, restoring the platform default",
        )
        iconImage = icon
        awaitIdle()
        assertEquals(icon, frame.iconImage, "an image declared over a cleared icon must become the frame's icon")
    }

    @Test
    fun initialGeometryIsAppliedToTheFrame() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(position = WindowPosition.Absolute(120, 80), size = Dimension(320, 240))
        setContent { Window(onCloseRequest = {}, state = state, title = "initial-geometry-test") {} }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(Dimension(320, 240), frame.size)
        assertEquals(Point(120, 80), frame.location)
    }

    @Test
    fun positionReactsToStateChange() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // An explicit size keeps the frame out of the size-to-content path, so its placement is the
        // one the state declares rather than one a pack settled on.
        val state = WindowState(position = WindowPosition.Absolute(120, 80), size = Dimension(320, 240))
        setContent { Window(onCloseRequest = {}, state = state, title = "position-test") {} }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(Point(120, 80), frame.location, "the frame must realize at the position the state holds")
        // Moving a realized frame is an asynchronous native reshape; wait for it to reach the declared
        // placement rather than assert right after the compose frame that requests it.
        state.position = WindowPosition.Absolute(220, 160)
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { frame.location == Point(220, 160) }
        assertEquals(
            Point(220, 160),
            frame.location,
            "the frame must move to the position the state takes after the first apply",
        )
        state.position = WindowPosition.Absolute(120, 80)
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { frame.location == Point(120, 80) }
        assertEquals(
            Point(120, 80),
            frame.location,
            "the frame must move back to a position the state returns to",
        )
    }

    @Test
    fun sizeReactsToStateChange() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(320, 240))
        setContent { Window(onCloseRequest = {}, state = state, title = "size-test") {} }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(Dimension(320, 240), frame.size)
        state.size = Dimension(500, 400)
        // Applying size to the peer is an asynchronous native resize; wait for the frame to reach the
        // target rather than assert right after the compose frame that requests it.
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { frame.size == Dimension(500, 400) }
        assertEquals(Dimension(500, 400), frame.size)
    }

    @Test
    fun widthReactsToStateChange() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(320, 240))
        setContent { Window(onCloseRequest = {}, state = state, title = "width-test") {} }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(Dimension(320, 240), frame.size, "the frame must realize with the size the state holds")
        state.width = 500
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { frame.size == Dimension(500, 240) }
        assertEquals(
            Dimension(500, 240),
            frame.size,
            "assigning width must resize the realized frame, leaving its height unchanged",
        )
    }

    @Test
    fun heightReactsToStateChange() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(320, 240))
        setContent { Window(onCloseRequest = {}, state = state, title = "height-test") {} }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(Dimension(320, 240), frame.size, "the frame must realize with the size the state holds")
        state.height = 400
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { frame.size == Dimension(320, 400) }
        assertEquals(
            Dimension(320, 400),
            frame.size,
            "assigning height must resize the realized frame, leaving its width unchanged",
        )
    }

    @Test
    fun minimumSizeRaisesASmallerDeclaredSize() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(200, 150))
        setContent {
            Window(
                onCloseRequest = {},
                state = state,
                title = "minimum-size-test",
                minimumSize = Dimension(320, 240),
            ) {}
        }
        assertEquals(
            Dimension(320, 240),
            onWindow().fetch<JFrame>().size,
            "a declared size below the declared minimum size must be raised to that minimum",
        )
    }

    @Test
    fun minimumSizeRaisesASizeTakenFromTheContent() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            // No size is declared, so the frame takes one from its content; the content asks for less
            // than the floor allows.
            Window(
                onCloseRequest = {},
                title = "minimum-size-pack-test",
                minimumSize = Dimension(320, 240),
            ) {
                FlowPanel(modifier = SwingModifier.preferredSize(40, 30))
            }
        }
        assertEquals(
            Dimension(320, 240),
            onWindow().fetch<JFrame>().size,
            "a size taken from content smaller than the declared minimum must be raised to that minimum",
        )
    }

    @Test
    fun minimumSizeReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var minimumSize by mutableStateOf<Dimension?>(Dimension(320, 240))
        val state = WindowState(size = Dimension(200, 150))
        setContent {
            Window(
                onCloseRequest = {},
                state = state,
                title = "minimum-size-release-test",
                minimumSize = minimumSize,
            ) {}
        }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(Dimension(320, 240), frame.minimumSize, "the declared minimum size must reach the frame")
        minimumSize = Dimension(400, 300)
        awaitIdle()
        assertEquals(
            Dimension(400, 300),
            frame.minimumSize,
            "the frame's minimum size must follow the recomposed value",
        )
        minimumSize = null
        awaitIdle()
        assertFalse(
            frame.isMinimumSizeSet,
            "a null minimumSize must release the floor, leaving the minimum size to the frame's layout",
        )
        minimumSize = Dimension(320, 240)
        awaitIdle()
        assertEquals(
            Dimension(320, 240),
            frame.minimumSize,
            "a minimum size declared over a released floor must reach the frame",
        )
    }

    @Test
    fun mutatingADeclaredMinimumSizeLeavesTheFrameFloorUntouched() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val declaredMinimumSize = Dimension(320, 240)
        setContent {
            Window(
                onCloseRequest = {},
                title = "minimum-size-copy-inert-test",
                visible = false,
                minimumSize = declaredMinimumSize,
            ) {}
        }
        val frame = onWindow().fetch<JFrame>()
        // The floor is only ever moved by a declaration the composition applies, so mutating the
        // Dimension the composition was handed cannot move it behind the composition's back.
        declaredMinimumSize.setSize(500, 400)
        awaitIdle()
        assertEquals(
            Dimension(320, 240),
            frame.minimumSize,
            "mutating the declared Dimension must leave the realized frame's minimum size untouched",
        )
    }

    @Test
    fun mutatingAReadSizeCopyLeavesTheFrameAndStateUntouched() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var title by mutableStateOf("size-copy-inert-test")
        val state = WindowState(size = Dimension(320, 240))
        setContent { Window(onCloseRequest = {}, state = state, title = title) {} }
        val frame = onWindow().fetch<JFrame>()
        state.size.setSize(640, 480)
        title = "size-copy-inert-test-updated"
        awaitIdle()
        assertEquals(
            "size-copy-inert-test-updated",
            frame.title,
            "the unrelated title change must reach the frame",
        )
        assertEquals(
            Dimension(320, 240),
            frame.size,
            "mutating the Dimension returned by size must leave the realized frame's size untouched",
        )
        assertEquals(320, state.width, "mutating the Dimension returned by size must leave width untouched")
        assertEquals(240, state.height, "mutating the Dimension returned by size must leave height untouched")
    }

    @Test
    fun sizeAssignmentAfterMutatingAReadCopyResizesTheFrame() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(320, 240))
        setContent { Window(onCloseRequest = {}, state = state, title = "size-detached-copy-test") {} }
        val frame = onWindow().fetch<JFrame>()
        // A Dimension read from the state is a detached copy ([java.awt.Component.getSize]
        // semantics): mutating it in place drives nothing, so a follow-up assignment of the same
        // values is a genuine change and must resize the frame.
        state.size.setSize(640, 480)
        state.size = Dimension(640, 480)
        // The native resize settles asynchronously, and a stale resize event can momentarily echo the
        // prior size back into the state before the resize completes; wait for the frame AND the state
        // to both reach the assigned size rather than catch that transient.
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) {
            frame.size == Dimension(640, 480) && state.size == Dimension(640, 480)
        }
        assertEquals(
            Dimension(640, 480),
            frame.size,
            "assigning size must resize the frame even after a read Dimension copy was mutated to equal values",
        )
        assertEquals(
            Dimension(640, 480),
            state.size,
            "the state must reflect the assigned size",
        )
    }

    @Test
    fun userResizeIsWrittenBackIntoState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(320, 240))
        setContent { Window(onCloseRequest = {}, state = state, title = "user-resize-test") {} }
        val frame = onWindow().fetch<JFrame>()
        frame.size = Dimension(640, 480)
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { state.size == Dimension(640, 480) }
        assertEquals(Dimension(640, 480), state.size)
    }

    @Test
    fun userMoveIsWrittenBackIntoState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // An explicit size keeps the frame out of the size-to-content path, so the move the write-back
        // reports is the one this test drives rather than a placement a pack settled on.
        val state = WindowState(position = WindowPosition.Absolute(120, 80), size = Dimension(320, 240))
        setContent { Window(onCloseRequest = {}, state = state, title = "user-move-test") {} }
        val frame = onWindow().fetch<JFrame>()
        moveWindowLikeAUser(frame, Point(300, 200)) { state.position == WindowPosition.Absolute(300, 200) }
        assertEquals(
            WindowPosition.Absolute(300, 200),
            state.position,
            "a move of the realized frame must be written back into the state",
        )
    }

    @Test
    fun userResizeIsNotFoughtByTheDeclaredValue() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(320, 240))
        setContent { Window(onCloseRequest = {}, state = state, title = "no-feedback-loop-test") {} }
        val frame = onWindow().fetch<JFrame>()
        frame.size = Dimension(640, 480)
        // The native resize settles asynchronously, and a stale resize event can momentarily echo the
        // prior size back into the state before the resize completes; wait for the dialog AND the state
        // to both reach the assigned size rather than catch that transient.
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) {
            frame.size == Dimension(640, 480) && state.size == Dimension(640, 480)
        }
        assertEquals(Dimension(640, 480), frame.size)
        assertEquals(Dimension(640, 480), state.size)
    }

    @Test
    fun platformDefaultPositionNeverRepositionsTheFrame() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var title by mutableStateOf("platform-default-test")
        val state = WindowState(position = WindowPosition.Absolute(300, 200))
        setContent { Window(onCloseRequest = {}, state = state, title = title, visible = false) {} }
        val frame = onWindow().fetch<JFrame>()
        // The peer is placed at concrete coordinates first, and the state then returns to
        // PlatformDefault: a placement request carries no coordinates, so it leaves an already-placed
        // peer where it is.
        assertEquals(Point(300, 200), frame.location, "the frame must realize at the declared position")
        state.position = WindowPosition.PlatformDefault
        awaitIdle()
        title = "platform-default-test-updated"
        awaitIdle()
        assertEquals(
            "platform-default-test-updated",
            frame.title,
            "the unrelated title change must reach the frame",
        )
        assertEquals(
            Point(300, 200),
            frame.location,
            "a PlatformDefault position must leave the frame's placement untouched",
        )
    }

    @Test
    fun platformDefaultToAbsolutePositionMovesTheFrame() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState()
        setContent {
            Window(onCloseRequest = {}, state = state, title = "platform-to-absolute-test", visible = false) {}
        }
        val frame = onWindow().fetch<JFrame>()
        state.position = WindowPosition.Absolute(260, 180)
        awaitIdle()
        assertEquals(
            Point(260, 180),
            frame.location,
            "assigning an absolute position over PlatformDefault must move the frame",
        )
    }

    @Test
    fun extendedStateReactsToStateChange() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeMaximizeIsSupported()
        val state = WindowState()
        setContent { Window(onCloseRequest = {}, state = state, title = "extended-state-test") {} }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(Frame.NORMAL, frame.extendedState, "a window starts in the normal extended state")
        state.extendedState = Frame.MAXIMIZED_BOTH
        awaitIdle()
        assertEquals(
            Frame.MAXIMIZED_BOTH,
            frame.extendedState,
            "assigning MAXIMIZED_BOTH must maximize the realized frame",
        )
    }

    @Test
    fun initialExtendedStateIsAppliedToTheFrame() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeMaximizeIsSupported()
        val state = WindowState(extendedState = Frame.MAXIMIZED_BOTH)
        setContent { Window(onCloseRequest = {}, state = state, title = "initial-extended-state-test") {} }
        assertEquals(
            Frame.MAXIMIZED_BOTH,
            onWindow().fetch<JFrame>().extendedState,
            "an initial MAXIMIZED_BOTH must reach the frame before it is shown",
        )
    }

    @Test
    fun userMaximizeIsWrittenBackIntoState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeMaximizeIsSupported()
        val state = WindowState()
        setContent { Window(onCloseRequest = {}, state = state, title = "user-maximize-test") {} }
        val frame = onWindow().fetch<JFrame>()
        frame.extendedState = Frame.MAXIMIZED_BOTH
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { state.extendedState == Frame.MAXIMIZED_BOTH }
        assertEquals(
            Frame.MAXIMIZED_BOTH,
            state.extendedState,
            "a user-driven maximize must be written back into the state",
        )
    }

    @Test
    fun userMaximizeIsNotFoughtByTheDeclaredValue() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeMaximizeIsSupported()
        val state = WindowState()
        setContent { Window(onCloseRequest = {}, state = state, title = "maximize-no-feedback-loop-test") {} }
        val frame = onWindow().fetch<JFrame>()
        frame.extendedState = Frame.MAXIMIZED_BOTH
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { state.extendedState == Frame.MAXIMIZED_BOTH }
        // The write-back updated both the state and the applied extended state, so the next apply is a
        // no-op and the user's maximize survives instead of being reverted to the initial declared
        // NORMAL value.
        awaitIdle()
        assertEquals(
            Frame.MAXIMIZED_BOTH,
            frame.extendedState,
            "the declared NORMAL value must not revert a user-driven maximize",
        )
        assertEquals(
            Frame.MAXIMIZED_BOTH,
            state.extendedState,
            "the state must keep reflecting the user-driven maximize",
        )
    }
}

/** Maximizing is a window-manager capability, absent for example on some Linux window managers. */
private fun assumeMaximizeIsSupported() {
    assumeTrue(
        Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH),
        "Maximizing a frame requires toolkit support for MAXIMIZED_BOTH",
    )
}

/**
 * Wall-clock deadline for conditions gated on native window-system notifications (moves, resizes,
 * maximize transitions), which arrive with real latency - including window-manager animations.
 */
private val NATIVE_EVENT_TIMEOUT = 10.seconds

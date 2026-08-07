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
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests asserting that a [Window] or [Dialog] created without an explicit size is sized to
 * its content rather than to an invented default, and that an explicit size is applied verbatim.
 * Skipped in headless environments where no real peer can be realized.
 */
class WindowPackToContentTest {
    @Test
    fun windowWithoutExplicitSizePacksToItsContent() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState()
        setContent {
            Window(onCloseRequest = {}, state = state, title = "window-pack-test") {
                FlowPanel(modifier = SwingModifier.preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
            }
        }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(
            frame.preferredSize,
            frame.size,
            "a window with no explicit size must pack to its preferred size",
        )
        assertEquals(
            Dimension(CONTENT_WIDTH, CONTENT_HEIGHT),
            frame.contentPane.size,
            "the packed content pane must realize at the content's preferred size",
        )
        assertTrue(frame.width > 0 && frame.height > 0, "a packed window must have a non-zero size")
        assertTrue(frame.size != INVENTED_WINDOW_SIZE, "a packed window must not fall back to an invented default")
    }

    @Test
    fun dialogWithoutExplicitSizePacksToItsContent() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState()
        setContent {
            Dialog(onCloseRequest = {}, state = state, title = "dialog-pack-test") {
                FlowPanel(modifier = SwingModifier.preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
            }
        }
        val dialog = onWindow().fetch<JDialog>()
        assertEquals(
            dialog.preferredSize,
            dialog.size,
            "a dialog with no explicit size must pack to its preferred size",
        )
        assertEquals(
            Dimension(CONTENT_WIDTH, CONTENT_HEIGHT),
            dialog.contentPane.size,
            "the packed content pane must realize at the content's preferred size",
        )
        assertTrue(dialog.width > 0 && dialog.height > 0, "a packed dialog must have a non-zero size")
        assertTrue(dialog.size != INVENTED_DIALOG_SIZE, "a packed dialog must not fall back to an invented default")
    }

    @Test
    fun windowWithAnExplicitSizeIsAppliedVerbatim() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(420, 300))
        setContent {
            Window(onCloseRequest = {}, state = state, title = "window-explicit-size-test") {
                FlowPanel(modifier = SwingModifier.preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
            }
        }
        val frame = onWindow().fetch<JFrame>()
        assertEquals(
            Dimension(420, 300),
            frame.size,
            "an explicit size must be applied verbatim rather than packed to content",
        )
    }

    @Test
    fun dialogWithAnExplicitSizeIsAppliedVerbatim() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = Dimension(360, 240))
        setContent {
            Dialog(onCloseRequest = {}, state = state, title = "dialog-explicit-size-test") {
                FlowPanel(modifier = SwingModifier.preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
            }
        }
        val dialog = onWindow().fetch<JDialog>()
        assertEquals(
            Dimension(360, 240),
            dialog.size,
            "an explicit size must be applied verbatim rather than packed to content",
        )
    }

    @Test
    fun packedWindowSizeIsWrittenBackIntoTheState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState()
        setContent {
            Window(onCloseRequest = {}, state = state, title = "window-pack-writeback-test") {
                FlowPanel(modifier = SwingModifier.preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
            }
        }
        val frame = onWindow().fetch<JFrame>()
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { state.size == frame.size }
        assertEquals(
            frame.size,
            state.size,
            "the realized packed size must flow back into the two-way state",
        )
        assertTrue(state.width > 0 && state.height > 0, "the written-back size must be non-zero")
    }

    @Test
    fun aWindowTheUserResizedFitsItsContentAgainWhenSizedToContentAgain() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState()
        setContent {
            Window(onCloseRequest = {}, state = state, title = "window-repack-test") {
                FlowPanel(modifier = SwingModifier.preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
            }
        }
        val frame = onWindow().fetch<JFrame>()
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { state.size == frame.size }
        val packed = frame.size

        // The window system leaves the window at a size of the user's, which reaches the state.
        val resizedByTheUser = Dimension(packed.width + 160, packed.height + 120)
        frame.resizeTo(resizedByTheUser)
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { state.size == resizedByTheUser }

        // Sizing to the content is the same declaration written 0 by 0, so declaring it over the size
        // the user left is a change again and fits the window back to what it holds.
        state.size = Dimension(0, 0)
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { frame.size == packed }
        assertEquals(
            packed,
            frame.size,
            "declaring no size over a size the user left must fit the window to its content again",
        )
    }

    @Test
    fun anAlreadyPackedWindowIsNotRepackedByAnUnrelatedRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState()
        var windowTitle by mutableStateOf("window-repack-noop-test")
        setContent {
            Window(onCloseRequest = {}, state = state, title = windowTitle) {
                FlowPanel(modifier = SwingModifier.preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
            }
        }
        val frame = onWindow().fetch<JFrame>()
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { state.size == frame.size }
        val packed = frame.size

        var resizeEvents = 0
        frame.addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    resizeEvents++
                }
            },
        )

        // A recomposition that leaves the declared size untouched (only the title changes) must find
        // the window already packed and leave it alone, rather than fit it to content on every apply.
        windowTitle = "window-repack-noop-test-renamed"
        waitUntil(timeout = NATIVE_EVENT_TIMEOUT) { frame.title == windowTitle }

        assertEquals(packed, frame.size, "an unrelated recomposition must not change an already-packed window's size")
        assertEquals(0, resizeEvents, "an unrelated recomposition must not pack an already-packed window again")
    }
}

private const val CONTENT_WIDTH = 321
private const val CONTENT_HEIGHT = 211

/**
 * Sizes no content asks for, standing in for a default a peer could be given instead of being sized to
 * what it holds.
 */
private val INVENTED_WINDOW_SIZE = Dimension(800, 600)
private val INVENTED_DIALOG_SIZE = Dimension(400, 300)

/**
 * Wall-clock deadline for conditions gated on native window-system notifications (the resize echo that
 * follows a pack), which arrive with real latency.
 */
private val NATIVE_EVENT_TIMEOUT = 10.seconds

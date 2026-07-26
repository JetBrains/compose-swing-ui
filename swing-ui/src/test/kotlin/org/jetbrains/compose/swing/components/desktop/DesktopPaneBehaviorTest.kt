package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for [DesktopPane] over a real [SwingApplier]. Each assertion reads the rendered
 * [JDesktopPane] and its [JInternalFrame] children: a declared frame is hosted with its title, bounds,
 * and controls; frames are added and removed dynamically; metadata updates on recomposition; and the
 * close control routes through `onClose` while leaving the frame in place until the composition drops
 * it.
 */
class DesktopPaneBehaviorTest {
    private fun ComposeSwingTest.desktop(): JDesktopPane = onNodeOfType<JDesktopPane>().fetch<JDesktopPane>()

    private fun frames(desktop: JDesktopPane): List<JInternalFrame> =
        desktop.components.filterIsInstance<JInternalFrame>()

    private fun frameTitled(
        desktop: JDesktopPane,
        title: String,
    ): JInternalFrame = frames(desktop).single { it.title == title }

    @Test
    fun eachDeclaredFrameIsHostedWithItsTitleBoundsAndControls() = runComposeSwingTest {
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Editor",
                    bounds = Rectangle(10, 20, 300, 200),
                    controls = InternalFrameControls(closable = true, iconifiable = true),
                ) { Label(text = "editor-body") }
            }
        }

        val desktop = desktop()
        assertEquals(1, frames(desktop).size, "frame was not hosted on the desktop")
        val frame = frameTitled(desktop, "Editor")
        assertEquals(Rectangle(10, 20, 300, 200), frame.bounds, "the frame should take its declared bounds")
        assertTrue(frame.isVisible, "internal frame should be visible")
        assertTrue(frame.isClosable, "the frame should be closable as declared")
        assertFalse(frame.isResizable, "the frame should not be resizable as declared")
        assertFalse(frame.isMaximizable, "the frame should not be maximizable as declared")
        assertTrue(frame.isIconifiable, "the frame should be iconifiable as declared")
        assertEquals(
            JInternalFrame.DO_NOTHING_ON_CLOSE,
            frame.defaultCloseOperation,
            "the frame should use the do-nothing close op",
        )
        onNodeWithText("editor-body").assertExists()
    }

    @Test
    fun framesAreAddedAndRemovedDynamically() = runComposeSwingTest {
        var showSecond by mutableStateOf(true)
        setContent {
            DesktopPane {
                internalFrame(title = "One", bounds = Rectangle(0, 0, 100, 100)) { Label(text = "1") }
                if (showSecond) {
                    internalFrame(title = "Two", bounds = Rectangle(0, 0, 100, 100)) { Label(text = "2") }
                }
            }
        }

        val desktop = desktop()
        assertEquals(2, frames(desktop).size, "both frames should be hosted initially")

        showSecond = false
        awaitIdle()
        assertEquals(1, frames(desktop).size, "dropped frame was not removed")
        assertEquals(listOf("One"), frames(desktop).map { it.title }, "only the surviving frame should remain")

        showSecond = true
        awaitIdle()
        assertEquals(2, frames(desktop).size, "re-added frame did not return")
    }

    @Test
    fun frameMetadataUpdatesOnRecomposition() = runComposeSwingTest {
        var title by mutableStateOf("Old")
        var resizable by mutableStateOf(true)
        var bounds by mutableStateOf(Rectangle(0, 0, 100, 100))
        setContent {
            DesktopPane {
                internalFrame(
                    title = title,
                    bounds = bounds,
                    controls = InternalFrameControls(resizable = resizable),
                ) { Label(text = "body") }
            }
        }

        val desktop = desktop()
        assertEquals("Old", frames(desktop).single().title, "the frame should start with its original title")
        assertTrue(frames(desktop).single().isResizable, "the frame should start resizable")

        title = "New"
        resizable = false
        bounds = Rectangle(40, 50, 250, 150)
        awaitIdle()

        val frame = frames(desktop).single()
        assertEquals("New", frame.title, "title did not update on recomposition")
        assertFalse(frame.isResizable, "resizable did not update on recomposition")
        assertEquals(Rectangle(40, 50, 250, 150), frame.bounds, "bounds did not update on recomposition")
    }

    @Test
    fun activatingCloseRoutesThroughOnCloseWithoutClosingTheFrame() = runComposeSwingTest {
        var closes = 0
        var show by mutableStateOf(true)
        setContent {
            DesktopPane {
                if (show) {
                    internalFrame(
                        title = "Closable",
                        bounds = Rectangle(0, 0, 100, 100),
                        onClose = { closes++ },
                    ) { Label(text = "body") }
                }
            }
        }

        val desktop = desktop()
        val frame = frameTitled(desktop, "Closable")

        // Headless: no realized peer, so dispatch the close action that a title-bar close button
        // would otherwise fire. The controlled frame stays in place; only onClose is invoked.
        frame.doDefaultCloseAction()
        awaitIdle()
        assertEquals(1, closes, "onClose was not invoked when the close control fired")
        assertEquals(1, frames(desktop).size, "controlled frame closed itself instead of waiting")

        // The caller actually closes it by dropping it from the composition.
        show = false
        awaitIdle()
        assertEquals(0, frames(desktop).size, "frame was not removed after the composition dropped it")
    }

    @Test
    fun rawInternalFrameListenerOverloadReceivesFrameEvents() = runComposeSwingTest {
        var closings = 0
        val listener =
            object : InternalFrameAdapter() {
                override fun internalFrameClosing(event: InternalFrameEvent) {
                    closings++
                }
            }
        setContent {
            DesktopPane {
                internalFrame(
                    title = "Listened",
                    bounds = Rectangle(0, 0, 100, 100),
                    internalFrameListener = listener,
                ) { Label(text = "body") }
            }
        }

        val desktop = desktop()
        val frame = frameTitled(desktop, "Listened")

        // The raw listener is attached as-is; the close action fires its internalFrameClosing.
        frame.doDefaultCloseAction()
        awaitIdle()
        assertEquals(1, closings, "the raw InternalFrameListener did not receive the close event")
        assertEquals(1, frames(desktop).size, "controlled frame closed itself instead of waiting")
    }

    @Test
    fun disposingTheDesktopPaneTearsItDown() = runComposeSwingTest {
        var show by mutableStateOf(true)
        setContent {
            if (show) {
                DesktopPane {
                    internalFrame(title = "Frame", bounds = Rectangle(0, 0, 100, 100)) { Label(text = "body") }
                }
            }
        }

        desktop()
        onNodeWithText("body").assertExists()

        show = false
        awaitIdle()

        val desktops = onAllNodesOfType<JDesktopPane>().fetchAll<JDesktopPane>()
        assertTrue(desktops.isEmpty(), "JDesktopPane was not removed on dispose.")
        onNodeWithText("body").assertDoesNotExist()
    }
}

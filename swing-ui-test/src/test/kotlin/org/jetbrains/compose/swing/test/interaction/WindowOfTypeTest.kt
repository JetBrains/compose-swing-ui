package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.test.onWindowOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Dialog
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Pins `onWindowOfType` and window `assertIsOfType`: which window they find, what the result reads, and
 * what a failed assertion leaves in place. Every case that finds a window realizes a top-level peer, so
 * each is skipped in headless environments.
 */
class WindowOfTypeTest {
    @Test
    fun theWindowOfTheNamedTypeIsFoundAndReadAsThatType() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "frame", visible = true) {}
            Dialog(onCloseRequest = {}, title = "dialog", visible = true) {}
        }

        // Two windows are realized, so only the type tells the frame from the dialog.
        val frame = onWindowOfType<JFrame>()
        assertSame(frame, frame.assertProperty("frame") { title })
        onWindowOfType<JDialog>().assertProperty("dialog") { title }
    }

    @Test
    fun noWindowOfTheNamedTypeFailsAndLeavesTheRealizedWindowUnchanged() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "frame", visible = true) {} }

        assertFailsWith<AssertionError> { onWindowOfType<JDialog>().assertIsVisible() }
        onWindowOfType<JFrame>().assertProperty("frame") { title }
    }

    @Test
    fun severalWindowsOfTheNamedTypeFailAsAnAmbiguousQueryAndLeaveEachWindowInPlace() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "first", visible = true) {}
            Window(onCloseRequest = {}, title = "second", visible = true) {}
        }

        assertFailsWith<AssertionError> { onWindowOfType<JFrame>().assertIsVisible() }
        onWindowWithTitle("first").assertIsVisible()
        onWindowWithTitle("second").assertIsVisible()
    }

    @Test
    fun aWindowMatchedByTitleIsReturnedTypedAsTheAssertedType() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "frame", visible = true) {} }

        onWindowWithTitle("frame").assertIsOfType<JFrame>().assertProperty("frame") { title }
    }

    @Test
    fun aMismatchedWindowFailsAndLeavesTheWindowUnchanged() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "frame", visible = true) {} }

        assertFailsWith<AssertionError> { onWindowWithTitle("frame").assertIsOfType<JDialog>() }
        onWindowWithTitle("frame").assertIsOfType<JFrame>()
    }
}

package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.test.onAllWindows
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the window form of [assertProperty]. Cases that find a window realize a top-level peer
 * and are skipped in headless environments.
 */
class WindowPropertyAssertionTest {
    @Test
    fun aMatchingPropertyPassesAndReturnsTheSameInteraction() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "props", visible = true, minimumSize = Dimension(200, 100)) {}
        }

        // The query selects the window by its title, so the property read is one it did not select.
        val window = onWindowWithTitle("props")
        assertSame(window, window.assertProperty(Dimension(200, 100)) { minimumSize })
    }

    @ParameterizedTest(name = "message {0}")
    @MethodSource("mismatchMessages")
    fun aMismatchNamesTheQueryAndBothValuesAndEndsWithTheCallersMessage(
        message: String?,
        ending: String,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "props", visible = true) {} }

        val failure =
            assertFailsWith<AssertionError> { onWindowWithTitle("props").assertProperty(false, message) { isVisible } }
        val text = failure.message.orEmpty()
        assertTrue(text.contains("hasTitle(\"props\")"), "the failure should name the query: $text")
        assertTrue(text.endsWith(ending), "the failure should end with both values and the caller's message: $text")
    }

    @Test
    fun anAbsentWindowFailsAsAnUnresolvedQueryAndRealizesNoWindow() = runComposeSwingTest {
        setContent {}

        // The property is read off a resolved window, so a query that resolves to none fails the way
        // every other use of that query does, rather than as a value mismatch.
        assertFailsWith<AssertionError> { onWindowWithTitle("absent").assertProperty(true) { isVisible } }
        onAllWindows().assertCountEquals(0)
    }

    companion object {
        @JvmStatic
        fun mismatchMessages(): Stream<Arguments> = Stream.of(
            Arguments.of(null, "property was true, expected false."),
            Arguments.of("the window is shown", "property was true, expected false. the window is shown"),
        )
    }
}

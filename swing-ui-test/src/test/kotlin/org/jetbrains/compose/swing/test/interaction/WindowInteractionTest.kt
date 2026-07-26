package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onAllWindows
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Dialog
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Validates the window-query surface of the harness: [ComposeSwingTest.onWindow]/[ComposeSwingTest.onAllWindows]
 * resolve the top-level windows realized by `Window { }`/`Dialog { }` composables in the composition
 * under test - whether or not they are shown - window-scoped node finders resolve inside one window's
 * content pane, and [ComposeSwingTest.awaitIdle] settles a window show that is applied on its own
 * event-dispatch turn.
 *
 * Every case realizes a real top-level peer, so each declares its display requirement up front and is
 * skipped in headless environments.
 */
class WindowInteractionTest {
    @Test
    fun onWindowFindsTheVisibleWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "shown-window", visible = true) {} }

        onWindow().assertExists()
        onWindowWithTitle("shown-window").assertExists()
        onWindowWithTitle("some-other-title").assertDoesNotExist()

        val frame = onWindow().fetch<JFrame>()
        assertEquals("shown-window", frame.title, "fetch should return the realized frame carrying its title")
        assertSame(
            frame,
            onWindowWithTitle("shown-window").fetch<JFrame>(),
            "each resolution should return the same live frame",
        )
    }

    @Test
    fun windowScopedNodeFindersResolveInsideThatWindowOnly() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Label(text = "outside")
            Window(onCloseRequest = {}, title = "scoped", visible = true) {
                Label(text = "inside")
            }
        }

        // The window's content pane is a detached top-level peer: harness-root finders never see it,
        // and the window-scoped finders never see the harness root's content.
        onNodeWithText("outside").assertExists()
        onNodeWithText("inside").assertDoesNotExist()

        val window = onWindowWithTitle("scoped")
        window.onNodeWithText("inside").assertExists()
        window.onNodeWithText("outside").assertDoesNotExist()
        window.onAllNodesWithText("inside").assertCountEquals(1)
        window.onAllNodesWithText("outside").assertCountEquals(0)
    }

    @Test
    fun aWindowLeavingTheCompositionStopsMatching() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var present by mutableStateOf(true)
        setContent {
            if (present) Window(onCloseRequest = {}, title = "transient", visible = true) {}
        }
        onWindowWithTitle("transient").assertExists()
        onAllWindows().assertCountEquals(1)

        // Leaving the composition disposes the peer, which retires it (it becomes non-displayable) and
        // so drops out of the realized-window match set even though it can linger in the global AWT
        // window list.
        present = false
        awaitIdle()
        onWindowWithTitle("transient").assertDoesNotExist()
        onAllWindows().assertCountEquals(0)
    }

    @Test
    fun onWindowRequiresExactlyOneMatchAndMatchersNarrow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "first", visible = true) {}
            Window(onCloseRequest = {}, title = "second", visible = true) {}
        }

        onAllWindows().assertCountEquals(2)
        assertEquals(2, onAllWindows().fetchSize(), "fetchSize should agree with the two realized windows")
        assertEquals(
            listOf("first", "second"),
            onAllWindows().fetchAll<JFrame>().map { it.title },
            "fetchAll should return both frames typed, in creation order",
        )

        // The all-windows query matches both, so the unique finder must fail; a title narrows it.
        assertFailsWith<AssertionError> { onWindow().assertExists() }
        onWindowWithTitle("first").assertExists()
        onWindow(SwingMatcher.hasTitle("second")).assertExists()
        onAllWindows(SwingMatcher.hasTitle("second")).assertCountEquals(1)
    }

    @Test
    fun aRealizedWindowMatchesRegardlessOfVisibility() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var visible by mutableStateOf(false)
        setContent { Window(onCloseRequest = {}, title = "visibility", visible = visible) {} }

        // A window realized by the composition is matched whether or not it is shown, so a hidden
        // window is found and reports itself hidden through assertIsNotVisible.
        onWindow().assertExists()
        onWindow().assertIsNotVisible()

        visible = true
        awaitIdle()
        onWindow().assertExists()
        onWindow().assertIsVisible()

        visible = false
        awaitIdle()
        // Hiding does not retire the peer, so the window keeps matching and reports itself hidden again.
        onWindow().assertExists()
        onWindow().assertIsNotVisible()
    }

    @Test
    fun nodeVisibilityInsideAWindowStopsAtThatWindowsContentPane() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "unshown", visible = false) {
                Label(text = "content")
            }
        }

        val window = onWindowWithTitle("unshown")
        window.assertIsNotVisible()
        // The frame carrying the hidden flag sits above the content pane the query is rooted at, so a
        // node under it reports the visibility it was given rather than the window's.
        window.onNodeWithText("content").assertIsVisible()
    }

    @Test
    fun awaitIdleSettlesADeferredDialogShow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var visible by mutableStateOf(false)
        setContent { Dialog(onCloseRequest = {}, title = "deferred-show", visible = visible) {} }
        // The dialog peer is realized while hidden, so it already matches; it is simply not yet shown.
        onWindow().assertIsNotVisible()

        visible = true
        awaitIdle()
        // A dialog show is applied on its own event-dispatch turn; awaitIdle returns only after that
        // turn has run, so the realized dialog is already showing here and enters the match set.
        onWindow().assertIsVisible()
        assertTrue(
            onWindow().fetch<JDialog>().isVisible,
            "the realized dialog must be visible once awaitIdle returns",
        )
    }

    @Test
    fun fetchFailsWhenTheWindowTypeMismatches() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "typed", visible = true) {} }
        assertFailsWith<AssertionError> { onWindow().fetch<JDialog>() }
    }
}

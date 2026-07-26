package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.testTag
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
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the failure contracts of the window-query surface and the remaining window-scoped finders.
 *
 * A window query resolves against the live set of realized windows, so its failures must say which
 * windows were realized instead - the only diagnostic a downstream consumer has when a window query
 * misses. Cases that realize a top-level peer declare their display requirement and are skipped in
 * headless environments; the no-window cases run everywhere.
 */
class WindowInteractionContractTest {
    @Test
    fun aWindowQueryWithNoRealizedWindowSaysSo() = runComposeSwingTest {
        setContent { Label(text = "no windows here") }

        val failure = assertFailsWith<AssertionError> { onWindowWithTitle("absent").assertExists() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("found none"), "an empty match set should read as none: $message")
        assertTrue(message.contains("hasTitle(\"absent\")"), "the failure should name the query: $message")
        assertTrue(
            message.contains("Realized windows: none."),
            "the summary should state that nothing was realized: $message",
        )
    }

    @Test
    fun aWindowCountAssertionFailsAgainstTheRealizedSet() = runComposeSwingTest {
        setContent { Label(text = "no windows here") }

        val failure = assertFailsWith<AssertionError> { onAllWindows().assertCountEquals(1) }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("but found 0"), "the failure should report the actual count: $message")
        assertTrue(
            message.contains("Realized windows: none."),
            "the failure should carry the realized-window summary: $message",
        )
    }

    @Test
    fun anAmbiguousWindowQueryReportsEveryRealizedWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "one", visible = true) {}
            Window(onCloseRequest = {}, title = "two", visible = true) {}
        }

        // Both the positive and the negative assertion must reject an ambiguous query rather than
        // pick a window arbitrarily.
        for (failure in listOf(
            assertFailsWith<AssertionError> { onWindow().assertExists() },
            assertFailsWith<AssertionError> { onWindow().assertDoesNotExist() },
        )) {
            val message = failure.message.orEmpty()
            assertTrue(message.contains("but found 2"), "the ambiguity should be quantified: $message")
            assertTrue(message.contains("title=\"one\""), "the summary should list each window: $message")
            assertTrue(message.contains("title=\"two\""), "the summary should list each window: $message")
        }
    }

    @Test
    fun assertDoesNotExistFailsWhenTheWindowIsRealized() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "realized", visible = true) {} }

        val failure = assertFailsWith<AssertionError> { onWindowWithTitle("realized").assertDoesNotExist() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("title=\"realized\""), "the failure should describe the window found: $message")
        assertTrue(message.contains("visible"), "the description should carry the window's visibility: $message")
    }

    @Test
    fun visibilityAssertionsFailAgainstTheOppositeState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var visible by mutableStateOf(false)
        setContent { Window(onCloseRequest = {}, title = "visibility", visible = visible) {} }

        // The peer is realized but never shown, so the visible claim must fail and name both states.
        val hiddenFailure = assertFailsWith<AssertionError> { onWindow().assertIsVisible() }
        val hiddenMessage = hiddenFailure.message.orEmpty()
        assertTrue(
            hiddenMessage.contains("was not visible"),
            "the failure should report the actual state: $hiddenMessage",
        )
        assertTrue(hiddenMessage.contains("expected visible"), "the failure should report the claim: $hiddenMessage")

        visible = true
        awaitIdle()

        // And symmetrically once shown, so neither direction can silently pass.
        val shownFailure = assertFailsWith<AssertionError> { onWindow().assertIsNotVisible() }
        val shownMessage = shownFailure.message.orEmpty()
        assertTrue(shownMessage.contains("was visible"), "the failure should report the actual state: $shownMessage")
        assertTrue(shownMessage.contains("expected not visible"), "the failure should report the claim: $shownMessage")
    }

    @Test
    fun aDialogIsMatchedByTitleAndDescribedByIt() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Dialog(onCloseRequest = {}, title = "dialog-title", visible = false) {} }

        // A Dialog carries a title just as a Frame does, so the title matcher and the failure
        // description must read it from either kind of top-level peer.
        onWindowWithTitle("dialog-title").assertExists()
        onWindowWithTitle("other-title").assertDoesNotExist()

        val failure = assertFailsWith<AssertionError> { onWindowWithTitle("dialog-title").assertDoesNotExist() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("title=\"dialog-title\""), "the dialog's title should be described: $message")
        assertTrue(message.contains("hidden"), "an unshown window should be described as hidden: $message")
    }

    @Test
    fun windowScopedFindersResolveByNameAndByTag() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Label(text = "outside", modifier = SwingModifier.name("outside-name").testTag("shared-tag"))
            Window(onCloseRequest = {}, title = "scoped", visible = true) {
                Label(text = "inside", modifier = SwingModifier.name("inside-name").testTag("shared-tag"))
            }
        }

        val window = onWindowWithTitle("scoped")
        assertEquals(
            "inside",
            window.onNodeWithName("inside-name").fetch<JLabel>().text,
            "a window-scoped name query should resolve inside that window",
        )
        assertEquals(
            "inside",
            window.onNodeWithTag("shared-tag").fetch<JLabel>().text,
            "a window-scoped tag query should resolve inside that window",
        )
        // The tag is shared with a node under the harness root, which the window scope excludes.
        window.onAllNodesWithTag("shared-tag").assertCountEquals(1)
        window.onNodeWithName("outside-name").assertDoesNotExist()
    }

    @Test
    fun fetchAllReturnsEveryRealizedWindowTyped() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "typed", visible = true) {} }

        assertEquals(
            listOf("typed"),
            onAllWindows().fetchAll<JFrame>().map { it.title },
            "fetchAll should return the realized frame typed",
        )
        // A window of the wrong type must be rejected rather than cast blindly.
        assertFailsWith<AssertionError> { onAllWindows().fetchAll<JDialog>() }
    }

    @Test
    fun anUnmetWaitDumpsTheRealizedWindowsAlongsideTheHarnessTree() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Label(text = "harness-root-content")
            Window(onCloseRequest = {}, title = "diagnostic", visible = true) {
                Label(text = "window-content")
            }
        }

        val failure =
            assertFailsWith<AssertionError> {
                waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { false }
            }
        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("harness-root-content"),
            "the diagnostic should dump the harness tree: $message",
        )
        assertTrue(
            message.contains("Visible window:") && message.contains("window-content"),
            "the diagnostic should dump each realized window's content: $message",
        )
    }

    @Test
    fun aWindowMatcherNarrowsTheRealizedSet() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "kept", visible = true) {}
            Window(onCloseRequest = {}, title = "dropped", visible = true) {}
        }

        assertEquals(1, onAllWindows(SwingMatcher.hasTitle("kept")).fetchSize())
        assertEquals(0, onAllWindows(SwingMatcher.hasTitle("neither")).fetchSize())
        onWindow(SwingMatcher.hasTitle("neither")).assertDoesNotExist()
    }

    private companion object {
        // Short enough to keep the suite quick; the condition is never true, so the wait always
        // runs to its deadline.
        const val WAIT_TIMEOUT_MILLIS: Long = 100
    }
}

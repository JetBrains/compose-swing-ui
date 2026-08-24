package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onFocus
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.event.FocusEvent
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import java.awt.Window as AwtWindow

/**
 * Pins what the harness's focus actions deliver and what its focus-ownership assertions claim.
 *
 * Focus splits into two things off-screen, and the tests below hold them apart. A focus notification
 * reaches the node itself, so behavior a widget drives from `processFocusEvent` is observable without a
 * display. Focus ownership is held by a component of the focused window, so nothing under the harness
 * root ever holds it, and a component of a window the composition realizes holds it once the window
 * system focuses that window - the one situation in which the ownership assertion can hold.
 */
class NodeFocusInteractionTest {
    /** Records the focus notifications the component processes, which a focus listener cannot observe. */
    private class ProcessingTextField : JTextField() {
        val processed: MutableList<String> = mutableListOf()

        override fun processFocusEvent(e: FocusEvent) {
            super.processFocusEvent(e)
            val kind = if (e.id == FocusEvent.FOCUS_GAINED) "gained" else "lost"
            processed += if (e.isTemporary) "$kind (temporary)" else kind
        }
    }

    @Test
    fun aFocusGainedNotificationReachesTheNodesOwnProcessing() = runComposeSwingTest {
        val field = ProcessingTextField()
        setContent { SwingNode(factory = { field }) }

        // Handing the event to the component the ordinary way is not enough off-screen: AWT consults
        // the keyboard focus manager first, which refuses focus for a component that is not showing and
        // keeps the event. The action has to get past that for the notification to be delivered at all.
        field.dispatchEvent(FocusEvent(field, FocusEvent.FOCUS_GAINED))
        assertEquals(emptyList(), field.processed, "a plainly dispatched focus event never reaches the node")

        onNodeOfType<JTextField>().performFocusGained()
        assertEquals(listOf("gained"), field.processed, "the action should deliver the notification to the node")
    }

    @Test
    fun aFocusLostNotificationReachesTheNodesOwnProcessing() = runComposeSwingTest {
        val field = ProcessingTextField()
        setContent { SwingNode(factory = { field }) }

        onNodeOfType<JTextField>().performFocusLost()
        assertEquals(listOf("lost"), field.processed, "the action should deliver the notification to the node")
    }

    @Test
    fun aTemporaryNotificationIsDeliveredAsTemporary() = runComposeSwingTest {
        val field = ProcessingTextField()
        setContent { SwingNode(factory = { field }) }

        // Widgets branch on this: a formatted text field acts on a permanent focus change and lets a
        // temporary one pass, so a test has to be able to say which one it is delivering.
        onNodeOfType<JTextField>().performFocusGained(temporary = true)
        onNodeOfType<JTextField>().performFocusLost(temporary = true)

        assertEquals(
            listOf("gained (temporary)", "lost (temporary)"),
            field.processed,
            "the temporariness the action declares should reach the node",
        )
    }

    @Test
    fun focusNotificationsReachTheNodesListeners() = runComposeSwingTest {
        var gained = 0
        var lost = 0
        setContent {
            Button(
                text = "X",
                onClick = { },
                modifier = SwingModifier.onFocus(onGained = { gained++ }, onLost = { lost++ }),
            )
        }

        onNodeOfType<JButton>().performFocusGained()
        assertEquals(1, gained, "a focus gain should reach a registered focus listener")
        assertEquals(0, lost, "a focus gain is not a loss")

        onNodeOfType<JButton>().performFocusLost()
        assertEquals(1, gained, "a focus loss should not re-announce the gain")
        assertEquals(1, lost, "a focus loss should reach a registered focus listener")
    }

    @Test
    fun aFocusNotificationSettlesWhatTheCallerDeclares() = runComposeSwingTest {
        var focus by mutableStateOf("none")
        setContent {
            Column {
                // Rendered from the caller's state alone, so its text can only be the text of a frame the
                // action settled; the listener's own write reaches the tree no other way.
                Label(text = "focus: $focus")
                Button(
                    text = "X",
                    onClick = { },
                    modifier =
                        SwingModifier.onFocus(onGained = { focus = "gained" }, onLost = {
                            focus =
                                "lost"
                        }),
                )
            }
        }

        onNodeOfType<JButton>().performFocusGained()
        onNodeOfType<JLabel>().assertTextEquals("focus: gained")

        onNodeOfType<JButton>().performFocusLost()
        onNodeOfType<JLabel>().assertTextEquals("focus: lost")
    }

    @Test
    fun aNodeUnderTheHarnessRootNeverOwnsFocus() = runComposeSwingTest {
        setContent {
            Column {
                Button(text = "X", onClick = { })
                // A sibling the query never names, so only the dump can account for it.
                Label(text = "sibling")
            }
        }

        onNodeOfType<JButton>().assertIsNotFocusOwner()

        val failure = assertFailsWith<AssertionError> { onNodeOfType<JButton>().assertIsFocusOwner() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("isOfType(JButton)"), "the failure should name the query: $message")
        assertTrue(message.contains("should be the focus owner"), "the failure should state the claim: $message")
        assertTrue(message.contains("held by nothing"), "the failure should say who holds focus: $message")
        assertTrue(message.contains("Tree:"), "the failure should carry a tree dump: $message")
        assertTrue(message.contains("JLabel"), "the tree dump should name the nodes it holds: $message")
    }

    @Test
    fun aDeliveredFocusGainConfersNoOwnership() = runComposeSwingTest {
        setContent { Button(text = "X", onClick = { }) }

        // The notification is delivered to the node, but ownership belongs to the windowing system and
        // the harness root is attached to no window; the two must not be confused for each other.
        onNodeOfType<JButton>().performFocusGained().assertIsNotFocusOwner()
    }

    @Test
    fun aComponentOfAFocusedWindowOwnsFocus() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "focus-owner", visible = true) {
                TextField(value = "owner", onValueChange = { })
            }
        }
        val frame = onWindow().fetch<JFrame>()
        frame.toFront()
        frame.requestFocus()
        // Whether this process's windows are focused at all is the window system's decision: a process
        // it declines to activate shows and lays out windows normally while none of them ever becomes
        // focused. Ownership cannot be held there, so the case reports itself skipped rather than
        // passing vacuously or failing for the environment.
        assumeTrue(awaitFocused(frame), "requires a window system that focuses this process's windows")

        val field = onWindow().onNode(SwingMatcher.isOfType<JTextField>())
        field.fetch<JTextField>().requestFocusInWindow()
        // Granting focus within the focused window is asynchronous too, so it is awaited. Two different
        // things keep it from arriving, and the wait tells them apart: a window this process no longer
        // holds focused withholds the capability ownership needs, while a focused window whose keyboard
        // went elsewhere is exactly the defect the assertion below exists to catch.
        awaitKeyboardOwnership(frame) { field.fetch<JTextField>() }

        field.assertIsFocusOwner()

        // The opposite claim now has an owner to name, which is what makes its failure actionable when
        // focus sits somewhere the test did not expect. It is named as the component itself: the query
        // description and the tree dump - the message's other two mentions of a component - render types,
        // so only the branch that reads the focus owner can produce this.
        val owner = field.fetch<JTextField>()
        val failure = assertFailsWith<AssertionError> { field.assertIsNotFocusOwner() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("should not be the focus owner"), "the failure should state the claim: $message")
        assertTrue(message.contains("held by $owner"), "the failure should name who holds focus: $message")
    }

    /**
     * True once [window] is the focused window, false if the window system has not focused it within
     * [FOCUS_TIMEOUT]. Activation arrives as a window-system notification with real latency, so it is
     * awaited rather than read once, from the test body - which runs on the event dispatch thread that
     * notification is delivered on.
     */
    private suspend fun awaitFocused(window: AwtWindow): Boolean {
        val deadline = TimeSource.Monotonic.markNow() + FOCUS_TIMEOUT
        while (!deadline.hasPassedNow()) {
            if (window.isFocused) return true
            delay(FOCUS_POLL_INTERVAL)
        }
        return false
    }

    /**
     * Suspends until the component [target] resolves holds the keyboard, and tells the two ways that can
     * fail to happen apart. [window] no longer being the focused one means the window system took back
     * the capability ownership needs, which no test can report as a defect, so the case skips as a failed
     * assumption; a focused window whose keyboard went to another component, or to nothing, is what the
     * ownership assertion exists to catch, so that fails and names where the keyboard went.
     */
    private suspend fun awaitKeyboardOwnership(
        window: AwtWindow,
        target: () -> Component,
    ) {
        val deadline = TimeSource.Monotonic.markNow() + FOCUS_TIMEOUT
        while (!target().isFocusOwner) {
            if (deadline.hasPassedNow()) {
                assumeTrue(
                    window.isFocused,
                    "requires a window system that focuses this process's windows: the window was no " +
                        "longer the focused one after $FOCUS_TIMEOUT",
                )
                val holder = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                throw AssertionError(
                    "The window is focused but the keyboard did not reach the component that requested " +
                        "it within $FOCUS_TIMEOUT; it is held by ${holder ?: "nothing"}.",
                )
            }
            delay(FOCUS_POLL_INTERVAL)
        }
    }

    private companion object {
        // Both waits sit on an asynchronous focus notification, and neither is a rate: measured against
        // this host, activation lands within a millisecond and the in-window grant within tens of them,
        // so the bound is what separates a slow delivery from a host that will never deliver one.
        val FOCUS_TIMEOUT = 5.seconds
        val FOCUS_POLL_INTERVAL = 25.milliseconds
    }
}

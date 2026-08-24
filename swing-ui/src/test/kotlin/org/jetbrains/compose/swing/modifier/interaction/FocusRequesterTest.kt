package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.assumeKeyboardFocusIsPossible
import org.jetbrains.compose.swing.assumeWindowBecomesFocused
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the hoisted focus requester: a request goes to the component the modifier
 * bound, an unbound requester asks for nothing instead of acting on a stale component, the binding ends
 * with the modifier that declared it, and a requester declared on a second component drives that one
 * while leaving the first driving nothing.
 *
 * Where a request was routed is read off the window's most recent focus owner: a request records its
 * component there before the window system is asked, so the routing is observable in a shown window
 * whether or not that window is focused, and in a focused window the same property reports the live
 * focus owner. Only the case that asserts a component actually holds the keyboard needs the window
 * system to grant focus.
 *
 * Which of the two the property reports therefore turns on something the window system decides for
 * itself, and a window it focuses owns no component for the moment in between. A read that waits for the
 * component it expects is unaffected - the routing it waits for outlives that moment - so the positive
 * cases assert through it. A read taken once cannot be held to name a particular component, so what a
 * request must *not* do is asserted on the request itself, which answers whether anything was asked for
 * without consulting the window system at all.
 */
@ExclusiveWindowSystem
class FocusRequesterTest {
    @Test
    fun aRequesterBoundToNothingRefusesTheRequest() = runComposeSwingTest {
        lateinit var requester: FocusRequester
        setContent {
            requester = rememberFocusRequester()
            TextField("unbound", onValueChange = {})
        }
        assertFalse(
            requester.requestFocus(),
            "a requester no component declared must report that it asked for nothing",
        )
    }

    @Test
    fun theRequestGoesToTheBoundComponent() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        lateinit var requester: FocusRequester
        setContent {
            Window(onCloseRequest = {}, title = "focus-requester-routing-test") {
                Column {
                    TextField("first", onValueChange = {})
                    requester = rememberFocusRequester()
                    TextField("second", onValueChange = {}, modifier = SwingModifier.focusRequester(requester))
                }
            }
        }
        val window = onWindowWithTitle("focus-requester-routing-test")
        awaitIdle()
        val frame = window.fetch<JFrame>()
        val second = window.onNodeWithText("second").fetch<JTextField>()

        requester.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === second }
    }

    @Test
    fun theBoundComponentTakesTheKeyboard() {
        assumeKeyboardFocusIsPossible()
        runComposeSwingTest {
            lateinit var requester: FocusRequester
            setContent {
                Window(onCloseRequest = {}, title = "focus-requester-test") {
                    Column {
                        TextField("first", onValueChange = {})
                        requester = rememberFocusRequester()
                        TextField("second", onValueChange = {}, modifier = SwingModifier.focusRequester(requester))
                    }
                }
            }
            val window = onWindowWithTitle("focus-requester-test")
            assumeWindowBecomesFocused(window.fetch<JFrame>())
            val second = window.onNodeWithText("second").fetch<JTextField>()

            assertTrue(requester.requestFocus(), "the request on a shown, focusable component must be made")
            waitUntil { second.isFocusOwner }
        }
    }

    @Test
    fun removingTheModifierEndsTheBinding() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var bound by mutableStateOf(true)
        lateinit var anchor: FocusRequester
        lateinit var requester: FocusRequester
        setContent {
            Window(onCloseRequest = {}, title = "focus-requester-unbind-test") {
                Column {
                    anchor = rememberFocusRequester()
                    TextField("anchor", onValueChange = {}, modifier = SwingModifier.focusRequester(anchor))
                    requester = rememberFocusRequester()
                    TextField(
                        "gated",
                        onValueChange = {},
                        modifier = if (bound) SwingModifier.focusRequester(requester) else SwingModifier,
                    )
                }
            }
        }
        val window = onWindowWithTitle("focus-requester-unbind-test")
        awaitIdle()
        val frame = window.fetch<JFrame>()
        val anchorField = window.onNodeWithText("anchor").fetch<JTextField>()
        val gatedField = window.onNodeWithText("gated").fetch<JTextField>()

        requester.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === gatedField }

        bound = false
        awaitIdle()
        anchor.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === anchorField }
        val requested = requester.requestFocus()
        awaitIdle()
        assertTrue(gatedField.isShowing, "the component itself stays in the window, so only the binding is gone")
        assertFalse(requested, "a requester whose modifier left the chain must ask for nothing")
        assertNotSame(
            gatedField,
            frame.mostRecentFocusOwner,
            "and the request must not have been routed to the component it no longer drives",
        )
    }

    @Test
    fun declaringASecondRequesterTakesTheBindingFromTheFirst() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var useFirst by mutableStateOf(true)
        lateinit var anchor: FocusRequester
        lateinit var first: FocusRequester
        lateinit var second: FocusRequester
        setContent {
            Window(onCloseRequest = {}, title = "focus-requester-swap-test") {
                Column {
                    anchor = rememberFocusRequester()
                    TextField("anchor", onValueChange = {}, modifier = SwingModifier.focusRequester(anchor))
                    first = rememberFocusRequester()
                    second = rememberFocusRequester()
                    TextField(
                        "driven",
                        onValueChange = {},
                        modifier = SwingModifier.focusRequester(if (useFirst) first else second),
                    )
                }
            }
        }
        val window = onWindowWithTitle("focus-requester-swap-test")
        awaitIdle()
        val frame = window.fetch<JFrame>()
        val anchorField = window.onNodeWithText("anchor").fetch<JTextField>()
        val drivenField = window.onNodeWithText("driven").fetch<JTextField>()

        anchor.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === anchorField }
        assertFalse(second.requestFocus(), "a requester that was never declared must ask for nothing")
        awaitIdle()
        assertNotSame(
            drivenField,
            frame.mostRecentFocusOwner,
            "a requester that was never declared must drive nothing",
        )
        first.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === drivenField }

        useFirst = false
        awaitIdle()
        anchor.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === anchorField }
        assertFalse(first.requestFocus(), "the replaced requester must ask for nothing")
        awaitIdle()
        assertNotSame(
            drivenField,
            frame.mostRecentFocusOwner,
            "the replaced requester must be left driving nothing",
        )
        second.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === drivenField }
    }

    @Test
    fun anEarlierDeclarationOfTheSameRequesterNeverTakesTheBindingBack() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var early by mutableStateOf(true)
        val label = mutableStateOf("first-pass")
        lateinit var anchor: FocusRequester
        lateinit var shared: FocusRequester
        setContent {
            Window(onCloseRequest = {}, title = "focus-requester-shared-test") {
                Column {
                    anchor = rememberFocusRequester()
                    TextField("anchor", onValueChange = {}, modifier = SwingModifier.focusRequester(anchor))
                    shared = rememberFocusRequester()
                    if (early) {
                        EarlierDeclaration(label, shared)
                    }
                    TextField("late", onValueChange = {}, modifier = SwingModifier.focusRequester(shared))
                }
            }
        }
        val window = onWindowWithTitle("focus-requester-shared-test")
        awaitIdle()
        val frame = window.fetch<JFrame>()
        val anchorField = window.onNodeWithText("anchor").fetch<JTextField>()
        val lateField = window.onNodeWithText("late").fetch<JTextField>()

        anchor.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === anchorField }
        label.value = "second-pass"
        awaitIdle()
        shared.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === lateField }

        anchor.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === anchorField }
        early = false
        awaitIdle()
        shared.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === lateField }
    }

    /**
     * Declares [requester] on a component whose chain carries [label], read here so that changing it
     * recomposes this declaration alone - the later declaration of the same requester is left untouched.
     */
    @Composable
    private fun EarlierDeclaration(
        label: State<String>,
        requester: FocusRequester,
    ) {
        TextField("early", onValueChange = {}, modifier = SwingModifier.name(label.value).focusRequester(requester))
    }
}

package org.jetbrains.compose.swing.modifier.interaction

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
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the initial-focus declaration: the window's focus goes to the declared
 * component rather than to the first in traversal order, and the declaration is spent once - a
 * recomposition or a later showing of the same component leaves the keyboard wherever the user has
 * taken it.
 *
 * Where the window's focus goes is read off its most recent focus owner: the declaration records its
 * component there as the window shows it, before the window system is asked, so the declaration is
 * observable whether or not that window is focused, and in a focused window the same property reports
 * the live focus owner. Opening a window is what the declaration reacts to, so those cases need a
 * display; the case that asserts the component actually holds the keyboard needs the window system to
 * grant focus as well. Without a display, what remains observable is that declaring and withdrawing the
 * modifier leaves the component's own focusability alone.
 *
 * Which of the two the property reports therefore turns on something the window system decides for
 * itself, and a window it focuses owns no component for the moment in between. A read that waits for the
 * component it expects is unaffected - the focus it waits for outlives that moment - so the cases that
 * assert where focus went wait. A read taken once cannot be held to name a particular component, so a
 * case that asserts a declaration was *not* acted on names the component that must not have taken the
 * focus, which reads the same whichever of the two the property is reporting.
 */
@ExclusiveWindowSystem
class InitialFocusTest {
    @Test
    fun theDeclarationLeavesTheComponentsOwnFocusabilityAlone() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            TextField(
                "field",
                onValueChange = {},
                modifier = if (declared) SwingModifier.initialFocus() else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertTrue(field.isFocusable, "the declaration must not change what the component allows")

        declared = false
        awaitIdle()
        assertTrue(field.isFocusable, "withdrawing the declaration must not change it either")
    }

    @Test
    fun theWindowOpensFocusedOnTheDeclaredComponent() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "initial-focus-routing-test") {
                Column {
                    TextField("first", onValueChange = {})
                    TextField("second", onValueChange = {}, modifier = SwingModifier.initialFocus())
                }
            }
        }
        val window = onWindowWithTitle("initial-focus-routing-test")
        awaitIdle()
        val frame = window.fetch<JFrame>()
        val second = window.onNodeWithText("second").fetch<JTextField>()

        waitUntil { frame.mostRecentFocusOwner === second }
    }

    @Test
    fun theDeclaredComponentHoldsTheKeyboardWhenTheWindowOpens() {
        assumeKeyboardFocusIsPossible()
        runComposeSwingTest {
            setContent {
                Window(onCloseRequest = {}, title = "initial-focus-test") {
                    Column {
                        TextField("first", onValueChange = {})
                        TextField("second", onValueChange = {}, modifier = SwingModifier.initialFocus())
                    }
                }
            }
            val window = onWindowWithTitle("initial-focus-test")
            assumeWindowBecomesFocused(window.fetch<JFrame>())
            val second = window.onNodeWithText("second").fetch<JTextField>()

            waitUntil { second.isFocusOwner }
        }
    }

    @Test
    fun showingTheComponentAgainDoesNotPullTheKeyboardBack() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var shown by mutableStateOf(true)
        lateinit var moveToFirst: FocusRequester
        setContent {
            Window(onCloseRequest = {}, title = "initial-focus-reshow-test") {
                Column {
                    moveToFirst = rememberFocusRequester()
                    TextField("first", onValueChange = {}, modifier = SwingModifier.focusRequester(moveToFirst))
                    TextField("second", onValueChange = {}, modifier = SwingModifier.visible(shown).initialFocus())
                }
            }
        }
        val window = onWindowWithTitle("initial-focus-reshow-test")
        awaitIdle()
        val frame = window.fetch<JFrame>()
        val second = window.onNodeWithText("second").fetch<JTextField>()
        val first = window.onNodeWithText("first").fetch<JTextField>()
        waitUntil { frame.mostRecentFocusOwner === second }

        moveToFirst.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === first }

        shown = false
        awaitIdle()
        shown = true
        awaitIdle()
        awaitEventsDelivered()
        assertNotSame(
            second,
            frame.mostRecentFocusOwner,
            "the declaration is spent on the first showing, so showing it again must leave the keyboard alone",
        )
    }

    @Test
    fun aWithdrawnDeclarationTakesNoFocusWhenTheComponentLaterShows() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var declared by mutableStateOf(true)
        var shown by mutableStateOf(false)
        lateinit var moveToFirst: FocusRequester
        setContent {
            Window(onCloseRequest = {}, title = "initial-focus-withdrawn-test") {
                Column {
                    moveToFirst = rememberFocusRequester()
                    TextField("first", onValueChange = {}, modifier = SwingModifier.focusRequester(moveToFirst))
                    // The declaration is made while the component is hidden, so it is still waiting for
                    // the component to show when it is withdrawn.
                    TextField(
                        "second",
                        onValueChange = {},
                        modifier =
                            SwingModifier.visible(shown).let { if (declared) it.initialFocus() else it },
                    )
                }
            }
        }
        val window = onWindowWithTitle("initial-focus-withdrawn-test")
        awaitIdle()
        val frame = window.fetch<JFrame>()
        val first = window.onNodeWithText("first").fetch<JTextField>()
        moveToFirst.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === first }

        declared = false
        awaitIdle()
        shown = true
        awaitIdle()
        awaitEventsDelivered()
        val second = window.onNodeWithText("second").fetch<JTextField>()
        assertNotSame(
            second,
            frame.mostRecentFocusOwner,
            "a withdrawn declaration must take no focus when the component it left finally shows",
        )
    }

    @Test
    fun aRecompositionDoesNotPullTheKeyboardBack() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var label by mutableStateOf("first-pass")
        lateinit var moveToFirst: FocusRequester
        setContent {
            Window(onCloseRequest = {}, title = "initial-focus-once-test") {
                Column {
                    moveToFirst = rememberFocusRequester()
                    TextField("first", onValueChange = {}, modifier = SwingModifier.focusRequester(moveToFirst))
                    // The declaring component's own chain carries the changing value, so the
                    // recomposition really re-applies the declaration rather than skipping the child.
                    TextField("second", onValueChange = {}, modifier = SwingModifier.name(label).initialFocus())
                }
            }
        }
        val window = onWindowWithTitle("initial-focus-once-test")
        awaitIdle()
        val frame = window.fetch<JFrame>()
        val second = window.onNodeWithText("second").fetch<JTextField>()
        val first = window.onNodeWithText("first").fetch<JTextField>()
        waitUntil { frame.mostRecentFocusOwner === second }

        moveToFirst.requestFocus()
        waitUntil { frame.mostRecentFocusOwner === first }

        label = "second-pass"
        awaitIdle()
        awaitEventsDelivered()
        assertNotSame(
            second,
            frame.mostRecentFocusOwner,
            "an initial-focus declaration is spent once, so a later recomposition must leave the keyboard alone",
        )
    }

    @Test
    fun aComponentDeclaredWhileHiddenTakesFocusWhenItLaterShows() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var shown by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "initial-focus-late-show-test") {
                Column {
                    TextField("first", onValueChange = {})
                    // Declared while hidden, so the declaration is still waiting for the component when
                    // the window opens: the focus it takes is taken on the component's own first showing
                    // rather than on the window's.
                    TextField("second", onValueChange = {}, modifier = SwingModifier.visible(shown).initialFocus())
                }
            }
        }
        val window = onWindowWithTitle("initial-focus-late-show-test")
        awaitIdle()
        val frame = window.fetch<JFrame>()

        shown = true
        awaitIdle()

        val second = window.onNodeWithText("second").fetch<JTextField>()
        waitUntil { frame.mostRecentFocusOwner === second }
    }
}

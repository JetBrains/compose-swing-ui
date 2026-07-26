package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assumeKeyboardFocusIsPossible
import org.jetbrains.compose.swing.assumeWindowBecomesFocused
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.FocusRequester
import org.jetbrains.compose.swing.modifier.interaction.focusRequester
import org.jetbrains.compose.swing.modifier.interaction.rememberFocusRequester
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import javax.swing.JFormattedTextField
import javax.swing.JFrame
import javax.swing.JTextField
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.NumberFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural coverage for what a [FormattedTextField] does with the keyboard: an edit the user leaves
 * uncommitted is settled when focus goes elsewhere, exactly once and carrying the parsed value, and
 * taking focus back does not report a commit the user never made.
 *
 * Losing the keyboard is what triggers the commit, so these run only where the window system grants
 * focus to this process's windows.
 */
class FormattedTextFieldFocusTest {
    private fun integerFactory(): DefaultFormatterFactory {
        val formatter = NumberFormatter()
        formatter.valueClass = Int::class.javaObjectType
        return DefaultFormatterFactory(formatter)
    }

    @Test
    fun anUncommittedEditIsCommittedAndReportedOnceWhenFocusLeaves() {
        assumeKeyboardFocusIsPossible()
        runComposeSwingTest {
            var value: Any? by mutableStateOf(42)
            val reported = mutableListOf<Any?>()
            lateinit var toField: FocusRequester
            lateinit var away: FocusRequester
            setContent {
                Window(onCloseRequest = {}, title = "formatted-focus-commit-test") {
                    Column {
                        toField = rememberFocusRequester()
                        FormattedTextField(
                            value = value,
                            modifier = SwingModifier.focusRequester(toField),
                            formatterFactory = remember { integerFactory() },
                            onValueChange = {
                                reported += it
                                value = it
                            },
                        )
                        away = rememberFocusRequester()
                        TextField("away", modifier = SwingModifier.focusRequester(away))
                    }
                }
            }
            val window = onWindowWithTitle("formatted-focus-commit-test")
            assumeWindowBecomesFocused(window.fetch<JFrame>())
            val fieldNode = window.onNode(SwingMatcher.isOfType<JFormattedTextField>())
            val field = fieldNode.fetch<JFormattedTextField>()
            val other = window.onNodeWithText("away").fetch<JTextField>()

            toField.requestFocus()
            waitUntil { field.isFocusOwner }
            assertEquals(emptyList(), reported, "taking focus must not report a commit the user never made")

            fieldNode.performTextReplacement("43")
            assertEquals(emptyList(), reported, "characters typed but not committed carry no new value")

            away.requestFocus()
            waitUntil { other.isFocusOwner }
            awaitIdle()
            assertEquals(listOf<Any?>(43), reported, "losing focus must commit the edit and report it once")
            assertEquals(43, value, "the application's state must hold the committed value")
        }
    }

    @Test
    fun retakingFocusAfterACommitReportsNothingFurther() {
        assumeKeyboardFocusIsPossible()
        runComposeSwingTest {
            var value: Any? by mutableStateOf(42)
            val reported = mutableListOf<Any?>()
            lateinit var toField: FocusRequester
            lateinit var away: FocusRequester
            setContent {
                Window(onCloseRequest = {}, title = "formatted-focus-retake-test") {
                    Column {
                        toField = rememberFocusRequester()
                        FormattedTextField(
                            value = value,
                            modifier = SwingModifier.focusRequester(toField),
                            formatterFactory = remember { integerFactory() },
                            onValueChange = {
                                reported += it
                                value = it
                            },
                        )
                        away = rememberFocusRequester()
                        TextField("away", modifier = SwingModifier.focusRequester(away))
                    }
                }
            }
            val window = onWindowWithTitle("formatted-focus-retake-test")
            assumeWindowBecomesFocused(window.fetch<JFrame>())
            val field = window.onNode(SwingMatcher.isOfType<JFormattedTextField>()).fetch<JFormattedTextField>()
            val other = window.onNodeWithText("away").fetch<JTextField>()

            away.requestFocus()
            waitUntil { other.isFocusOwner }
            toField.requestFocus()
            waitUntil { field.isFocusOwner }
            away.requestFocus()
            waitUntil { other.isFocusOwner }
            awaitIdle()

            assertTrue(reported.isEmpty(), "focus moving in and out with nothing typed must report nothing")
            assertEquals("42", field.text, "and the committed value must still be what the field displays")
        }
    }
}

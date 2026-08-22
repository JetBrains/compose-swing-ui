package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.components.text.PasswordField
import javax.swing.JPasswordField
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins that a password field's characters never reach a failure message: not through the one-line
 * component description every tree dump carries, not through [SwingMatcher.hasText], and not through
 * `SwingNodeInteraction.assertTextEquals`.
 */
class PasswordFieldTextLeakTest {
    @Test
    fun theTreeDumpNeverCarriesThePassword() = runComposeSwingTest {
        setContent {
            PasswordField(value = SECRET.toCharArray(), onValueChange = {})
        }

        val failure = assertFailsWith<AssertionError> { onNodeWithText("no-such-node").assertExists() }
        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("PasswordField"),
            "the dump should still name the field by type: $message",
        )
        assertFalse(message.contains(SECRET), "the dump must not carry the password's characters: $message")
    }

    @Test
    fun hasTextNeverMatchesAPasswordField() = runComposeSwingTest {
        setContent {
            PasswordField(value = SECRET.toCharArray(), onValueChange = {})
        }

        // A password field carries no readable text, so it never matches - not even its own value.
        onAllNodes(SwingMatcher.hasText(SECRET)).assertCountEquals(0)
    }

    @Test
    fun assertTextEqualsFailsWithoutReadingTheActualCharacters() = runComposeSwingTest {
        setContent {
            PasswordField(value = SECRET.toCharArray(), onValueChange = {})
        }

        val failure = assertFailsWith<AssertionError> { onNodeOfType<JPasswordField>().assertTextEquals(SECRET) }
        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("text was null,"),
            "the assertion should read no text off a password field: $message",
        )
        assertFalse(
            message.contains("text was \"$SECRET\""),
            "the failure must not echo the actual characters: $message",
        )
    }

    private companion object {
        const val SECRET: String = "hunter2-secret"
    }
}

package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import javax.swing.JPasswordField
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for the state-based [PasswordField] overload driving a realized [JPasswordField]
 * over a real applier. Every assertion goes through the public state API and the rendered field, never
 * a private field: the shared document is the single source of truth, so an edit on either side is
 * observable on the other, and component-specific params (echoChar, columns) reach the widget.
 */
class PasswordFieldStateTest {
    @Test
    fun fieldSharesTheStateDocument() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("seed")
            PasswordField(state = state)
        }

        // Sharing one document means an edit made through either side is what the other renders: a write
        // through the state reaches the field, and typing in the field reaches the state.
        state.edit { append(" grown") }
        awaitIdle()
        val field = onNodeOfType<JPasswordField>().fetch()
        assertEquals("seed grown", String(field.password))

        onNodeOfType<JPasswordField>().performTextReplacement("typed")
        assertEquals("typed", state.text.toString())
    }

    @Test
    fun stateTextAndEditUpdateTheField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            PasswordField(state = state)
        }

        val field = onNodeOfType<JPasswordField>().fetch()
        assertEquals("ab", String(field.password))

        state.text = "abc"
        awaitIdle()
        assertEquals("abc", String(field.password))

        state.edit { append("d") }
        awaitIdle()
        assertEquals("abcd", String(field.password))
    }

    @Test
    fun typingIntoFieldUpdatesStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            PasswordField(state = state)
        }

        onNodeOfType<JPasswordField>().performTextReplacement("secret")

        assertEquals("secret", state.text.toString())
    }

    @Test
    fun settingSelectionMovesTheCaret() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello world")
            PasswordField(state = state)
        }

        val field = onNodeOfType<JPasswordField>().fetch()
        state.selection = TextRange(0, 5)
        awaitIdle()

        assertEquals(0, field.selectionStart)
        assertEquals(5, field.selectionEnd)
    }

    @Test
    fun echoCharAppliesToTheField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hidden")
            PasswordField(state = state, echoChar = '#')
        }

        val field = onNodeOfType<JPasswordField>().fetch()
        assertEquals('#', field.echoChar)
    }

    @Test
    fun columnsApplyToTheField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            PasswordField(state = state, columns = 12)
        }

        val field = onNodeOfType<JPasswordField>().fetch()
        assertEquals(12, field.columns)
    }
}

package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JFormattedTextField
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.NumberFormatter
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Behavioral tests for the state-based [FormattedTextField] overload driving a realized
 * `JFormattedTextField` over a real applier. Every assertion goes through the public state API and the
 * rendered field: the shared document is the single source of truth, so an edit on either side is
 * observable on the other, and the formatter param reaches the widget.
 */
class StateFormattedTextFieldTest {
    @Test
    fun fieldSharesTheStateDocument() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("seed")
            FormattedTextField(state = state)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch<JFormattedTextField>()
        assertSame(state.document, field.document, "the field must render the state's own document")
    }

    @Test
    fun callerSuppliedDocumentIsInstalledIntoTheField() = runComposeSwingTest {
        val document = PlainDocument().apply { insertString(0, "preset", null) }
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            FormattedTextField(state = state)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch<JFormattedTextField>()
        assertSame(document, field.document)
        assertEquals("preset", field.text)
    }

    @Test
    fun editAppendUpdatesRealizedField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            FormattedTextField(state = state)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch<JFormattedTextField>()
        assertEquals("ab", field.text)

        state.edit { append("c") }
        awaitIdle()
        assertEquals("abc", field.text)
    }

    @Test
    fun assigningTextReplacesFieldContent() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello")
            FormattedTextField(state = state)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch<JFormattedTextField>()
        state.text = "help"
        awaitIdle()
        assertEquals("help", field.text)
        assertEquals("help", state.text.toString())
    }

    @Test
    fun typingIntoFieldUpdatesStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            FormattedTextField(state = state)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch<JFormattedTextField>()
        field.text = "typed"
        awaitIdle()

        assertEquals("typed", state.text.toString())
    }

    @Test
    fun clearTextEmptiesTheField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("something")
            FormattedTextField(state = state)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch<JFormattedTextField>()
        state.clearText()
        awaitIdle()

        assertEquals("", field.text)
        assertEquals("", state.text.toString())
    }

    @Test
    fun formatterFactoryParamReachesTheField() = runComposeSwingTest {
        val factory = DefaultFormatterFactory(NumberFormatter())
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            FormattedTextField(state = state, formatterFactory = factory)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch<JFormattedTextField>()
        assertSame(factory, field.formatterFactory, "the formatterFactory param must reach the widget")
    }

    @Test
    fun focusLostBehaviorParamReachesTheField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            FormattedTextField(state = state, focusLostBehavior = JFormattedTextField.PERSIST)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch<JFormattedTextField>()
        assertEquals(JFormattedTextField.PERSIST, field.focusLostBehavior)
    }

    @Test
    fun columnsParamReachesTheField() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            FormattedTextField(state = state, columns = 8)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch<JFormattedTextField>()
        assertEquals(8, field.columns)
    }
}

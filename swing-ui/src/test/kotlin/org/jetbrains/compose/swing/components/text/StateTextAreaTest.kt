package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextArea
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Behavioral tests for the state-based [TextArea] overload driving a realized [JTextArea] over a real
 * applier. The area shares the state's document, edits on either side are observable on the other, and
 * the area's own row/column geometry is honored. Every assertion goes through the public state API and
 * the rendered [JTextArea], never a private field.
 */
class StateTextAreaTest {
    @Test
    fun areaSharesTheStateDocument() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("seed")
            TextArea(state = state)
        }

        val area = onNodeOfType<JTextArea>().fetch<JTextArea>()
        assertSame(state.document, area.document, "the area must render the state's own document")
    }

    @Test
    fun callerSuppliedDocumentIsInstalledIntoTheArea() = runComposeSwingTest {
        val document = PlainDocument().apply { insertString(0, "preset", null) }
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextArea(state = state)
        }

        val area = onNodeOfType<JTextArea>().fetch<JTextArea>()
        assertSame(document, area.document)
        assertEquals("preset", area.text)
    }

    @Test
    fun editAppendUpdatesRealizedArea() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            TextArea(state = state)
        }

        val area = onNodeOfType<JTextArea>().fetch<JTextArea>()
        assertEquals("ab", area.text)

        state.edit { append("c") }
        awaitIdle()
        assertEquals("abc", area.text)
    }

    @Test
    fun assigningTextUpdatesTheArea() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello")
            TextArea(state = state)
        }

        val area = onNodeOfType<JTextArea>().fetch<JTextArea>()
        state.text = "help"
        awaitIdle()
        assertEquals("help", area.text)
        assertEquals("help", state.text.toString())
    }

    @Test
    fun typingIntoAreaUpdatesStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            TextArea(state = state)
        }

        val area = onNodeOfType<JTextArea>().fetch<JTextArea>()
        area.text = "typed"
        awaitIdle()

        assertEquals("typed", state.text.toString())
    }

    @Test
    fun typingRecomposesASiblingReadingStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hi")
            BoxPanel {
                TextArea(state = state)
                Label(text = "Echo: ${state.text}")
            }
        }

        onNodeWithText("Echo: hi").assertExists()

        val area = onNodeOfType<JTextArea>().fetch<JTextArea>()
        area.text = "bye"
        awaitIdle()

        onNodeWithText("Echo: bye").assertExists()
        onNodeWithText("Echo: hi").assertDoesNotExist()
    }

    @Test
    fun rowsAndColumnsApplyToTheArea() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            TextArea(state = state, rows = 5, columns = 12)
        }

        val area = onNodeOfType<JTextArea>().fetch<JTextArea>()
        assertEquals(5, area.rows)
        assertEquals(12, area.columns)
    }
}

package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
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

        // Sharing one document means an edit made through either side is what the other renders: a write
        // through the state reaches the area, and typing in the area reaches the state.
        state.edit { append(" grown") }
        awaitIdle()
        onNodeOfType<JTextArea>().assertTextEquals("seed grown")

        onNodeOfType<JTextArea>().performTextReplacement("typed")
        assertEquals("typed", state.text.toString())
    }

    @Test
    fun callerSuppliedDocumentIsInstalledIntoTheArea() = runComposeSwingTest {
        val document = PlainDocument().apply { insertString(0, "preset", null) }
        setContent {
            TextArea(state = rememberDocumentState(document = document))
        }

        val area = onNodeOfType<JTextArea>()
        assertSame(document, area.fetch().document)
        area.assertTextEquals("preset")
    }

    @Test
    fun editAppendUpdatesRealizedArea() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("ab")
            TextArea(state = state)
        }

        onNodeOfType<JTextArea>().assertTextEquals("ab")

        state.edit { append("c") }
        awaitIdle()
        onNodeOfType<JTextArea>().assertTextEquals("abc")
    }

    @Test
    fun assigningTextUpdatesTheArea() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello")
            TextArea(state = state)
        }

        state.text = "help"
        awaitIdle()
        onNodeOfType<JTextArea>().assertTextEquals("help")
        assertEquals("help", state.text.toString())
    }

    @Test
    fun typingIntoAreaUpdatesStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState()
            TextArea(state = state)
        }

        onNodeOfType<JTextArea>().performTextReplacement("typed")

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

        onNodeOfType<JTextArea>().performTextReplacement("bye")

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

        val area = onNodeOfType<JTextArea>().fetch()
        assertEquals(5, area.rows)
        assertEquals(12, area.columns)
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
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

    /**
     * A state-driven area renders the state's own document, so a write through the state is a write to
     * the document the area is showing: it lands as it is made, with no composition pass in between. The
     * test holds the frames back to say so - the area holds the new text before any frame is sent.
     */
    @Test
    fun assigningTextUpdatesTheArea() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello")
            TextArea(state = state)
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val area = onNodeOfType<JTextArea>()
        state.text = "help"

        area.assertTextEquals("help")

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
            Panel(PanelLayout.Box()) {
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

        val area = onNodeOfType<JTextArea>()
        area.assertProperty(5) { rows }
        area.assertProperty(12) { columns }
    }
}

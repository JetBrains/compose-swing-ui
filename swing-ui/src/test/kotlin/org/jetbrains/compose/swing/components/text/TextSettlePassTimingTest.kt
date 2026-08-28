package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That a text component is settled against its declared value on the apply pass that carries the caller's
 * answer to an edit, and that the pass writes only where the component is not already holding the answer.
 *
 * What the composition declares and what the user types move independently, and a text component follows
 * each of them apiece. The frames are driven by the test, which is what makes the passes countable: each
 * frame carries one, and a raw document listener hears every change the document takes - the writes a
 * settle makes included. An edit is published to the composition by the idle gate, which sends no frame of
 * its own while the test drives them, so the frame that follows is the apply pass that carries it.
 *
 * A settle writes only the span that differs, which the document publishes as a removal of that span
 * and, where the declaration puts text in its place, an insertion; each entry below is the kind of
 * change and the document's length once it has been made. Putting a typed character back is therefore
 * a removal alone.
 */
class TextSettlePassTimingTest {
    @Test
    fun anEditTheCallerDoesNotAdoptIsSettledBackByThePassCarryingTheAnswer() = runComposeSwingTest {
        val changes = mutableListOf<String>()
        // Attached as-is and never rebuilt, so it stays on the document across every pass below.
        val recorder = documentChangeRecorder(changes)
        setContent {
            TextField(value = DECLARED, onValueChange = {}, modifier = SwingModifier.documentListener(recorder))
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val field = onNodeOfType<JTextField>().fetch()
        changes.clear()

        field.type("!")
        awaitIdle()

        assertEquals(listOf(EDIT), changes, "the edit alone should reach the document until a pass answers it")
        assertEquals(EDITED, field.text, "the field should hold what the user typed until a pass answers it")

        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(EDIT, DECLARATION_RESTORED),
            changes,
            "the pass carrying the caller's answer should write the standing declaration back",
        )
        assertEquals(DECLARED, field.text, "the field should be back on the declared text")
    }

    @Test
    fun anEditTheCallerAdoptsInTheSamePassCostsTheDocumentNoWrite() = runComposeSwingTest {
        val changes = mutableListOf<String>()
        val recorder = documentChangeRecorder(changes)
        var value by mutableStateOf(DECLARED)
        setContent {
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier = SwingModifier.documentListener(recorder),
            )
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val field = onNodeOfType<JTextField>().fetch()
        changes.clear()

        // The edit and the caller's answer to it both reach the frame below, so one pass carries them.
        field.type("!")
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(EDIT),
            changes,
            "a pass whose declaration the field already holds should write nothing over the user's text",
        )
        assertEquals(EDITED, field.text, "the field should stand where the user left it")
    }

    @Test
    fun aDeclarationAnsweringAnEditReachesTheFieldOnThatPass() = runComposeSwingTest {
        val changes = mutableListOf<String>()
        val recorder = documentChangeRecorder(changes)
        var value by mutableStateOf(DECLARED)
        setContent {
            TextField(
                value = value,
                onValueChange = { value = it.uppercase() },
                modifier = SwingModifier.documentListener(recorder),
            )
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val field = onNodeOfType<JTextField>().fetch()
        changes.clear()

        // The caller answers the edit with a value of its own: the field is settled on that answer, not
        // put back on the declaration that stood before the edit.
        field.type("!")
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(EDIT, ANSWER_REPLACED, ANSWER_WRITTEN),
            changes,
            "the pass carrying the answer should write it, once",
        )
        assertEquals(EDITED.uppercase(), field.text, "the field should hold the value that pass settled it on")
    }

    private companion object {
        /** What the composition declares, and goes on declaring while the user types over it. */
        const val DECLARED = "hello"

        /** What the field holds once the user has typed, away from the declaration. */
        const val EDITED = "hello!"

        /** The user's keystroke, which the document publishes as an insertion. */
        const val EDIT = "insert@6"

        /** A settle putting [DECLARED] back, which takes the typed character off and adds nothing. */
        const val DECLARATION_RESTORED = "remove@5"

        /** The removal half of a settle onto an answer that differs from [EDITED] everywhere but its last character. */
        const val ANSWER_REPLACED = "remove@1"

        /** The insertion half of that settle, which leaves the document as long as [EDITED]. */
        const val ANSWER_WRITTEN = "insert@6"
    }
}

/** Records the kind of every change a document takes, and the length the document is left with. */
private fun documentChangeRecorder(into: MutableList<String>): DocumentListener = object : DocumentListener {
    override fun insertUpdate(event: DocumentEvent) {
        into += "insert@${event.document.length}"
    }

    override fun removeUpdate(event: DocumentEvent) {
        into += "remove@${event.document.length}"
    }

    override fun changedUpdate(event: DocumentEvent) {
        into += "changed@${event.document.length}"
    }
}

/** Types [text] at the end of the field's content, as a keystroke reaches the document. */
private fun JTextField.type(text: String) {
    document.insertString(document.length, text, null)
}

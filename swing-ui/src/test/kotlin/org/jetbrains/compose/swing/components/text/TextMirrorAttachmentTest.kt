package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.Document
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins where a raw-listener text component's observers live. A `JTextComponent` publishes its changes
 * through its document, and that document's single listener list is the only channel there is, so the
 * caller's own listener and the mirror the component settles against share it.
 *
 * The caller's instance is attached as-is: it is what the document holds while the component is
 * composed, and what the document gives up when it is withdrawn. The mirror belongs to the component's
 * own binding and joins and leaves with it, so a withdrawn component leaves nothing of the wrapper's
 * observing the document it rendered.
 */
class TextMirrorAttachmentTest {
    @Test
    fun aTextFieldHoldsTheCallersInstanceAndTakesItsMirrorAway() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val listener = documentChangeListener {}
        setContent { if (shown) TextField(value = "hello", documentListener = listener) }
        val document = onNodeOfType<JTextField>().fetch().document

        val mounted = document.registeredListeners()
        shown = false
        awaitIdle()

        assertOnlyTheCallerAndTheMirrorLeft(mounted, document.registeredListeners(), listener)
    }

    @Test
    fun aTextAreaHoldsTheCallersInstanceAndTakesItsMirrorAway() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val listener = documentChangeListener {}
        setContent { if (shown) TextArea(value = "hello", documentListener = listener) }
        val document = onNodeOfType<JTextArea>().fetch().document

        val mounted = document.registeredListeners()
        shown = false
        awaitIdle()

        assertOnlyTheCallerAndTheMirrorLeft(mounted, document.registeredListeners(), listener)
    }

    @Test
    fun aTextPaneHoldsTheCallersInstanceAndTakesItsMirrorAway() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val listener = documentChangeListener {}
        setContent { if (shown) TextPane(value = "hello", documentListener = listener) }
        val document = onNodeOfType<JTextPane>().fetch().document

        val mounted = document.registeredListeners()
        shown = false
        awaitIdle()

        assertOnlyTheCallerAndTheMirrorLeft(mounted, document.registeredListeners(), listener)
    }

    @Test
    fun aPasswordFieldHoldsTheCallersInstanceAndTakesItsMirrorAway() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val listener = documentChangeListener {}
        setContent {
            if (shown) PasswordField(value = "hunter2".toCharArray(), documentListener = listener)
        }
        val document = onNodeOfType<JPasswordField>().fetch().document

        val mounted = document.registeredListeners()
        shown = false
        awaitIdle()

        assertOnlyTheCallerAndTheMirrorLeft(mounted, document.registeredListeners(), listener)
    }

    @Test
    fun aReactivatedFieldSettlesAnEditItsCallerDoesNotAdopt() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val listener = documentChangeListener {}
        setContent {
            ReusableContentHost(active = active) {
                TextField(value = "hello", documentListener = listener)
            }
        }

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        val field = onNodeOfType<JTextField>()
        val document = field.fetch().document
        document.insertString(document.length, "!", null)
        awaitIdle()

        // Only a mirror that rejoined the reactivated field reports the edit as a change, which is what
        // brings the pass that writes the declared text back over it.
        field.assertTextEquals("hello")
    }

    @Test
    fun everyTextBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { textMirror(MirrorState("")) }
        assertDeclaredChainCarriedOnce { onTextEdit(MirrorState("")) { } }
        assertDeclaredChainCarriedOnce { documentStateBinding(DocumentState(PlainDocument())) }
        assertDeclaredChainCarriedOnce { formattedValueStateBinding(FormattedValueState(null)) }
    }
}

private fun Document.registeredListeners(): List<DocumentListener> =
    (this as AbstractDocument).documentListeners.toList()

// Asserts that [listener] - the caller's own instance - was on the document while the component was
// composed, and that withdrawing the component took it and the component's own mirror, and nothing
// else, off the document again.
private fun assertOnlyTheCallerAndTheMirrorLeft(
    mounted: List<DocumentListener>,
    remaining: List<DocumentListener>,
    listener: DocumentListener,
) {
    assertTrue(
        mounted.any { it === listener },
        "the caller's own instance is what the document holds, not a wrapper standing in for it",
    )
    assertFalse(
        remaining.any { it === listener },
        "the caller's listener leaves the document with the component",
    )
    assertEquals(
        2,
        mounted.size - remaining.size,
        "the component put the caller's listener and one mirror on the document, and withdrew both",
    )
}

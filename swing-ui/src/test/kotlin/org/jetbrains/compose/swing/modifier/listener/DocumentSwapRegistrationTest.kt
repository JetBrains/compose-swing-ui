package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextField
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A text component publishes its changes through its document, and that document can be replaced under
 * it. A listener declared on the component follows it there, or it is left reporting changes to text
 * nothing renders; and it leaves the document it followed to, or it outlives its own detach.
 */
class DocumentSwapRegistrationTest {
    @Test
    fun aDocumentListenerFollowsTheComponentAcrossADocumentSwap() = runComposeSwingTest {
        var inserts = 0
        setContent {
            FlowPanel {
                TextField(
                    value = "",
                    onValueChange = {},
                    modifier = SwingModifier.documentListener(onInsert = { inserts++ }),
                )
            }
        }
        val field = onNodeOfType<JTextField>().fetch<JTextField>()

        field.document.insertString(0, "before", null)
        assertEquals(1, inserts, "the listener reports on the document the component starts with")

        field.document = PlainDocument()
        field.document.insertString(0, "after", null)
        assertEquals(2, inserts, "and keeps reporting once that document is swapped out")
    }

    @Test
    fun aDocumentListenerLeavesTheDocumentItFollowedTo() = runComposeSwingTest {
        var inserts = 0
        var declared by mutableStateOf(true)
        setContent {
            FlowPanel {
                TextField(
                    value = "",
                    onValueChange = {},
                    modifier =
                        if (declared) {
                            SwingModifier.documentListener(onInsert = { inserts++ })
                        } else {
                            SwingModifier
                        },
                )
            }
        }
        val field = onNodeOfType<JTextField>().fetch<JTextField>()

        val swapped = PlainDocument()
        field.document = swapped
        swapped.insertString(0, "while declared", null)
        assertEquals(1, inserts, "the listener rode the swap")

        declared = false
        awaitIdle()

        swapped.insertString(0, "after", null)
        assertEquals(
            1,
            inserts,
            "and detach removed it from the document it had followed to, not the one it started on",
        )
    }

    @Test
    fun aDocumentListenerLeavesNoSwapListenerBehind() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        setContent {
            FlowPanel {
                TextField(
                    value = "",
                    onValueChange = {},
                    modifier =
                        if (declared) {
                            SwingModifier.documentListener(onInsert = { })
                        } else {
                            SwingModifier
                        },
                )
            }
        }
        val field = onNodeOfType<JTextField>().fetch<JTextField>()

        // The swap listener is private, so it can only be counted, and a text component may carry
        // `document` listeners of its own. What the seam owns is the difference from the count taken
        // while nothing is declared.
        val baseline = field.getPropertyChangeListeners("document").size

        declared = true
        awaitIdle()
        assertEquals(
            baseline + 1,
            field.getPropertyChangeListeners("document").size,
            "declaring the listener installs the swap listener that follows the document",
        )

        declared = false
        awaitIdle()
        assertEquals(
            baseline,
            field.getPropertyChangeListeners("document").size,
            "and dropping it takes that swap listener off the component again",
        )
    }
}

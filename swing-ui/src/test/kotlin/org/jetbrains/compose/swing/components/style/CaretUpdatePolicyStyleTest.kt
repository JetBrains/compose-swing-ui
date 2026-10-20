package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import javax.swing.JTextArea
import javax.swing.text.DefaultCaret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class CaretUpdatePolicyStyleTest {
    @ParameterizedTest
    @CsvSource("-1, false, 21", "1, false, 11", "2, true, 21", "0, true, 11")
    fun policyControlsCaretMovement(
        policy: Int,
        offEdt: Boolean,
        expected: Int,
    ) = runComposeSwingTest {
        val area = JTextArea("hello world")
        setContent {
            SwingNode(
                factory = { area },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        if (policy != -1) caretUpdatePolicy(policy)
                    },
            )
        }
        area.caretPosition = 11
        val insert = { area.document.insertString(0, "appended: ", null) }
        if (offEdt) {
            Thread(insert).apply {
                start()
                join()
            }
        } else {
            insert()
        }
        awaitIdle()
        assertEquals(expected, area.caretPosition)
    }

    @Test
    fun policyRestoresAfterCaretRemoval() = runComposeSwingTest {
        val mine = DefaultCaret()
        var decorated by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextArea(TEXT) },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        if (decorated) {
                            caretUpdatePolicy(DefaultCaret.NEVER_UPDATE)
                            caret(mine)
                        }
                    },
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()
        val installedPolicy = (JTextArea().caret as DefaultCaret).updatePolicy
        assertEquals(DefaultCaret.NEVER_UPDATE, mine.updatePolicy)

        // The caret is declared last, so it comes apart first: the area carries its own caret again
        // while the policy declaration still stands, and the policy hands that caret back what it read.
        decorated = false
        awaitIdle()

        assertNotSame(mine, area.caret)
        assertEquals(installedPolicy, (area.caret as DefaultCaret).updatePolicy)
        assertEquals(DefaultCaret.NEVER_UPDATE, mine.updatePolicy)
    }

    @Test
    fun replacementCaretKeepsPolicy() = runComposeSwingTest {
        val first = DefaultCaret()
        val second = DefaultCaret()
        var swapped by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { JTextArea(TEXT) },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        caret(if (swapped) second else first)
                        caretUpdatePolicy(DefaultCaret.NEVER_UPDATE)
                    },
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()

        // The caret slot writes on this pass, so the policy slot declared after it is written again
        // rather than adopted, onto the caret that has just arrived.
        swapped = true
        awaitIdle()
        assertSame(second, area.caret)

        area.caretPosition = TEXT.length
        area.document.insertString(0, "appended: ", null)
        awaitIdle()

        assertEquals(11, area.caretPosition)
    }
}

private const val TEXT = "hello world"

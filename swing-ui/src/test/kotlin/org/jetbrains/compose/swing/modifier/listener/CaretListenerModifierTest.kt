package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextField
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.text.JTextComponent
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the caret listener builder: an existing [CaretListener] object reports the
 * live component's caret offset and selection anchor, two of them on one chain both fire, the
 * registration follows the component rather than a document, and the instance stops reporting once its
 * element leaves the chain.
 */
class CaretListenerModifierTest {
    private class CaretRecorder : CaretListener {
        val dots: MutableList<Int> = mutableListOf()
        val marks: MutableList<Int> = mutableListOf()

        override fun caretUpdate(event: CaretEvent) {
            dots += event.dot
            marks += event.mark
        }
    }

    @Test
    fun theCaretOffsetAndSelectionAnchorAreReported() = runComposeSwingTest {
        val recorder = CaretRecorder()
        setContent {
            TextField("hello", modifier = SwingModifier.caretListener(recorder))
        }
        val field = onNodeOfType<JTextField>().fetch<JTextComponent>()

        field.select(1, 4)
        assertEquals(4, recorder.dots.last(), "the caret offset must be reported")
        assertEquals(1, recorder.marks.last(), "the selection anchor must be reported")

        field.caretPosition = 0
        assertEquals(0, recorder.dots.last(), "a collapsed caret must be reported at its offset")
        assertEquals(0, recorder.marks.last(), "a collapsed caret reports the same anchor and offset")
    }

    @Test
    fun theExactInstanceIsRegisteredAndTwoOfThemBothFire() = runComposeSwingTest {
        val first = CaretRecorder()
        val second = CaretRecorder()
        setContent {
            TextField("hello", modifier = SwingModifier.caretListener(first).caretListener(second))
        }
        val field = onNodeOfType<JTextField>().fetch<JTextComponent>()
        assertTrue(field.caretListeners.any { it === first }, "the first instance must be registered")
        assertTrue(field.caretListeners.any { it === second }, "the second instance must be registered")

        field.caretPosition = 2
        assertEquals(2, first.dots.last(), "the first instance must fire")
        assertEquals(2, second.dots.last(), "the second instance must fire")
    }

    @Test
    fun theRegistrationSurvivesADocumentSwap() = runComposeSwingTest {
        val recorder = CaretRecorder()
        setContent {
            TextField("hello", modifier = SwingModifier.caretListener(recorder))
        }
        val field = onNodeOfType<JTextField>().fetch<JTextComponent>()

        field.document = PlainDocument().apply { insertString(0, "replaced", null) }
        val reportsBefore = recorder.dots.size
        field.caretPosition = 3
        assertTrue(
            recorder.dots.size > reportsBefore && recorder.dots.last() == 3,
            "a caret listener is bound to the component, so it keeps reporting on a replacement document",
        )
    }

    @Test
    fun theInstanceStopsReportingOnceItsElementLeavesTheChain() = runComposeSwingTest {
        var attached by mutableStateOf(true)
        val recorder = CaretRecorder()
        setContent {
            TextField("hello", modifier = if (attached) SwingModifier.caretListener(recorder) else SwingModifier)
        }
        val field = onNodeOfType<JTextField>().fetch<JTextComponent>()
        field.caretPosition = 1
        val reportsWhileAttached = recorder.dots.size
        assertTrue(reportsWhileAttached > 0, "the listener must report while its element is in the chain")

        attached = false
        awaitIdle()
        assertTrue(
            field.caretListeners.none { it === recorder },
            "the exact instance must be removed when its element leaves the chain",
        )
        field.caretPosition = 4
        assertEquals(
            reportsWhileAttached,
            recorder.dots.size,
            "a detached instance must not report later caret moves",
        )
    }

    @Test
    fun aNonTextTargetIsRejectedNamingTheRequiredType() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label("X", modifier = SwingModifier.caretListener(CaretRecorder()))
                }
                awaitIdle()
            }
        assertTrue(
            JTextComponent::class.java.name in error.message.orEmpty(),
            "the wrong-target error must name the required JTextComponent target, but was: ${error.message}",
        )
    }
}

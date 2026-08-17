package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.DocumentState
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import javax.swing.JTextField
import javax.swing.text.NavigationFilter
import javax.swing.text.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Behavioral coverage for the `SwingModifier.navigationFilter` seam. A navigation filter answers every
 * caret move, so these drive the caret the way a caller does - by assigning a [DocumentState]'s
 * selection - and assert where it actually lands.
 */
class NavigationFilterModifierTest {
    /**
     * Keeps the caret out of the prompt at the head of the document, the way a console keeps the caret
     * in the line being typed. Both entry points clamp: a caret is put with `setDot` and a selection is
     * then extended with `moveDot`.
     */
    private object AfterThePromptFilter : NavigationFilter() {
        override fun setDot(
            fb: FilterBypass,
            dot: Int,
            bias: Position.Bias,
        ) {
            fb.setDot(dot.coerceAtLeast(PROMPT_LENGTH), bias)
        }

        override fun moveDot(
            fb: FilterBypass,
            dot: Int,
            bias: Position.Bias,
        ) {
            fb.moveDot(dot.coerceAtLeast(PROMPT_LENGTH), bias)
        }
    }

    @Test
    fun theFilterIsInstalledOnTheField() = runComposeSwingTest {
        setContent {
            TextField(
                state = rememberDocumentState(TEXT),
                modifier = SwingModifier.navigationFilter(AfterThePromptFilter),
            )
        }

        val field = onNodeOfType<JTextField>().fetch()
        assertSame(AfterThePromptFilter, field.navigationFilter, "the declared filter should reach the field")
    }

    @Test
    fun aSelectionDeclaredThroughTheStateIsRedirectedByTheFilter() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(TEXT)
            TextField(state = state, modifier = SwingModifier.navigationFilter(AfterThePromptFilter))
        }

        // Inside the prompt, so the filter redirects the caret to the first offset it allows, and the
        // state reports the place the caret settled rather than the one it asked for.
        state.selection = TextRange(INSIDE_THE_PROMPT, INSIDE_THE_PROMPT)
        awaitIdle()

        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(PROMPT_LENGTH, field.caretPosition, "the filter should move the caret out of the prompt")
        assertEquals(
            TextRange(PROMPT_LENGTH, PROMPT_LENGTH),
            state.selection,
            "the state should report the offset the filter allowed",
        )
    }

    @Test
    fun droppingTheModifierPutsBackTheFilterTheFieldCarried() = runComposeSwingTest {
        lateinit var state: DocumentState
        var filtering by mutableStateOf(false)
        setContent {
            state = rememberDocumentState(TEXT)
            TextField(
                state = state,
                modifier = if (filtering) SwingModifier.navigationFilter(AfterThePromptFilter) else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertNull(field.navigationFilter, "a field starts with its caret free to go anywhere")

        filtering = true
        awaitIdle()
        assertSame(AfterThePromptFilter, field.navigationFilter, "the declared filter should reach the field")

        filtering = false
        awaitIdle()
        assertNull(field.navigationFilter, "dropping the declaration should put back the filter the field carried")

        state.selection = TextRange(INSIDE_THE_PROMPT, INSIDE_THE_PROMPT)
        awaitIdle()
        assertEquals(INSIDE_THE_PROMPT, field.caretPosition, "with no filter the caret goes where it is put")
    }
}

private const val TEXT = "> hello"
private const val PROMPT_LENGTH = 2
private const val INSIDE_THE_PROMPT = 1

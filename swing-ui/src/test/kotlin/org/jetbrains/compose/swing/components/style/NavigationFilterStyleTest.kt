package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.DocumentState
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import javax.swing.JTextField
import javax.swing.text.NavigationFilter
import javax.swing.text.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NavigationFilterStyleTest {
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
    fun filterRedirectsSelectionAndRestores() = runComposeSwingTest {
        lateinit var state: DocumentState
        var filtering by mutableStateOf(false)
        setContent {
            state = rememberDocumentState("> hello")
            TextField(state = state, style = { if (filtering) navigationFilter(AfterThePromptFilter) })
        }
        val field = onNodeOfType<JTextField>()
        field.assertProperty(null) { navigationFilter }
        filtering = true
        awaitIdle()
        field.assertProperty(true) { navigationFilter === AfterThePromptFilter }
        state.selection = TextRange(1, 1)
        awaitIdle()
        field.assertProperty(PROMPT_LENGTH) { caretPosition }
        assertEquals(TextRange(PROMPT_LENGTH, PROMPT_LENGTH), state.selection)
        filtering = false
        awaitIdle()
        field.assertProperty(null) { navigationFilter }
        state.selection = TextRange(1, 1)
        awaitIdle()
        field.assertProperty(1) { caretPosition }
    }

    @Test
    fun formattedFieldRejectsFilter() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                runComposeSwingTest {
                    setContent {
                        FormattedTextField(
                            value = 42,
                            onValueChange = {},
                            style = { navigationFilter(AfterThePromptFilter) },
                        )
                    }
                }
            }

        assertTrue(
            "getNavigationFilter" in failure.message.orEmpty(),
            "the message should name the formatter method that owns the filter, but was: ${failure.message}",
        )
    }
}

private const val PROMPT_LENGTH = 2

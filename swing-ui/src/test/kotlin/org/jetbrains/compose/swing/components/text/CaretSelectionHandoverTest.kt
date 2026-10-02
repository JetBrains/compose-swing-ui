package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.caret
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JTextField
import javax.swing.text.DefaultCaret
import javax.swing.text.NavigationFilter
import javax.swing.text.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CaretSelectionHandoverTest {
    @Test
    fun replacingADeclaredCaretAdoptsTheReplacementCaretsSelection() = runComposeSwingTest {
        val first = DefaultCaret()
        val second = DefaultCaret()
        var replaced by mutableStateOf(false)
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("abcdef")
            TextField(state = state, modifier = SwingModifier.caret(if (replaced) second else first))
        }
        val field = onNodeOfType<JTextField>().fetch()
        state.selection = TextRange(2, 4)
        assertEquals(
            TextRange(2, 4),
            TextRange(field.caret.mark, field.caret.dot),
            "the declared selection reaches the caret",
        )
        replaced = true
        awaitIdle()
        assertSame(second, field.caret, "the replacement declared caret must be installed")
        assertEquals(
            TextRange(0, 0),
            state.selection,
            "the state adopts the replacement caret's selection",
        )
        assertEquals(
            TextRange(0, 0),
            TextRange(field.caret.mark, field.caret.dot),
            "the replacement caret starts at offset zero",
        )
    }

    @TestFactory
    fun aReplacementCaretReportsItsNativeSelection(): List<DynamicTest> =
        listOf("setCaret", "updateUI").map { replacement ->
            dynamicTest("replacement=$replacement") {
                runComposeSwingTest {
                    lateinit var state: DocumentState
                    setContent {
                        state = rememberDocumentState("abcdef")
                        TextField(state = state)
                    }
                    val field = onNodeOfType<JTextField>().fetch()
                    state.selection = TextRange(5, 2)
                    assertEquals(
                        TextRange(5, 2),
                        TextRange(field.caret.mark, field.caret.dot),
                        "the selection must reach the original caret",
                    )
                    if (replacement == "setCaret") {
                        field.caret = DefaultCaret()
                    } else {
                        field.updateUI()
                    }
                    assertEquals(
                        TextRange(0, 0),
                        state.selection,
                        "the replacement caret's native selection is adopted",
                    )
                    assertEquals(
                        TextRange(field.caret.mark, field.caret.dot),
                        state.selection,
                        "the state reports the replacement caret's selection",
                    )
                }
            }
        }

    @Test
    fun aNavigationFilterSettlesAnAssignedSelection() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("abcdef")
            TextField(state = state)
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.navigationFilter =
            object : NavigationFilter() {
                override fun setDot(
                    fb: FilterBypass,
                    dot: Int,
                    bias: Position.Bias,
                ) = fb.setDot(dot.coerceAtMost(3), bias)

                override fun moveDot(
                    fb: FilterBypass,
                    dot: Int,
                    bias: Position.Bias,
                ) = fb.moveDot(dot.coerceAtMost(3), bias)
            }
        state.selection = TextRange(5, 1)
        assertEquals(TextRange(3, 1), state.selection, "the navigation filter settles the assigned anchor")
        assertEquals(
            state.selection,
            TextRange(field.caret.mark, field.caret.dot),
            "the filtered selection matches the caret",
        )
        field.caret = DefaultCaret()
        assertEquals(TextRange(0, 0), state.selection, "replacing a filtered caret adopts its native selection")
        state.selection = TextRange(5, 1)
        assertEquals(
            TextRange(3, 1),
            state.selection,
            "the replacement caret still settles through the navigation filter",
        )
    }

    @Test
    fun aComponentWithNoCaretReportsAnEmptySelection() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("abcdef")
            TextField(state = state)
        }
        val field = onNodeOfType<JTextField>().fetch()
        val originalCaret = field.caret
        try {
            state.selection = TextRange(5, 2)
            field.caret = null
            assertEquals(TextRange(0, 0), state.selection, "a component without a caret reports an empty selection")
            state.selection = TextRange(2, 4)
            assertEquals(TextRange(0, 0), state.selection, "a component without a caret reports an empty selection")
        } finally {
            field.caret = originalCaret
        }
    }

    @Test
    fun anUnboundComponentCannotUpdateTheStatesSelection() = runComposeSwingTest {
        lateinit var state: DocumentState
        var mounted by mutableStateOf(true)
        setContent {
            state = rememberDocumentState("abcdef")
            if (mounted) TextField(state = state)
        }
        val field = onNodeOfType<JTextField>().fetch()
        state.selection = TextRange(5, 2)
        field.caret = DefaultCaret()
        assertEquals(TextRange(0, 0), state.selection, "a mounted component reports its replacement caret")
        state.selection = TextRange(4, 2)
        mounted = false
        awaitIdle()
        field.caret = DefaultCaret()
        assertEquals(TextRange(4, 2), state.selection, "a removed component cannot change the retained state")
    }
}

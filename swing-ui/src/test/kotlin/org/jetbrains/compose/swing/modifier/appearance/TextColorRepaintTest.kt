package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural tests that a text component's declared colours reach the screen.
 *
 * A `JTextComponent` colour setter only fires a property change; the colour it stores is consumed when
 * the component paints - the caret, for one, reads it inside its own paint. So a colour that changes in
 * a recomposition is invisible until something unrelated happens to repaint the component, unless the
 * write asks for that repaint itself.
 *
 * Each case drives its colour through the real public API (a [SwingNode] over a text field, plus the
 * colour modifier re-applied across a recomposition) and observes both halves: the field carries the
 * colour it was given, and the field was asked to repaint.
 */
class TextColorRepaintTest {
    /**
     * A text field that counts the repaint requests made on it. `repaint()` is an overridden public
     * method, so counting its invocations observes the modifier's behaviour through the component's own
     * surface.
     */
    private class CountingTextField : JTextField() {
        // A JTextComponent repaints from its own constructor, before this class is initialized; the
        // count starts at zero once initialization completes, so it only ever covers requests made on a
        // built field.
        var repaintCount: Int = 0
            private set

        override fun repaint() {
            repaintCount++
            super.repaint()
        }
    }

    @Test
    fun aCaretColourChangeRepaintsTheField() = runComposeSwingTest {
        assertColourChangeRepaints(
            declare = { SwingModifier.caretColor(it) },
            read = { it.caretColor },
            property = "caretColor",
        )
    }

    @Test
    fun aSelectionColourChangeRepaintsTheField() = runComposeSwingTest {
        assertColourChangeRepaints(
            declare = { SwingModifier.selectionColor(it) },
            read = { it.selectionColor },
            property = "selectionColor",
        )
    }

    @Test
    fun aSelectedTextColourChangeRepaintsTheField() = runComposeSwingTest {
        assertColourChangeRepaints(
            declare = { SwingModifier.selectedTextColor(it) },
            read = { it.selectedTextColor },
            property = "selectedTextColor",
        )
    }

    @Test
    fun aDisabledTextColourChangeRepaintsTheField() = runComposeSwingTest {
        assertColourChangeRepaints(
            declare = { SwingModifier.disabledTextColor(it) },
            read = { it.disabledTextColor },
            property = "disabledTextColor",
        )
    }

    /**
     * Composes a text field carrying the colour [declare] builds, drives that colour to a second value
     * through a state change, and asserts the field both carries the new colour and was asked to
     * repaint for it. [read] reads the colour back off the field and [property] names it in the failure
     * message.
     */
    private suspend fun ComposeSwingTest.assertColourChangeRepaints(
        declare: (Color) -> SwingModifier,
        read: (JTextComponent) -> Color,
        property: String,
    ) {
        val field = CountingTextField()
        var colour by mutableStateOf(FirstColour)

        setContent {
            SwingNode(factory = { field }, update = { applyModifier(declare(colour)) })
        }

        awaitIdle()
        assertEquals(FirstColour, read(field), "the field should carry the declared $property")
        val repaintsBefore = field.repaintCount

        colour = SecondColour
        awaitIdle()

        assertEquals(SecondColour, read(field), "the field should carry the recomposed $property")
        assertTrue(
            field.repaintCount > repaintsBefore,
            "changing the declared $property must ask the field to repaint: count before " +
                "$repaintsBefore, after ${field.repaintCount} - the setter only fires a property " +
                "change, so without the repaint the new colour stays off the screen.",
        )
    }
}

private val FirstColour = Color(0x11, 0x22, 0x33)
private val SecondColour = Color(0xAA, 0xBB, 0xCC)

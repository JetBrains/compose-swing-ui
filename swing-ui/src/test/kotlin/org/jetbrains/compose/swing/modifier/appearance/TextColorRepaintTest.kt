package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests that a text component's declared colors reach the screen.
 *
 * A `JTextComponent` color setter only fires a property change; the color it stores is consumed when
 * the component paints - the caret, for one, reads it inside its own paint. So a color that changes in
 * a recomposition is invisible until something unrelated happens to repaint the component, unless the
 * write asks for that repaint itself.
 *
 * Each case drives its color through the real public API (a [SwingNode] over a text field, plus the
 * color modifier re-applied across a recomposition) and observes both halves: the field carries the
 * color it was given, and the field was asked to repaint.
 */
class TextColorRepaintTest {
    /**
     * A text field that counts the repaint requests made on it. `repaint()` is an overridden public
     * method, so counting its invocations observes the modifier's behavior through the component's own
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
    fun aCaretColorChangeRepaintsTheField() = runComposeSwingTest {
        assertColorChangeRepaints(
            declare = { SwingModifier.caretColor(it) },
            read = { it.caretColor },
            property = "caretColor",
        )
    }

    @Test
    fun aSelectionColorChangeRepaintsTheField() = runComposeSwingTest {
        assertColorChangeRepaints(
            declare = { SwingModifier.selectionColor(it) },
            read = { it.selectionColor },
            property = "selectionColor",
        )
    }

    @Test
    fun aSelectedTextColorChangeRepaintsTheField() = runComposeSwingTest {
        assertColorChangeRepaints(
            declare = { SwingModifier.selectedTextColor(it) },
            read = { it.selectedTextColor },
            property = "selectedTextColor",
        )
    }

    @Test
    fun aDisabledTextColorChangeRepaintsTheField() = runComposeSwingTest {
        assertColorChangeRepaints(
            declare = { SwingModifier.disabledTextColor(it) },
            read = { it.disabledTextColor },
            property = "disabledTextColor",
        )
    }

    /**
     * Composes a text field carrying the color [declare] builds, drives that color to a second value
     * through a state change, and asserts the field both carries the new color and was asked to
     * repaint for it. [read] reads the color back off the field and [property] names it in the failure
     * message.
     */
    private suspend fun ComposeSwingTest.assertColorChangeRepaints(
        declare: (Color) -> SwingModifier,
        read: (JTextComponent) -> Color,
        property: String,
    ) {
        val field = CountingTextField()
        var color by mutableStateOf(FirstColor)

        setContent {
            SwingNode(factory = { field }, modifier = declare(color))
        }

        awaitIdle()
        assertEquals(FirstColor, read(field), "the field should carry the declared $property")
        val repaintsBefore = field.repaintCount

        color = SecondColor
        awaitIdle()

        assertEquals(SecondColor, read(field), "the field should carry the recomposed $property")
        assertTrue(
            field.repaintCount > repaintsBefore,
            "changing the declared $property must ask the field to repaint: count before " +
                "$repaintsBefore, after ${field.repaintCount} - the setter only fires a property " +
                "change, so without the repaint the new color stays off the screen.",
        )
    }
}

private val FirstColor = Color(0x11, 0x22, 0x33)
private val SecondColor = Color(0xAA, 0xBB, 0xCC)

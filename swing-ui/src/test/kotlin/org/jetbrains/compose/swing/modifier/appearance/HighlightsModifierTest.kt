package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import java.awt.Color
import javax.swing.JTextArea
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the `SwingModifier.highlights` markup seam. Each case reads the marks back
 * off the live area's highlighter - the same place a repaint reads them from - and asserts what a
 * reader would see: the declared ranges are painted, a changed declaration replaces the marks the
 * previous one left instead of piling onto them, marks made outside the declaration survive, and
 * dropping the modifier leaves the area unmarked.
 */
class HighlightsModifierTest {
    @Test
    fun declaredRangesArePainted() = runComposeSwingTest {
        setContent {
            TextArea(
                value = TEXT,
                onValueChange = {},
                modifier = SwingModifier.highlights(listOf(TextRange(0, 5), TextRange(6, 11)), Painter),
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()

        assertEquals(listOf(0 to 5, 6 to 11), area.paintedSpans(), "both declared ranges should be painted")
    }

    @Test
    fun aChangedDeclarationReplacesTheMarksItLeft() = runComposeSwingTest {
        var ranges by mutableStateOf(listOf(TextRange(0, 5)))
        setContent {
            TextArea(value = TEXT, onValueChange = {}, modifier = SwingModifier.highlights(ranges, Painter))
        }
        val area = onNodeOfType<JTextArea>().fetch()
        assertEquals(listOf(0 to 5), area.paintedSpans(), "the first declaration should be painted")

        ranges = listOf(TextRange(6, 11))
        awaitIdle()

        assertEquals(
            listOf(6 to 11),
            area.paintedSpans(),
            "the new declaration should replace the previous marks rather than accumulate onto them",
        )
    }

    @Test
    fun aDeclaredStateListIsFollowedWhenTheCallerMutatesIt() = runComposeSwingTest {
        val ranges = mutableStateListOf(TextRange(0, 5))
        setContent {
            TextArea(value = TEXT, onValueChange = {}, modifier = SwingModifier.highlights(ranges, Painter))
        }
        val area = onNodeOfType<JTextArea>().fetch()
        assertEquals(listOf(0 to 5), area.paintedSpans(), "the first declaration should be painted")

        ranges.add(TextRange(6, 11))
        awaitIdle()

        assertEquals(
            listOf(0 to 5, 6 to 11),
            area.paintedSpans(),
            "a range added to the declared list should be painted",
        )
    }

    @Test
    fun aRangeIsPaintedWhicheverWayRoundItsOffsetsAre() = runComposeSwingTest {
        setContent {
            TextArea(
                value = TEXT,
                onValueChange = {},
                modifier = SwingModifier.highlights(listOf(TextRange(11, 6)), Painter),
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()

        assertEquals(listOf(6 to 11), area.paintedSpans(), "a reversed range should paint the span it covers")
    }

    @Test
    fun aRangeBeyondTheTextPaintsAsFarAsTheTextGoes() = runComposeSwingTest {
        setContent {
            TextArea(
                value = TEXT,
                onValueChange = {},
                modifier = SwingModifier.highlights(listOf(TextRange(6, 400)), Painter),
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()

        assertEquals(
            listOf(6 to TEXT.length),
            area.paintedSpans(),
            "a range running past the document should be clamped to it",
        )
    }

    @Test
    fun marksMadeOutsideTheDeclarationSurviveIt() = runComposeSwingTest {
        var ranges by mutableStateOf(listOf(TextRange(0, 5)))
        setContent {
            TextArea(value = TEXT, onValueChange = {}, modifier = SwingModifier.highlights(ranges, Painter))
        }
        val area = onNodeOfType<JTextArea>().fetch()
        // A mark another party painted straight onto the highlighter, outside any modifier chain.
        area.highlighter.addHighlight(2, 4, ForeignPainter)

        ranges = listOf(TextRange(6, 11))
        awaitIdle()

        assertEquals(listOf(6 to 11), area.paintedSpans(), "the declaration should own only its own marks")
        assertTrue(
            area.highlighter.highlights.any { it.painter === ForeignPainter },
            "a mark painted outside the declaration should survive a redeclaration",
        )
    }

    @Test
    fun droppingTheModifierUnmarksTheArea() = runComposeSwingTest {
        var marked by mutableStateOf(true)
        setContent {
            TextArea(
                value = TEXT,
                onValueChange = {},
                modifier = if (marked) SwingModifier.highlights(listOf(TextRange(0, 5)), Painter) else SwingModifier,
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()
        assertEquals(listOf(0 to 5), area.paintedSpans(), "the declaration should be painted while present")

        marked = false
        awaitIdle()

        assertEquals(emptyList(), area.paintedSpans(), "leaving the chain should take the declaration's marks away")
    }

    @Test
    fun anEmptyDeclarationPaintsNothing() = runComposeSwingTest {
        var ranges by mutableStateOf(listOf(TextRange(0, 5)))
        setContent {
            TextArea(value = TEXT, onValueChange = {}, modifier = SwingModifier.highlights(ranges, Painter))
        }
        val area = onNodeOfType<JTextArea>().fetch()
        assertEquals(listOf(0 to 5), area.paintedSpans(), "the declaration should be painted while it has a range")

        ranges = emptyList()
        awaitIdle()

        assertEquals(emptyList(), area.paintedSpans(), "declaring no ranges should leave the area unmarked")
    }

    @Test
    fun aChangedPainterRepaintsTheSameRanges() = runComposeSwingTest {
        val ranges = listOf(TextRange(0, 5))
        var painter by mutableStateOf<Highlighter.HighlightPainter>(Painter)
        setContent {
            TextArea(value = TEXT, onValueChange = {}, modifier = SwingModifier.highlights(ranges, painter))
        }
        val area = onNodeOfType<JTextArea>().fetch()
        assertEquals(listOf(0 to 5), area.paintedSpans(), "the first painter should paint the declared range")

        painter = ForeignPainter
        awaitIdle()

        assertEquals(listOf(0 to 5), area.paintedSpans(ForeignPainter), "the new painter should paint the same range")
        assertEquals(emptyList(), area.paintedSpans(), "the previous painter's mark should be gone")
    }

    @Test
    fun aDeclarationSurvivesBindingToADocumentState() = runComposeSwingTest {
        setContent {
            val source = rememberDocumentState(TEXT)
            TextArea(state = source, modifier = SwingModifier.highlights(listOf(TextRange(0, 5)), Painter))
        }
        val area = onNodeOfType<JTextArea>().fetch()

        assertEquals(listOf(0 to 5), area.paintedSpans(), "the declared range should paint over the state's document")
    }

    /** The spans [painter] currently marks on this component, in the order the highlighter holds them. */
    private fun JTextComponent.paintedSpans(painter: Highlighter.HighlightPainter = Painter): List<Pair<Int, Int>> =
        highlighter.highlights
            .filter { it.painter === painter }
            .map { it.startOffset to it.endOffset }
}

private const val TEXT = "hello world"

private val Painter = DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
private val ForeignPainter = DefaultHighlighter.DefaultHighlightPainter(Color.PINK)

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.core.TracedTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSlider
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That a declaration settled against the value a widget holds is written once for each apply pass that
 * carries it, however many of the inputs it follows moved.
 *
 * What the composition declares and where the user left the widget move independently, and a component
 * follows each of them apiece, so a pass can reach the settle from either or from both. The frames are
 * driven by the test, which is what makes the passes countable: each frame carries one, and a raw change
 * listener hears every value the slider takes - the writes the settle makes included.
 *
 * A move is published to the composition by the idle gate, which sends no frame of its own while the test
 * drives them, so the frame that follows is the apply pass that carries the move.
 *
 * Each case states how many passes it cost and what each spent them on, because that is where a settle
 * can go wrong without the widget ever holding the wrong value. A settle that stopped recognizing its own
 * write would take the widget's echo of it for the user's, re-dirtying the mirror on every pass and never
 * converging - a slider that reads correctly forever while the pipeline turns for nothing. The settlement
 * is named in the trace, so a pass that settled the slider is told from one that merely ran.
 */
class SettleOncePerApplyPassTest : TracedTest() {
    @Test
    fun aDeclarationAndAWidgetMoveArrivingInOneFrameAreSettledByOneWrite() = runComposeSwingTest {
        val values = mutableListOf<Int>()
        // Attached as-is and never rebuilt, so it stays on the slider across every pass below.
        val recorder = ChangeListener { event -> values += (event.source as JSlider).value }
        var declared by mutableIntStateOf(DECLARED)
        setContent {
            Slider(value = declared, changeListener = recorder, min = MIN, max = MAX)
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(DECLARED, slider.value, "the slider should open on what the composition declares")
        assertEquals(
            emptyList(),
            values,
            "the first pass settles the slider before it attaches the listener, so that write is not heard",
        )

        // Both inputs move before the frame that carries them: the user's move and a declaration of its
        // own reach the slider in a single apply pass.
        slider.value = MOVED
        declared = REDECLARED
        tracer.clear()
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(MOVED, REDECLARED),
            values,
            "the pass carrying both moves should write the declaration once",
        )
        assertEquals(REDECLARED, slider.value, "the slider should hold the value that pass settled it on")
        assertEquals(
            listOf(listOf("settle")),
            tracer.passes(),
            "the frame the test drove should carry exactly one pass, settling the slider once: a settle " +
                "writes a property rather than rebuilding the slider: ${tracer.sections}",
        )
    }

    @Test
    fun theSameDivergenceSplitAcrossTwoFramesIsSettledTwice() = runComposeSwingTest {
        val values = mutableListOf<Int>()
        val recorder = ChangeListener { event -> values += (event.source as JSlider).value }
        var declared by mutableIntStateOf(DECLARED)
        setContent {
            Slider(value = declared, changeListener = recorder, min = MIN, max = MAX)
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(DECLARED, slider.value, "the slider should open on what the composition declares")

        // The user's move alone reaches the first pass, so it settles against the standing declaration.
        slider.value = MOVED
        tracer.clear()
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(MOVED, DECLARED),
            values,
            "a move the declaration does not adopt should be written back by the pass that follows it",
        )
        assertEquals(DECLARED, slider.value, "the slider should be back on the declaration")
        assertEquals(
            listOf(listOf("settle")),
            tracer.passes(),
            "the first driven frame should carry one pass, settling the slider and changing no " +
                "container: ${tracer.sections}",
        )

        // The new declaration reaches a pass of its own, which settles the widget a second time.
        declared = REDECLARED
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(MOVED, DECLARED, REDECLARED),
            values,
            "each pass should settle the slider apiece, so the split move passes through the declaration",
        )
        assertEquals(REDECLARED, slider.value, "the slider should hold the value the second pass settled it on")
        assertEquals(
            listOf(listOf("settle"), listOf("settle")),
            tracer.passes(),
            "each driven frame should carry a pass of its own, settling the slider once apiece, which is " +
                "what makes the split settle two: ${tracer.sections}",
        )
    }

    @Test
    fun aDeclarationTheSliderCoercesSettlesRatherThanOscillating() = runComposeSwingTest {
        var declared by mutableIntStateOf(DECLARED)
        setContent {
            Slider(value = declared, changeListener = {}, min = MIN, max = MAX)
        }
        awaitIdle()

        val slider = onNodeOfType<JSlider>().fetch()
        tracer.clear()

        // Out of the slider's range, so what the widget holds after the write is not what was declared:
        // the settle has to absorb the coerced value rather than declare its way back to it.
        declared = BEYOND_MAX
        awaitIdle()

        assertEquals(MAX, slider.value, "the slider should hold the value it coerced the declaration to")
        assertEquals(
            listOf(listOf("settle")),
            tracer.passes(),
            "a declaration settled against the widget costs the one pass that writes it and reads the " +
                "answer back, coerced or not: the mirror knows the widget's echo of its own write and " +
                "buys no further pass to absorb it: ${tracer.sections}",
        )
        tracer.clear()
        awaitIdle()

        assertEquals(
            emptyList(),
            tracer.passes(),
            "and then it converges: a settle that did not recognize its own write would re-dirty the " +
                "mirror forever while the slider read correctly throughout: ${tracer.sections}",
        )
    }

    private companion object {
        const val MIN = 0
        const val MAX = 100

        /** What the composition declares, and goes on declaring while the user moves the slider away from it. */
        const val DECLARED = 40

        /** Where the user leaves the slider, away from the declaration. */
        const val MOVED = 70

        /** The declaration that replaces [DECLARED]. */
        const val REDECLARED = 20

        /** A declaration past the slider's range, which the widget answers with [MAX]. */
        const val BEYOND_MAX = 150
    }
}

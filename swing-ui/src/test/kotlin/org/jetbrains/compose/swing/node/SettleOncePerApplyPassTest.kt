package org.jetbrains.compose.swing.node

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Slider
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
 */
class SettleOncePerApplyPassTest {
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
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(MOVED, REDECLARED),
            values,
            "the pass carrying both moves should write the declaration once",
        )
        assertEquals(REDECLARED, slider.value, "the slider should hold the value that pass settled it on")
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
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(MOVED, DECLARED),
            values,
            "a move the declaration does not adopt should be written back by the pass that follows it",
        )
        assertEquals(DECLARED, slider.value, "the slider should be back on the declaration")

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
    }
}

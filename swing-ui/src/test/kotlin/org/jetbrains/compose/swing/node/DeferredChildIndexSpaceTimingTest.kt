package org.jetbrains.compose.swing.node

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.core.TracedTest
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import javax.swing.JSplitPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * When the child-index-space walk answers for a child that moved from one region of its host to another:
 * on the turn of the event queue after the pass that moved it, never on the pass itself.
 *
 * The test drives the frames, so the whole move lands in a single apply pass and the walk has one
 * settled state to answer for. That is what the wait buys: while the pass runs the child names its new
 * region and stands in neither, a state the composition allows and the walk would refuse. A divergence
 * that outlives the pass is refused, and the tree the refusal names is the one the pass finished leaving.
 *
 * That the move really is one pass is stated rather than assumed: the sides a split pane holds read the
 * same whether one pass carried the move or three did, and a move split across passes would give the walk
 * a torn-down state to answer for.
 */
class DeferredChildIndexSpaceTimingTest : TracedTest() {
    @Test
    fun aChildMovedBetweenRegionsIsAnsweredForInTheRegionThePassLeftItIn() = runComposeSwingTest {
        var onFirstSide by mutableStateOf(true)
        setContent {
            SplitPane {
                Label(
                    text = "Movable",
                    modifier = if (onFirstSide) SwingModifier.first() else SwingModifier.second(),
                )
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val pane = onNodeOfType<JSplitPane>().fetch()
        val movable = onNodeWithText("Movable").fetch()
        assertSame(movable, pane.leftComponent, "the child should start on the side it first names")

        // The idle gate publishes the write and leaves the recomposition it invalidates parked at the
        // frame it is waiting for, without sending one. The frame that follows then carries the whole
        // move and the turn the pass schedules the walk on, so the walk reads the child where the pass
        // left it rather than mid-move.
        onFirstSide = false
        tracer.clear()
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertSame(movable, pane.rightComponent, "the child should stand on the side it now names")
        assertNull(pane.leftComponent, "the side the child left should be empty")
        assertEquals(
            listOf(emptyList()),
            tracer.passes(),
            "the whole move should land in one pass, and a region restated at the same host is settled " +
                "as the pass ends rather than by taking the child out and putting it back: ${tracer.sections}",
        )

        tracer.clear()
        mainClock.advanceTimeByFrame()

        assertEquals(
            emptyList(),
            tracer.passes(),
            "the move needs no successor pass, so the walk has only the one settled state to answer for: " +
                "${tracer.sections}",
        )
    }

    @Test
    fun aDivergenceOutlivingTheMoveIsRefusedOverTheTreeThePassFinishedLeaving() = runComposeSwingTest {
        var onFirstSide by mutableStateOf(true)
        setContent {
            SplitPane {
                BoxPanel(
                    modifier = (if (onFirstSide) SwingModifier.first() else SwingModifier.second()).testTag(HOST),
                ) {
                    Label(text = "held")
                }
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val pane = onNodeOfType<JSplitPane>().fetch()
        val panel = onNodeWithTag(HOST).fetch<JComponent>()

        // Take a child out from under the applier: its children list still holds the label, the panel no
        // longer does. The walk the moving pass schedules is what tells this test about that.
        panel.remove(0)
        onFirstSide = false
        // The move is left parked at the frame gate, so the frame below carries it whole - see the first
        // test for what the gate settles and what the frame is left to do.
        awaitIdle()

        val failure = assertFailsWith<IllegalStateException> { mainClock.advanceTimeByFrame() }

        assertSame(
            panel,
            pane.rightComponent,
            "the move should have run whole before the walk answered for what it left",
        )
        assertTrue(
            failure.message.orEmpty().contains("the applier's children list has for it"),
            "the failure should name the child the panel no longer holds: ${failure.message}",
        )
    }

    private companion object {
        const val HOST = "host-under-test"
    }
}

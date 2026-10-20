package org.jetbrains.compose.swing.samples.agent.shell

import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performClick
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PaneAnimationTest {
    @Test
    fun sessionsExpandFromTheLeadingEdgeAndMoveTheTranscript() =
        runAgentTest(rootSize = windowSize) {
            val settings = settingsStateOf()
            settings.setReduceMotion(false)
            showApp(seededState(), settings = settings)
            val fullWidth = paneBounds(onNodeWithTag("new-session").fetch<JComponent>()).width
            mainClock.autoAdvance = false
            onNodeWithTag("sessions-toggle").performClick()
            mainClock.advanceTimeBy(1.seconds)
            onNodeWithTag("new-session").assertDoesNotExist()

            onNodeWithTag("sessions-toggle").performClick()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(96.milliseconds)
            val pane = paneBounds(onNodeWithTag("new-session").fetch<JComponent>())
            val transcript = transcriptBounds()
            assertTrue(pane.width in 1 until fullWidth, "visible sessions width: ${pane.width}, full: $fullWidth")
            assertEquals(0, pane.x, "sessions stay at the leading edge")
            assertEquals(pane.x + pane.width, transcript.x, "transcript follows the visible sessions edge")
        }

    @Test
    fun contextExpandsFromTheTrailingEdgeAndMovesTheTranscript() =
        runAgentTest(rootSize = windowSize) {
            val settings = settingsStateOf()
            settings.setReduceMotion(false)
            showApp(seededState(), settings = settings)
            val fullWidth = paneBounds(onNodeWithText("CONTEXT").fetch<JComponent>()).width
            mainClock.autoAdvance = false
            onNodeWithTag("context-toggle").performClick()
            mainClock.advanceTimeBy(1.seconds)
            onNodeWithText("CONTEXT").assertDoesNotExist()

            onNodeWithTag("context-toggle").performClick()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(96.milliseconds)
            val pane = paneBounds(onNodeWithText("CONTEXT").fetch<JComponent>())
            val transcript = transcriptBounds()
            assertTrue(pane.width in 1 until fullWidth, "visible context width: ${pane.width}, full: $fullWidth")
            assertEquals(root.width, pane.x + pane.width, "context stays at the trailing edge")
            assertEquals(pane.x, transcript.x + transcript.width, "transcript follows the visible context edge")
        }

    private fun ComposeSwingTest.paneBounds(anchor: JComponent): Rectangle {
        val transcript = onNodeWithTag("transcript").fetch<Component>()
        var pane = anchor
        while (!SwingUtilities.isDescendingFrom(transcript, pane.parent)) {
            pane = pane.parent as JComponent
        }
        val visible = pane.visibleRect
        val y = visible.y + visible.height / 2
        val columns = (visible.x until visible.x + visible.width).filter { pane.contains(it, y) }
        assertTrue(columns.isNotEmpty(), "the opening pane has visible content")
        val bounds = Rectangle(columns.first(), visible.y, columns.last() - columns.first() + 1, visible.height)
        return SwingUtilities.convertRectangle(pane, bounds, root)
    }

    private fun ComposeSwingTest.transcriptBounds(): Rectangle {
        val transcript = onNodeWithTag("transcript").fetch<Component>()
        return SwingUtilities.convertRectangle(transcript.parent, transcript.bounds, root)
    }
}

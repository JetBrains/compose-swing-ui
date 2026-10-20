package org.jetbrains.compose.swing.samples.agent.transcript

import org.jetbrains.compose.swing.samples.agent.openSession
import org.jetbrains.compose.swing.samples.agent.runScreenshotTest
import org.jetbrains.compose.swing.samples.agent.saveScreenshot
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.samples.agent.shell.seededState
import org.jetbrains.compose.swing.samples.agent.shell.showApp
import org.jetbrains.compose.swing.samples.agent.shell.windowSize
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.AssistantText
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.Failure
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.Thinking
import org.jetbrains.compose.swing.samples.agent.state.Turn
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TranscriptScreenshotTest {
    @Test
    fun narrowTranscriptWithLongLines() = longLinesScreenshot(600)

    @Test
    fun wideTranscriptWithLongLines() = longLinesScreenshot(windowSize.width)

    private fun longLinesScreenshot(width: Int) =
        runScreenshotTest(rootSize = Dimension(width, 1000)) {
            val frames = List(11) { "at stack.frame.$it(${"source".repeat(30)})" }
            val detail = (listOf("x".repeat(300)) + frames).joinToString("\n")
            showTranscript(
                listOf(
                    Turn(
                        "Keep this prompt visible",
                        listOf(Failure(detail), AssistantText("```\n${"value".repeat(100)}\n```")),
                    ),
                ),
            )
            awaitIdle()
            val prompt = onNodeWithText("Keep this prompt visible").fetch<Component>()
            val pane = onNodeWithTag("transcript").fetch<JScrollPane>()
            val bounds = SwingUtilities.convertRectangle(prompt.parent, prompt.bounds, pane.viewport)
            assertTrue(Rectangle(pane.viewport.extentSize).contains(bounds))
            val painted = captureToImage()
            val promptBounds = SwingUtilities.convertRectangle(prompt.parent, prompt.bounds, root)
            assertNotEquals(painted.getRGB(0, 0), painted.getRGB(promptBounds.x + 5, promptBounds.y + 5))
            saveScreenshot("transcript-long-lines-$width")
        }

    @Test
    fun transcriptScreenshots() =
        // Tall enough that the transcript does not scroll: a scrolled pane is reset when the test disposes the content,
        // which the modifier restore check reports.
        runScreenshotTest(rootSize = Dimension(windowSize.width, windowSize.height + 240)) {
            val state = seededState()
            val settings = settingsStateOf()
            state.openSession.turns +=
                Turn(
                    "Now cover the toggle with a test.",
                    listOf(Thinking("A UI test can flip the switch and read the theme back.")),
                    isRunning = true,
                )
            showApp(state, settings = settings)
            saveScreenshot("transcript-dark")

            settings.selectTheme(ThemeMode.Light)
            awaitIdle()
            // FlatLaf.updateUI reaches every window; the harness root stands in none.
            SwingUtilities.updateComponentTreeUI(root)
            saveScreenshot("transcript-light")

            settings.selectTheme(ThemeMode.Dark)
            awaitIdle()
            SwingUtilities.updateComponentTreeUI(root)
            settings.setRightToLeft(true)
            saveScreenshot("transcript-rtl")
        }
}

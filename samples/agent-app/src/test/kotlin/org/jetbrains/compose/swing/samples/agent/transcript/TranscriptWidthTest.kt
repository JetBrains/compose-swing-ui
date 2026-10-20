package org.jetbrains.compose.swing.samples.agent.transcript

import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.state.ToolStatus
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.state.Turn
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performClick
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptWidthTest {
    @Test
    fun aLongFailureFitsTheViewportAndKeepsItsDetailsSelectable() =
        runAgentTest(rootSize = Dimension(600, 1000)) {
            val summary = "x".repeat(300)
            val frames = List(11) { "at stack.frame.$it(${"source".repeat(30)})" }
            val detail = (listOf(summary) + frames).joinToString("\n")
            showTranscript(listOf(Turn("Keep this prompt visible", listOf(TranscriptItem.Failure(detail)))))

            assertInsideViewport()
            val card = onNodeWithTag("error-card").fetch<Container>()
            val labels = descendants(card).filterIsInstance<JLabel>()
            val heading = labels.single { it.text == summary }
            assertTrue(heading.width > 0 && heading.getFontMetrics(heading.font).stringWidth(summary) > heading.width)
            val text = descendants(card).filterIsInstance<JTextArea>().single()
            assertEquals(detail.substringAfter('\n'), text.text)
            assertFalse(text.isEditable)
            assertTrue(text.lineWrap && text.wrapStyleWord)
            text.selectAll()
            assertEquals(detail.substringAfter('\n'), text.selectedText)
        }

    @Test
    fun aLongToolOutputFitsTheViewport() =
        runAgentTest(rootSize = Dimension(600, 1000)) {
            val call =
                TranscriptItem.ToolCall(
                    "tool",
                    "bash",
                    "command".repeat(50),
                    "output".repeat(100),
                    ToolStatus.Done,
                )
            showTranscript(listOf(Turn("Keep this prompt visible", listOf(call))))
            onNodeWithTag("tool-call-header").performClick()
            awaitIdle()

            assertInsideViewport()
            val output = onNodeWithText(call.output!!).fetch<JTextArea>()
            assertTrue(output.lineWrap && output.wrapStyleWord)
        }

    @Test
    fun aLongMarkdownCodeLineFitsTheViewport() =
        runAgentTest(rootSize = Dimension(600, 1000)) {
            val code = "value".repeat(100)
            showTranscript(
                listOf(Turn("Keep this prompt visible", listOf(TranscriptItem.AssistantText("```\n$code\n```")))),
            )

            assertInsideViewport()
            val panes = descendants(root).filterIsInstance<JScrollPane>()
            val codePane = panes.single { it !== onNodeWithTag("transcript").fetch<JScrollPane>() }
            assertTrue(codePane.horizontalScrollBar.isVisible, "wide code must be scrollable inside its card")
            codePane.horizontalScrollBar.value = codePane.horizontalScrollBar.maximum
            assertTrue(codePane.viewport.viewPosition.x > 0)
        }

    private fun ComposeSwingTest.assertInsideViewport() {
        val viewport = onNodeWithTag("transcript").fetch<JScrollPane>().viewport
        assertEquals(viewport.extentSize.width, viewport.view.width, "the transcript must track the viewport width")
        assertEquals(0, viewport.viewPosition.x)
        assertTrue(viewport.view.height >= viewport.height, "a short transcript must fill the viewport background")
        for (component in descendants(viewport.view as Container)) {
            val ancestors = generateSequence(component.parent) { it.parent }.takeWhile { it !== viewport }
            if (ancestors.any { it is JScrollPane }) {
                continue
            }
            val bounds = SwingUtilities.convertRectangle(component.parent, component.bounds, viewport)
            assertTrue(
                bounds.x >= 0 && bounds.x + bounds.width <= viewport.width,
                "${component.javaClass.simpleName}: $bounds outside ${viewport.width}",
            )
        }
        val prompt = onNodeWithText("Keep this prompt visible").fetch<Component>()
        val promptBounds = SwingUtilities.convertRectangle(prompt.parent, prompt.bounds, viewport)
        assertTrue(
            prompt.width > 0 && Rectangle(viewport.extentSize).contains(promptBounds),
            "the user prompt must be fully visible",
        )
    }

    private fun descendants(parent: Container): List<Component> =
        parent.components.flatMap { listOf(it) + if (it is Container) descendants(it) else emptyList() }
}

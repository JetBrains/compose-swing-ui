package org.jetbrains.compose.swing.samples.agent.transcript.markdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.text.html.HTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarkdownViewTest {
    @Test
    fun imageMarkupShowsOnlyItsAltText() =
        runAgentTest {
            showMarkdown({ "![Диаграмма & *shape*](https://example.com/image.png)" })
            val pane = markdownPane()
            assertEquals("Диаграмма & shape", pane.document.getText(0, pane.document.length).trim())
        }

    @Test
    fun imagesAndRawHtmlAreOnlyText() =
        runAgentTest {
            showMarkdown(
                {
                    "![diagram](https://example.com/image.png)\n\n" +
                        "<img src=\"file:///secret\">\n\nInline <b>literal</b>."
                },
            )
            val pane = markdownPane()
            assertEquals(emptyList(), pane.textsOf(HTML.Tag.IMG))
            val text = pane.document.getText(0, pane.document.length)
            assertTrue(text.contains("diagram"), text)
            assertTrue(text.contains("<img src=\"file:///secret\">"), text)
            assertTrue(text.contains("<b>literal</b>"), text)
        }

    @Test
    fun fileLinksNeverActivate() =
        runAgentTest {
            val activated = mutableListOf<String>()
            showMarkdown({ "Read [local file](file:///secret)." }, onLinkActivate = { activated += it })
            val pane = markdownPane()
            val text = pane.document.getText(0, pane.document.length)
            val link = pane.modelToView2D(text.indexOf("local"))
            onNodeOfType<JEditorPane>().performClick(Point(link.centerX.toInt(), link.centerY.toInt()))
            assertEquals(emptyList(), activated)
        }

    @Test
    fun proseKeepsTheTranscriptBackground() =
        runAgentTest {
            showMarkdown({ sampleMarkdown })
            val pane = markdownPane()
            val scroll = javax.swing.SwingUtilities.getAncestorOfClass(JScrollPane::class.java, pane) as JScrollPane
            for (component in listOf<JComponent>(pane, scroll.viewport, scroll)) {
                assertTrue(!component.isOpaque, "${component.javaClass.simpleName} must show the transcript background")
            }
        }

    @Test
    fun headingsListsCodeBlocksAndTablesRenderAsHtml() =
        runAgentTest {
            showMarkdown({ sampleMarkdown })
            val pane = markdownPane()
            assertEquals(listOf("Sync test fix"), pane.textsOf(HTML.Tag.H1))
            assertEquals(listOf("Steps"), pane.textsOf(HTML.Tag.H2))
            assertTrue(pane.textsOf(HTML.Tag.PRE).single().startsWith("@Test"))
            assertEquals(listOf("File", "Added", "Removed"), pane.textsOf(HTML.Tag.TH))
            assertEquals(listOf("SyncTest.kt", "12", "4", "Sync.kt", "3", "1"), pane.textsOf(HTML.Tag.TD))
            assertEquals(3, pane.textsOf(HTML.Tag.LI).size)
            assertEquals(0, pane.componentCount, "the task box is a character, not an embedded check box")
        }

    @Test
    fun taskItemsStartWithABoxAndCarryTheirState() =
        runAgentTest {
            showMarkdown({ "- [x] Replace the sleep\n- [ ] Watch CI\n- Plain item" })
            val pane = markdownPane()

            assertEquals(listOf("☑ Replace the sleep", "☐ Watch CI", "Plain item"), pane.textsOf(HTML.Tag.LI))
            assertEquals(listOf("done", "task", null), pane.classesOf(HTML.Tag.LI))
            assertEquals(0, pane.componentCount, "the task box is a character, not an embedded check box")
        }

    @Test
    fun streamedTextGrowsTheSamePane() =
        runAgentTest {
            var markdown by mutableStateOf("The agent starts to answer.")
            showMarkdown({ markdown })
            val pane = markdownPane()
            val oneLine = pane.height

            repeat(4) {
                markdown += "\n\nAnother paragraph streams in, long enough to need a line of its own."
                awaitIdle()
            }
            assertSame(pane, markdownPane(), "streaming replaces the text, not the pane")
            assertTrue(pane.height > oneLine * 4, "height ${pane.height} grows with five paragraphs from $oneLine")
        }

    @Test
    fun aNarrowerWidthWrapsIntoATallerView() =
        runAgentTest {
            var width by mutableStateOf(560)
            showMarkdown({ sampleMarkdown.lines().take(3).joinToString("\n") }, width = { width })
            val pane = markdownPane()
            val wide = pane.size

            width = 280
            awaitIdle()
            assertEquals(280 - 2 * 16, pane.width, "the pane fills the card inside its padding")
            assertTrue(pane.height > wide.height, "height ${pane.height} at the narrow width exceeds ${wide.height}")
            assertEquals(pane.preferredSize.height, pane.height, "the pane is as tall as its wrapped text")
        }

    @Test
    fun clickingALinkActivatesItsHref() =
        runAgentTest {
            val activated = mutableListOf<String>()
            showMarkdown({ "Read [the guide](https://example.com/guide) first." }, onLinkActivate = { activated += it })
            val pane = markdownPane()
            val text = pane.document.getText(0, pane.document.length)
            val link = pane.modelToView2D(text.indexOf("guide"))

            onNodeOfType<JEditorPane>().performClick(Point(link.centerX.toInt(), link.centerY.toInt()))
            assertEquals(listOf("https://example.com/guide"), activated)
        }
}

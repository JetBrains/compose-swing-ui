package org.jetbrains.compose.swing.samples.agent.transcript.cards

import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.samples.agent.theme.readAgentTheme
import org.jetbrains.compose.swing.samples.agent.transcript.DiffView
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performKeyPress
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.KeyEvent.VK_ENTER
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DiffViewTest {
    @Test
    fun largeDiffsPreviewTwoHundredLinesAndCanExpand() =
        runAgentTest {
            val diff = "@@ -0,0 +1,300 @@\n" + (1..300).joinToString("\n") { "+line $it" }
            showDiff(diff)
            onNodeWithText("line 199").assertExists()
            onNodeWithText("line 200").assertDoesNotExist()
            onNodeWithText("Show all", substring = true).performClick()
            onNodeWithText("line 300").assertExists()
            onNodeWithText("Show less").performKeyPress(VK_ENTER)
            onNodeWithText("line 300").assertDoesNotExist()
        }

    @Test
    fun longDiffLinesScrollInsideTheDarkCard() = longDiffLinesScrollInsideTheCard(ThemeMode.Dark)

    @Test
    fun longDiffLinesScrollInsideTheLightCard() = longDiffLinesScrollInsideTheCard(ThemeMode.Light)

    private fun longDiffLinesScrollInsideTheCard(theme: ThemeMode) =
        runAgentTest(rootSize = Dimension(360, 600), theme = theme) {
            root.layout = BorderLayout()
            val line = "long content ".repeat(100)
            showDiff("@@ -1 +1 @@\n-previous\n+$line")
            val label = onNodeWithText(line).fetch<JLabel>()
            val scroll =
                SwingUtilities.getAncestorOfClass(
                    JScrollPane::class.java,
                    label,
                ) as? JScrollPane
            assertTrue(scroll != null, "the diff has its own horizontal scroll pane")
            assertTrue(scroll.horizontalScrollBar.isVisible)
            assertTrue(label.width >= label.preferredSize.width)
            val lastRow = SwingUtilities.convertRectangle(label.parent, label.bounds, scroll.viewport.view)
            assertTrue(
                lastRow.y + lastRow.height <= scroll.viewport.height,
                "the horizontal bar must not cover the last line",
            )
        }

    @Test
    fun theDiffTintsAddedAndRemovedLinesApart() =
        runAgentTest {
            val diff = "--- a/f.kt\n+++ b/f.kt\n@@ -1,2 +1,2 @@\n kept line\n-removed line\n+added line"
            showDiff(diff)
            val added = lineTint("added line")
            val removed = lineTint("removed line")
            val kept = lineTint("kept line")
            assertNotEquals(added, removed)
            assertNotEquals(kept, added)
            assertNotEquals(kept, removed)
            assertTrue(
                added.green - added.red > removed.green - removed.red,
                "added $added is greener than removed $removed",
            )
        }

    private fun ComposeSwingTest.showDiff(diff: String) {
        setContent {
            CompositionLocalProvider(LocalAgentTheme provides readAgentTheme()) {
                Column { DiffView(diff) }
            }
        }
    }

    /** The color at the trailing end of the diff row showing [text], where no glyph is painted. */
    private fun ComposeSwingTest.lineTint(text: String): Color {
        val row = onNodeWithText(text).onParent().captureToImage()
        return Color(row.getRGB(row.width - 2, row.height / 2))
    }
}

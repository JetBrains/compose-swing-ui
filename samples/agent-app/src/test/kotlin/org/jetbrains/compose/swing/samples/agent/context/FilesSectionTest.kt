package org.jetbrains.compose.swing.samples.agent.context

import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.state.TouchedFile
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilesSectionTest {
    @Test
    fun aRootFileUsesTheRoomAnEmptyFolderWouldTake() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(ContextInput(files = listOf(TouchedFile("build.gradle.kts", 0, 2))))
            val name = onNodeWithText("build.gradle.kts").fetch<JLabel>()
            assertTrue(name.width >= name.preferredSize.width, "the root filename fits beside its badge")
        }

    @Test
    fun longFileNamesLeaveBothBadgesAtTheirPreferredWidth() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(
                ContextInput(
                    files =
                        listOf(
                            TouchedFile("src/" + "long-name".repeat(30), 123, 456),
                        ),
                ),
            )
            for (text in listOf("+123", "−456")) {
                val badge = onNodeWithText(text).fetch<JLabel>()
                assertEquals(badge.preferredSize.width, badge.width)
            }
        }

    @Test
    fun fileRowsShowNameFolderAndChangedLineCounts() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(ContextInput(files = sampleFiles))
            onNodeWithText("Settings.kt").assertIsDisplayed()
            onAllNodesWithText("src/ui").assertCountEquals(2)
            onNodeWithText("src/data").assertExists()
            onNodeWithText(message(English.locale, "diff.added", 12)).assertIsDisplayed()
            onNodeWithText(message(English.locale, "diff.removed", 3)).assertIsDisplayed()
            onNodeWithText(message(English.locale, "diff.added", 1_204)).assertIsDisplayed()
            onNodeWithText("build.gradle.kts").assertIsDisplayed()
            onNodeWithText(message(English.locale, "diff.removed", 2)).assertIsDisplayed()
        }

    @Test
    fun aFileWithNoChangedLinesShowsNoBadge() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(ContextInput(files = sampleFiles.take(1)))
            onNodeWithText("Theme.kt").assertIsDisplayed()
            onAllNodesWithText(message(English.locale, "diff.added", 0)).assertCountEquals(0)
            onAllNodesWithText(message(English.locale, "diff.removed", 0)).assertCountEquals(0)
        }
}

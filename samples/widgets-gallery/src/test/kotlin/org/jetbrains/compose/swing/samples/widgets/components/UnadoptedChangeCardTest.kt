package org.jetbrains.compose.swing.samples.widgets.components

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class UnadoptedChangeCardTest {
    @Test
    fun aClickTheCardDoesNotAdoptLeavesTheBoxClear() =
        runComposeSwingTest {
            openSection("Components")

            onNodeWithText("Sync in the background").performClick()
            onNodeWithText("Sync in the background").assert(SwingMatcher.isSelected(false))
            onNodeWithText("refused 1 click(s)", substring = true).assertExists()

            onNodeWithText("Signed in").performClick()
            onNodeWithText("Sync in the background").performClick()
            onNodeWithText("Sync in the background").assert(SwingMatcher.isSelected())
            onNodeWithText("Syncing is on", substring = true).assertExists()
        }
}

package org.jetbrains.compose.swing.samples.widgets.modifier

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class AccessibilitySectionTest {
    @Test
    fun theNamedFieldAdvertisesItsAccessibleMetadata() =
        runComposeSwingTest {
            openSection("Accessibility")

            onNode(
                SwingMatcher.hasAccessibleName("Search query") and
                    SwingMatcher.hasAccessibleDescription("Type a term to filter the results list."),
            ).assertExists()
        }

    @Test
    fun theMnemonicAndDefaultButtonsDriveTheirCounters() =
        runComposeSwingTest {
            openSection("Accessibility")

            onNodeWithText("Saved 0 time(s)", substring = true).assertExists()
            onNodeWithText("Save").performClick()
            onNodeWithText("Saved 1 time(s)", substring = true).assertExists()

            onNodeWithText("Submitted 0 time(s)", substring = true).assertExists()
            onNodeWithText("Submit").performClick()
            onNodeWithText("Submitted 1 time(s)", substring = true).assertExists()
        }
}

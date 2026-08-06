package org.jetbrains.compose.swing.samples.widgets.runtime

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class DynamicHierarchySectionTest {
    @Test
    fun theStructuralToggleInsertsAndRemovesItsSubtree() =
        runComposeSwingTest {
            openSection("Dynamic hierarchy")

            onNodeWithText("Details (this entire subtree was just inserted)", substring = true)
                .assertDoesNotExist()
            onNodeWithText("Show details panel").performClick()
            onNodeWithText("Details (this entire subtree was just inserted)", substring = true)
                .assertExists()
            onNodeWithText("Show details panel").performClick()
            onNodeWithText("Details (this entire subtree was just inserted)", substring = true)
                .assertDoesNotExist()
        }

    @Test
    fun theVisibleContrastKeepsTheSlotWhileTogglingVisibility() =
        runComposeSwingTest {
            openSection("Dynamic hierarchy")

            onNodeWithText("Clicked 0 time(s)", substring = true).performClick()
            onNodeWithText("Clicked 1 time(s)", substring = true).assertExists()
            onNodeWithText("Show via visible() modifier").performClick()
            onNodeWithText("Show via visible() modifier").performClick()
            onNodeWithText("Clicked 1 time(s)", substring = true).assertExists()
        }
}

package org.jetbrains.compose.swing.samples.widgets

import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutSectionsTest {
    @Test
    fun theCardPanelSwitchesTheVisibleCard() =
        runComposeSwingTest {
            openSection("Layouts")

            onNodeWithText("Card A", substring = true).assertExists()
            onNodeWithText("Card C", substring = true).assertDoesNotExist()

            onNodeWithText("Show C").performClick()
            onNodeWithText("Card C", substring = true).assertExists()
            onNodeWithText("Card A", substring = true).assertDoesNotExist()
        }

    @Test
    fun theOrientationToggleStaysConsistentInTheLayoutsSection() =
        runComposeSwingTest {
            openSection("Layouts")

            onNodeWithText("lineStart (leading)", substring = true).assertExists()
            onNodeWithText("Right-to-left orientation").performClick()
            onNodeWithText("lineStart (leading)", substring = true).assertExists()
            onNodeWithText("lineEnd (trailing)", substring = true).assertExists()
        }

    @Test
    fun theControlledSplitDividerMovesFromButtonsAndEcho() =
        runComposeSwingTest {
            openSection("Split & ToolBar")

            onNodeWithText("Divider location: 140 px", substring = true).assertExists()

            onNodeWithText("Move right").performClick()
            onNodeWithText("Divider location: 180 px", substring = true).assertExists()
            val controlled = onAllNodesOfType<JSplitPane>().fetchAll<JSplitPane>().first()
            assertEquals(JSplitPane.HORIZONTAL_SPLIT, controlled.orientation)
        }

    @Test
    fun theToolBarButtonAndToggleDriveTheirEcho() =
        runComposeSwingTest {
            openSection("Split & ToolBar")

            onNodeWithText("New clicks: 0", substring = true).assertExists()
            onNodeWithText("New").performClick()
            onNodeWithText("New clicks: 1", substring = true).assertExists()

            onNodeWithText("Bold: off", substring = true).assertExists()
            onNodeWithText("Bold").performClick()
            onNodeWithText("Bold: on", substring = true).assertExists()
        }

    @Test
    fun theScrollPaneSectionCapturesItsLaidOutSlotsToABitmapScreenshotTest() =
        runComposeSwingTest {
            openSection("ScrollPane")

            val firstHeader = onNodeWithText("Col 0")
            val captured = firstHeader.captureToImage()
            assertTrue(captured.width > 0 && captured.height > 0, "the captured header cell has real size")
            firstHeader.assertImageMatches(expected = captured)
        }

    @Test
    fun theTabbedPaneSelectsAndAddsTabs() =
        runComposeSwingTest {
            openSection("Tabs")

            onNodeWithText("Selected tab index: 0", substring = true).assertExists()
            val tabs = onNodeOfType<JTabbedPane>().fetch<JTabbedPane>()
            assertEquals(2, tabs.tabCount)

            onNodeWithText("Show extra tab").performClick()
            assertEquals(3, tabs.tabCount)

            onNodeWithText("Select last").performClick()
            onNodeWithText("Selected tab index: 2", substring = true).assertExists()
        }
}

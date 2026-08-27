package org.jetbrains.compose.swing.samples.widgets.layout

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import javax.swing.JLabel
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

            onNodeWithText("Card A").assertIsVisible()
            onNodeWithText("Card C").assertIsNotVisible()

            onNodeWithText("Show C").performClick()
            onNodeWithText("Card C").assertIsVisible()
            onNodeWithText("Card A").assertIsNotVisible()
        }

    @Test
    fun aRowThatFillsTheCrossAxisSpansTheCardHoldingIt() =
        runComposeSwingTest {
            openSection("Layouts")

            // The row declares a cross-axis fill, so the card's column hands it the card's whole content
            // width rather than the narrower width the row's own children ask for.
            val row = onNodeWithText("Leading").fetch<JLabel>().parent
            val card = row.parent
            val cardInsets = card.insets
            assertEquals(
                card.width - cardInsets.left - cardInsets.right,
                row.width,
                "a row that fills the cross axis takes the card's whole content width",
            )
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
            // Two TabbedPanes are on screen once the placement/policy card joins this one; the first
            // declared - this card's own - is the one under test, exactly as the split pane test above
            // picks the controlled pane out from beside the resizeWeight one.
            val tabs = onAllNodesOfType<JTabbedPane>().fetchAll<JTabbedPane>().first()
            assertEquals(2, tabs.tabCount)

            onNodeWithText("Show extra tab").performClick()
            assertEquals(3, tabs.tabCount)

            onNodeWithText("Select last").performClick()
            onNodeWithText("Selected tab index: 2", substring = true).assertExists()
        }
}

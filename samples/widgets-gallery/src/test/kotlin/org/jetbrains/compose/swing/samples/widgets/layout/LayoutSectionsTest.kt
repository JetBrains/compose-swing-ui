package org.jetbrains.compose.swing.samples.widgets.layout

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTextField
import javax.swing.UIManager
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.View
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutSectionsTest {
    @Test
    fun theCardDeckSwitchesTheVisibleCard() =
        runComposeSwingTest {
            openSection("Panel layouts")

            onNodeWithText("Card A").assertIsVisible()
            onNodeWithText("Card C").assertIsNotVisible()

            onNodeWithText("Show C").performClick()
            onNodeWithText("Card C").assertIsVisible()
            onNodeWithText("Card A").assertIsNotVisible()
        }

    @Test
    fun aRowThatFillsTheCrossAxisSpansTheCardHoldingIt() =
        runComposeSwingTest {
            openSection("Linear layouts")

            // The row fills the bounded width the card's column offers rather than keeping the narrower
            // width its own children prefer.
            val row = onNodeWithText("fixed · 88 px").fetch<JButton>().parent
            val card = row.parent
            val cardInsets = card.insets
            assertEquals(
                card.width - cardInsets.left - cardInsets.right,
                row.width,
                "a row that fills the cross axis takes the card's whole content width",
            )
        }

    @Test
    fun theArrangementRowKeepsItsIntrinsicHeight() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val row = onNodeWithText("B · 96 px").fetch<JLabel>().parent
            assertEquals(row.preferredSize.height, row.height, "the row stays as tall as its content")
        }

    @Test
    fun rowArrangementMovesEverySwatchWithinTheSameTrack() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val swatches = listOf("A · 72 px", "B · 96 px", "C · 64 px").map { onNodeWithText(it).fetch<JLabel>() }
            val before = swatches.map { it.x }
            val verticalBefore = swatches.map { it.y }

            layoutParameterSelector("horizontalArrangement").selectedItem = "End"
            awaitIdle()

            val shifts = swatches.mapIndexed { index, swatch -> swatch.x - before[index] }
            assertTrue(shifts.first() > 0, "End moves the packed row away from the leading edge")
            assertTrue(
                shifts.all { it == shifts.first() },
                "the arrangement moves the packed group without reshaping it",
            )

            layoutParameterSelector("verticalAlignment").selectedItem = "Bottom edge"
            awaitIdle()

            assertTrue(swatches.first().y > verticalBefore.first(), "Bottom aligns the shorter child lower")
            assertEquals(verticalBefore[1], swatches[1].y, "the tallest child already spans the row's height")
        }

    @Test
    fun columnArrangementMovesEverySwatchWithinTheSameTrack() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val swatches = listOf("A · 32 px", "B · 40 px", "C · 28 px").map { onNodeWithText(it).fetch<JLabel>() }
            val before = swatches.map { it.y }
            val horizontalBefore = swatches.map { it.x }

            layoutParameterSelector("verticalArrangement").selectedItem = "Bottom"
            awaitIdle()

            val shifts = swatches.mapIndexed { index, swatch -> swatch.y - before[index] }
            assertTrue(shifts.first() > 0, "Bottom moves the packed column away from the top edge")
            assertTrue(
                shifts.all { it == shifts.first() },
                "the arrangement moves the packed group without reshaping it",
            )

            layoutParameterSelector("horizontalAlignment").selectedItem = "End edge"
            awaitIdle()

            assertTrue(
                swatches.indices.all { swatches[it].x > horizontalBefore[it] },
                "End aligns every child farther toward the trailing edge",
            )
        }

    @Test
    fun rowAndColumnChildrenRespondToTheirDirectInteractions() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val fixed = onNodeWithText("fixed · 88 px").fetch<JButton>()
            val fixedBefore = fixed.width
            fixed.doClick()
            awaitIdle()
            onNodeWithText("fixed · 120 px").assertExists()
            assertTrue(fixed.width > fixedBefore, "activating the fixed swatch changes its actual width")

            val weighted = onNodeWithText("weight(1f)").fetch<JButton>()
            val peer = onNodeWithText("peer · weight(1f)").fetch<JLabel>()
            val weightedBefore = weighted.width
            val peerBefore = peer.width
            weighted.doClick()
            awaitIdle()
            onNodeWithText("weight(1.5f)").assertExists()
            assertTrue(weighted.width > weightedBefore, "a larger weight grants the swatch more width")
            assertTrue(peer.width < peerBefore, "the peer gives up the width granted to the heavier swatch")

            val aligned = onNodeWithText("align(End) · 144 px").fetch<JButton>()
            val alignedBefore = aligned.x
            aligned.doClick()
            awaitIdle()
            onNodeWithText("align(Start) · 144 px").assertExists()
            assertTrue(aligned.x < alignedBefore, "cycling End to Start moves the child to the leading edge")
        }

    @Test
    fun baselineAlignmentFollowsTheFieldFontSliderImmediately() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val label = onNodeWithText("Label:").fetch<JLabel>()
            val field = onNodeWithText("Text field").fetch<JTextField>()
            val labelYBefore = label.y
            val fieldHeightBefore = field.height

            val fontSizeLabel = onNodeWithText("Field font: 28 pt").fetch<JLabel>()
            val slider = sliderNamed("Field font size")
            assertTrue(
                slider.x >= fontSizeLabel.x + fontSizeLabel.width,
                "the font slider shares a row with its label instead of consuming another line",
            )
            slider.valueIsAdjusting = true
            slider.value = 72
            awaitIdle()

            onNodeWithText("Field font: 72 pt").assertExists()
            assertTrue(field.height > fieldHeightBefore, "the slider makes the field visibly taller")
            assertTrue(
                label.y > labelYBefore,
                "the baseline-aligned label must move with the taller field without another interaction",
            )
            onNodeWithText("label sits at y = ${label.y} px", substring = true).assertExists()
        }

    @Test
    fun scopeFillModifiersFollowTheirSharedFraction() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val rowChild = onNodeWithText("fills 1.0 of available height").fetch<JLabel>()
            val columnChild = onNodeWithText("fills 1.0 of available width").fetch<JLabel>()
            val boxChild = onNodeWithText("fills 1.0 of the box").fetch<JLabel>()
            val filledSizes = listOf(rowChild.size, columnChild.size, boxChild.size)

            val fractionSlider = fillMaxFractionSlider()
            fractionSlider.value = 0
            awaitIdle()

            onNodeWithText("Row cross axis: fillMaxHeight(0.0)").assertExists()
            onNodeWithText("Column cross axis: fillMaxWidth(0.0)").assertExists()
            onNodeWithText("Box axes: fillMaxSize(0.0)").assertExists()
            assertEquals(0, rowChild.height, "fillMaxHeight(0f) must occupy no height")
            assertEquals(0, columnChild.width, "fillMaxWidth(0f) must occupy no width")
            assertEquals(Dimension(), boxChild.size, "fillMaxSize(0f) must occupy neither axis")

            fractionSlider.value = 5
            awaitIdle()

            onNodeWithText("Row cross axis: fillMaxHeight(0.5)").assertExists()
            onNodeWithText("Column cross axis: fillMaxWidth(0.5)").assertExists()
            onNodeWithText("Box axes: fillMaxSize(0.5)").assertExists()
            assertEquals(filledSizes[0].height / 2, rowChild.height, "fillMaxHeight must follow the 0.5 fraction")
            assertEquals(filledSizes[1].width / 2, columnChild.width, "fillMaxWidth must follow the 0.5 fraction")
            assertEquals(
                Dimension(filledSizes[2].width / 2, filledSizes[2].height / 2),
                boxChild.size,
                "fillMaxSize must apply the 0.5 fraction on both axes",
            )

            fractionSlider.value = 10
            awaitIdle()

            onNodeWithText("fillMax fraction: 1.0").assertExists()
            assertEquals(
                filledSizes,
                listOf(rowChild.size, columnChild.size, boxChild.size),
                "1.0 must restore full fill",
            )
        }

    @Test
    fun gridTransposeAndGridBagFillControlsUpdateTheirReadouts() =
        runComposeSwingTest {
            openSection("Panel layouts")

            onNodeWithText("2 rows x 3 columns").assertExists()
            val cellThree = onNodeWithText("Cell 3").fetch<JLabel>()
            val cellThreeBefore = cellThree.location
            onNodeWithText("Transpose rows and columns").performClick()
            onNodeWithText("3 rows x 2 columns").assertExists()
            assertTrue(
                cellThree.x < cellThreeBefore.x && cellThree.y > cellThreeBefore.y,
                "transposing moves Cell 3 from the first row's end to the second row's start",
            )

            val button = onNodeWithText("Pick a name").fetch<JButton>()
            val filledWidth = button.width
            onNodeWithText("Button fills remaining width").performClick()
            onNodeWithText("fill = NONE", substring = true).assertExists()
            assertTrue(button.width < filledWidth, "GridBag NONE leaves the button narrower than HORIZONTAL fill")
        }

    @Test
    fun flowAlignmentMovesEachRowToTheTrailingEdgeWithoutResizingChildren() =
        runComposeSwingTest {
            openSection("Panel layouts")

            val swatches = listOf("A", "B", "C").map { onNodeWithText(it).fetch<JLabel>() }
            val positionsBefore = swatches.map { it.location }
            val sizesBefore = swatches.map { it.size }

            layoutParameterSelector("alignment").selectedItem = "Trailing"
            awaitIdle()

            val horizontalShifts = swatches.mapIndexed { index, swatch -> swatch.x - positionsBefore[index].x }
            assertTrue(horizontalShifts.all { it > 0 }, "Trailing moves every flow row away from the leading edge")
            assertEquals(horizontalShifts[0], horizontalShifts[1], "children on the same row move together")
            assertTrue(
                horizontalShifts[2] > horizontalShifts[0],
                "the shorter second row moves farther to reach the same trailing edge",
            )
            assertEquals(sizesBefore, swatches.map { it.size }, "alignment does not resize the children")
            assertEquals(
                positionsBefore.map { it.y },
                swatches.map { it.y },
                "horizontal alignment does not move children between rows",
            )
        }

    @Test
    fun flowAndBorderGapControlsChangeTheVisibleSpacing() =
        runComposeSwingTest {
            openSection("Panel layouts")

            val flowA = onNodeWithText("A").fetch<JLabel>()
            val flowB = onNodeWithText("B").fetch<JLabel>()
            val flowGapBefore = flowB.x - (flowA.x + flowA.width)
            sliderNamed("Flow gaps").value = 16
            awaitIdle()
            assertTrue(
                flowB.x - (flowA.x + flowA.width) > flowGapBefore,
                "increasing Flow hgap increases the visible space between adjacent children",
            )

            val west = onNodeWithText("west").fetch<JLabel>()
            val center =
                onAllNodesOfType<JLabel>()
                    .fetchAll<JLabel>()
                    .single { it.text == "center" && it.parent === west.parent }
            val borderGapBefore = center.x - (west.x + west.width)
            val centerWidthBefore = center.width
            sliderNamed("Border gaps").value = 12
            awaitIdle()
            assertTrue(
                center.x - (west.x + west.width) > borderGapBefore,
                "increasing Border hgap increases the visible space between regions",
            )
            assertTrue(center.width < centerWidthBefore, "the wider gaps leave less room for the center region")
        }

    @Test
    fun panelBoxAxisAndGlueControlsReshapeTheirTracks() =
        runComposeSwingTest {
            openSection("Panel layouts")

            val first = onNodeWithText("First").fetch<JLabel>()
            val second = onNodeWithText("Second").fetch<JLabel>()
            val third = onNodeWithText("Third").fetch<JLabel>()
            assertEquals(Dimension(88, 28), first.size, "the first swatch keeps its declared extent")
            assertEquals(Dimension(104, 32), second.size, "the second swatch keeps its declared extent")
            assertEquals(Dimension(72, 24), third.size, "the third swatch keeps its declared extent")
            assertTrue(
                second.y >= first.y + first.height,
                "the PanelLayout.Box children start stacked without overlap on the Y axis",
            )
            assertEquals(first.x, second.x, "the second Y-axis child shares the first child's horizontal origin")
            assertEquals(first.x, third.x, "the third Y-axis child shares the first child's horizontal origin")

            onNodeWithText("Arrange on the X axis (left to right)").performClick()
            awaitIdle()

            assertEquals(Dimension(88, 28), first.size, "changing axis does not turn preferred size into stretch")
            assertEquals(Dimension(104, 32), second.size, "changing axis preserves each swatch's own extent")
            assertEquals(Dimension(72, 24), third.size, "the shortest swatch remains visibly shortest")
            assertTrue(
                listOf(first, second, third).zipWithNext().all { (left, right) ->
                    right.x >= left.x + left.width
                },
                "the axis control restacks every adjacent pair without overlap on X",
            )
            val verticalCenter = first.y + first.height / 2
            assertEquals(verticalCenter, second.y + second.height / 2, "the second X-axis child is centered")
            assertEquals(verticalCenter, third.y + third.height / 2, "the third X-axis child is centered")

            val trailing = onNodeWithText("Trailing").fetch<JLabel>()
            val trailingWithGlue = trailing.x
            onNodeWithText("Include Glue").performClick()
            awaitIdle()
            assertTrue(trailing.x < trailingWithGlue, "removing Glue releases the trailing child from the far edge")
        }

    @Test
    fun layoutMechanicsCardsExposeChangingPlacementState() =
        runComposeSwingTest {
            openSection("Layout mechanics")

            val relative = onNodeWithText("placeRelative(0,y)", substring = true).fetch<JButton>()
            val physical = onNodeWithText("place(width -", substring = true).fetch<JLabel>()
            val orientation = onNodeWithText("Orientation: LTR").fetch<JButton>()
            val parentControl = onNodeWithText("Parent: 200 × 96").fetch<JButton>()
            assertHtmlFits(relative)
            assertEquals(
                "placeRelative, gap 8; activate to cycle",
                relative.accessibleContext.accessibleName,
            )
            val defaultButtonBackground = UIManager.getColor("Button.background")
            assertEquals(
                defaultButtonBackground,
                orientation.background,
                "the orientation control uses the LAF background",
            )
            assertEquals(
                defaultButtonBackground,
                parentControl.background,
                "the parent control uses the LAF background",
            )
            val placementStatus = onNodeWithText("placeRelative x=", substring = true).fetch<JLabel>()
            assertTrue(
                placementStatus.width >= placementStatus.preferredSize.width,
                "the compact placement readout receives enough width",
            )
            val relativeBefore = relative.x
            val physicalBefore = physical.x
            onNodeWithText("Right-to-left placement").performClick()
            awaitIdle()
            assertTrue(relative.x > relativeBefore, "placeRelative mirrors to the right under RTL")
            assertEquals(physicalBefore, physical.x, "physical place keeps the green child on the right")

            val physicalYBefore = physical.y
            onNodeWithText("gap=8 px", substring = true).assertExists()
            onNodeWithText("gap=8 px", substring = true).performClick()
            onNodeWithText("gap=18 px", substring = true).assertExists()
            awaitIdle()
            assertEquals(
                "placeRelative, gap 18; activate to cycle",
                relative.accessibleContext.accessibleName,
            )
            assertTrue(physical.y > physicalYBefore, "the larger custom-layout gap moves the second child down")
            assertDirectionSwatchesMirrored()
        }

    private suspend fun ComposeSwingTest.assertDirectionSwatchesMirrored() {
        val directionSwatches = onAllNodesOfType<JLabel>().fetchAll<JLabel>()
        val padding = directionSwatches.single { it.text.contains("<center>padding<br>") }
        val absolutePadding = directionSwatches.single { it.text.contains("<center>absolutePadding<br>") }
        val offset = directionSwatches.single { it.text.contains("<center>offset<br>") }
        val absoluteOffset = directionSwatches.single { it.text.contains("<center>absoluteOffset<br>") }
        listOf(padding, absolutePadding, offset, absoluteOffset).forEach(::assertHtmlFits)
        assertSame(padding.parent.parent, absolutePadding.parent.parent, "padding variants share a row")
        assertSame(offset.parent.parent, absoluteOffset.parent.parent, "offset variants share a row")
        assertNotSame(padding.parent.parent, offset.parent.parent, "each comparison has its own bounded row")
        val directionXs = listOf(padding.x, absolutePadding.x, offset.x, absoluteOffset.x)
        onNodeWithText("Orientation: LTR").performClick()
        onNodeWithText("Orientation: RTL").assertExists()
        awaitIdle()
        assertTrue(padding.x != directionXs[0], "logical padding mirrors under RTL")
        assertEquals(directionXs[1], absolutePadding.x, "absolute padding remains physically fixed")
        assertTrue(offset.x != directionXs[2], "logical offset mirrors under RTL")
        assertEquals(directionXs[3], absoluteOffset.x, "absolute offset remains physically fixed")
    }

    @Test
    fun theAspectRatioSwatchCyclesItsRatioAndFollowsTheParentConstraint() =
        runComposeSwingTest {
            openSection("Layout mechanics")

            val aspect = buttonNamed("aspectRatio 2f")
            val minimum = onNodeWithText("defaultMinSize<br>", substring = true).fetch<JLabel>()
            val wideAspect = aspect.size
            val roomyMinimum = minimum.size
            assertEquals("2", aspect.text, "the swatch shows only its current numeric ratio")

            aspect.doClick()
            awaitIdle()

            assertEquals("aspectRatio 0.5f", aspect.accessibleContext.accessibleName)
            assertEquals("0.5", aspect.text, "the numeric label follows the cycled ratio")
            assertTrue(aspect.width < wideAspect.width, "cycling from 2f to 0.5f makes the swatch narrower")
            assertTrue(
                kotlin.math.abs(aspect.width * 2 - aspect.height) <= 1,
                "aspectRatio(0.5f) keeps the child at one unit wide by two high",
            )
            val roomyAspect = aspect.size

            onNodeWithText("Parent: 200 × 96").performClick()
            awaitIdle()

            assertTrue(aspect.height < roomyAspect.height, "the ratio child follows the tighter parent")
            assertTrue(minimum.width < roomyMinimum.width, "the default minimum yields to a tighter maximum")
        }

    @Test
    fun theOrientationToggleStaysConsistentInThePanelLayoutsSection() =
        runComposeSwingTest {
            openSection("Panel layouts")

            val lineStart = onNodeWithText("lineStart (leading)", substring = true).fetch<JLabel>()
            val lineEnd = onNodeWithText("lineEnd (trailing)", substring = true).fetch<JLabel>()
            assertTrue(lineStart.x < lineEnd.x, "lineStart begins on the physical left under LTR")
            onNodeWithText("Right-to-left orientation").performClick()
            awaitIdle()
            assertTrue(lineStart.x > lineEnd.x, "lineStart and lineEnd swap physical sides under RTL")
        }

    @Test
    fun theControlledSplitDividerMovesFromButtonsAndEcho() =
        runComposeSwingTest {
            openSection("Split & ToolBar")

            onNodeWithText("Divider location: 140 px", substring = true).assertExists()

            onNodeWithText("Move right").performClick()
            onNodeWithText("Divider location: 180 px", substring = true).assertExists()
            val controlled =
                onAllNodesOfType<JSplitPane>()
                    .fetchAll<JSplitPane>()
                    .single { it.orientation == JSplitPane.HORIZONTAL_SPLIT }
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
    fun theScrollPaneSectionCapturesALaidOutHeaderCell() =
        runComposeSwingTest {
            openSection("ScrollPane")

            val firstHeader = onNodeWithText("Col 0")
            val captured = firstHeader.captureToImage()
            assertTrue(captured.width > 0 && captured.height > 0, "the captured header cell has real size")
        }

    @Test
    fun theTabbedPaneSelectsAndAddsTabs() =
        runComposeSwingTest {
            openSection("Tabs")

            onNodeWithText("Selected tab index: 0", substring = true).assertExists()
            val tabs =
                onAllNodesOfType<JTabbedPane>()
                    .fetchAll<JTabbedPane>()
                    .single { it.getTitleAt(0) == "General" }
            assertEquals(2, tabs.tabCount)

            onNodeWithText("Show extra tab").performClick()
            assertEquals(3, tabs.tabCount)

            onNodeWithText("Select last").performClick()
            onNodeWithText("Selected tab index: 2", substring = true).assertExists()
        }
}

private fun ComposeSwingTest.buttonNamed(name: String): JButton =
    onAllNodesOfType<JButton>()
        .fetchAll<JButton>()
        .single { it.accessibleContext.accessibleName == name }

private fun ComposeSwingTest.fillMaxFractionSlider(): JSlider =
    onAllNodesOfType<JSlider>()
        .fetchAll<JSlider>()
        .single { it.accessibleContext.accessibleName == "fillMax fraction" }

private fun assertHtmlFits(component: JComponent) {
    val view = component.getClientProperty(BasicHTML.propertyKey) as View
    val insets = component.insets
    assertTrue(
        view.getPreferredSpan(View.X_AXIS) <= component.width - insets.left - insets.right,
        "the HTML label fits the component horizontally",
    )
    assertTrue(
        view.getPreferredSpan(View.Y_AXIS) <= component.height - insets.top - insets.bottom,
        "the HTML label fits the component vertically",
    )
}

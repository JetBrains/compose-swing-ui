package org.jetbrains.compose.swing.samples.widgets.layout

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Insets
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// Every card in these two sections is driven from a control and prints the placement or extent the
// layout granted. These tests drive the control and read that printout back, which is the same thing a
// person looking at the card does - so a card whose readout stops following its control fails here.
class BoxAndWeightSectionsTest {
    @Test
    fun interactiveSwatchesPaintTheirColorInsideTheButton() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val node = onNodeWithText("fixed · 88 px")
            val button = node.fetch<JButton>()
            val image = node.captureToImage()

            assertFalse(button.isContentAreaFilled, "the look and feel does not paint over the swatch color")
            assertTrue(button.isOpaque, "the interactive swatch paints its full bounds")
            assertEquals(LayoutSampleColors.Text, button.foreground, "the shared dark text remains legible")
            assertEquals(
                LayoutSampleColors.Blue.rgb,
                image.getRGB(4, 4),
                "the button interior is the swatch color, not a colored gap around a LAF button",
            )
        }

    @Test
    fun theContentAlignmentSelectorMovesTheChildTheBoxPlaces() =
        runComposeSwingTest {
            openSection("Box")

            onNodeWithText("contentAlignment = Center,", substring = true).assertExists()
            val centered = readoutNumbers("contentAlignment = ")

            layoutParameterSelector("contentAlignment").selectedItem = "BottomEnd"
            awaitIdle()

            onNodeWithText("contentAlignment = BottomEnd", substring = true).assertExists()
            val bottomEnd = readoutNumbers("contentAlignment = ")
            assertTrue(
                bottomEnd[0] > centered[0] && bottomEnd[1] > centered[1],
                "BottomEnd places the front child further right and further down than Center did",
            )
        }

    @Test
    fun aChildThatNamesItsOwnAlignIgnoresTheBoxsContentAlignment() =
        runComposeSwingTest {
            openSection("Box")

            val cornerBefore = onNodeWithText("align(TopStart)").fetch<JLabel>().location
            onNodeWithText("Unaligned child sits at Center", substring = true).assertExists()

            onNodeWithText("Box places unaligned children at BottomCenter").performClick()
            awaitIdle()

            onNodeWithText("Unaligned child sits at BottomCenter", substring = true).assertExists()
            assertEquals(
                cornerBefore,
                onNodeWithText("align(TopStart)").fetch<JLabel>().location,
                "the corner child keeps its own placement while the box moves the unaligned one",
            )
        }

    @Test
    fun theChildAlignmentExampleShowsEveryChildAsADistinctBoundedSwatch() =
        runComposeSwingTest {
            openSection("Box")

            val names =
                listOf("align(TopStart)", "align(TopEnd)", "align(BottomStart)", "align(BottomEnd)", "Plain child")
            val children = names.map { onNodeWithText(it).fetch<JLabel>() }

            assertEquals(1, children.map(JLabel::getSize).toSet().size, "all aligned children use one swatch size")
            assertTrue(children.all(JLabel::isOpaque), "every aligned child exposes its colored bounds")
            assertTrue(
                children.all { it.foreground == LayoutSampleColors.Text },
                "every swatch uses the shared dark foreground",
            )
            names.forEach { name ->
                val node = onNodeWithText(name)
                val child = node.fetch<JLabel>()
                val image = node.captureToImage()
                assertEquals(Insets(1, 1, 1, 1), child.border.getBorderInsets(child), "$name has a one-pixel border")
                assertEquals(
                    LayoutSampleColors.Border.rgb,
                    image.getRGB(0, image.height / 2),
                    "$name paints the shared boundary color at its outer edge",
                )
            }
            assertEquals(
                children.size,
                children.map(JLabel::getBackground).toSet().size,
                "each child has its own color",
            )
        }

    @Test
    fun theBackdropTakesTheBoxsExtentOnlyWhileItDeclaresMatchParentSize() =
        runComposeSwingTest {
            openSection("Box")

            val box = onNode(SwingMatcher.hasAccessibleName("matchParentSize box"))
            val matched = readoutNumbers("Blue backdrop: ")
            val matchedImage = box.captureToImage()
            assertTrue(matched[0] > 140 && matched[1] > 50, "the blue backdrop extends beyond the orange child")
            assertEquals(
                LayoutSampleColors.Blue.rgb,
                matchedImage.getRGB(4, 4),
                "matchParentSize leaves the blue backdrop visible around the smaller child",
            )

            onNodeWithText("Backdrop declares matchParentSize").performClick()
            awaitIdle()

            val unmatched = readoutNumbers("Blue backdrop: ")
            val unmatchedImage = box.captureToImage()
            assertTrue(
                unmatched[0] < matched[0] && unmatched[1] < matched[1],
                "without matchParentSize the backdrop falls back to the extent it asks for on its own",
            )
            assertEquals(
                LayoutSampleColors.Track.rgb,
                unmatchedImage.getRGB(4, 4),
                "without matchParentSize the box's neutral track is visible around the orange child",
            )
        }

    @Test
    fun clickingALayerMakesItTheHitTargetAtTheOverlap() =
        runComposeSwingTest {
            openSection("Box")

            val a = onNodeWithText("A - hand").fetch<JButton>()
            val b = onNodeWithText("B - crosshair").fetch<JButton>()
            onNodeWithText("Front child: A").assertExists()
            assertEquals(Cursor.HAND_CURSOR, a.cursor.type, "A advertises its hit area with a hand cursor")
            assertEquals(Cursor.CROSSHAIR_CURSOR, b.cursor.type, "B advertises its hit area with a crosshair")
            assertEquals(a, componentAtOverlap(a, b), "A starts as the overlap's pointer target")

            assertEquals(
                b,
                clickAt(b.parent, b.x + b.width / 2, b.y + 1),
                "the exposed top of layer B receives the pointer click",
            )
            awaitIdle()

            onNodeWithText("Front child: B").assertExists()
            assertEquals(b, componentAtOverlap(a, b), "clicking B makes it the overlap's pointer target")
        }

    @Test
    fun aHeavierShareTakesWidthFromItsNeighborRatherThanFromTheRow() =
        runComposeSwingTest {
            openSection("Linear layouts")

            onNodeWithText("Second swatch weight: 3f").assertExists()
            val (lightBefore, heavyBefore, rowBefore) = readoutNumbers("Shares granted: ")

            sliderNamed("Second swatch weight").value = 31
            awaitIdle()

            onNodeWithText("Second swatch weight: 3.1f").assertExists()
            val (lightAfter, heavyAfter, rowAfter) = readoutNumbers("Shares granted: ")
            assertTrue(
                heavyAfter > heavyBefore,
                "the heavier swatch is granted more width; was $heavyBefore, now $heavyAfter",
            )
            assertTrue(
                lightAfter < lightBefore,
                "the swatch beside it gives up what the heavier one gains; was $lightBefore, now $lightAfter",
            )
            assertEquals(
                rowBefore,
                rowAfter,
                "the weights divide the width the row was offered, so the row itself never moves",
            )
        }

    @Test
    fun aSwatchTakesItsWholeShareOnlyOnceItFills() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val (leftBefore, rightBefore) = readoutNumbers("Shares: left swatch ")
            assertTrue(leftBefore < rightBefore, "without fill the left swatch keeps only the width it prefers")

            onNodeWithText("Left swatch fills its share").performClick()
            awaitIdle()

            val (leftAfter, rightAfter) = readoutNumbers("Shares: left swatch ")
            assertEquals(rightAfter, leftAfter, "two equal shares are equal widths once both fill")
            assertEquals(rightBefore, rightAfter, "the other swatch's share never depended on its neighbor")
        }

    @Test
    fun aMaximumSizeCapsHowMuchOfItsShareAChildOccupies() =
        runComposeSwingTest {
            openSection("Linear layouts")

            onNodeWithText("Share taken: 80 px", substring = true).assertExists()

            onNodeWithText("Cap the swatch at 80 px").performClick()
            awaitIdle()

            val (taken, row) = readoutNumbers("Share taken: ")
            assertTrue(taken > 80, "the uncapped child takes the whole share the row grants it")
            assertTrue(taken <= row, "the share cannot exceed the row it comes out of")
        }

    @Test
    fun aRelativeArrangementMirrorsWhereAnAbsoluteOneHolds() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val endBefore = readoutNumbers("End: leading child at x = ").first()
            val absoluteBefore = readoutNumbers("Absolute.Right: leading child at x = ").first()

            onNodeWithText("Right-to-left orientation (arrangement)").performClick()
            awaitIdle()

            assertNotEquals(
                endBefore,
                readoutNumbers("End: leading child at x = ").first(),
                "Arrangement.End follows the row's reading order",
            )
            assertEquals(
                absoluteBefore,
                readoutNumbers("Absolute.Right: leading child at x = ").first(),
                "Arrangement.Absolute.Right packs against the physical right edge under either orientation",
            )
        }

    @Test
    fun aRelativeAlignmentMirrorsWhereAnAbsoluteOneHolds() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val startBefore = readoutNumbers("Start: child at x = ").first()
            val leftBefore = readoutNumbers("AbsoluteAlignment.Left: child at x = ").first()

            onNodeWithText("Right-to-left orientation (alignment)").performClick()
            awaitIdle()

            assertNotEquals(
                startBefore,
                readoutNumbers("Start: child at x = ").first(),
                "Alignment.Start follows the column's reading order",
            )
            assertEquals(
                leftBefore,
                readoutNumbers("AbsoluteAlignment.Left: child at x = ").first(),
                "AbsoluteAlignment.Left never mirrors",
            )
        }

    @Test
    fun theBiasSliderSlidesTheChildAcrossTheBox() =
        runComposeSwingTest {
            openSection("Box")

            val centered = readoutNumbers("Horizontal bias: ").last()

            sliderNamed("Horizontal bias").value = 100
            awaitIdle()

            onNodeWithText("Horizontal bias: 1.0", substring = true).assertExists()
            val slid = readoutNumbers("Horizontal bias: ").last()
            assertTrue(
                slid > centered,
                "a bias of 1.0 puts the child further right than a bias of 0.0; was $centered, now $slid",
            )
        }

    @Test
    fun droppingTheSharedBaselineMovesTheLabelOntoTheRowsOwnAlignment() =
        runComposeSwingTest {
            openSection("Linear layouts")

            val onBaseline = readoutNumbers("Align by baseline: ").first()

            onNodeWithText("Align by baseline").performClick()
            awaitIdle()

            onNodeWithText("Align by baseline: off", substring = true).assertExists()
            assertNotEquals(
                onBaseline,
                readoutNumbers("Align by baseline: ").first(),
                "off, the label sits at the row's Bottom alignment instead of on the shared baseline",
            )
        }
}

private val integers = Regex("-?\\d+")

// The numbers a card printed, in the order they appear in its readout. Prefixes are unique per section,
// so the substring names exactly one label.
private suspend fun ComposeSwingTest.readoutNumbers(prefix: String): List<Int> {
    // A card measures itself from a componentListener, and a layout pass announces the geometry it
    // changed on the event queue rather than to the listener directly, so the announcement is dispatched
    // one gate after the pass that posts it. This gate is that later one: it delivers what the caller's
    // own settle left queued and then settles the write the listener makes, so the label being read
    // prints the layout it is standing in.
    awaitIdle()
    val readout = onNodeWithText(prefix, substring = true).fetch<JLabel>().text
    return integers.findAll(readout).map { it.value.toInt() }.toList()
}

private fun componentAtOverlap(
    first: JButton,
    second: JButton,
): Component? {
    require(first.parent === second.parent)
    val left = maxOf(first.x, second.x)
    val right = minOf(first.x + first.width, second.x + second.width)
    val top = maxOf(first.y, second.y)
    val bottom = minOf(first.y + first.height, second.y + second.height)
    require(left < right && top < bottom)
    return SwingUtilities.getDeepestComponentAt(first.parent, (left + right) / 2, (top + bottom) / 2)
}

private fun clickAt(
    parent: Container,
    x: Int,
    y: Int,
): Component? {
    val target = SwingUtilities.getDeepestComponentAt(parent, x, y) ?: return null
    val point = SwingUtilities.convertPoint(parent, x, y, target)
    val whenMillis = System.currentTimeMillis()
    target.dispatchEvent(
        MouseEvent(
            target,
            MouseEvent.MOUSE_PRESSED,
            whenMillis,
            InputEvent.BUTTON1_DOWN_MASK,
            point.x,
            point.y,
            1,
            false,
            MouseEvent.BUTTON1,
        ),
    )
    target.dispatchEvent(
        MouseEvent(
            target,
            MouseEvent.MOUSE_RELEASED,
            whenMillis,
            0,
            point.x,
            point.y,
            1,
            false,
            MouseEvent.BUTTON1,
        ),
    )
    return target
}

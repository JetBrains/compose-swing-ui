package org.jetbrains.compose.swing.samples.widgets.layout

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PaintOutsetsSectionTest {
    @Test
    fun theFormsBoundsShareAnEdgeUntilFullInsetsMovesItToTheContent() =
        runComposeSwingTest {
            openSection("Paint outsets")
            val label = onNodeWithTag(PAINT_OUTSETS_FORM_LABEL_TAG).fetch<JLabel>()
            val field = onNodeWithTag(PAINT_OUTSETS_FORM_FIELD_TAG).fetch<JTextField>()
            val button = onNodeWithTag(PAINT_OUTSETS_FORM_BUTTON_TAG).fetch<JButton>()
            assertTrue(field.insets.left > 0 && button.insets.left > 0, "the field and the button have insets")

            assertEquals(listOf(label.x, label.x), listOf(field.x, button.x), "nothing declared: the bounds line up")

            declare("PaintOutsets.None")
            assertEquals(listOf(label.x, label.x), listOf(field.x, button.x), "None: the bounds line up")

            declare("PaintOutsets.FullInsets")
            assertEquals(label.x, field.x + field.insets.left, "FullInsets: the field's content starts at the label")
            assertEquals(label.x, button.x + button.insets.left, "FullInsets: the button's content does too")
        }

    @Test
    fun theRowsBaselinesStayOnOneLineUnderEveryValue() =
        runComposeSwingTest {
            openSection("Paint outsets")
            val label = onNodeWithTag(PAINT_OUTSETS_BASELINE_LABEL_TAG).fetch<JLabel>()
            val field = onNodeWithTag(PAINT_OUTSETS_BASELINE_FIELD_TAG).fetch<JTextField>()
            val button = onNodeWithTag(PAINT_OUTSETS_BASELINE_BUTTON_TAG).fetch<JButton>()
            val gaps = HashSet<Int>()

            for (value in layoutParameterSelector("paintOutsets").items()) {
                declare(value)
                assertEquals(
                    listOf(label.baselineY, label.baselineY),
                    listOf(field.baselineY, button.baselineY),
                    "$value: the three baselines are on one line",
                )
                gaps += field.x - (label.x + label.width)
            }
            assertTrue(gaps.size > 1, "the values place the field's bounds at different distances: $gaps")
        }

    @Test
    fun theShadowTakesLayoutSpaceUnderNoneAndPaintsPastTheBoxUnderDecoration() =
        runComposeSwingTest {
            openSection("Paint outsets")
            val stock = onNodeWithTag(PAINT_OUTSETS_STOCK_TAG).fetch<JButton>()
            val shadowed = onNodeWithTag(PAINT_OUTSETS_SHADOWED_TAG).fetch<JComponent>()
            // The row's spacing past the stock button's bounds.
            val spaced = stock.x + stock.width + 16

            declare("PaintOutsets.Decoration")
            val outsets = (shadowed as Decoratable).decoration.paintOutsets()
            assertTrue(outsets.left > 0, "Decoration: the shadow is paint outsets")
            assertEquals(spaced, shadowed.x + outsets.left, "Decoration: the layout bounds keep the spacing")

            declare("PaintOutsets.None")
            assertEquals(spaced, shadowed.x, "None: the bounds, shadow included, keep the spacing")

            declare("PaintOutsets.FullInsets")
            assertEquals(
                stock.x + stock.width - stock.insets.right + 16,
                shadowed.x + shadowed.insets.left,
                "FullInsets: the content areas keep the spacing",
            )
        }

    @Test
    fun anExplicitDeclarationReplacesTheInheritedValue() =
        runComposeSwingTest {
            openSection("Paint outsets")
            val inherited = onNodeWithTag(PAINT_OUTSETS_INHERITED_TAG).fetch<JTextField>()
            val explicit = onNodeWithTag(PAINT_OUTSETS_EXPLICIT_TAG).fetch<JTextField>()
            assertTrue(explicit.insets.top > 0, "the field has insets")

            declare("PaintOutsets.None")
            assertEquals(
                inherited.y,
                explicit.y + explicit.insets.top,
                "None: the row tops the inherited field's bounds and the explicit field's content area",
            )

            declare("PaintOutsets.FullInsets")
            assertEquals(inherited.y, explicit.y, "FullInsets: both are placed by the content area")
        }

    @Test
    fun theDeclarationsExcessGoesToTheFieldAfterAWidthAndOffTheBoxBeforeIt() =
        runComposeSwingTest {
            openSection("Paint outsets")
            val widthFirst = onNodeWithTag(PAINT_OUTSETS_WIDTH_FIRST_TAG).fetch<JTextField>()
            val widthLast = onNodeWithTag(PAINT_OUTSETS_WIDTH_LAST_TAG).fetch<JTextField>()
            assertEquals(listOf(120, 120), listOf(widthFirst.width, widthLast.width), "nothing declared")

            declare("PaintOutsets.FullInsets")
            // The readout follows the resize one event-queue cycle later.
            awaitIdle()

            val grown = 120 + widthFirst.insets.left + widthFirst.insets.right
            assertTrue(grown > 120, "the field has insets")
            assertEquals(listOf(grown, 120), listOf(widthFirst.width, widthLast.width), "FullInsets")
            onNodeWithText("width(120).paintOutsets(value): field $grown px").assertExists()
            onNodeWithText("paintOutsets(value).width(120): field 120 px").assertExists()

            declare("PaintOutsets(n)")
            sliderNamed("Fixed paint outsets").value = 2
            awaitIdle()
            awaitIdle()

            assertEquals(listOf(124, 120), listOf(widthFirst.width, widthLast.width), "PaintOutsets(2)")
        }

    @Test
    fun underASwingParentNoneGivesTheShadowInsetsAndSizeAndDecorationTakesThemBack() =
        runComposeSwingTest {
            openSection("Paint outsets")
            val shadowed = onNodeWithTag(PAINT_OUTSETS_SWING_PARENT_SHADOWED_TAG).fetch<JComponent>()
            val border = shadowed.insets
            val size = shadowed.size

            declare("PaintOutsets.None")
            awaitIdle()

            val shadow = (shadowed as Decoratable).decoration.paintOutsets()
            assertTrue(shadow.left > 0, "None: the shadow is kept under a Swing parent")
            val insets = shadowed.insets
            assertEquals(
                Insets(
                    border.top + shadow.top,
                    border.left + shadow.left,
                    border.bottom + shadow.bottom,
                    border.right + shadow.right,
                ),
                insets,
                "None: the insets carry the shadow",
            )
            assertEquals(
                Dimension(size.width + shadow.left + shadow.right, size.height + shadow.top + shadow.bottom),
                shadowed.size,
                "None: the size carries the shadow",
            )
            onNodeWithText(
                "Shadowed box: getInsets() = ${insets.top}, ${insets.left}, ${insets.bottom}, ${insets.right}",
            ).assertExists()
            onNodeWithText("Shadowed box: size = ${shadowed.width} × ${shadowed.height} px").assertExists()

            declare("PaintOutsets.Decoration")
            awaitIdle()

            assertEquals(Insets(0, 0, 0, 0), shadowed.decoration.paintOutsets(), "Decoration: no paint outsets")
            assertEquals(border, shadowed.insets, "Decoration: the insets are the border")
            assertEquals(size, shadowed.size, "Decoration: the size is the plain Swing size")
            onNodeWithText("Shadowed box: size = ${size.width} × ${size.height} px").assertExists()
        }

    @Test
    fun aButtonMovedPastTheTrackIsPaintedAndHitThereUntilTheTrackClips() =
        runComposeSwingTest {
            openSection("Paint outsets")
            val stageNode = onNodeWithTag(PAINT_OUTSETS_STAGE_TAG)
            val stage = stageNode.fetch<JComponent>()
            val track = onNodeWithTag(PAINT_OUTSETS_TRACK_TAG).fetch<JComponent>()
            val button = onNodeWithTag(PAINT_OUTSETS_PAST_BUTTON_TAG).fetch<JButton>()
            sliderNamed("Child offset").value = 120
            awaitIdle()

            val middle = SwingUtilities.convertPoint(button, button.width / 2, button.height / 2, stage)
            val trackBox = (track as Decoratable).decoration.localLayoutBounds(track)
            assertTrue(
                middle.x >= track.x + trackBox.x + trackBox.width,
                "the button's middle is past the track's layout bounds",
            )
            assertSame(button, stage.findComponentAt(middle), "the button is hit past the track")
            assertNotEquals(
                LayoutSampleColors.Blue.rgb,
                stageNode.captureToImage().getRGB(middle.x, middle.y),
                "the button is painted past the track",
            )
            onNodeWithTag(PAINT_OUTSETS_PAST_BUTTON_TAG).performClick()
            assertEquals("Clicked 1", button.text, "a click past the track counts")

            onNodeWithText("clipToBounds() on the track").performClick()

            assertNotSame(button, stage.findComponentAt(middle), "clipped, a click there misses it")
            assertEquals(
                LayoutSampleColors.Blue.rgb,
                stageNode.captureToImage().getRGB(middle.x, middle.y),
                "clipped, the stage shows where the button was painted",
            )
        }

    @Test
    fun theBandOfAStockFieldFillsItsInsetsUnderFullInsetsAndNoStageHasABandUnderNone() =
        runComposeSwingTest {
            openSectionWithMenuBar("Paint outsets")
            val form = onNodeWithTag(PAINT_OUTSETS_FORM_TAG)
            val field = onNodeWithTag(PAINT_OUTSETS_FORM_FIELD_TAG).fetch<JTextField>()
            val insets = field.insets
            assertTrue(insets.left > 1 && insets.top > 1, "the field's insets are wider than an outline")

            declare("PaintOutsets.FullInsets")
            val guided = form.captureToImage()
            declare("PaintOutsets.None")
            val guidedUnderNone = form.captureToImage()
            switchAlignmentGuides()
            val plainUnderNone = form.captureToImage()
            declare("PaintOutsets.FullInsets")
            val plain = form.captureToImage()

            val lineColor = AlignmentGuideColors.Line.rgb
            val contentArea =
                Rectangle(
                    field.x + insets.left,
                    field.y + insets.top,
                    field.width - insets.left - insets.right,
                    field.height - insets.top - insets.bottom,
                )
            // The outermost pixels of the bounds are their outline, and the dashed line runs down the column of the
            // layout box's left edge.
            val insideOutline = Rectangle(field.x + 1, field.y + 1, field.width - 2, field.height - 2)
            assertEquals(
                field.bounds.points().filter {
                    it in insideOutline && it !in contentArea && guided.getRGB(it.x, it.y) != lineColor
                },
                guided.bandPixels(plain).filter { it in field.bounds },
                "FullInsets: the band fills the field's insets inside the outline of its bounds",
            )
            assertEquals(
                listOf(contentArea.x, contentArea.x + contentArea.width - 1),
                guided.columnsWith(
                    GuideMark.LayoutBox.color,
                    contentArea.y + 1 until contentArea.y + contentArea.height - 1,
                ),
                "FullInsets: the layout box is outlined at the content area",
            )
            assertEquals(emptyList(), guidedUnderNone.bandPixels(plainUnderNone), "None: the stage has no band")
        }

    @Test
    fun theBandOfTheShadowedBoxIsItsShadowUnderDecorationAndItsLayoutBoxIsItsBoundsUnderNone() =
        runComposeSwingTest {
            openSectionWithMenuBar("Paint outsets")
            val row = onNodeWithTag(PAINT_OUTSETS_DECORATED_TAG)
            val shadowed = onNodeWithTag(PAINT_OUTSETS_SHADOWED_TAG).fetch<JComponent>()

            declare("PaintOutsets.Decoration")
            val bounds = shadowed.bounds
            val shadow = (shadowed as Decoratable).decoration.paintOutsets()
            assertTrue(shadow.left > 1 && shadow.top > 1, "the shadow is wider than an outline")
            val guided = row.captureToImage()
            declare("PaintOutsets.None")
            val boundsUnderNone = shadowed.bounds
            val guidedUnderNone = row.captureToImage()
            switchAlignmentGuides()
            val plainUnderNone = row.captureToImage()
            declare("PaintOutsets.Decoration")
            val plain = row.captureToImage()

            val lineColor = AlignmentGuideColors.Line.rgb
            val layoutBounds =
                Rectangle(
                    bounds.x + shadow.left,
                    bounds.y + shadow.top,
                    bounds.width - shadow.left - shadow.right,
                    bounds.height - shadow.top - shadow.bottom,
                )
            // The outermost pixels of the bounds are their outline, and the dashed line runs along the row of the
            // layout box's top edge.
            val insideOutline = Rectangle(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2)
            assertEquals(
                bounds.points().filter {
                    it in insideOutline && it !in layoutBounds && guided.getRGB(it.x, it.y) != lineColor
                },
                guided.bandPixels(plain).filter { it in bounds },
                "Decoration: the band fills the shadow, between the bounds and the layout bounds",
            )
            assertEquals(
                listOf(layoutBounds.x, layoutBounds.x + layoutBounds.width - 1),
                guided
                    .columnsWith(
                        GuideMark.LayoutBox.color,
                        layoutBounds.y + 1 until layoutBounds.y + layoutBounds.height - 1,
                    ).filter { it >= bounds.x },
                "Decoration: the layout box is outlined at the layout bounds",
            )
            assertEquals(
                listOf(boundsUnderNone.x, boundsUnderNone.x + boundsUnderNone.width - 1),
                guidedUnderNone
                    .columnsWith(
                        GuideMark.LayoutBox.color,
                        boundsUnderNone.y + 1 until boundsUnderNone.y + boundsUnderNone.height - 1,
                    ).filter { it >= boundsUnderNone.x },
                "None: the layout box is outlined at the bounds, shadow included",
            )
            assertEquals(emptyList(), guidedUnderNone.bandPixels(plainUnderNone), "None: the stage has no band")
        }

    @Test
    fun whereTwoChildrenOverlapTheOutlinesAreDrawnOverBothBands() =
        runComposeSwingTest {
            openSection("Paint outsets")
            val stock = onNodeWithTag(PAINT_OUTSETS_STOCK_TAG).fetch<JButton>()
            val shadowed = onNodeWithTag(PAINT_OUTSETS_SHADOWED_TAG).fetch<JComponent>()
            declare("PaintOutsets.FullInsets")

            val insets = stock.insets
            val right = stock.x + stock.width - insets.right - 1
            assertTrue(right >= shadowed.x, "the stock button's layout box reaches into the shadowed box's band")
            assertEquals(
                listOf(stock.x + insets.left, right),
                onNodeWithTag(PAINT_OUTSETS_DECORATED_TAG)
                    .captureToImage()
                    .columnsWith(
                        GuideMark.LayoutBox.color,
                        stock.y + insets.top + 1 until stock.y + stock.height - insets.bottom - 1,
                    ).filter { it <= right },
                "the first child's layout box keeps its color inside the band of the second",
            )
        }

    @Test
    fun theGuidesOfAButtonMovedPastTheTracksStartEdgeAreDrawnWhereItIs() =
        runComposeSwingTest {
            openSection("Paint outsets")
            val trackNode = onNodeWithTag(PAINT_OUTSETS_TRACK_TAG)
            val track = trackNode.fetch<JComponent>()
            val button = onNodeWithTag(PAINT_OUTSETS_PAST_BUTTON_TAG).fetch<JButton>()
            sliderNamed("Child offset").value = -60
            awaitIdle()

            assertTrue(
                (track as Decoratable).decoration.paintOutsets().left > 0,
                "the track's bounds start before its layout bounds, where the button is",
            )
            assertEquals(
                listOf(button.x, button.x + button.width - 1),
                trackNode
                    .captureToImage()
                    .columnsWith(GuideMark.LayoutBox.color, button.y + 1 until button.y + button.height - 1),
                "nothing declared: the layout box is outlined at the button's bounds",
            )
        }
}

private suspend fun ComposeSwingTest.declare(value: String) {
    layoutParameterSelector("paintOutsets").selectedItem = value
    awaitIdle()
}

/** Every pixel of this rectangle, row by row. */
private fun Rectangle.points(): List<Point> =
    (y until y + height).flatMap { row -> (x until x + width).map { Point(it, row) } }

private fun JComboBox<*>.items(): List<String> = (0 until itemCount).map { getItemAt(it) as String }

/** Where this component's first baseline is, in its parent's coordinates. */
private val Component.baselineY: Int get() = y + getBaseline(width, height)

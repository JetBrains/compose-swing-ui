package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.PaintOutsets
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.paintOutsets
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlignmentGuidesTest {
    @Test
    fun theBaselineSegmentsShareOneRowAndFollowTheFieldFont() =
        runComposeSwingTest {
            openSection("Linear layouts")
            val label = onNodeWithText("Label:").fetch<JLabel>()
            val field = onNodeWithText("Text field").fetch<JTextField>()
            val row = onNodeWithTag(BASELINE_ROW_TAG)

            val baseline = label.y + label.getBaseline(label.width, label.height)
            assertEquals(baseline, field.y + field.getBaseline(field.width, field.height), "the baseline is shared")
            val image = row.captureToImage()
            assertEquals(listOf(baseline), image.rowsWith(LINE_COLOR, label.xs), "inside the label")
            assertEquals(listOf(baseline), image.rowsWith(LINE_COLOR, field.xs), "inside the field")
            assertEquals(
                emptyList(),
                image.rowsWith(LINE_COLOR, label.xs.last + 1 until field.x),
                "a container does not define a baseline, so nothing crosses the gap",
            )

            sliderNamed("Field font size").value = 72
            awaitIdle()

            val moved = label.y + label.getBaseline(label.width, label.height)
            assertNotEquals(baseline, moved, "a larger field font moves the shared baseline")
            assertEquals(
                listOf(moved),
                row.captureToImage().rowsWith(LINE_COLOR, label.xs.first..field.xs.last),
                "both segments are at the new baseline, and none is left at the old one",
            )
        }

    @Test
    fun theRowPlaygroundDrawsTheLineOfItsVerticalAlignmentAndNoOther() =
        runComposeSwingTest {
            openSection("Linear layouts")
            val swatches = listOf("A · 72 px", "B · 96 px", "C · 64 px").map { onNodeWithText(it).fetch<JLabel>() }
            val stage = onNodeWithTag(ROW_PLAYGROUND_TAG)
            val row = stage.fetch<JComponent>()
            val content = row.insets.top until row.height - row.insets.bottom

            val centered = stage.captureToImage()
            val center = content.first + content.count() / 2
            assertEquals(
                listOf(center),
                centered.rowsWith(LINE_COLOR, 0 until centered.width),
                "the center of the row's content box, and no other line",
            )
            swatches.forEach {
                assertEquals(listOf(center), centered.rowsWith(LINE_COLOR, it.xs), "${it.text} is on it")
                assertEquals(
                    emptyList(),
                    centered.rowsWith(LINE_COLOR, it.x + it.width / 3 until it.x + 2 * it.width / 3),
                    "the line enters ${it.text} at its sides only, clear of its text",
                )
            }

            layoutParameterSelector("verticalAlignment").selectedItem = "Bottom edge"
            awaitIdle()

            val bottomed = stage.captureToImage()
            assertEquals(
                listOf(content.last),
                bottomed.rowsWith(LINE_COLOR, 0 until bottomed.width),
                "the bottom of the row's content box, and no other line",
            )
            swatches.forEach {
                assertEquals(
                    listOf(content.last),
                    bottomed.rowsWith(LINE_COLOR, it.xs),
                    "${it.text} is on it",
                )
            }
        }

    @Test
    fun theColumnPlaygroundDrawsTheLineOfItsHorizontalAlignmentAndNoOther() =
        runComposeSwingTest {
            openSection("Linear layouts")
            val swatches = listOf("A · 32 px", "B · 40 px", "C · 28 px").map { onNodeWithText(it).fetch<JLabel>() }
            val stage = onNodeWithTag(COLUMN_PLAYGROUND_TAG)
            val column = stage.fetch<JComponent>()
            val content = column.insets.left until column.width - column.insets.right

            val centered = stage.captureToImage()
            val center = content.first + content.count() / 2
            assertEquals(
                listOf(center),
                centered.columnsWith(LINE_COLOR, 0 until centered.height),
                "the center of the column's content box, and no other line",
            )
            swatches.forEach {
                assertEquals(listOf(center), centered.columnsWith(LINE_COLOR, it.ys), "${it.text} is on it")
            }

            layoutParameterSelector("horizontalAlignment").selectedItem = "End edge"
            awaitIdle()

            val ended = stage.captureToImage()
            assertEquals(
                listOf(content.last),
                ended.columnsWith(LINE_COLOR, 0 until ended.height),
                "the right edge of the column's content box, and no other line",
            )
            swatches.forEach {
                assertEquals(listOf(content.last), ended.columnsWith(LINE_COLOR, it.ys), "${it.text} is on it")
            }
        }

    @Test
    fun theBoxCardDrawsTheTwoLinesItsContentAlignmentNames() =
        runComposeSwingTest {
            openSection("Box")
            val front = onNodeWithText("front").fetch<JLabel>()
            val stage = onNodeWithTag(BOX_CONTENT_ALIGNMENT_TAG)
            val box = stage.fetch<JComponent>()

            layoutParameterSelector("contentAlignment").selectedItem = "TopStart"
            awaitIdle()

            // Away from the corner the two lines meet in, a column of pixels crosses the horizontal line alone and
            // a row the vertical one.
            val beside = front.xs.last + 1 until box.width - box.insets.right - 1
            val below = front.ys.last + 1 until box.height - box.insets.bottom - 1
            val topStart = stage.captureToImage()
            assertEquals(listOf(box.insets.top), topStart.rowsWith(LINE_COLOR, beside), "the top edge")
            assertEquals(listOf(box.insets.left), topStart.columnsWith(LINE_COLOR, below), "the left edge")
            assertEquals(listOf(front.y, front.x), listOf(box.insets.top, box.insets.left), "the child is on both")

            layoutParameterSelector("contentAlignment").selectedItem = "BottomEnd"
            awaitIdle()

            val bottomEnd = stage.captureToImage()
            val bottom = box.height - box.insets.bottom - 1
            val right = box.width - box.insets.right - 1
            assertEquals(
                listOf(bottom),
                bottomEnd.rowsWith(LINE_COLOR, box.insets.left + 1 until front.x),
                "the bottom edge",
            )
            assertEquals(
                listOf(right),
                bottomEnd.columnsWith(LINE_COLOR, box.insets.top + 1 until front.y),
                "the right edge",
            )
            assertEquals(listOf(front.ys.last, front.xs.last), listOf(bottom, right), "the child is on both")
        }

    @Test
    fun uncheckingTheViewMenuItemRemovesTheGuidesAndTheirLegends() =
        runComposeSwingTest {
            openSectionWithMenuBar("Linear layouts")
            val stage = onNodeWithTag(ROW_PLAYGROUND_TAG)
            val guided = stage.captureToImage()
            assertTrue(guided.rowsWith(LINE_COLOR, 0 until guided.width).isNotEmpty(), "checked: the line")
            assertTrue(
                guided.rowsWith(GuideMark.LayoutBox.color, 0 until guided.width).isNotEmpty(),
                "checked: the layout boxes",
            )

            switchAlignmentGuides()

            val plain = stage.captureToImage()
            assertEquals(emptyList(), plain.rowsWith(LINE_COLOR, 0 until plain.width), "unchecked: no line")
            assertEquals(
                emptyList(),
                plain.rowsWith(GuideMark.LayoutBox.color, 0 until plain.width),
                "unchecked: no layout box",
            )
            onAllNodesWithText(GuideMark.LayoutBox.label).assertCountEquals(0)
        }

    @Test
    fun onlyTheContainersLineIsDrawnBetweenTwoChildren() =
        runComposeSwingTest {
            openSection("Linear layouts")
            val (first, second) = listOf("A · 72 px", "B · 96 px").map { onNodeWithText(it).fetch<JLabel>() }
            val stage = onNodeWithTag(ROW_PLAYGROUND_TAG)
            layoutParameterSelector("horizontalArrangement").selectedItem = "SpaceBetween"
            layoutParameterSelector("verticalAlignment").selectedItem = "Bottom edge"
            awaitIdle()

            val gap = first.xs.last + 1 until second.x
            assertTrue(gap.count() > 16, "the arrangement leaves a gap between the first two children")
            val image = stage.captureToImage()
            val row = stage.fetch<JComponent>()
            assertEquals(
                listOf(row.height - row.insets.bottom - 1),
                image.rowsWith(LINE_COLOR, gap),
                "the row's own line crosses the gap",
            )
            assertEquals(emptyList(), image.rowsWith(GuideMark.LayoutBox.color, gap), "and no mark of a child does")
        }

    @Test
    fun aChildThatMovesChangesNothingOutsideTheAreasItLeftAndTook() =
        runComposeSwingTest {
            openSection("Linear layouts")
            val swatches = listOf("A · 72 px", "B · 96 px", "C · 64 px").map { onNodeWithText(it).fetch<JLabel>() }
            val stage = onNodeWithTag(ROW_PLAYGROUND_TAG)
            val before = stage.captureToImage()
            val left = swatches.map { it.bounds }

            layoutParameterSelector("horizontalArrangement").selectedItem = "End"
            awaitIdle()

            val moved = swatches.first()
            assertNotEquals(left.first().x, moved.x, "the arrangement moves the children inside the unchanged row")
            // Swing repaints the area a moved child left and the area it took, and nothing else.
            val repainted = left + swatches.map { it.bounds }
            val after = stage.captureToImage()
            assertNull(
                differingPixelBounds(before.outside(repainted), after.outside(repainted)),
                "no mark of a child and no part of the row's line changes outside those areas",
            )
            val outlines = after.columnsWith(GuideMark.LayoutBox.color, moved.ys)
            assertTrue(moved.x in outlines, "the layout box is outlined where the child is now")
            assertTrue(left.first().x !in outlines, "and not where it was")
        }

    @Test
    fun aChildThatIsHiddenChangesNothingOutsideTheAreaItLeft() =
        runComposeSwingTest {
            val guides = GuidedChildren()
            var shown by mutableStateOf(true)
            setContent {
                Row(
                    modifier =
                        SwingModifier
                            .preferredSize(240, 40)
                            .testTag(STAGE_TAG)
                            .alignmentGuides(guides, shown = true, setOf(GuideMark.LayoutBox), setOf(GuideLine.Top)),
                    horizontalArrangement = Arrangement.spacedBy(16),
                ) {
                    Label("kept", SwingModifier.alignmentGuide(guides))
                    Label("hidden", SwingModifier.alignmentGuide(guides).visible(shown))
                }
            }
            val stage = onNodeWithTag(STAGE_TAG)
            val hidden = onNodeWithText("hidden").fetch<JLabel>()
            val place = hidden.xs
            val before = stage.captureToImage()
            assertTrue(
                before.rowsWith(GuideMark.LayoutBox.color, place).isNotEmpty(),
                "shown, the child is outlined",
            )

            shown = false
            awaitIdle()

            // Swing repaints the area a hidden child left, and nothing else.
            val repainted = listOf(hidden.bounds)
            val after = stage.captureToImage()
            assertNull(
                differingPixelBounds(before.outside(repainted), after.outside(repainted)),
                "nothing changes outside that area",
            )
            assertEquals(emptyList(), after.rowsWith(GuideMark.LayoutBox.color, place), "hidden, it has no outline")
            assertEquals(listOf(0), after.rowsWith(LINE_COLOR, place), "and the row's line takes its place")
        }

    @Test
    fun aChildDeclaredWithAnotherValueAsksItselfToRepaint() =
        runComposeSwingTest {
            val guides = GuidedChildren()
            var value by mutableStateOf(PaintOutsets.None)
            setContent {
                // Centered, a child with the same insets on every side keeps its bounds under every value.
                Box(
                    SwingModifier
                        .preferredSize(240, 60)
                        .testTag(STAGE_TAG)
                        .alignmentGuides(guides, shown = true, setOf(GuideMark.LayoutBox)),
                    contentAlignment = Alignment.Center,
                ) {
                    Label("Label", SwingModifier.emptyBorder(8).paintOutsets(value).alignmentGuide(guides, value))
                }
            }
            val stage = onNodeWithTag(STAGE_TAG)
            val label = onNodeWithText("Label").fetch<JLabel>()
            val bounds = label.bounds
            val inside = label.y + 9 until label.y + label.height - 9
            assertEquals(
                listOf(label.xs.first, label.xs.last),
                stage.captureToImage().columnsWith(GuideMark.LayoutBox.color, inside),
                "None: the layout box is the bounds",
            )

            val asked = repaintsAskedDuring { value = PaintOutsets.FullInsets }

            assertEquals(bounds, label.bounds, "the label stays where it was, so Swing repaints nothing for it")
            assertEquals(
                setOf(label to Rectangle(label.size)),
                asked.toSet(),
                "the label alone asks to repaint, and asks for its whole area, which holds its guides",
            )
            assertEquals(
                listOf(label.x + 8, label.xs.last - 8),
                stage.captureToImage().columnsWith(GuideMark.LayoutBox.color, inside),
                "FullInsets: the layout box is the content area, and no outline is left at the bounds",
            )
        }

    @Test
    fun aComponentAsksItselfToRepaintAsItsGuideIsDeclaredAndAsItIsDropped() =
        runComposeSwingTest {
            val guides = GuidedChildren()
            var guided by mutableStateOf(false)
            setContent {
                Row(
                    SwingModifier
                        .preferredSize(240, 40)
                        .testTag(STAGE_TAG)
                        .alignmentGuides(guides, shown = true, setOf(GuideMark.LayoutBox)),
                ) {
                    Label("Label", if (guided) SwingModifier.alignmentGuide(guides) else SwingModifier)
                }
            }
            val stage = onNodeWithTag(STAGE_TAG)
            val label = onNodeWithText("Label").fetch<JLabel>()
            val whole = label to Rectangle(label.size)
            val inside = label.y + 1 until label.y + label.height - 1

            val declared = repaintsAskedDuring { guided = true }

            assertEquals(setOf(whole), declared.toSet(), "declared on a component that stands, the guide repaints it")
            assertEquals(
                listOf(label.xs.first, label.xs.last),
                stage.captureToImage().columnsWith(GuideMark.LayoutBox.color, inside),
                "and the row outlines it",
            )

            val dropped = repaintsAskedDuring { guided = false }

            assertEquals(setOf(whole), dropped.toSet(), "dropped, the guide repaints it again")
            assertEquals(
                emptyList(),
                stage.captureToImage().columnsWith(GuideMark.LayoutBox.color, inside),
                "and the row outlines it no more",
            )
        }
}

/** The columns of pixels this component takes in its parent. */
private val Component.xs: IntRange get() = x until x + width

/** The rows of pixels this component takes in its parent. */
private val Component.ys: IntRange get() = y until y + height

private val LINE_COLOR = AlignmentGuideColors.Line
private const val STAGE_TAG = "stage"

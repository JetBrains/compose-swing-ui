package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.paintOutsets
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.border.LineBorder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A stock widget declaring which part of its insets is paint outsets lines up by the rest under a Foundation parent:
 * its layout box is its bounds less that part, and the part paints past the box.
 */
class PaintOutsetsAlignmentTest {
    @Test
    fun aFieldTakingItsInsetsAsPaintOutsetsLinesUpItsBorderWithALabel() =
        runComposeSwingTest {
            var filled by mutableStateOf(false)
            setContent {
                Column(modifier = SwingModifier.testTag("column")) {
                    Label("label", modifier = SwingModifier.testTag("label"))
                    LinedField(
                        "field",
                        if (filled) SwingModifier.fillMaxWidth() else SwingModifier,
                        style = { paintOutsets(PaintOutsets.FullInsets) },
                    )
                }
            }
            val field = onNodeWithTag("field").fetch()
            onNodeWithTag("label").assertProperty(0) { x }
            assertEquals(-LINE, field.x, "the field's content edge lines up with the label")

            filled = true
            awaitIdle()

            val column = onNodeWithTag("column").fetch()
            assertEquals(column.layoutBounds.width, field.width - 2 * LINE, "the border reaches past the column")
            assertEquals(-LINE, field.x)
        }

    @Test
    fun aRowOfFieldsTakingTheirInsetsAsPaintOutsetsIsAsTallAsTheirContent() =
        runComposeSwingTest {
            setContent {
                Column {
                    Row(modifier = SwingModifier.testTag("row")) {
                        LinedField("first", style = { paintOutsets(PaintOutsets.FullInsets) })
                        LinedField("second", style = { paintOutsets(PaintOutsets.FullInsets) })
                    }
                    Row(modifier = SwingModifier.height(40)) {
                        LinedField(
                            "filling",
                            SwingModifier.fillMaxHeight(),
                            style = { paintOutsets(PaintOutsets.FullInsets) },
                        )
                    }
                }
            }
            val first = onNodeWithTag("first").fetch()
            onNodeWithTag("row").assertProperty(first.preferredSize.height - 2 * LINE) { layoutBounds.height }
            val second = onNodeWithTag("second").fetch()
            assertEquals(-LINE, first.layoutBounds.y)
            assertEquals(-LINE, second.layoutBounds.y)
            assertEquals(first.width - 2 * LINE, second.x - first.x, "the row places the boxes side by side")
            onNodeWithTag("filling").assertProperty(40 + 2 * LINE, "a filling field reaches past its row") { height }
        }

    @Test
    fun aBorderPastANestedColumnPaintsPastItsLayoutBox() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("box")) {
                    Column(modifier = SwingModifier.testTag("column").padding(10)) {
                        LinedField("field", style = { paintOutsets(PaintOutsets.FullInsets) })
                    }
                }
            }
            val column = onNodeWithTag("column").fetch<JComponent>()
            assertEquals(Insets(LINE, LINE, LINE, LINE), column.paintOutsets)
            val box = column.layoutBounds
            val image = onNodeWithTag("box").captureToImage()
            image.assertPixelColor(Color.RED, box.x - 1, box.y + box.height / 2, "the line past the column")
        }

    @Test
    fun theRootClipsWhatReachesPastIt() =
        runComposeSwingTest {
            setContent {
                Panel(modifier = SwingModifier.testTag("panel").opaque(true), style = { background(Color.WHITE) }) {
                    Column(modifier = SwingModifier.testTag("column")) {
                        LinedField(
                            "field",
                            SwingModifier.fillMaxWidth(),
                            style = { paintOutsets(PaintOutsets.FullInsets) },
                        )
                    }
                }
            }
            val column = onNodeWithTag("column").fetch<JComponent>()
            assertEquals(Insets(0, 0, 0, 0), column.paintOutsets, "a root takes no paint outsets")
            val image = onNodeWithTag("panel").captureToImage()
            image.assertPixelColor(Color.WHITE, column.x - 1, column.y + column.height / 2, "cut at its edge")
        }

    @Test
    fun aFieldDeclaringNothingKeepsItsInsetsInItsLayoutBox() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag("column")) { LinedField("field") }
            }
            val column = onNodeWithTag("column").fetch()
            val field = onNodeWithTag("field").fetch()
            assertEquals(Rectangle(0, 0, column.width, column.height), field.bounds)
            assertEquals(field.preferredSize, column.size, "the column's size includes the insets")
        }

    /**
     * A text field centers its text, so its baseline follows the height it is asked with. A text area keeps its
     * baseline a fixed distance below its top, so it shows whether a size query moves the line with the box.
     */
    @Test
    fun aFieldAlignedByItsBaselineKeepsItsBaselineInPlace() =
        runComposeSwingTest {
            setContent {
                Column {
                    Row(modifier = SwingModifier.testTag("row")) {
                        Label("label", modifier = SwingModifier.testTag("label").alignByBaseline())
                        LinedField(
                            "field",
                            SwingModifier.alignByBaseline(),
                            style = { paintOutsets(PaintOutsets.FullInsets) },
                        )
                        TextArea(
                            "area",
                            {},
                            modifier =
                                SwingModifier
                                    .testTag("area")
                                    .border(LineBorder(Color.RED, LINE))
                                    .alignByBaseline(),
                            style = { paintOutsets(PaintOutsets.FullInsets) },
                        )
                    }
                }
            }
            val row = onNodeWithTag("row").fetch<JComponent>()
            val label = onNodeWithTag("label").fetch()
            val marked = listOf("field", "area").associateWith { onNodeWithTag(it).fetch() }
            for ((tag, each) in marked) {
                assertEquals(label.y + label.baseline(), each.y + each.baseline(), "$tag: measured")
            }
            val top = minOf(label.y, marked.values.minOf { it.y + LINE })
            val bottom = maxOf(label.y + label.height, marked.values.maxOf { it.y + it.height - LINE })
            assertEquals(bottom - top, row.layoutBounds.height, "the row holds the label and the boxes of the two")
            val insets = row.insets
            assertEquals(
                row.layoutBounds.height,
                row.preferredSize.height - insets.top - insets.bottom,
                "the intrinsic walk holds the same lines",
            )
        }

    @Test
    fun aThickerBorderKeepsTheContentEdgeAndMeasuresOnce() =
        runComposeSwingTest {
            var thickness by mutableIntStateOf(LINE)
            var measures = 0
            setContent {
                Column {
                    Label("label", modifier = SwingModifier.testTag("label"))
                    TextField(
                        "field",
                        {},
                        modifier =
                            SwingModifier
                                .testTag("field")
                                .border(LineBorder(Color.RED, thickness))
                                .countingMeasures { measures++ },
                        style = { paintOutsets(PaintOutsets.FullInsets) },
                    )
                }
            }
            measures = 0

            thickness = 5
            awaitIdle()

            onNodeWithTag("label").assertProperty(0) { x }
            onNodeWithTag("field").assertProperty(-5) { x }
            assertEquals(1, measures)
        }

    @Test
    fun aValueNamesPartOfTheInsetsClampedToThem() =
        runComposeSwingTest {
            setContent {
                Column {
                    Label("label", modifier = SwingModifier.testTag("label"))
                    LinedField("field", style = { paintOutsets(TwoPixels) })
                    Label("bare", modifier = SwingModifier.testTag("bare"), style = { paintOutsets(TwoPixels) })
                }
            }
            onNodeWithTag("label").assertProperty(0) { x }
            onNodeWithTag("field").assertProperty(-2) { x }
            onNodeWithTag("bare").assertProperty(0, "a label with no insets keeps its bounds") { x }
        }

    @Test
    fun aValueOfTheAppsOwnNamesARingForAFieldAndNothingForALabel() =
        runComposeSwingTest {
            setContent {
                Column {
                    Label("label", modifier = SwingModifier.testTag("label"))
                    LinedField("field", style = { paintOutsets(FieldRing) })
                    Label(
                        "lined",
                        modifier =
                            SwingModifier
                                .testTag("lined")
                                .border(LineBorder(Color.RED, LINE)),
                        style = { paintOutsets(FieldRing) },
                    )
                }
            }
            val field = onNodeWithTag("field").fetch()
            onNodeWithTag("label").assertProperty(0) { x }
            assertEquals(-RING, field.x, "the field's ring is past its box")
            val lined = onNodeWithTag("lined").fetch()
            assertEquals(
                Rectangle(Point(0, field.y + field.height - RING), lined.preferredSize),
                lined.bounds,
                "a label the value names nothing for keeps its bounds as its box",
            )
        }

    @Test
    fun aNegativeInsetNamesNoPaintOutsets() =
        runComposeSwingTest {
            setContent {
                Column {
                    Label("label", modifier = SwingModifier.testTag("label"))
                    Label(
                        "tight",
                        modifier =
                            SwingModifier
                                .testTag("tight")
                                .emptyBorder(-2, 3, -2, 3),
                        style = { paintOutsets(PaintOutsets.FullInsets) },
                    )
                }
            }
            val label = onNodeWithTag("label").fetch()
            val tight = onNodeWithTag("tight").fetch()
            onNodeWithTag("label").assertProperty(0) { x }
            assertEquals(-3, tight.x, "the left inset is paint outsets")
            assertEquals(label.height, tight.layoutBounds.y, "a negative top inset names none")
        }

    @Test
    fun aSizeInTheModifierSetsTheBoxTheBorderReachesPast() =
        runComposeSwingTest {
            setContent {
                Column {
                    LinedField("field", SwingModifier.width(100), style = { paintOutsets(PaintOutsets.FullInsets) })
                }
            }
            onNodeWithTag("field").assertProperty(100 + 2 * LINE, "a 100 px box, the border past it") { width }
        }

    @Test
    fun anEqualValueMeasuresNothingAndALargerOneTakesLessSpace() =
        runComposeSwingTest {
            var value by mutableStateOf(TwoPixels, neverEqualPolicy())
            var measures = 0
            setContent {
                Column(modifier = SwingModifier.testTag("column")) {
                    LinedField("first", SwingModifier.countingMeasures { measures++ }, style = { paintOutsets(value) })
                    LinedField("second", style = { paintOutsets(value) })
                }
            }
            val column = onNodeWithTag("column").fetch()
            val height = column.preferredSize.height
            measures = 0

            value = PaintOutsets(2)
            awaitIdle()
            assertEquals(0, measures, "an equal value lays nothing out")

            value = PaintOutsets.FullInsets
            awaitIdle()
            assertEquals(-4, column.preferredSize.height - height, "2 px less per field")
        }

    @Test
    fun aPlacementReportNamesTheBoundsPastTheBox() =
        runComposeSwingTest {
            var value by mutableStateOf(PaintOutsets.FullInsets)
            val placements = mutableListOf<Rectangle>()
            setContent {
                Column {
                    LinedField("field", SwingModifier.onPlaced { placements += it }, style = { paintOutsets(value) })
                }
            }
            assertEquals(Point(-LINE, -LINE), placements.last().location)

            value = TwoPixels
            awaitIdle()
            assertEquals(Point(-2, -2), placements.last().location)
        }

    @Test
    fun aLabelPaddedByAnEmptyBorderLinesUpItsText() =
        runComposeSwingTest {
            setContent {
                Column {
                    Label(
                        "padded",
                        modifier = SwingModifier.testTag("padded").emptyBorder(8),
                        style = { paintOutsets(PaintOutsets.FullInsets) },
                    )
                    Label("plain", modifier = SwingModifier.testTag("plain"))
                }
            }
            val padded = onNodeWithTag("padded").fetch<JLabel>()
            val plain = onNodeWithTag("plain").fetch<JLabel>()
            assertEquals(0, plain.x)
            assertEquals(-8, padded.x)
            assertEquals(
                plain.x + plain.insets.left,
                padded.x + padded.insets.left,
                "the padded label's text starts where the plain label's does",
            )
        }
}

private fun Component.baseline(): Int = getBaseline(width, height)

/** A [RING] px focus ring for a text field and none for any other component, as a look and feel gives a label none. */
private object FieldRing : PaintOutsets {
    override fun outsetsOf(
        component: JComponent,
        insets: Insets,
        decorationOutsets: Insets,
    ): Insets = if (component is JTextField) Insets(RING, RING, RING, RING) else Insets(0, 0, 0, 0)
}

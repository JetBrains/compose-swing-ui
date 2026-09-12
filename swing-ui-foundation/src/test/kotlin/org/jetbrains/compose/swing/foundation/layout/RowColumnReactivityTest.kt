package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Dimension
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Everything a row or column is declared with is composition state, and every child extent it consumes
 * can change with Swing state. Both reach the live layout on the composition that declares them and
 * follow every later value, not only the first. A child that stops declaring a placement goes back to
 * the one its container gives it.
 *
 * Each test drives one declaration through at least two values and reads the bounds back after each.
 */
class RowColumnReactivityTest {
    @Test
    fun aColumnFollowsTheArrangementItIsDeclaredWith() =
        runComposeSwingTest {
            var arrangement by mutableStateOf(Arrangement.Top)
            setContent {
                Column(
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                    verticalArrangement = arrangement,
                ) {
                    repeat(CHILD_COUNT) { SizedChild(it) }
                }
            }

            assertEquals(columnRows(0, 40, 80), childBounds(), "the arrangement the column is declared with")

            arrangement = Arrangement.Bottom
            awaitIdle()
            assertEquals(columnRows(180, 220, 260), childBounds(), "the arrangement declared on the next pass")

            arrangement = Arrangement.spacedBy(GAP)
            awaitIdle()
            assertEquals(columnRows(0, 50, 100), childBounds(), "an arrangement that holds a gap of its own")
        }

    @Test
    fun aRowFollowsTheArrangementItIsDeclaredWith() =
        runComposeSwingTest {
            var arrangement by mutableStateOf(Arrangement.Start)
            setContent {
                Row(
                    modifier = containerModifier(MAIN_EXTENT, CROSS_EXTENT),
                    horizontalArrangement = arrangement,
                ) {
                    repeat(CHILD_COUNT) { SizedChild(it) }
                }
            }

            assertEquals(rowCells(0, 50, 100), childBounds(), "the arrangement the row is declared with")

            arrangement = Arrangement.End
            awaitIdle()
            assertEquals(rowCells(150, 200, 250), childBounds(), "the arrangement declared on the next pass")
        }

    @Test
    fun aColumnFollowsTheAlignmentItIsDeclaredWith() =
        runComposeSwingTest {
            var alignment by mutableStateOf(Alignment.Start)
            setContent {
                Column(
                    modifier = containerModifier(WIDE_CROSS_EXTENT, SHORT_MAIN_EXTENT),
                    horizontalAlignment = alignment,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(childAt(0), childBounds(), "the alignment the column is declared with")

            alignment = Alignment.CenterHorizontally
            awaitIdle()
            assertEquals(childAt(75), childBounds(), "the alignment declared on the next pass")

            alignment = Alignment.End
            awaitIdle()
            assertEquals(childAt(150), childBounds(), "the alignment declared on the pass after that")
        }

    @Test
    fun aChildFollowsTheWeightItDeclares() =
        runComposeSwingTest {
            var firstShare by mutableFloatStateOf(1f)
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, WEIGHTED_MAIN_EXTENT)) {
                    SizedChild(0, SwingModifier.weight(firstShare))
                    SizedChild(1, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, 170), Rectangle(0, 170, CHILD_WIDTH, 170)),
                childBounds(),
                "equal weights split the column's height evenly",
            )

            firstShare = 3f
            awaitIdle()
            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, 255), Rectangle(0, 255, CHILD_WIDTH, 85)),
                childBounds(),
                "a weight declared on a later pass re-splits the height three shares to one",
            )
        }

    @Test
    fun aChildThatStopsDeclaringAWeightGoesBackToTheHeightItPrefers() =
        runComposeSwingTest {
            var weighted by mutableStateOf(true)
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT)) {
                    SizedChild(0)
                    SizedChild(1, if (weighted) SwingModifier.weight(1f) else SwingModifier)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, MAIN_EXTENT - CHILD_HEIGHT),
                ),
                childBounds(),
                "the weighted child takes the height the column has left over",
            )

            weighted = false
            awaitIdle()
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "with the weight dropped the child claims nothing and the surplus is left empty again",
            )
        }

    @Test
    fun aChildFollowsTheSizeItDeclaresForItself() =
        runComposeSwingTest {
            var wide by mutableStateOf(false)
            setContent {
                Row(modifier = containerModifier(MAIN_EXTENT, CROSS_EXTENT)) {
                    val width = if (wide) WIDE_CHILD else CHILD_WIDTH
                    Label("child", modifier = SwingModifier.preferredSize(width, CHILD_HEIGHT))
                }
            }

            assertEquals(rowCells(0), childBounds(), "the size the child is first declared with")

            wide = true
            awaitIdle()
            assertEquals(
                listOf(Rectangle(0, 0, WIDE_CHILD, CHILD_HEIGHT)),
                childBounds(),
                "the size it declares on the next pass",
            )
        }

    @Test
    fun aBaselineAlignedSiblingMovesWhenAChildFontChanges() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var fontSize by mutableIntStateOf(12)
            setContent {
                Window(
                    onCloseRequest = {},
                    state = WindowState(size = Dimension(MAIN_EXTENT, CROSS_EXTENT)),
                    title = BASELINE_WINDOW_TITLE,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Label(
                            text = "Label:",
                            modifier =
                                SwingModifier
                                    .font(Font(Font.SANS_SERIF, Font.PLAIN, 12))
                                    .alignByBaseline(),
                        )
                        SwingNode(
                            factory = { JTextField("Text field", 12) },
                            modifier = SwingModifier.alignByBaseline(),
                            update = {
                                set(fontSize) { font = Font(Font.SANS_SERIF, Font.PLAIN, it) }
                            },
                        )
                    }
                }
            }

            val window = onWindowWithTitle(BASELINE_WINDOW_TITLE)
            val label = window.onNodeWithText("Label:").fetch<JLabel>()
            val field = window.onNodeWithText("Text field").fetch<JTextField>()
            val labelYBefore = label.y
            val fieldHeightBefore = field.height
            assertTrue(field.parent.isValid, "the realized Row must start valid")
            fontSize = 48
            awaitIdle()

            assertTrue(
                label.y > labelYBefore,
                "the small label must move down to the larger field's new baseline without another update: " +
                    "label y $labelYBefore -> ${label.y}, field height $fieldHeightBefore -> ${field.height}, " +
                    "preferred ${field.preferredSize.height}, row valid ${field.parent.isValid}",
            )
            assertTrue(
                field.height > fieldHeightBefore,
                "the larger font must increase the text field's measured height",
            )
        }

    @Test
    fun aChildFollowsTheAlignmentItDeclaresForItself() =
        runComposeSwingTest {
            var alignment by mutableStateOf<Alignment.Horizontal?>(Alignment.End)
            setContent {
                Column(
                    modifier = containerModifier(WIDE_CROSS_EXTENT, SHORT_MAIN_EXTENT),
                    horizontalAlignment = Alignment.Start,
                ) {
                    SizedChild(0, alignment?.let { SwingModifier.align(it) } ?: SwingModifier)
                }
            }

            assertEquals(childAt(150), childBounds(), "the alignment the child names for itself")

            alignment = Alignment.CenterHorizontally
            awaitIdle()
            assertEquals(childAt(75), childBounds(), "the alignment the child names on the next pass")

            alignment = null
            awaitIdle()
            assertEquals(childAt(0), childBounds(), "with its own alignment dropped the child takes the column's")
        }

    private companion object {
        const val MAIN_EXTENT = 300
        const val CROSS_EXTENT = 100
        const val GAP = 10

        // Two weighted children and nothing else, so the whole height is theirs to split.
        const val WEIGHTED_MAIN_EXTENT = 340

        // Room across the axis for an alignment to move a child through.
        const val WIDE_CROSS_EXTENT = 200
        const val SHORT_MAIN_EXTENT = 120

        /** A width that differs from the fixture child's, so a re-measurement shows in the bounds. */
        const val WIDE_CHILD = 90

        const val BASELINE_WINDOW_TITLE = "reactive-baseline-row"

        /** The bounds of a lone column child placed [left] pixels across the column. */
        fun childAt(left: Int): List<Rectangle> = listOf(Rectangle(left, 0, CHILD_WIDTH, CHILD_HEIGHT))
    }
}

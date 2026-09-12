package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The behavior a [Row] or [Column] falls back to at the edges of what an arrangement, an alignment or
 * a weight normally covers: a container with less room than its children ask for, a weighted surplus
 * that does not split into whole pixels, a weight sharing the surplus with a fixed arrangement gap, a
 * gap wider than the container has room for, a negative gap that overlaps its children instead of
 * spacing them, a container whose weighted child is itself a [Row] or [Column] with children of its
 * own, a child carrying a negative maximum size, and a weight so large that the extent it implies does
 * not fit an `Int`.
 */
class RowColumnEdgeCaseTest {
    @Test
    fun aRowGivesEveryChildTheRoomLeftOnceTheEarlierOnesHaveTakenTheirs() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(NARROW_MAIN, NARROW_CROSS)) {
                    SizedChild(0)
                    SizedChild(1)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, NARROW_MAIN, NARROW_CROSS),
                    Rectangle(NARROW_MAIN, 0, 0, NARROW_CROSS),
                ),
                childBounds(),
                "a row narrower than its children's combined width must give the first child all of it, " +
                    "leaving the second no width and no room to overflow into",
            )
        }

    @Test
    fun aColumnGivesEveryChildTheRoomLeftOnceTheEarlierOnesHaveTakenTheirs() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(NARROW_CROSS, NARROW_MAIN)) {
                    SizedChild(0)
                    SizedChild(1)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, NARROW_CROSS, NARROW_MAIN),
                    Rectangle(0, NARROW_MAIN, NARROW_CROSS, 0),
                ),
                childBounds(),
                "a column shorter than its children's combined height must give the first child all of it, " +
                    "leaving the second no height and no room to overflow into",
            )
        }

    @Test
    fun threeEquallyWeightedChildrenSplitASurplusThatDoesNotDivideEvenlyAndStillFillItExactly() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, UNEVEN_SURPLUS)) {
                    SizedChild(0, SwingModifier.weight(1f))
                    SizedChild(1, SwingModifier.weight(1f))
                    SizedChild(2, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, 34),
                    Rectangle(0, 34, CHILD_WIDTH, 33),
                    Rectangle(0, 67, CHILD_WIDTH, 33),
                ),
                childBounds(),
                "the pixel a third of 100 loses to rounding must go to the first child, and the three " +
                    "heights must still add up to the whole 100px surplus between them",
            )
        }

    @Test
    fun weightedChildrenShareTheSurplusOnceTheArrangementsSpacingIsHeldBack() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = containerModifier(SPACED_ROW_WIDTH, CROSS_EXTENT),
                    horizontalArrangement = Arrangement.spacedBy(SPACING),
                ) {
                    SizedChild(0, SwingModifier.weight(1f))
                    SizedChild(1, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 16, CHILD_HEIGHT),
                    Rectangle(24, 0, 16, CHILD_HEIGHT),
                ),
                childBounds(),
                "the row must hold its 8px gap back from the 40px width before splitting what is left " +
                    "between the two weighted children, 16px each",
            )
        }

    @Test
    fun aSpacedByGapWiderThanTheRowTakesTheRoomLeftAndStillKeepsEveryChildInsideIt() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = containerModifier(GAP_TEST_ROW_WIDTH, CROSS_EXTENT),
                    horizontalArrangement = Arrangement.spacedBy(HUGE_GAP),
                ) {
                    SmallChild(0)
                    SmallChild(1)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, SMALL_CHILD_WIDTH, SMALL_CHILD_HEIGHT),
                    Rectangle(GAP_TEST_ROW_WIDTH, 0, 0, SMALL_CHILD_HEIGHT),
                ),
                childBounds(),
                "a 1000px gap declared between two 20px children in a 44px row must shrink to the 24px the " +
                    "row has left once the first child has taken its width, leaving the second child no room " +
                    "to be measured in and placing it at the trailing edge rather than 1000px past it",
            )
        }

    @Test
    fun aNegativeSpacedByGapOverlapsTheChildrenInsteadOfSpacingThem() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = containerModifier(WIDE_ROW_WIDTH, CROSS_EXTENT),
                    horizontalArrangement = Arrangement.spacedBy(NEGATIVE_GAP),
                ) {
                    SizedChild(0)
                    SizedChild(1)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(40, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "a -10px gap between two 50px children must pull the second 10px under the first's trailing " +
                    "edge rather than space them apart",
            )
        }

    @Test
    fun aRowNestedInAColumnIsMeasuredAndPlacedAsAWeightedChildAndThenLaysOutItsOwnChildren() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, NESTED_COLUMN_HEIGHT)) {
                    SizedChild(0)
                    Row(
                        modifier = SwingModifier.testTag(NESTED_ROW_TAG).weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SizedChild(1)
                        SizedChild(2)
                    }
                }
            }

            val nestedRow = onNodeWithTag(NESTED_ROW_TAG).fetch<JPanel>()

            assertEquals(
                Rectangle(0, CHILD_HEIGHT, CROSS_EXTENT, NESTED_COLUMN_HEIGHT - CHILD_HEIGHT),
                nestedRow.bounds,
                "the weight must give the nested row the column's whole leftover height, and the row's own " +
                    "preferred width since it declared no fill of its own",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 60, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 60, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                nestedRow.childrenInDeclarationOrder().map { it.bounds },
                "inside the height its weight granted, the nested row must place its own children by its " +
                    "own arrangement and alignment - spread edge to edge and centered vertically - exactly " +
                    "as it would laid out on its own",
            )
        }

    @Test
    fun aWeightedNestedRowKeepsItsOwnWidthInAColumnWiderThanItPrefers() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(WIDE_CROSS_EXTENT, NESTED_COLUMN_HEIGHT)) {
                    SizedChild(0)
                    Row(
                        modifier = SwingModifier.testTag(NESTED_ROW_TAG).weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SizedChild(1)
                        SizedChild(2)
                    }
                }
            }

            val nestedRow = onNodeWithTag(NESTED_ROW_TAG).fetch<JPanel>()

            assertEquals(
                Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH * 2, NESTED_COLUMN_HEIGHT - CHILD_HEIGHT),
                nestedRow.bounds,
                "a weighted row without fillMaxWidth must keep the width it prefers " +
                    "once its main-axis weight is resolved, not stretch to a column wider than that",
            )
            assertEquals(
                rowCells(0, CHILD_WIDTH),
                nestedRow.childrenInDeclarationOrder().map { it.bounds },
                "and must space its children across the width it settled on rather than the wider one its " +
                    "column offered, which would put the last of them outside the row",
            )
        }

    @Test
    fun aNestedRowsWeightsAreDividedOnceRatherThanAgainAtTheWidthTheyShrankItTo() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(SPACIOUS_CROSS_EXTENT, NESTED_COLUMN_HEIGHT)) {
                    Row(modifier = SwingModifier.testTag(NESTED_ROW_TAG)) {
                        SizedChild(0, SwingModifier.weight(1f, fill = false))
                        SizedChild(1, SwingModifier.weight(3f, fill = false))
                    }
                }
            }

            val nestedRow = onNodeWithTag(NESTED_ROW_TAG).fetch<JPanel>()

            assertEquals(
                rowCells(0, CHILD_WIDTH),
                nestedRow.childrenInDeclarationOrder().map { it.bounds },
                "a child claiming a share it does not fill shrinks the row below the width it was offered, " +
                    "and the row must place the children that pass measured rather than divide the width " +
                    "they shrank it to among the same weights again",
            )
        }

    @Test
    fun aChildWhoseMaximumSizeIsNegativeIsHeldToNothingRatherThanTakingTheRowsPassDown() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(CROSS_EXTENT, CROSS_EXTENT)) {
                    SizedChild(0, SwingModifier.weight(1f).maximumSize(NEGATIVE_MAXIMUM, NEGATIVE_MAXIMUM))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 0, 0)),
                childBounds(),
                "a maximum size below zero must hold the child to no width and no height, and the row must " +
                    "complete its pass rather than measure that child under an inverted range",
            )
        }

    @Test
    fun aRowAskedWhatItPrefersBesideAWeightTooLargeToFitAnIntAsksForTheLargestWidthThereIs() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier =
                        SwingModifier
                            .testTag(CONTAINER_TAG)
                            .border(EmptyBorder(BORDER_TOP, BORDER_LEFT, BORDER_BOTTOM, BORDER_RIGHT)),
                ) {
                    SizedChild(0, SwingModifier.weight(Float.MAX_VALUE))
                    SizedChild(1, SwingModifier.weight(1f))
                    SizedChild(2)
                }
            }

            assertEquals(
                Dimension(Int.MAX_VALUE, CHILD_HEIGHT + BORDER_TOP + BORDER_BOTTOM),
                containerPreferredSize(),
                "the width the 1f child implies for a weight of Float.MAX_VALUE beside it does not fit an " +
                    "Int, so the row must ask for the largest width there is and keep the height its " +
                    "children ask for - neither the unweighted child beside that width nor the border " +
                    "around it may carry the row past it and back to a negative width",
            )
        }

    private companion object {
        // Narrower, or shorter, than the two 50x40 fixture children combined, so a deficit is unmistakable.
        const val NARROW_MAIN = 30
        const val NARROW_CROSS = 10

        // Wider than a fixture child, so a weight or a gap is never short of room on its own.
        const val CROSS_EXTENT = 100

        // 100px split three ways by an equal weight does not divide evenly.
        const val UNEVEN_SURPLUS = 100

        // 40px split between two equally weighted children once an 8px gap is held back.
        const val SPACING = 8
        const val SPACED_ROW_WIDTH = 40

        // Two 20px children in a 44px row: 24px of room for a gap declared far wider than that, which
        // the gap then takes in full, leaving the second child nothing to be measured in.
        const val SMALL_CHILD_WIDTH = 20
        const val SMALL_CHILD_HEIGHT = 40
        const val GAP_TEST_ROW_WIDTH = 44
        const val HUGE_GAP = 1000

        // Wide enough that a negative gap's overlap, not a shortfall, is what the row demonstrates.
        const val WIDE_ROW_WIDTH = 300
        const val NEGATIVE_GAP = -10

        // Room for the fixture child plus a weighted nested row's leftover height.
        const val NESTED_COLUMN_HEIGHT = 200
        const val NESTED_ROW_TAG = "nestedRow"

        // Wider than the nested row's own preferred width (two fixture children), so a stretched row
        // would be caught: a column exactly as wide as the row prefers cannot tell the two apart.
        const val WIDE_CROSS_EXTENT = 150

        // A maximum a component may carry as readily as any other, and which no extent can fit inside.
        const val NEGATIVE_MAXIMUM = -1

        // The four insets of a border on the row, each different, so no two can be mistaken.
        const val BORDER_TOP = 3
        const val BORDER_LEFT = 7
        const val BORDER_BOTTOM = 11
        const val BORDER_RIGHT = 13

        // Four times the width a nested row prefers, so a share of it is unmistakably wider than a
        // child asks for and a second division of the row's own width grants visibly less.
        const val SPACIOUS_CROSS_EXTENT = 400
    }
}

/** A child narrower than [SizedChild], so a 44px row still has room to hold part of an oversized gap. */
@Composable
private fun SmallChild(index: Int) {
    Label("small $index", modifier = SwingModifier.preferredSize(20, 40))
}

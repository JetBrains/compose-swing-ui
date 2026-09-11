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
import javax.swing.JComponent
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The behavior a [Row] or [Column] falls back to at the edges of what an arrangement, an alignment or
 * a weight normally covers: a container with less space than its children ask for, a weighted surplus
 * that does not split into whole pixels, a weight sharing the surplus with a fixed arrangement gap, a
 * gap wider than the container has space for, a negative gap that overlaps its children instead of
 * spacing them, a container whose weighted child is itself a [Row] or [Column] with children of its
 * own, a child carrying a negative maximum size, and a weight so large that the extent it implies does
 * not fit an `Int`.
 */
class RowColumnEdgeCaseTest {
    @Test
    fun aRowGivesEveryChildTheSpaceLeftOnceTheEarlierOnesHaveTakenTheirs() =
        runComposeSwingTest {
            setContent {
                // Narrower and shorter than the two fixture children combined, so a deficit is unmistakable.
                Row(modifier = containerModifier(30, 10)) {
                    SizedChild(0)
                    SizedChild(1)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 30, 10),
                    Rectangle(30, 0, 0, 10),
                ),
                childBounds(),
                "a row narrower than its children's combined width must give the first child all of it, " +
                    "leaving the second no width and no space to overflow into",
            )
        }

    @Test
    fun aColumnGivesEveryChildTheSpaceLeftOnceTheEarlierOnesHaveTakenTheirs() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(10, 30)) {
                    SizedChild(0)
                    SizedChild(1)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 10, 30),
                    Rectangle(0, 30, 10, 0),
                ),
                childBounds(),
                "a column shorter than its children's combined height must give the first child all of it, " +
                    "leaving the second no height and no space to overflow into",
            )
        }

    @Test
    fun threeEquallyWeightedChildrenSplitASurplusThatDoesNotDivideEvenlyAndStillFillItExactly() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(100, 100)) {
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
                    modifier = containerModifier(40, 100),
                    horizontalArrangement = Arrangement.spacedBy(8),
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
    fun aSpacedByGapWiderThanTheRowTakesTheSpaceLeftAndStillKeepsEveryChildInsideIt() =
        runComposeSwingTest {
            setContent {
                // Two 20px children in a 44px row: 24px of space for a gap declared far wider than that, which
                // the gap then takes in full, leaving the second child nothing to be measured in.
                Row(
                    modifier = containerModifier(44, 100),
                    horizontalArrangement = Arrangement.spacedBy(1000),
                ) {
                    SmallChild(0)
                    SmallChild(1)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 20, 40),
                    Rectangle(44, 0, 0, 40),
                ),
                childBounds(),
                "a 1000px gap declared between two 20px children in a 44px row must shrink to the 24px the " +
                    "row has left once the first child has taken its width, leaving the second child no space " +
                    "to be measured in and placing it at the trailing edge rather than 1000px past it",
            )
        }

    @Test
    fun aNegativeSpacedByGapOverlapsTheChildrenInsteadOfSpacingThem() =
        runComposeSwingTest {
            setContent {
                // Wide enough that a negative gap's overlap, not a shortfall, is what the row demonstrates.
                Row(
                    modifier = containerModifier(300, 100),
                    horizontalArrangement = Arrangement.spacedBy(-10),
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
                Column(modifier = containerModifier(100, 200)) {
                    SizedChild(0)
                    Row(
                        modifier = SwingModifier.testTag("nestedRow").weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SizedChild(1)
                        SizedChild(2)
                    }
                }
            }

            val nestedRow = onNodeWithTag("nestedRow").fetch<JComponent>()

            assertEquals(
                Rectangle(0, CHILD_HEIGHT, 100, 160),
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
                // Wider than the nested row's own preferred width (two fixture children), so a stretched row
                // would be caught: a column exactly as wide as the row prefers cannot tell the two apart.
                Column(modifier = containerModifier(150, 200)) {
                    SizedChild(0)
                    Row(
                        modifier = SwingModifier.testTag("nestedRow").weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SizedChild(1)
                        SizedChild(2)
                    }
                }
            }

            val nestedRow = onNodeWithTag("nestedRow").fetch<JComponent>()

            assertEquals(
                Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH * 2, 160),
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
                // Four times the width a nested row prefers, so a share of it is unmistakably wider than a
                // child asks for and a second division of the row's own width grants visibly less.
                Column(modifier = containerModifier(400, 200)) {
                    Row(modifier = SwingModifier.testTag("nestedRow")) {
                        SizedChild(0, SwingModifier.weight(1f, fill = false))
                        SizedChild(1, SwingModifier.weight(3f, fill = false))
                    }
                }
            }

            val nestedRow = onNodeWithTag("nestedRow").fetch<JComponent>()

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
                Row(modifier = containerModifier(100, 100)) {
                    SizedChild(0, SwingModifier.weight(1f).maximumSize(-1, -1))
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
                            .border(EmptyBorder(3, 7, 11, 13)),
                ) {
                    SizedChild(0, SwingModifier.weight(Float.MAX_VALUE))
                    SizedChild(1, SwingModifier.weight(1f))
                    SizedChild(2)
                }
            }

            assertEquals(
                Dimension(Int.MAX_VALUE, 54),
                containerPreferredSize(),
                "the width the 1f child implies for a weight of Float.MAX_VALUE beside it does not fit an " +
                    "Int, so the row must ask for the largest width there is and keep the height its " +
                    "children ask for - neither the unweighted child beside that width nor the border " +
                    "around it may carry the row past it and back to a negative width",
            )
        }
}

/** A child narrower than [SizedChild], so a 44px row still has space to hold part of an oversized gap. */
@Composable
private fun SmallChild(index: Int) {
    Label("small $index", modifier = SwingModifier.preferredSize(20, 40))
}

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import org.jetbrains.compose.swing.modifier.layout.alignmentY
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a [Row], a [Column] or a [Box] answers the layout above it, which is everything a parent can ask
 * of a container before it decides where to put it: the smallest extent it can be laid out at, the
 * largest, the alignment its content lines up on, and the extent it prefers.
 *
 * A container's minimum carries weight beyond a parent that honors it: `SplitPane` clamps divider travel
 * against it, so a minimum read off the wrong extent either refuses the divider room it has or lets it
 * crush content that said it could not shrink.
 */
class RowColumnParentQueryTest {
    @Test
    fun aColumnsMinimumStacksWhatItsChildrenCanShrinkTo() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    repeat(CHILD_COUNT) { index -> SizedChild(index, SwingModifier.minimumSize(MIN_WIDTH, MIN_HEIGHT)) }
                }
            }

            assertEquals(
                Dimension(MIN_WIDTH, CHILD_COUNT * MIN_HEIGHT),
                container().minimumSize,
                "a column must ask for no more than the heights its children can shrink to, stacked, and the " +
                    "widest of the widths they can shrink to - not the extents they prefer",
            )
        }

    @Test
    fun aColumnsMinimumHoldsTheGapItsArrangementKeeps() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    verticalArrangement = Arrangement.spacedBy(SPACING),
                ) {
                    repeat(CHILD_COUNT) { index -> SizedChild(index, SwingModifier.minimumSize(MIN_WIDTH, MIN_HEIGHT)) }
                }
            }

            assertEquals(
                Dimension(MIN_WIDTH, CHILD_COUNT * MIN_HEIGHT + (CHILD_COUNT - 1) * SPACING),
                container().minimumSize,
                "the gap the arrangement keeps between each adjacent pair must be held in the minimum as well " +
                    "as in the preferred extent, since a column shrunk to its minimum still keeps its children apart",
            )
        }

    @Test
    fun aWeightedChildsShareOfAColumnsMinimumIsTakenAgainstItsOwnMinimum() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.minimumSize(MIN_WIDTH, MIN_HEIGHT))
                    SizedChild(1, SwingModifier.weight(1f).minimumSize(MIN_WIDTH, WEIGHTED_MIN_HEIGHT))
                }
            }

            assertEquals(
                Dimension(MIN_WIDTH, MIN_HEIGHT + WEIGHTED_MIN_HEIGHT),
                container().minimumSize,
                "the height a weight implies must be the height that child can shrink to divided by its " +
                    "share, so the column asks for the weighted child's minimum rather than what it prefers",
            )
        }

    @Test
    fun aRowAColumnAndABoxTakeAnyExtentTheyAreOffered() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(ROW_TAG)) { SizedChild(0) }
                Column(modifier = SwingModifier.testTag(COLUMN_TAG)) { SizedChild(1) }
                Box(modifier = SwingModifier.testTag(BOX_TAG)) { SizedChild(2) }
            }

            val unbounded = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            for (tag in listOf(ROW_TAG, COLUMN_TAG, BOX_TAG)) {
                assertEquals(
                    unbounded,
                    panel(tag).maximumSize,
                    "'$tag' must offer its parent no ceiling: it places whatever extent it is given rather " +
                        "than refusing the surplus",
                )
            }
        }

    @Test
    fun aRowReportsTheAlignmentItsFirstChildReports() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.alignmentX(LEADING).alignmentY(TRAILING))
                    SizedChild(1, SwingModifier.alignmentX(TRAILING).alignmentY(LEADING))
                }
            }

            // Swing's physical array is reversed by stacking, but a row's parent alignment follows the
            // declaration order it measures and arranges.
            assertEquals(LEADING, container().alignmentX, "the row must report the x alignment of its first child")
            assertEquals(TRAILING, container().alignmentY, "and the y alignment of that same child")
        }

    @Test
    fun aColumnReportsTheAlignmentItsFirstChildReports() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.alignmentX(LEADING).alignmentY(TRAILING))
                    SizedChild(1, SwingModifier.alignmentX(TRAILING).alignmentY(LEADING))
                }
            }

            // A column makes the same declaration-order promise even though its visual stack is reversed.
            assertEquals(LEADING, container().alignmentX, "the column must report the x alignment of its first child")
            assertEquals(TRAILING, container().alignmentY, "and the y alignment of that same child")
        }

    @Test
    fun aBoxReportsTheAlignmentOfTheChildOnTopOfTheStack() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.alignmentX(LEADING).alignmentY(TRAILING))
                    SizedChild(1, SwingModifier.alignmentX(TRAILING).alignmentY(LEADING))
                }
            }

            // A box stacks a later child over an earlier one, so the last declared is the one on top.
            assertEquals(TRAILING, container().alignmentX, "the box must report the x alignment of the child on top")
            assertEquals(LEADING, container().alignmentY, "and the y alignment of that same child")
        }

    @Test
    fun aHiddenChildDecidesWhatItsContainerReportsLikeAnyOther() =
        runComposeSwingTest {
            setContent {
                // In each container the hidden child is the one that is asked: the first declared in a row,
                // the last declared - the top of the stack - in a box.
                Row(modifier = SwingModifier.testTag(ROW_TAG)) {
                    SizedChild(0, SwingModifier.alignmentX(TRAILING).alignmentY(LEADING).visible(false))
                    SizedChild(1, SwingModifier.alignmentX(LEADING).alignmentY(TRAILING))
                }
                Box(modifier = SwingModifier.testTag(BOX_TAG)) {
                    SizedChild(0, SwingModifier.alignmentX(LEADING).alignmentY(TRAILING))
                    SizedChild(1, SwingModifier.alignmentX(TRAILING).alignmentY(LEADING).visible(false))
                }
            }

            for (tag in listOf(ROW_TAG, BOX_TAG)) {
                val container = panel(tag)
                assertEquals(TRAILING, container.alignmentX, "$tag reports the hidden child's x alignment")
                assertEquals(LEADING, container.alignmentY, "and its y alignment on the other axis")
            }
        }

    @Test
    fun aContainerWithNothingInItReportsTheCenteredAlignment() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(ROW_TAG)) {}
                Column(modifier = SwingModifier.testTag(COLUMN_TAG)) {}
                Box(modifier = SwingModifier.testTag(BOX_TAG)) {}
            }

            for (tag in listOf(ROW_TAG, COLUMN_TAG, BOX_TAG)) {
                assertEquals(
                    Component.CENTER_ALIGNMENT,
                    panel(tag).alignmentX,
                    "'$tag' has no child to take an x alignment from, so it must report the centered default",
                )
                assertEquals(
                    Component.CENTER_ALIGNMENT,
                    panel(tag).alignmentY,
                    "'$tag' has no child to take a y alignment from either",
                )
            }
        }

    @Test
    fun aColumnsBorderInsetsReachBothTheExtentsItAsksFor() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG).border(EmptyBorder(TOP, LEFT, BOTTOM, RIGHT))) {
                    SizedChild(0, SwingModifier.minimumSize(MIN_WIDTH, MIN_HEIGHT))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH + LEFT + RIGHT, CHILD_HEIGHT + TOP + BOTTOM),
                container().preferredSize,
                "the room the border takes must be added to the extent the column prefers, or its child is " +
                    "laid out in less width and height than it asked for",
            )
            assertEquals(
                Dimension(MIN_WIDTH + LEFT + RIGHT, MIN_HEIGHT + TOP + BOTTOM),
                container().minimumSize,
                "and to the extent it can shrink to, which the border does not shrink with it",
            )
        }

    private companion object {
        // Below the fixture child on both axes, so a minimum read off the preferred extent is unmistakable.
        const val MIN_WIDTH = 18
        const val MIN_HEIGHT = 12

        // Different again, so the weighted child's own minimum is what its share is taken against.
        const val WEIGHTED_MIN_HEIGHT = 30

        const val SPACING = 8

        // Neither is the centered default, and the two differ, so an alignment reported on the wrong axis
        // is caught as readily as a fixed one.
        const val LEADING = 0f
        const val TRAILING = 1f

        // The four insets of the border a column is given, each different, so no two can be mistaken.
        const val TOP = 5
        const val LEFT = 10
        const val BOTTOM = 15
        const val RIGHT = 20

        const val ROW_TAG = "row"
        const val COLUMN_TAG = "column"
        const val BOX_TAG = "box"
    }
}

/** The one container a test tagged [CONTAINER_TAG], which every reading of a single container is taken from. */
private fun ComposeSwingTest.container(): JComponent = panel(CONTAINER_TAG)

/** The container a test tagged [tag], for a case that declares more than one. */
private fun ComposeSwingTest.panel(tag: String): JComponent = onNodeWithTag(tag).fetch<JComponent>()

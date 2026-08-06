package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The behaviour a [Row] or [Column] falls back to at the edges of what an arrangement, an alignment or
 * a weight normally covers: a container with less room than its children ask for, a weighted surplus
 * that does not split into whole pixels, a weight sharing the surplus with a fixed arrangement gap, a
 * gap wider than the container has room for, and a container whose weighted child is itself a [Row] or
 * [Column] with children of its own.
 */
class RowColumnEdgeCaseTest {
    @Test
    fun aRowGivesEveryChildTheRoomLeftOnceTheEarlierOnesHaveTakenTheirs() = runComposeSwingTest {
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
    fun aColumnGivesEveryChildTheRoomLeftOnceTheEarlierOnesHaveTakenTheirs() = runComposeSwingTest {
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
    fun threeEquallyWeightedChildrenSplitASurplusThatDoesNotDivideEvenlyAndStillFillItExactly() = runComposeSwingTest {
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
    fun weightedChildrenShareTheSurplusOnceTheArrangementsSpacingIsHeldBack() = runComposeSwingTest {
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
    fun aSpacedByGapWiderThanTheRowTakesTheRoomLeftAndStillKeepsEveryChildInsideIt() = runComposeSwingTest {
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
    fun aRowNestedInAColumnIsMeasuredAndPlacedAsAWeightedChildAndThenLaysOutItsOwnChildren() = runComposeSwingTest {
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
            nestedRow.components.map { it.bounds },
            "inside the height its weight granted, the nested row must place its own children by its " +
                "own arrangement and alignment - spread edge to edge and centered vertically - exactly " +
                "as it would laid out on its own",
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

        // Room for the fixture child plus a weighted nested row's leftover height.
        const val NESTED_COLUMN_HEIGHT = 200
        const val NESTED_ROW_TAG = "nestedRow"
    }
}

/** A child narrower than [SizedChild], so a 44px row still has room to hold part of an oversized gap. */
@Composable
private fun SmallChild(index: Int) {
    Label("small $index", modifier = SwingModifier.preferredSize(20, 40))
}

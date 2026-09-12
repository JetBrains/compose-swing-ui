package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A row lines its children up along its width and a column stacks them down its height, each child at
 * the extent it prefers unless it claims a share of what the container has left over. Across the axis
 * a child keeps the extent it prefers and sits where its own alignment, or its container's, puts it.
 *
 * A case that androidx `foundation-layout`'s own `RowColumnTest` makes keeps that test's name, so the
 * two files read side by side. A case that test makes and this library cannot - an alignment line
 * beyond the baseline, a cross-axis-parameterized intrinsic, `IntrinsicSize` as a modifier - is
 * replaced here by one pinning what this library does instead.
 *
 * Port provenance: case names and order follow AndroidX
 * [`RowColumnTest.kt` at 2846f08e5bd9b827b90d7b6c07dc209b63c7e5fb](https://github.com/androidx/androidx/blob/2846f08e5bd9b827b90d7b6c07dc209b63c7e5fb/compose/foundation/foundation-layout/src/androidDeviceTest/kotlin/androidx/compose/foundation/layout/RowColumnTest.kt),
 * including its RTL correction. Weighted intrinsic rounding follows
 * [`RowColumnImpl.kt` at dcaa116fbfda77e64a319e1668056ce3b032469f](https://github.com/androidx/androidx/blob/dcaa116fbfda77e64a319e1668056ce3b032469f/compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/foundation/layout/RowColumnImpl.kt#L414-L437).
 *
 * One case here per case in that suite, in its order and under its names, so the two can be read
 * against each other and a case dropped in translation shows up as a gap. That correspondence is what
 * holds the class together and what the size suppression below protects: it is as large as its source
 * rather than a class that grew by accretion, and splitting it would break the reading it exists for.
 */
@Suppress("LargeClass")
class RowColumnTest {
    @Test
    fun testRow_measuresChildrenCorrectly_whenMeasuredWithInfiniteWidth() {
        val laidOut =
            measuredUnder(
                rowPolicy(),
                Constraints(minWidth = 100, maxWidth = Int.MAX_VALUE),
                FixedSizeChild(30) to null,
                FixedSizeChild(30) to null,
                FixedSizeChild(30) to LinearConstraint(weight = WeightPlacement(1f, fill = true)),
            )

        assertEquals(
            listOf(
                Rectangle(0, 0, 30, 0),
                Rectangle(30, 0, 30, 0),
                Rectangle(60, 0, 40, 0),
            ),
            laidOut,
            "an unbounded width narrows to nothing for a child that claims no share, and the weighted " +
                "child divides the least width the row was asked to occupy",
        )
    }

    @Test
    fun testColumn_measuresChildrenCorrectly_whenMeasuredWithInfiniteHeight() {
        val laidOut =
            measuredUnder(
                columnPolicy(),
                Constraints(minHeight = 100, maxHeight = Int.MAX_VALUE),
                FixedSizeChild(height = 30) to null,
                FixedSizeChild(height = 30) to null,
                FixedSizeChild(height = 30) to LinearConstraint(weight = WeightPlacement(1f, fill = true)),
            )

        assertEquals(
            listOf(
                Rectangle(0, 0, 0, 30),
                Rectangle(0, 30, 0, 30),
                Rectangle(0, 60, 0, 40),
            ),
            laidOut,
            "an unbounded height narrows to nothing for a child that claims no share, and the weighted " +
                "child divides the least height the column was asked to occupy",
        )
    }

    @Test
    fun testRow_protectsAgainstOverflow() {
        val laidOut =
            measuredUnder(
                rowPolicy(),
                Constraints.Unbounded,
                FixedSizeChild(1 shl 23) to null,
                FixedSizeChild(1 shl 23) to LinearConstraint(weight = WeightPlacement(1f, fill = true)),
                FixedSizeChild(1 shl 23) to LinearConstraint(weight = WeightPlacement(1e-8f, fill = true)),
            )

        assertEquals(
            listOf(
                Rectangle(0, 0, 1 shl 23, 0),
                Rectangle(1 shl 23, 0, 0, 0),
                Rectangle(1 shl 23, 0, 0, 0),
            ),
            laidOut,
            "a child wider than any container, beside a weight small enough to round to nothing, must " +
                "leave the arithmetic intact rather than wrapping past the largest extent there is",
        )
    }

    @Test
    fun aWeightedChildAsksForWhatItsSiblingsImplyRatherThanForAllOfItsParent() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 50, 50, SwingModifier.weight(1f))
                    Child(1, 100, 100)
                }
            }

            assertEquals(
                Dimension(150, 100),
                containerPreferredSize(),
                "a row asks for the width its own children imply - the share a weight claims is worked out " +
                    "against them, because nothing has offered the row a width to fill",
            )
        }

    @Test
    fun aWeightedChildTakesTheWholeOfAWidthItsParentImposes() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(400, 100)) {
                    Child(0, 50, 50, SwingModifier.weight(1f))
                    Child(1, 100, 100)
                }
            }

            assertEquals(
                Rectangle(0, 0, 300, 50),
                childBounds()[0],
                "given a width it did not ask for, the row hands the whole surplus to the weighted child",
            )
        }

    @Test
    fun aWeightedChildAsksForWhatItsSiblingsImplyInAColumnToo() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 50, 50, SwingModifier.weight(1f))
                    Child(1, 100, 100)
                }
            }

            assertEquals(
                Dimension(100, 150),
                containerPreferredSize(),
                "a column asks for the height its own children imply, the same way a row asks for its width",
            )
        }

    @Test
    fun testRow() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 50, 50)
                    Child(1, 2 * 50, 2 * 50)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 50),
                    Rectangle(50, 0, 2 * 50, 2 * 50),
                ),
                childBounds(),
                "a row must line its children up in declaration order, each at the size it prefers and " +
                    "against the row's top edge, so neither child is stretched to the other's size",
            )
        }

    @Test
    fun testRow_withChildrenWithWeight() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(300, 80)) {
                    Child(0, 50, 80, SwingModifier.weight(1f))
                    Child(1, 50, 80, SwingModifier.weight(2f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 300 / 3, 80),
                    Rectangle(300 / 3, 0, 300 / 3 * 2, 80),
                ),
                childBounds(),
                "children weighted 1f and 2f must take a third and two thirds of the row's whole width, so " +
                    "the width either of them prefers is no longer what it occupies",
            )
        }

    @Test
    fun testRow_withChildrenWithWeightNonFilling() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(300, 2 * 80)) {
                    Child(0, 50, 80, SwingModifier.weight(1f, fill = false))
                    Child(1, 50, 2 * 80, SwingModifier.weight(2f, fill = false))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 80),
                    Rectangle(50, 0, 50, 2 * 80),
                ),
                childBounds(),
                "a child that does not fill must keep the width it prefers however large the share it was " +
                    "granted, and must be followed at that width rather than at its share",
            )
        }

    @Test
    fun testRow_withChildrenWithMaxValueWeight() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(300, 80)) {
                    Child(0, 50, 80, SwingModifier.weight(Float.MAX_VALUE))
                    Child(1, 50, 80, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 300, 80),
                    Rectangle(300, 0, 0, 80),
                ),
                childBounds(),
                "a child claiming the largest share there is must take the whole width, leaving its sibling " +
                    "none of it - not a share the arithmetic rounded up into a width",
            )
        }

    @Test
    fun testRow_withChildrenWithPositiveInfinityWeight() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(300, 80)) {
                    Child(
                        0,
                        50,
                        80,
                        SwingModifier.weight(Float.POSITIVE_INFINITY),
                    )
                    Child(1, 50, 80, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 300, 80),
                    Rectangle(300, 0, 0, 80),
                ),
                childBounds(),
                "an infinite weight must be taken as the largest finite one, so the child asking for " +
                    "everything is given the whole width rather than a width no arithmetic can name",
            )
        }

    @Test
    fun testRow_invalidWeight() {
        with(RowScopeInstance) {
            assertFailsWith<IllegalArgumentException>("a negative share of a row's width is no share at all") {
                SwingModifier.weight(-1f)
            }
            assertFailsWith<IllegalArgumentException>("a weight there is no share to compute from is no weight") {
                SwingModifier.weight(Float.NaN)
            }
            assertFailsWith<IllegalArgumentException>("a weight below zero is refused however far below it is") {
                SwingModifier.weight(Float.NEGATIVE_INFINITY)
            }
        }
    }

    @Test
    fun testColumn() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 50, 50)
                    Child(1, 2 * 50, 2 * 50)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 50),
                    Rectangle(0, 50, 2 * 50, 2 * 50),
                ),
                childBounds(),
                "a column must stack its children in declaration order, each at the size it prefers and " +
                    "against the column's leading edge, so neither child is stretched to the other's size",
            )
        }

    @Test
    fun testColumn_withChildrenWithWeight() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(80, 300)) {
                    Child(0, 80, 50, SwingModifier.weight(1f))
                    Child(1, 80, 50, SwingModifier.weight(2f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 80, 300 / 3),
                    Rectangle(0, 300 / 3, 80, 300 / 3 * 2),
                ),
                childBounds(),
                "children weighted 1f and 2f must take a third and two thirds of the column's whole height, " +
                    "so the height either of them prefers is no longer what it occupies",
            )
        }

    @Test
    fun testColumn_withChildrenWithWeightNonFilling() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(80, 300)) {
                    Child(
                        0,
                        80,
                        50,
                        SwingModifier.weight(1f, fill = false),
                    )
                    Child(
                        1,
                        80,
                        50,
                        SwingModifier.weight(2f, fill = false),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 80, 50),
                    Rectangle(0, 50, 80, 50),
                ),
                childBounds(),
                "a child that does not fill must keep the height it prefers however large the share it was " +
                    "granted, and the child after it must follow at that height rather than after its share",
            )
        }

    @Test
    fun testColumn_withChildrenWithMaxValueWeight() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(80, 300)) {
                    Child(
                        0,
                        80,
                        50,
                        SwingModifier.weight(Float.MAX_VALUE),
                    )
                    Child(1, 80, 50, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 80, 300),
                    Rectangle(0, 300, 80, 0),
                ),
                childBounds(),
                "a child claiming the largest share there is must take the whole height, leaving its sibling " +
                    "none of it - not a share the arithmetic rounded up into a height",
            )
        }

    @Test
    fun testColumn_withChildrenWithPositiveInfinityWeight() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(80, 300)) {
                    Child(
                        0,
                        80,
                        50,
                        SwingModifier.weight(Float.POSITIVE_INFINITY),
                    )
                    Child(1, 80, 50, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 80, 300),
                    Rectangle(0, 300, 80, 0),
                ),
                childBounds(),
                "an infinite weight must be taken as the largest finite one, so the child asking for " +
                    "everything is given the whole height rather than a height no arithmetic can name",
            )
        }

    @Test
    fun testColumn_invalidWeight() {
        with(ColumnScopeInstance) {
            assertFailsWith<IllegalArgumentException>("a negative share of a column's height is no share") {
                SwingModifier.weight(-1f)
            }
            assertFailsWith<IllegalArgumentException>("a weight there is no share to compute from is no weight") {
                SwingModifier.weight(Float.NaN)
            }
            assertFailsWith<IllegalArgumentException>("a weight below zero is refused however far below it is") {
                SwingModifier.weight(Float.NEGATIVE_INFINITY)
            }
        }
    }

    @Test
    fun testRow_doesNotPlaceChildrenOutOfBounds_becauseOfRoundings() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(11, CHILD_HEIGHT)) {
                    SizedChild(0, SwingModifier.weight(1f))
                    SizedChild(1, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 5, CHILD_HEIGHT),
                    Rectangle(5, 0, 6, CHILD_HEIGHT),
                ),
                childBounds(),
                "a width that does not divide evenly between two equal weights must be handed out whole - " +
                    "5px and 6px of 11px - so the second child is placed inside the row, not past its edge",
            )
            assertEquals(
                11,
                containerSize().width,
                "and the row stays at the width it was given, so what its children hold accounts for all of it",
            )
        }

    @Test
    fun testRow_isNotLargerThanItsChildren_becauseOfRoundings() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(8, CHILD_HEIGHT)) {
                    SizedChild(0, SwingModifier.weight(2f))
                    SizedChild(1, SwingModifier.weight(2f))
                    SizedChild(2, SwingModifier.weight(3f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 3, CHILD_HEIGHT),
                    Rectangle(3, 0, 2, CHILD_HEIGHT),
                    Rectangle(5, 0, 3, CHILD_HEIGHT),
                ),
                childBounds(),
                "weights of 2f, 2f and 3f over 8px round to shares of 3px, 2px and 3px: the pixel the " +
                    "rounding drops is handed to a leading child, never left over at the end",
            )
            assertEquals(
                8,
                containerSize().width,
                "so the widths the children hold sum to the row's own width, which is no wider than they are",
            )
        }

    @Test
    fun testColumn_isNotLargetThanItsChildren_becauseOfRoundings() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CHILD_WIDTH, 8)) {
                    SizedChild(0, SwingModifier.weight(1f))
                    SizedChild(1, SwingModifier.weight(1f))
                    SizedChild(2, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, 2),
                    Rectangle(0, 2, CHILD_WIDTH, 3),
                    Rectangle(0, 5, CHILD_WIDTH, 3),
                ),
                childBounds(),
                "8px over three equal weights rounds to 2px, 3px and 3px: the pixel the rounding would gain " +
                    "is taken off a leading child, so the three shares still sum to the height there was",
            )
            assertEquals(
                8,
                containerSize().height,
                "and the column is no taller than what its children hold between them",
            )
        }

    @Test
    fun testColumn_doesNotPlaceChildrenOutOfBounds_becauseOfRoundings() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CHILD_WIDTH, 11)) {
                    SizedChild(0, SwingModifier.weight(1f))
                    SizedChild(1, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, 5),
                    Rectangle(0, 5, CHILD_WIDTH, 6),
                ),
                childBounds(),
                "a height that does not divide evenly between two equal weights must be handed out whole - " +
                    "5px and 6px of 11px - so the second child ends at the column's bottom edge, not past it",
            )
            assertEquals(
                11,
                containerSize().height,
                "and the column stays at the height it was given, all of which its children hold",
            )
        }

    @Test
    fun testRow_withCustomVertical_alignment() =
        runComposeSwingTest {
            val alignment = RecordingVerticalAlignment()
            setContent {
                Row(
                    modifier = containerModifier(200, 200),
                    verticalAlignment = alignment,
                ) {
                    Child(0, 20, 20)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        0,
                        (200 - 20) / 2,
                        20,
                        20,
                    ),
                ),
                childBounds(),
                "a row must place its child wherever the vertical alignment it is declared with says, one " +
                    "written by its caller as much as one of the standard alignments",
            )
            assertEquals(
                20,
                alignment.capturedSize,
                "the alignment must be handed the height the child occupies, which is what it has to place",
            )
            assertEquals(
                200,
                alignment.capturedSpace,
                "and the row's whole height as the space to place that child within",
            )
        }

    @Test
    fun testColumn_withCustomHorizontal_alignment() =
        runComposeSwingTest {
            val alignment = RecordingHorizontalAlignment()
            setContent {
                Column(
                    modifier = containerModifier(300, 200),
                    horizontalAlignment = alignment,
                ) {
                    Child(0, 40, 40)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        (300 - 40) / 2,
                        0,
                        40,
                        40,
                    ),
                ),
                childBounds(),
                "a column must place its child wherever the horizontal alignment it is declared with says, " +
                    "one written by its caller as much as one of the standard alignments",
            )
            assertEquals(
                40,
                alignment.capturedSize,
                "the alignment must be handed the width the child occupies, which is what it has to place",
            )
            assertEquals(
                300,
                alignment.capturedSpace,
                "and the column's whole width as the space to place that child within",
            )
        }

    @Test
    fun testColumn_withCustomHorizontalAlignModifier() =
        runComposeSwingTest {
            val alignment = RecordingHorizontalAlignment()
            setContent {
                Column(modifier = containerModifier(300, 200)) {
                    Child(
                        index = 0,
                        width = 40,
                        height = 40,
                        modifier = SwingModifier.align(alignment),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        (300 - 40) / 2,
                        0,
                        40,
                        40,
                    ),
                ),
                childBounds(),
                "a child naming a horizontal alignment of its own must be placed by it, a caller's own " +
                    "alignment included",
            )
            assertEquals(
                40,
                alignment.capturedSize,
                "an alignment a child names must be handed that child's width",
            )
            assertEquals(
                300,
                alignment.capturedSpace,
                "and the same whole width of the column that the column's own alignment would have been",
            )
        }

    @Test
    fun testRow_withCustomVerticalAlignModifier() =
        runComposeSwingTest {
            val alignment = RecordingVerticalAlignment()
            setContent {
                Row(modifier = containerModifier(200, 200)) {
                    Child(
                        index = 0,
                        width = 20,
                        height = 20,
                        modifier = SwingModifier.align(alignment),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        0,
                        (200 - 20) / 2,
                        20,
                        20,
                    ),
                ),
                childBounds(),
                "a child naming a vertical alignment of its own must be placed by it, a caller's own " +
                    "alignment included",
            )
            assertEquals(
                20,
                alignment.capturedSize,
                "an alignment a child names must be handed that child's height",
            )
            assertEquals(
                200,
                alignment.capturedSpace,
                "and the same whole height of the row that the row's own alignment would have been",
            )
        }

    @Test
    fun testRow_withColumnAlignModifier_usesCorrectCrossAxisSize() =
        runComposeSwingTest {
            setContent {
                Column {
                    val columnModifier = SwingModifier.align(Alignment.End)
                    Row(modifier = containerModifier(20, 100)) {
                        Child(0, 20, 50, columnModifier)
                    }
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        0,
                        100 - 50,
                        20,
                        50,
                    ),
                ),
                childBounds(),
                "an alignment built for one container and declared on a child of another must resolve " +
                    "against the container that places the child, so a horizontal alignment placing a child " +
                    "of a row reads the row's height and the height that child occupies of it",
            )
        }

    @Test
    fun testColumn_withRowAlignModifier_usesCorrectCrossAxisSize() =
        runComposeSwingTest {
            setContent {
                Row {
                    val rowModifier = SwingModifier.align(Alignment.Bottom)
                    Column(modifier = containerModifier(100, 20)) {
                        Child(0, 50, 20, rowModifier)
                    }
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        100 - 50,
                        0,
                        50,
                        20,
                    ),
                ),
                childBounds(),
                "an alignment built for one container and declared on a child of another must resolve " +
                    "against the container that places the child, so a vertical alignment placing a child " +
                    "of a column reads the column's width and the width that child occupies of it",
            )
        }

    @Test
    fun testRow_withStretchCrossAxisAlignment() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(150, 200)) {
                    Child(0, 50, 50, SwingModifier.fillMaxHeight())
                    Child(1, 2 * 50, 2 * 50, SwingModifier.fillMaxHeight())
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 200),
                    Rectangle(50, 0, 2 * 50, 200),
                ),
                childBounds(),
                "a child filling the row's height must take the whole of it however much less it prefers, " +
                    "and keep along the row the width it asked for",
            )
        }

    @Test
    fun testRow_withGravityModifier_andGravityParameter() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = containerModifier(150, 200),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Child(0, 50, 50, SwingModifier.align(Alignment.Top))
                    Child(1, 50, 50)
                    Child(2, 50, 50, SwingModifier.align(Alignment.Bottom))
                }
            }

            assertEquals(
                rowCrossCells(),
                childBounds(),
                "each child naming a vertical alignment of its own must be placed by that one in place of " +
                    "the row's, and the child naming none by the row's",
            )
        }

    @Test
    fun testRow_withGravityModifier() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(150, 200)) {
                    Child(0, 50, 50, SwingModifier.align(Alignment.Top))
                    Child(
                        index = 1,
                        width = 50,
                        height = 50,
                        modifier = SwingModifier.align(Alignment.CenterVertically),
                    )
                    Child(2, 50, 50, SwingModifier.align(Alignment.Bottom))
                }
            }

            assertEquals(
                rowCrossCells(),
                childBounds(),
                "the top, the middle and the bottom of the row's height must each be reachable by a child " +
                    "naming that alignment for itself, and each child keeps the height it asked for",
            )
        }

    @Test
    fun testColumn_withStretchCrossAxisAlignment() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(200, 150)) {
                    Child(0, 50, 50, SwingModifier.fillMaxWidth())
                    Child(1, 2 * 50, 2 * 50, SwingModifier.fillMaxWidth())
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 200, 50),
                    Rectangle(0, 50, 200, 2 * 50),
                ),
                childBounds(),
                "a child filling the column's width must take the whole of it however much less it prefers, " +
                    "and keep along the column the height it asked for",
            )
        }

    @Test
    fun testColumn_withGravityModifier() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(200, 150)) {
                    Child(0, 50, 50, SwingModifier.align(Alignment.Start))
                    Child(
                        index = 1,
                        width = 50,
                        height = 50,
                        modifier = SwingModifier.align(Alignment.CenterHorizontally),
                    )
                    Child(2, 50, 50, SwingModifier.align(Alignment.End))
                }
            }

            assertEquals(
                columnCrossCells(),
                childBounds(),
                "the leading edge, the middle and the trailing edge of the column's width must each be " +
                    "reachable by a child naming that alignment for itself, and each child keeps the width " +
                    "it asked for",
            )
        }

    @Test
    fun testColumn_withGravityModifier_andGravityParameter() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = containerModifier(200, 150),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Child(0, 50, 50, SwingModifier.align(Alignment.Start))
                    Child(1, 50, 50)
                    Child(2, 50, 50, SwingModifier.align(Alignment.End))
                }
            }

            assertEquals(
                columnCrossCells(),
                childBounds(),
                "each child naming a horizontal alignment of its own must be placed by that one in place of " +
                    "the column's, and the child naming none by the column's",
            )
        }

    @Test
    fun testRow_expandedWidth_withExpandedModifier() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.preferredSize(400, 2 * 50)) {
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG).fillMaxWidth()) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                400,
                containerSize().width,
                "a row filling its parent's width must take the whole of it, however much wider than its " +
                    "children that is",
            )
        }

    @Test
    fun testRow_wrappedWidth_withNoWeightChildren() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 50, 50)
                    Child(1, 2 * 50, 2 * 50)
                }
            }

            assertEquals(
                3 * 50,
                containerPreferredSize().width,
                "a row no child claims a share of must ask for its children's widths added up and nothing more",
            )
        }

    @Test
    fun testRow_withMaxCrossAxisSize() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.preferredSize(3 * 50, 300)) {
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG).fillMaxHeight()) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                300,
                containerSize().height,
                "a row filling its parent's height must take the whole of it, however much taller than its " +
                    "tallest child that is",
            )
        }

    @Test
    fun testRow_withMinCrossAxisSize() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 50, 50)
                    Child(1, 2 * 50, 2 * 50)
                }
            }

            assertEquals(
                2 * 50,
                containerPreferredSize().height,
                "a row must ask for the height of its tallest child, which is height enough for every " +
                    "shorter one as well",
            )
        }

    @Test
    fun testRow_withExpandedModifier_respectsMaxWidthConstraint() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.preferredSize(250, 2 * 50)) {
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG).fillMaxWidth()) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                250,
                containerSize().width,
                "a filling row must stop at the width its parent holds it to rather than run on past it",
            )
        }

    @Test
    fun testRow_withChildrenWithWeight_respectsMaxWidthConstraint() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(250, 2 * 50)) {
                    Row(modifier = SwingModifier.center().testTag(CONTAINER_TAG)) {
                        Child(0, 50, 50, SwingModifier.weight(1f))
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                250,
                containerSize().width,
                "the room a weighted child claims comes out of the row's own width, so a row handed 250px " +
                    "must stay at 250px however large that child's share is",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, 250 - 2 * 50, 50),
                    Rectangle(250 - 2 * 50, 0, 2 * 50, 2 * 50),
                ),
                childBounds(),
                "and inside that width the weighted child must take everything the other child leaves",
            )
        }

    @Test
    fun testRow_withNoWeightChildren_respectsMinWidthConstraint() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(250, 2 * 50)) {
                    Row(modifier = SwingModifier.center().testTag(CONTAINER_TAG)) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                250,
                containerSize().width,
                "a row handed more width than it asked for must take all of it rather than shrink back to " +
                    "the width its children need",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 50),
                    Rectangle(50, 0, 2 * 50, 2 * 50),
                ),
                childBounds(),
                "and the children, claiming no share of it, must keep the widths they prefer and leave the " +
                    "rest of the row empty",
            )
        }

    @Test
    fun testRow_withMaxCrossAxisSize_respectsMaxHeightConstraint() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.preferredSize(3 * 50, 250)) {
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG).fillMaxHeight()) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                250,
                containerSize().height,
                "a filling row must stop at the height its parent holds it to, as it stops at that parent's width",
            )
        }

    @Test
    fun testRow_withMinCrossAxisSize_respectsMinHeightConstraint() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(3 * 50, 150)) {
                    Row(modifier = SwingModifier.center().testTag(CONTAINER_TAG)) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                150,
                containerSize().height,
                "a row handed more height than its tallest child asked for must take all of it rather than " +
                    "shrink back to that child",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 50),
                    Rectangle(50, 0, 2 * 50, 2 * 50),
                ),
                childBounds(),
                "and neither child, declaring no fill of its own, may be stretched into the height the row gained",
            )
        }

    @Test
    fun testRow_measuresNoWeightChildrenCorrectly() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(100, 200)) {
                    Child(0, 50, 100)
                    Child(1, 200, 100)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 100),
                    Rectangle(50, 0, 100 - 50, 100),
                ),
                childBounds(),
                "a child claiming no share is offered only what the children before it left, so the second " +
                    "one - which prefers more width than the whole row has - must end at the 50px the first " +
                    "did not take",
            )
        }

    @Test
    fun testRow_doesNotExpand_whenWeightChildrenDoNotFill() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(
                        index = 0,
                        width = 10,
                        height = 10,
                        modifier = SwingModifier.weight(1f, fill = false),
                    )
                }
            }

            assertEquals(
                10,
                containerPreferredSize().width,
                "a share the child will not fill wins the row no width, so a row asking for its own must " +
                    "ask for what that child prefers and no more",
            )
        }

    @Test
    fun testRow_includesSpacing_withWeightChildren() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = containerModifier(40, 40),
                    horizontalArrangement = Arrangement.spacedBy(8),
                ) {
                    Child(0, 0, 40, SwingModifier.weight(1f))
                    Child(1, 0, 40, SwingModifier.weight(1f))
                }
            }

            val share = (40 - 8) / 2

            assertEquals(
                listOf(
                    Rectangle(0, 0, share, 40),
                    Rectangle(share + 8, 0, share, 40),
                ),
                childBounds(),
                "the gap the arrangement holds must be taken out of the row's width before the two weighted " +
                    "children split what is left, or the second would be pushed past the row's trailing edge",
            )
        }

    @Test
    fun testColumn_expandedHeight_withExpandedModifier() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.preferredSize(300, 400)) {
                    Column(modifier = SwingModifier.testTag(CONTAINER_TAG).fillMaxHeight()) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                400,
                containerSize().height,
                "a column declaring a fill of its parent's height must take the whole of it, not stop at " +
                    "the height its children ask for",
            )
        }

    @Test
    fun testColumn_wrappedHeight_withNoChildrenWithWeight() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 50, 50)
                    Child(1, 2 * 50, 2 * 50)
                }
            }

            assertEquals(
                3 * 50,
                containerPreferredSize().height,
                "a column nobody imposed a height on must ask for exactly what its children stack up to, " +
                    "since no child claims a share of anything left over",
            )
        }

    @Test
    fun testColumn_withMaxCrossAxisSize() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.preferredSize(300, 400)) {
                    Column(modifier = SwingModifier.testTag(CONTAINER_TAG).fillMaxWidth()) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                300,
                containerSize().width,
                "a column declaring a fill of its parent's width must take the whole of it, however narrow " +
                    "its widest child is",
            )
        }

    @Test
    fun testColumn_withMinCrossAxisSize() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 50, 50)
                    Child(1, 2 * 50, 2 * 50)
                }
            }

            assertEquals(
                2 * 50,
                containerPreferredSize().width,
                "a column declaring no fill must ask for the width of its widest child, so no child of it " +
                    "is cut short across the axis",
            )
        }

    @Test
    fun testColumn_withExpandedModifier_respectsMaxHeightConstraint() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.preferredSize(300, 250)) {
                    Column(modifier = SwingModifier.testTag(CONTAINER_TAG).fillMaxHeight()) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                250,
                containerSize().height,
                "a filling column must stop at the height its parent has to give, neither shrinking to the " +
                    "height its children ask for nor growing past that parent",
            )
        }

    @Test
    fun testColumn_withWeightChildren_respectsMaxHeightConstraint() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(300, 250)) {
                    Column(modifier = SwingModifier.center().testTag(CONTAINER_TAG)) {
                        Child(0, 50, 50, SwingModifier.weight(1f))
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                250,
                containerSize().height,
                "the center region hands the column its own height, which is the whole of the height the " +
                    "column has to share out",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 250 - 2 * 50),
                    Rectangle(0, 250 - 2 * 50, 2 * 50, 2 * 50),
                ),
                childBounds(),
                "the weighted child must take everything the child claiming no share leaves, so none of the " +
                    "height handed to the column stays empty",
            )
        }

    @Test
    fun testColumn_withChildren_respectsMinHeightConstraint() =
        runComposeSwingTest {
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(300, 250)) {
                    Column(modifier = SwingModifier.center().testTag(CONTAINER_TAG)) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                250,
                containerSize().height,
                "a column handed more height than it asked for must keep the height it was handed rather " +
                    "than shrink back to what its children stack up to",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 50),
                    Rectangle(0, 50, 2 * 50, 2 * 50),
                ),
                childBounds(),
                "with no child claiming a share of it, the height nobody asked for is left empty below them",
            )
        }

    @Test
    fun testColumn_withMaxCrossAxisSize_respectsMaxWidthConstraint() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.preferredSize(250, 400)) {
                    Column(modifier = SwingModifier.testTag(CONTAINER_TAG).fillMaxWidth()) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                250,
                containerSize().width,
                "a filling column must stop at the width its parent has to give, which is what a fill of a " +
                    "width means",
            )
        }

    @Test
    fun testColumn_withMinCrossAxisSize_respectsMinWidthConstraint() =
        runComposeSwingTest {
            setContent {
                Panel(
                    PanelLayout.Border(),
                    modifier = SwingModifier.preferredSize(150, 400),
                ) {
                    Column(modifier = SwingModifier.center().testTag(CONTAINER_TAG)) {
                        Child(0, 50, 50)
                        Child(1, 2 * 50, 2 * 50)
                    }
                }
            }

            assertEquals(
                150,
                containerSize().width,
                "a column handed more width than its widest child asks for must keep that width, since it " +
                    "is the region it was given to place its children in",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 50),
                    Rectangle(0, 50, 2 * 50, 2 * 50),
                ),
                childBounds(),
                "and neither child, declaring no fill of its own, may be stretched into the width the " +
                    "column gained: each keeps the width it prefers, at the start of the cross axis",
            )
        }

    @Test
    fun testColumn_measuresNoWeightChildrenCorrectly() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(100, 200)) {
                    Child(0, 2 * 100, 100)
                    Child(1, 2 * 100, 2 * 200)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 100, 100),
                    Rectangle(
                        0,
                        100,
                        100,
                        200 - 100,
                    ),
                ),
                childBounds(),
                "a child claiming no share is offered the column's whole width and whatever height the " +
                    "children before it left, so neither of these two may take more than that",
            )
        }

    @Test
    fun testColumn_doesNotExpand_whenWeightChildrenDoNotFill() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 10, 10, SwingModifier.weight(1f, fill = false))
                }
            }

            assertEquals(
                10,
                containerPreferredSize().height,
                "a weighted child that does not fill takes only the height it prefers, so the column has no " +
                    "reason to ask for any more than that",
            )
        }

    @Test
    fun testColumn_includesSpacing_withWeightChildren() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = containerModifier(CHILD_WIDTH, 40),
                    verticalArrangement = Arrangement.spacedBy(8),
                ) {
                    SizedChild(0, SwingModifier.weight(1f))
                    SizedChild(1, SwingModifier.weight(1f))
                }
            }

            val shared = (40 - 8) / 2
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, shared),
                    Rectangle(0, shared + 8, CHILD_WIDTH, shared),
                ),
                childBounds(),
                "the gap the arrangement holds is taken out of the column's height before the weights share " +
                    "what is left, so the two children never overlap that gap",
            )
        }

    @Test
    fun testRow_withStartArrangement() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(450, 50)) {
                    repeat(CHILD_COUNT) { Child(it, 50, 50) }
                }
            }

            assertEquals(
                squareRowCells(50, 0, 50, 100),
                childBounds(),
                "a row declared with no arrangement of its own must pack its children edge to edge against " +
                    "its leading edge and leave the width they did not ask for after them",
            )
        }

    @Test
    fun testRow_withEndArrangement() =
        runComposeSwingTest {
            setContent { ArrangedRow(Arrangement.End) }

            assertEquals(
                squareRowCells(50, 300, 350, 400),
                childBounds(),
                "Arrangement.End must pack the children edge to edge against the row's trailing edge, so " +
                    "the width they did not ask for falls before them",
            )
        }

    @Test
    fun testRow_withCenterArrangement() =
        runComposeSwingTest {
            setContent { ArrangedRow(Arrangement.Center) }

            assertEquals(
                squareRowCells(50, 150, 200, 250),
                childBounds(),
                "Arrangement.Center must keep the children edge to edge and give the width they did not " +
                    "ask for to either side of them in equal halves",
            )
        }

    @Test
    fun testRow_withSpaceEvenlyArrangement() =
        runComposeSwingTest {
            setContent { ArrangedRow(Arrangement.SpaceEvenly) }

            assertEquals(
                squareRowCells(50, 75, 200, 325),
                childBounds(),
                "Arrangement.SpaceEvenly must split the width the children did not ask for into gaps of " +
                    "one size, counting the two at the row's edges alongside those between the children",
            )
        }

    @Test
    fun testRow_withSpaceBetweenArrangement_singleItem() =
        runComposeSwingTest {
            setContent { ArrangedRow(Arrangement.SpaceBetween, children = 1) }

            assertEquals(
                squareRowCells(50, 0),
                childBounds(),
                "Arrangement.SpaceBetween has no gap between children to fill when there is only one of " +
                    "them, so that child must stay at the leading edge rather than be centered",
            )
        }

    @Test
    fun testRow_withSpaceBetweenArrangement_multipleItems() =
        runComposeSwingTest {
            setContent { ArrangedRow(Arrangement.SpaceBetween) }

            assertEquals(
                squareRowCells(50, 0, 200, 400),
                childBounds(),
                "Arrangement.SpaceBetween must put the whole of the width the children did not ask for " +
                    "into the gaps between them and none of it at either edge",
            )
        }

    @Test
    fun testRow_withSpaceAroundArrangement() =
        runComposeSwingTest {
            setContent { ArrangedRow(Arrangement.SpaceAround) }

            assertEquals(
                squareRowCells(50, 50, 200, 350),
                childBounds(),
                "Arrangement.SpaceAround must give every child a gap of its own size, halved where that " +
                    "gap meets one of the row's edges",
            )
        }

    @Test
    fun testRow_withSpacedByArrangement() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    horizontalArrangement = Arrangement.spacedBy(10),
                ) {
                    repeat(2) { Child(it, 20, 20) }
                }
            }

            assertEquals(
                Dimension(50, 20),
                containerPreferredSize(),
                "a row nobody imposed a width on must ask for its children's widths plus the gap its " +
                    "arrangement holds between them, or the gap would have nowhere to go",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 20, 20), Rectangle(30, 0, 20, 20)),
                childBounds(),
                "Arrangement.spacedBy must hold exactly its gap between the two children and no more",
            )
        }

    @Test
    fun testRow_withSpacedByAlignedArrangement() =
        runComposeSwingTest {
            setContent { TightRow(Arrangement.spacedBy(10, Alignment.End), children = 2) }

            assertEquals(
                50,
                containerSize().width,
                "the row must be laid out at the width imposed on it, which is what its arrangement divides",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 20, 20), Rectangle(30, 0, 20, 20)),
                childBounds(),
                "the children and the gap between them fill the row exactly, so the alignment the " +
                    "arrangement carries has no room left to shift the group into",
            )
        }

    @Test
    fun testRow_withSpacedByArrangement_insufficientSpace() =
        runComposeSwingTest {
            setContent { TightRow(Arrangement.spacedBy(15), children = 3) }

            assertEquals(
                50,
                containerSize().width,
                "the row must keep the width imposed on it however much its children ask for",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 20, 20), Rectangle(35, 0, 15, 20), Rectangle(50, 0, 0, 20)),
                childBounds(),
                "a row must charge each gap against its own width before measuring the next child, so the " +
                    "second child gets only the width left over and the third none at all, and neither the " +
                    "last child nor the gap before it may be placed past the trailing edge",
            )
        }

    @Test
    fun testRow_withAlignedArrangement() =
        runComposeSwingTest {
            setContent { TightRow(Arrangement.aligned(Alignment.End), children = 2) }

            assertEquals(
                50,
                containerSize().width,
                "the row must be laid out at the width imposed on it, which is what its arrangement divides",
            )
            assertEquals(
                listOf(Rectangle(10, 0, 20, 20), Rectangle(30, 0, 20, 20)),
                childBounds(),
                "Arrangement.aligned must leave the children edge to edge and only move the group as a " +
                    "whole to where its alignment says, holding no gap of its own between them",
            )
        }

    @Test
    fun testRow_withSpacedByArrangement_rtl() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier =
                        containerModifier(
                            20 * 2 + 10 + 15,
                            20,
                            ComponentOrientation.RIGHT_TO_LEFT,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(10),
                ) {
                    repeat(2) { Child(it, 20, 20) }
                }
            }

            assertEquals(
                listOf(Rectangle(45, 0, 20, 20), Rectangle(15, 0, 20, 20)),
                childBounds(),
                "under a right-to-left orientation Arrangement.spacedBy must pack the group against the " +
                    "right edge, the leading one there, putting the first child furthest right and leaving " +
                    "the width nobody claimed on the left",
            )
        }

    @Test
    fun testRow_withSpacedByArrangement_insufficientSpace_rtl() =
        runComposeSwingTest {
            setContent {
                TightRow(
                    Arrangement.spacedBy(15),
                    children = 3,
                    rightToLeft = true,
                )
            }

            assertEquals(
                50,
                containerSize().width,
                "the row must keep the width imposed on it however much its children ask for",
            )
            assertEquals(
                listOf(Rectangle(30, 0, 20, 20), Rectangle(0, 0, 15, 20), Rectangle(0, 0, 0, 20)),
                childBounds(),
                "a right-to-left row must measure its children the same way a left-to-right one does and " +
                    "mirror only where they land, so the width each child is left at is unchanged and no " +
                    "child is placed past the left edge",
            )
        }

    @Test
    fun testColumn_withTopArrangement() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(50, 450)) {
                    repeat(CHILD_COUNT) { Child(it, 50, 50) }
                }
            }

            assertEquals(
                squareColumnCells(0, 50, 100),
                childBounds(),
                "a column declared with no arrangement of its own must stack its children edge to edge " +
                    "against its top and leave the height they did not ask for below them",
            )
        }

    @Test
    fun testColumn_withBottomArrangement() =
        runComposeSwingTest {
            setContent { ArrangedColumn(Arrangement.Bottom) }

            assertEquals(
                squareColumnCells(300, 350, 400),
                childBounds(),
                "Arrangement.Bottom must stack the children edge to edge against the column's bottom, so " +
                    "the height they did not ask for falls above them",
            )
        }

    @Test
    fun testColumn_withCenterArrangement() =
        runComposeSwingTest {
            setContent { ArrangedColumn(Arrangement.Center) }

            assertEquals(
                squareColumnCells(150, 200, 250),
                childBounds(),
                "Arrangement.Center must keep the children edge to edge and give the height they did not " +
                    "ask for to either side of them in equal halves",
            )
        }

    @Test
    fun testColumn_withSpaceEvenlyArrangement() =
        runComposeSwingTest {
            setContent { ArrangedColumn(Arrangement.SpaceEvenly) }

            assertEquals(
                squareColumnCells(75, 200, 325),
                childBounds(),
                "Arrangement.SpaceEvenly must split the height the children did not ask for into gaps of " +
                    "one size, counting the two at the column's edges alongside those between the children",
            )
        }

    @Test
    fun testColumn_withSpaceBetweenArrangement() =
        runComposeSwingTest {
            setContent { ArrangedColumn(Arrangement.SpaceBetween) }

            assertEquals(
                squareColumnCells(0, 200, 400),
                childBounds(),
                "Arrangement.SpaceBetween must put the whole of the height the children did not ask for " +
                    "into the gaps between them and none of it at either edge",
            )
        }

    @Test
    fun testColumn_withSpaceAroundArrangement() =
        runComposeSwingTest {
            setContent { ArrangedColumn(Arrangement.SpaceAround) }

            assertEquals(
                squareColumnCells(50, 200, 350),
                childBounds(),
                "Arrangement.SpaceAround must give every child a gap of its own size, halved where that " +
                    "gap meets one of the column's edges",
            )
        }

    @Test
    fun testColumn_withSpacedByArrangement() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    verticalArrangement = Arrangement.spacedBy(10),
                ) {
                    repeat(2) { Child(it, 20, 20) }
                }
            }

            assertEquals(
                Dimension(20, 50),
                containerPreferredSize(),
                "a column nobody imposed a height on must ask for its children's heights plus the gap its " +
                    "arrangement holds between them, or the gap would have nowhere to go",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 20, 20), Rectangle(0, 30, 20, 20)),
                childBounds(),
                "Arrangement.spacedBy must hold exactly its gap between the two children and no more",
            )
        }

    @Test
    fun testColumn_withSpacedByAlignedArrangement() =
        runComposeSwingTest {
            setContent { TightColumn(Arrangement.spacedBy(10, Alignment.Bottom), children = 2) }

            assertEquals(
                50,
                containerSize().height,
                "the column must be laid out at the height imposed on it, which is what its arrangement divides",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 20, 20), Rectangle(0, 30, 20, 20)),
                childBounds(),
                "the children and the gap between them fill the column exactly, so the alignment the " +
                    "arrangement carries has no room left to shift the group into",
            )
        }

    @Test
    fun testColumn_withSpacedByArrangement_insufficientSpace() =
        runComposeSwingTest {
            setContent { TightColumn(Arrangement.spacedBy(15), children = 3) }

            assertEquals(
                50,
                containerSize().height,
                "the column must keep the height imposed on it however much its children ask for",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 20, 20), Rectangle(0, 35, 20, 15), Rectangle(0, 50, 20, 0)),
                childBounds(),
                "a column must charge each gap against its own height before measuring the next child, so " +
                    "the second child gets only the height left over and the third none at all, and neither " +
                    "the last child nor the gap before it may be placed past the bottom edge",
            )
        }

    @Test
    fun testColumn_withAlignedArrangement() =
        runComposeSwingTest {
            setContent { TightColumn(Arrangement.aligned(Alignment.Bottom), children = 2) }

            assertEquals(
                50,
                containerSize().height,
                "the column must be laid out at the height imposed on it, which is what its arrangement divides",
            )
            assertEquals(
                listOf(Rectangle(0, 10, 20, 20), Rectangle(0, 30, 20, 20)),
                childBounds(),
                "Arrangement.aligned must leave the children edge to edge and only move the group as a " +
                    "whole to where its alignment says, holding no gap of its own between them",
            )
        }

    @Test
    fun testRow_doesNotUseMinConstraintsOnChildren() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(50, 50)) {
                    FixedChild(0, 30, 30)
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 30, 30)),
                childBounds(),
                "a child keeps the extent it asks for however much room its row was given: the extent a " +
                    "parent imposes on a row is the room the row has to place children in, never a floor the " +
                    "row passes down to them",
            )
        }

    @Test
    fun testColumn_doesNotUseMinConstraintsOnChildren() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(50, 50)) {
                    FixedChild(0, 30, 30)
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 30, 30)),
                childBounds(),
                "a column stretches a child no more than a row does: a child that asked for less than the " +
                    "column was given keeps what it asked for, and the rest of the column stays empty",
            )
        }

    @Test
    fun testColumn_withSpacedByArrangement_rtl() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier =
                        containerModifier(
                            width = 20,
                            height = 100,
                            orientation = ComponentOrientation.RIGHT_TO_LEFT,
                        ),
                    verticalArrangement = Arrangement.spacedBy(10),
                ) {
                    FixedChild(0, 20, 20)
                    FixedChild(1, 20, 20)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 20, 20),
                    Rectangle(0, 20 + 10, 20, 20),
                ),
                childBounds(),
                "a right-to-left column must stack its children exactly as a left-to-right one does: the " +
                    "orientation decides nothing about an axis that runs top to bottom, and the gap the " +
                    "arrangement holds still decides where the second child begins",
            )
        }

    @Test
    fun testRow_withNoWeightChildren_hasCorrectIntrinsicMeasurements() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizelessChild(0)
                    FixedChild(1, 50, 40)
                }
            }

            assertEquals(
                Dimension(50, 40),
                containerPreferredSize(),
                "a row of children that claim no share asks for their widths laid end to end and the tallest " +
                    "of their heights, so a child asking for nothing adds nothing along either axis",
            )
            assertEquals(
                Dimension(50, 40),
                containerMinimumSize(),
                "and it answers the same for the extent it can shrink to, since neither child can shrink " +
                    "below what it asks for",
            )
        }

    @Test
    fun testColumn_withNoWeightChildren_hasCorrectIntrinsicMeasurements() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizelessChild(0)
                    FixedChild(1, 50, 40)
                }
            }

            assertEquals(
                Dimension(50, 40),
                containerPreferredSize(),
                "a column of children that claim no share asks for their heights stacked and the widest of " +
                    "their widths, the two axes of a row's answer read the other way round",
            )
            assertEquals(
                Dimension(50, 40),
                containerMinimumSize(),
                "and the extent it can shrink to is the same, since neither child can shrink below what it " +
                    "asks for",
            )
        }

    @Test
    fun testRow_withWeightChildren_hasCorrectIntrinsicMeasurements() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    FixedChild(0, 20, 30, SwingModifier.weight(3f))
                    FixedChild(1, 30, 40, SwingModifier.weight(2f))
                    SizelessChild(2, SwingModifier.weight(2f))
                    FixedChild(3, 20, 30)
                }
            }

            assertEquals(
                Dimension(30 / 2 * 7 + 20, 40),
                containerPreferredSize(),
                "a weighted child asking for 30px over two of the seven shares implies 30px for every two " +
                    "shares the row hands out, and that is the widest implication of the three, so the row " +
                    "asks for all seven shares at that rate plus the 20px the unweighted child claims outright",
            )
            assertEquals(
                Dimension(30 / 2 * 7 + 20, 40),
                containerMinimumSize(),
                "the extent it can shrink to is taken the same way against what each child can shrink to, " +
                    "which here is what each of them asks for",
            )
        }

    @Test
    fun testColumn_withWeightChildren_hasCorrectIntrinsicMeasurements() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    FixedChild(0, 30, 20, SwingModifier.weight(3f))
                    FixedChild(1, 40, 30, SwingModifier.weight(2f))
                    SizelessChild(2, SwingModifier.weight(2f))
                    FixedChild(3, 30, 20)
                }
            }

            assertEquals(
                Dimension(40, 30 / 2 * 7 + 20),
                containerPreferredSize(),
                "a column works its height out of the shares its children imply exactly as a row works out " +
                    "its width, and asks across the axis for the widest child, weighted or not",
            )
            assertEquals(
                Dimension(40, 30 / 2 * 7 + 20),
                containerMinimumSize(),
                "the height it can shrink to is taken the same way against what each child can shrink to, " +
                    "which here is what each of them asks for",
            )
        }

    @Test
    fun testRow_withArrangementSpacing() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    horizontalArrangement = Arrangement.spacedBy(5),
                ) {
                    repeat(3) { index -> FixedChild(index, 10, 10) }
                }
            }

            assertEquals(
                3 * 10 + (3 - 1) * 5,
                containerPreferredSize().width,
                "the gap an arrangement holds between each adjacent pair is width the row needs as much as " +
                    "the widths of the children themselves, so it must be in the width the row asks for",
            )
            assertEquals(
                3 * 10 + (3 - 1) * 5,
                containerMinimumSize().width,
                "and in the width it can shrink to, since the gaps are held whatever width the row ends up at",
            )
        }

    @Test
    fun testColumn_withArrangementSpacing() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    verticalArrangement = Arrangement.spacedBy(5),
                ) {
                    repeat(3) { index -> FixedChild(index, 10, 10) }
                }
            }

            assertEquals(
                3 * 10 + (3 - 1) * 5,
                containerPreferredSize().height,
                "a column carries its arrangement's gaps into the height it asks for the way a row carries " +
                    "them into its width",
            )
            assertEquals(
                3 * 10 + (3 - 1) * 5,
                containerMinimumSize().height,
                "and into the height it can shrink to, since the gaps are held whatever height it ends up at",
            )
        }

    @Test
    fun testRow_withNoItems_hasCorrectIntrinsicMeasurements() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = SwingModifier.testTag("empty row"),
                    horizontalArrangement = Arrangement.spacedBy(48),
                ) {}
                Column(
                    modifier = SwingModifier.testTag("empty column"),
                    verticalArrangement = Arrangement.spacedBy(48),
                ) {}
            }

            for (tag in listOf("empty row", "empty column")) {
                assertEquals(
                    Dimension(0, 0),
                    taggedPanel(tag).preferredSize,
                    "'$tag' holds no adjacent pair for its arrangement to keep apart, so it must ask for " +
                        "nothing at all rather than for a gap it has nothing to put on either side of",
                )
                assertEquals(
                    Dimension(0, 0),
                    taggedPanel(tag).minimumSize,
                    "'$tag' can shrink to nothing for the same reason, so a parent reading its minimum is " +
                        "not told to keep room for an absent child",
                )
            }
        }

    @Test
    fun testRowColumnModifiersChain_lastDeclarationWins() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CHILD_WIDTH, 24)) {
                    Child(0, CHILD_WIDTH, 0, SwingModifier.weight(2f).weight(1f))
                    Child(1, CHILD_WIDTH, 0, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, 24 / 2),
                    Rectangle(0, 24 / 2, CHILD_WIDTH, 24 / 2),
                ),
                childBounds(),
                "a modifier is folded in declaration order, so the weight a child declares last is the one " +
                    "it keeps: the two children claim one share each and split the column in half",
            )
        }

    @Test
    fun testAlignByModifiersChain_lastDeclarationWins() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    BaselineChild(40, SwingModifier.alignByBaseline())
                    BaselineChild(
                        40 / 2,
                        SwingModifier.align(Alignment.Top).alignByBaseline(),
                    )
                }
            }

            assertEquals(
                Rectangle(40, 40 / 2, 40, 40),
                childBounds()[1],
                "an alignment and a baseline stand in the same place on a modifier, which is folded in " +
                    "declaration order, so the baseline declared last places the child on the shared line " +
                    "rather than the alignment before it holding it against the row's top edge",
            )
        }

    @Test
    fun testRow_Rtl_arrangementStart() =
        runComposeSwingTest {
            setContent { UnevenRightToLeftRow(Arrangement.Start) }

            assertEquals(
                listOf(
                    Rectangle(420 - 35, 0, 35, 35),
                    Rectangle(420 - 3 * 35, 0, 2 * 35, 2 * 35),
                ),
                childBounds(),
                "a row arranges its children from its leading edge, the right one under a right-to-left " +
                    "orientation, so the first child declared is the rightmost and the width to spare is " +
                    "left at the left edge",
            )
        }

    @Test
    fun testRow_Rtl_arrangementCenter() =
        runComposeSwingTest {
            setContent { RightToLeftRow(Arrangement.Center) }

            assertEquals(
                squareRowCells(100, 260, 160, 60),
                childBounds(),
                "Arrangement.Center must keep the children together with half the width to spare on either " +
                    "side, laid out from the row's right edge under a right-to-left orientation",
            )
        }

    @Test
    fun testRow_Rtl_arrangementSpaceEvenly() =
        runComposeSwingTest {
            setContent { RightToLeftRow(Arrangement.SpaceEvenly) }

            assertEquals(
                squareRowCells(100, 290, 160, 30),
                childBounds(),
                "Arrangement.SpaceEvenly must make the gaps between the children and at both edges equal, " +
                    "counting them from the row's right edge under a right-to-left orientation",
            )
        }

    @Test
    fun testRow_Rtl_arrangementSpaceBetween_singleItem() =
        runComposeSwingTest {
            setContent { RightToLeftRow(Arrangement.SpaceBetween, count = 1) }

            assertEquals(
                squareRowCells(100, 420 - 100),
                childBounds(),
                "Arrangement.SpaceBetween has no gap between children to fill for a lone child, which stays " +
                    "at the row's leading edge, the right one under a right-to-left orientation",
            )
        }

    @Test
    fun testRow_Rtl_arrangementSpaceBetween_multipleItems() =
        runComposeSwingTest {
            setContent { RightToLeftRow(Arrangement.SpaceBetween) }

            assertEquals(
                squareRowCells(100, 320, 160, 0),
                childBounds(),
                "Arrangement.SpaceBetween must put the whole width to spare into the gaps between the " +
                    "children and none at the edges, reading those children from the row's right edge " +
                    "under a right-to-left orientation",
            )
        }

    @Test
    fun testRow_Rtl_arrangementSpaceAround() =
        runComposeSwingTest {
            setContent { RightToLeftRow(Arrangement.SpaceAround) }

            assertEquals(
                squareRowCells(100, 300, 160, 20),
                childBounds(),
                "Arrangement.SpaceAround must give each child an equal gap, halved where it meets an edge, " +
                    "reading those children from the row's right edge under a right-to-left orientation",
            )
        }

    @Test
    fun testRow_Rtl_arrangementEnd() =
        runComposeSwingTest {
            setContent { UnevenRightToLeftRow(Arrangement.End) }

            assertEquals(
                listOf(
                    Rectangle(2 * 35, 0, 35, 35),
                    Rectangle(0, 0, 2 * 35, 2 * 35),
                ),
                childBounds(),
                "Arrangement.End must pack the children against the row's trailing edge, the left one " +
                    "under a right-to-left orientation, so the width to spare is left at the right edge",
            )
        }

    @Test
    fun testRow_Rtl_withSpacedByAlignedArrangement() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier =
                        containerModifier(50, 50, ComponentOrientation.RIGHT_TO_LEFT),
                    horizontalArrangement = Arrangement.spacedBy(10, Alignment.End),
                ) {
                    Child(0, 20, 20)
                    Child(1, 20, 20)
                }
            }

            assertEquals(
                Dimension(50, 50),
                containerSize(),
                "the row must keep the extent imposed on it, which its two children and their gap fill exactly",
            )
            assertEquals(
                listOf(
                    Rectangle(20 + 10, 0, 20, 20),
                    Rectangle(0, 0, 20, 20),
                ),
                childBounds(),
                "Arrangement.spacedBy must hold its gap between the children and read them from the row's " +
                    "leading edge, the right one under a right-to-left orientation, so the first child " +
                    "declared sits a child and a gap in from that edge",
            )
        }

    @Test
    fun testColumn_Rtl_gravityStart() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier =
                        containerModifier(420, 3 * 35, ComponentOrientation.RIGHT_TO_LEFT),
                ) {
                    Child(0, 35, 35)
                    Child(1, 2 * 35, 2 * 35)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(420 - 35, 0, 35, 35),
                    Rectangle(
                        420 - 2 * 35,
                        35,
                        2 * 35,
                        2 * 35,
                    ),
                ),
                childBounds(),
                "a column places a child it was given no alignment for against its leading edge, the right " +
                    "one under a right-to-left orientation, whatever width that child asks for",
            )
        }

    @Test
    fun testColumn_Rtl_gravityEnd() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier =
                        containerModifier(
                            420,
                            3 * 50,
                            ComponentOrientation.RIGHT_TO_LEFT,
                        ),
                ) {
                    Child(0, 50, 50, SwingModifier.align(Alignment.End))
                    Child(
                        1,
                        2 * 50,
                        2 * 50,
                        SwingModifier.align(Alignment.End),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 50, 50),
                    Rectangle(0, 50, 2 * 50, 2 * 50),
                ),
                childBounds(),
                "Alignment.End must put each child against the column's trailing edge, the left one under a " +
                    "right-to-left orientation",
            )
        }

    @Test
    fun testRow_absoluteArrangementLeft() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.Left) }

            assertEquals(
                squareRowCells(100, 0, 100, 200),
                childBounds(),
                "Arrangement.Absolute.Left must pack the children against the row's left edge in " +
                    "declaration order, leaving the surplus to their right",
            )
        }

    @Test
    fun testRow_Rtl_absoluteArrangementLeft() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.Left, rightToLeft = true) }

            assertEquals(
                squareRowCells(100, 0, 100, 200),
                childBounds(),
                "Arrangement.Absolute.Left names an edge of the row rather than its leading side, so a " +
                    "right-to-left orientation must leave the children exactly where a left-to-right one puts them",
            )
        }

    @Test
    fun testRow_absoluteArrangementRight() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.Right) }

            assertEquals(
                squareRowCells(100, 300, 400, 500),
                childBounds(),
                "Arrangement.Absolute.Right must pack the children against the row's right edge in " +
                    "declaration order, leaving the surplus to their left",
            )
        }

    @Test
    fun testRow_Rtl_absoluteArrangementRight() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.Right, rightToLeft = true) }

            assertEquals(
                squareRowCells(100, 300, 400, 500),
                childBounds(),
                "Arrangement.Absolute.Right names an edge of the row rather than its trailing side, so a " +
                    "right-to-left orientation must leave the children exactly where a left-to-right one puts them",
            )
        }

    @Test
    fun testRow_absoluteArrangementCenter() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.Center) }

            assertEquals(
                squareRowCells(100, 150, 250, 350),
                childBounds(),
                "Arrangement.Absolute.Center must keep the children together in declaration order with " +
                    "half the surplus at either edge of the row",
            )
        }

    @Test
    fun testRow_Rtl_absoluteArrangementCenter() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.Center, rightToLeft = true) }

            assertEquals(
                squareRowCells(100, 150, 250, 350),
                childBounds(),
                "Arrangement.Absolute.Center must keep the children in declaration order from the left " +
                    "even under a right-to-left orientation, unlike the mirroring Arrangement.Center",
            )
        }

    @Test
    fun testRow_absoluteArrangementSpaceEvenly() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.SpaceEvenly) }

            assertEquals(
                squareRowCells(100, 75, 250, 425),
                childBounds(),
                "Arrangement.Absolute.SpaceEvenly must make the gaps between the children and at both " +
                    "edges of the row equal, the children staying in declaration order",
            )
        }

    @Test
    fun testRow_Row_absoluteArrangementSpaceEvenly() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.SpaceEvenly, rightToLeft = true) }

            assertEquals(
                squareRowCells(100, 75, 250, 425),
                childBounds(),
                "Arrangement.Absolute.SpaceEvenly must lay the equal gaps out from the left even under a " +
                    "right-to-left orientation, unlike the mirroring Arrangement.SpaceEvenly",
            )
        }

    @Test
    fun testRow_absoluteArrangementSpaceBetween() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.SpaceBetween) }

            assertEquals(
                squareRowCells(100, 0, 250, 500),
                childBounds(),
                "Arrangement.Absolute.SpaceBetween must put the whole surplus between the children and " +
                    "none at the edges, the first child staying at the row's left edge",
            )
        }

    @Test
    fun testRow_Row_absoluteArrangementSpaceBetween() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.SpaceBetween, rightToLeft = true) }

            assertEquals(
                squareRowCells(100, 0, 250, 500),
                childBounds(),
                "Arrangement.Absolute.SpaceBetween must share the surplus out from the left in " +
                    "declaration order even under a right-to-left orientation, unlike the mirroring " +
                    "Arrangement.SpaceBetween",
            )
        }

    @Test
    fun testRow_absoluteArrangementSpaceAround() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.SpaceAround) }

            assertEquals(
                squareRowCells(100, 50, 250, 450),
                childBounds(),
                "Arrangement.Absolute.SpaceAround must give each child an equal gap of its own, halved " +
                    "where that gap meets an edge of the row",
            )
        }

    @Test
    fun testRow_Rtl_absoluteArrangementSpaceAround() =
        runComposeSwingTest {
            setContent { AbsoluteArrangementRow(Arrangement.Absolute.SpaceAround, rightToLeft = true) }

            assertEquals(
                squareRowCells(100, 50, 250, 450),
                childBounds(),
                "Arrangement.Absolute.SpaceAround must hand out the per-child gaps from the left even " +
                    "under a right-to-left orientation, unlike the mirroring Arrangement.SpaceAround",
            )
        }

    @Test
    fun testRow_Rtl_withSpacedByAlignedAbsoluteArrangement() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier =
                        containerModifier(
                            50,
                            50,
                            ComponentOrientation.RIGHT_TO_LEFT,
                        ),
                    horizontalArrangement = Arrangement.Absolute.spacedBy(10, Alignment.End),
                ) {
                    repeat(2) { Child(it, 20, 20) }
                }
            }

            assertEquals(
                50,
                containerSize().width,
                "the row must hold the width it was given, which its two children and the gap between " +
                    "them exactly fill",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, 20, 20),
                    Rectangle(
                        20 + 10,
                        0,
                        20,
                        20,
                    ),
                ),
                childBounds(),
                "Arrangement.Absolute.spacedBy must hold its gap between the children and pack them from " +
                    "the left in declaration order even under a right-to-left orientation, its alignment " +
                    "having no width left over to place the group in",
            )
        }

    @Test
    fun testRow_AlignInspectableValue() {
        val declared = with(RowScopeInstance) { SwingModifier.align(Alignment.Bottom) }

        assertEquals("align", declared.lastElement().name, "align must report itself under its own name")
        assertEquals(
            mapOf("alignment" to VerticalAxisAlignment(Alignment.Bottom)),
            declared.lastElement().declaredValues,
            "and must report the cross-axis placement it was declared with",
        )
    }

    @Test
    fun testRow_AlignByInspectableValue() {
        val declared = with(RowScopeInstance) { SwingModifier.alignByBaseline() }

        assertEquals(
            "align",
            declared.lastElement().name,
            "a placement on the shared baseline stands where an align does, so it reports that name",
        )
        assertEquals(
            mapOf("alignment" to BaselineAxisAlignment),
            declared.lastElement().declaredValues,
            "and must report the shared baseline as the placement it declared",
        )
    }

    @Test
    fun testRow_WeightInspectableValue() {
        val declared = with(RowScopeInstance) { SwingModifier.weight(2f, fill = false) }

        assertEquals("weight", declared.lastElement().name, "weight must report itself under its own name")
        assertEquals(
            mapOf("weight" to WeightPlacement(2f, fill = false)),
            declared.lastElement().declaredValues,
            "and must report both the share it claims and that the child is not to fill what it is granted",
        )
    }

    @Test
    fun testColumn_AlignInspectableValue() {
        val declared = with(ColumnScopeInstance) { SwingModifier.align(Alignment.Start) }

        assertEquals("align", declared.lastElement().name, "align must report itself under its own name")
        assertEquals(
            mapOf("alignment" to HorizontalAxisAlignment(Alignment.Start)),
            declared.lastElement().declaredValues,
            "and must report the cross-axis placement it was declared with",
        )
    }

    @Test
    fun testColumn_WeightInspectableValue() {
        val declared = with(ColumnScopeInstance) { SwingModifier.weight(2f, fill = false) }

        assertEquals("weight", declared.lastElement().name, "weight must report itself under its own name")
        assertEquals(
            mapOf("weight" to WeightPlacement(2f, fill = false)),
            declared.lastElement().declaredValues,
            "and must report both the share it claims and that the child is not to fill what it is granted",
        )
    }
}

/** A child of the baseline row whose component reports [baseline] whatever extent it is asked at. */
@Composable
private fun BaselineChild(
    baseline: Int,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { ConstantBaselinePanel(baseline) },
        modifier = modifier.preferredSize(40, 40),
    )
}

/**
 * A component carrying the baseline the test chose for it. It reports that baseline at every extent,
 * because a child claiming a share of its row's leftover width is asked at the width it was granted
 * rather than at the width it asked for.
 */
private class ConstantBaselinePanel(
    private val reported: Int,
) : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = reported
}

/** A vertical alignment centering its child, and recording the height and the space it was handed. */
private class RecordingVerticalAlignment : Alignment.Vertical {
    var capturedSize = 0
    var capturedSpace = 0

    override fun align(
        size: Int,
        space: Int,
    ): Int {
        capturedSize = size
        capturedSpace = space
        return ((space - size) / 2).coerceIn(0, space - size)
    }
}

/** A horizontal alignment centering its child, and recording the width and the space it was handed. */
private class RecordingHorizontalAlignment : Alignment.Horizontal {
    var capturedSize = 0
    var capturedSpace = 0

    override fun align(
        size: Int,
        space: Int,
        orientation: ComponentOrientation,
    ): Int {
        capturedSize = size
        capturedSpace = space
        return ((space - size) / 2).coerceIn(0, space - size)
    }
}

/** The bounds a row assigns three 50-pixel children placed 0, 75 and 150 pixels down its height. */
private fun rowCrossCells(): List<Rectangle> =
    listOf(0, 75, 150).mapIndexed { index, top ->
        Rectangle(index * 50, top, 50, 50)
    }

/** The bounds a column assigns three 50-pixel children placed 0, 75 and 150 pixels across its width. */
private fun columnCrossCells(): List<Rectangle> =
    listOf(0, 75, 150).mapIndexed { index, left ->
        Rectangle(left, index * 50, 50, 50)
    }

/** A row given more width than its children ask for, so an arrangement has room to place. */
@Composable
private fun ArrangedRow(
    arrangement: Arrangement.Horizontal,
    children: Int = CHILD_COUNT,
) {
    Row(
        modifier = containerModifier(450, 50),
        horizontalArrangement = arrangement,
    ) {
        repeat(children) { Child(it, 50, 50) }
    }
}

/** A column given more height than its children ask for, so an arrangement has room to place. */
@Composable
private fun ArrangedColumn(arrangement: Arrangement.Vertical) {
    Column(
        modifier = containerModifier(50, 450),
        verticalArrangement = arrangement,
    ) {
        repeat(CHILD_COUNT) { Child(it, 50, 50) }
    }
}

/** A row with room for two children and the gap between them, and nothing to spare beyond that. */
@Composable
private fun TightRow(
    arrangement: Arrangement.Horizontal,
    children: Int,
    rightToLeft: Boolean = false,
) {
    Row(
        modifier =
            containerModifier(
                50,
                50,
                if (rightToLeft) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT,
            ),
        horizontalArrangement = arrangement,
    ) {
        repeat(children) { Child(it, 20, 20) }
    }
}

/** A column with room for two children and the gap between them, and nothing to spare beyond that. */
@Composable
private fun TightColumn(
    arrangement: Arrangement.Vertical,
    children: Int,
) {
    Column(
        modifier = containerModifier(50, 50),
        verticalArrangement = arrangement,
    ) {
        repeat(children) { Child(it, 20, 20) }
    }
}

/** The bounds a row assigns square children of [extent] lined up at [lefts]. */
private fun squareRowCells(
    extent: Int,
    vararg lefts: Int,
): List<Rectangle> = lefts.map { Rectangle(it, 0, extent, extent) }

/** The bounds a column assigns 50-pixel square children stacked at the requested vertical positions. */
private fun squareColumnCells(vararg tops: Int): List<Rectangle> = tops.map { Rectangle(0, it, 50, 50) }

/**
 * A child of one fixed extent: it asks for [width] by [height] and declares it can shrink to no less,
 * so what its container asks for follows from the child's own declaration and not from the text it holds.
 */
@Composable
private fun FixedChild(
    index: Int,
    width: Int,
    height: Int,
    modifier: SwingModifier = SwingModifier,
) {
    Label("child $index", modifier = modifier.preferredSize(width, height).minimumSize(width, height))
}

/** A child asking for no extent of its own along either axis, which its container adds nothing for. */
@Composable
@NonRestartableComposable
private fun SizelessChild(
    index: Int,
    modifier: SwingModifier = SwingModifier,
) {
    FixedChild(index, 0, 0, modifier)
}

/** The container a case tagged [tag], for a case declaring more than one. */
private fun ComposeSwingTest.taggedPanel(tag: String): JComponent = onNodeWithTag(tag).fetch<JComponent>()

/** A right-to-left row with width to spare over [count] equal children, for [arrangement] to place. */
@Composable
private fun RightToLeftRow(
    arrangement: Arrangement.Horizontal,
    count: Int = 3,
) {
    Row(
        modifier = containerModifier(420, 100, ComponentOrientation.RIGHT_TO_LEFT),
        horizontalArrangement = arrangement,
    ) {
        repeat(count) { Child(it, 100, 100) }
    }
}

/** The same row with two children of different sizes, so which end it reads them from is unmistakable. */
@Composable
private fun UnevenRightToLeftRow(arrangement: Arrangement.Horizontal) {
    Row(
        modifier =
            containerModifier(420, 2 * 35, ComponentOrientation.RIGHT_TO_LEFT),
        horizontalArrangement = arrangement,
    ) {
        Child(0, 35, 35)
        Child(1, 2 * 35, 2 * 35)
    }
}

/**
 * A row wider than its three children ask for, so every absolute arrangement has surplus to place,
 * laid out either left-to-right or right-to-left so an arrangement naming an edge of the row can be
 * told from one naming a side of it.
 */
@Composable
private fun AbsoluteArrangementRow(
    arrangement: Arrangement.Horizontal,
    rightToLeft: Boolean = false,
) {
    Row(
        modifier =
            containerModifier(
                600,
                100,
                if (rightToLeft) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT,
            ),
        horizontalArrangement = arrangement,
    ) {
        repeat(CHILD_COUNT) { Child(it, 100, 100) }
    }
}

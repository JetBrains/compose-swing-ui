package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An arrangement decides where the space a container's children leave along its axis goes. The
 * children keep the extent they asked for either way, so an arrangement's whole effect is the position
 * each child ends up at, which is what every test here reads back.
 *
 * The fixture children are three, one, and none, because an arrangement that shares space out between
 * children has to report for the cases where there are no gaps to share it into.
 */
class RowColumnArrangementTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource(
        "columnEdgeAndCenterArrangements",
        "columnDistributedSpaceArrangements",
        "columnSpacedAndAlignedArrangements",
    )
    fun aColumnPlacesItsChildrenWhereItsArrangementSays(
        arrangement: Arrangement.Vertical,
        expected: List<Rectangle>,
        message: String,
    ) = runComposeSwingTest {
        setContent { ArrangedColumn(arrangement) }
        assertEquals(expected, childBounds(), message)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(
        "rowStartAndEndArrangements",
        "rowSpaceBetweenArrangements",
        "rowSpacedAndAlignedArrangements",
        "rowAbsoluteEdgeAndCenterArrangements",
        "rowAbsoluteDistributedSpaceArrangements",
        "rowAbsoluteSpacedAndAlignedArrangements",
    )
    fun aRowPlacesItsChildrenWhereItsArrangementSays(
        arrangement: Arrangement.Horizontal,
        orientation: ComponentOrientation,
        expected: List<Rectangle>,
        message: String,
    ) = runComposeSwingTest {
        setContent { ArrangedRow(arrangement, orientation) }
        assertEquals(expected, childBounds(), message)
    }

    @Test
    fun aLoneChildGoesWhereEachArrangementPutsIt() =
        runComposeSwingTest {
            var arrangement by mutableStateOf(Arrangement.Top)
            setContent { ArrangedLoneColumn(arrangement) }

            assertEquals(columnRows(0), childBounds(), "Arrangement.Top puts a lone child at the top")

            arrangement = Arrangement.Bottom
            awaitIdle()
            assertEquals(columnRows(260), childBounds(), "Arrangement.Bottom puts a lone child at the bottom")

            arrangement = Arrangement.Center
            awaitIdle()
            assertEquals(columnRows(130), childBounds(), "Arrangement.Center puts a lone child halfway down")

            arrangement = Arrangement.SpaceBetween
            awaitIdle()
            assertEquals(
                columnRows(0),
                childBounds(),
                "Arrangement.SpaceBetween has no gap to fill for a lone child, which stays at the leading edge",
            )

            arrangement = Arrangement.SpaceAround
            awaitIdle()
            assertEquals(
                columnRows(130),
                childBounds(),
                "Arrangement.SpaceAround gives a lone child the whole surplus as its own gap, half on each side",
            )

            arrangement = Arrangement.SpaceEvenly
            awaitIdle()
            assertEquals(
                columnRows(130),
                childBounds(),
                "Arrangement.SpaceEvenly leaves a lone child one equal gap above it and one below",
            )
        }

    @Test
    fun aLoneChildInARightToLeftRowGoesWhereEachArrangementPutsIt() =
        runComposeSwingTest {
            var arrangement by mutableStateOf(Arrangement.Start)
            setContent { ArrangedLoneRightToLeftRow(arrangement) }

            assertEquals(
                rowCells(250),
                childBounds(),
                "Arrangement.Start puts a lone child at the row's leading edge, the right one under a " +
                    "right-to-left orientation",
            )

            arrangement = Arrangement.End
            awaitIdle()
            assertEquals(
                rowCells(0),
                childBounds(),
                "Arrangement.End puts a lone child at the row's trailing edge, the left one under a " +
                    "right-to-left orientation",
            )

            arrangement = Arrangement.Center
            awaitIdle()
            assertEquals(rowCells(125), childBounds(), "Arrangement.Center puts a lone child halfway across the row")

            arrangement = Arrangement.SpaceBetween
            awaitIdle()
            assertEquals(
                rowCells(250),
                childBounds(),
                "Arrangement.SpaceBetween has no gap to fill for a lone child, which stays at the row's " +
                    "leading edge, the right one under a right-to-left orientation",
            )

            arrangement = Arrangement.SpaceAround
            awaitIdle()
            assertEquals(
                rowCells(125),
                childBounds(),
                "Arrangement.SpaceAround gives a lone child the whole surplus as its own gap, half on each side",
            )

            arrangement = Arrangement.SpaceEvenly
            awaitIdle()
            assertEquals(
                rowCells(125),
                childBounds(),
                "Arrangement.SpaceEvenly leaves a lone child one equal gap on each side",
            )
        }

    @Test
    fun anEmptyColumnPlacesNothingAndAsksForNoSpace() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {}
            }

            assertEquals(emptyList(), childBounds(), "a column with no children has nothing to place")
            onNodeWithTag(CONTAINER_TAG).assertProperty(
                Dimension(0, 0),
                "a column with no children must ask for no space of its own",
            ) { preferredSize }
        }

    @Test
    fun anEmptyRowPlacesNothingAndAsksForNoGap() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                ) {}
            }

            assertEquals(emptyList(), childBounds(), "a row with no children has nothing to place")
            onNodeWithTag(CONTAINER_TAG).assertProperty(
                Dimension(0, 0),
                "a row with no children holds no gap, so it must ask for no space of its own",
            ) { preferredSize }
        }

    @Test
    fun aColumnAsksForTheGapItsArrangementHoldsBetweenTheChildren() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    verticalArrangement = Arrangement.spacedBy(GAP),
                ) {
                    repeat(CHILD_COUNT) { SizedChild(it) }
                }
            }

            onNodeWithTag(CONTAINER_TAG).assertProperty(
                Dimension(CHILD_WIDTH, CHILD_COUNT * CHILD_HEIGHT + GAP * (CHILD_COUNT - 1)),
                "a column must ask for its children's heights plus the gap its arrangement holds between them",
            ) { preferredSize }
        }

    companion object {
        @JvmStatic
        fun columnEdgeAndCenterArrangements(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of("topPacksTheChildrenAgainstTheTopOfTheColumn", Arrangement.Top),
                    columnRows(0, 40, 80),
                    "Arrangement.Top must pack the children against the top, leaving the surplus below them",
                ),
                Arguments.of(
                    Named.of("bottomPacksTheChildrenAgainstTheBottomOfTheColumn", Arrangement.Bottom),
                    columnRows(180, 220, 260),
                    "Arrangement.Bottom must pack the children against the bottom, leaving the surplus above them",
                ),
                Arguments.of(
                    Named.of("centerKeepsTheChildrenTogetherHalfwayDownTheColumn", Arrangement.Center),
                    columnRows(90, 130, 170),
                    "Arrangement.Center must keep the children together with half the surplus on either side",
                ),
            )

        @JvmStatic
        fun columnDistributedSpaceArrangements(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of("spaceBetweenSplitsTheSurplusIntoTheGapsBetweenChildren", Arrangement.SpaceBetween),
                    columnRows(0, 130, 260),
                    "Arrangement.SpaceBetween must put the surplus between the children and none at the edges",
                ),
                Arguments.of(
                    Named.of("spaceAroundGivesEveryChildAGapOfItsOwn", Arrangement.SpaceAround),
                    columnRows(30, 130, 230),
                    "Arrangement.SpaceAround must give each child an equal gap, halved where it meets an edge",
                ),
                Arguments.of(
                    Named.of("spaceEvenlySplitsTheSurplusIntoEqualGapsIncludingTheEdges", Arrangement.SpaceEvenly),
                    columnRows(45, 130, 215),
                    "Arrangement.SpaceEvenly must make the gaps between the children and at both edges equal",
                ),
            )

        @JvmStatic
        fun columnSpacedAndAlignedArrangements(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of("spacedByHoldsAFixedGapAndPacksTheGroupAgainstTheTop", Arrangement.spacedBy(GAP)),
                    columnRows(0, 50, 100),
                    "Arrangement.spacedBy must hold its gap between the children and leave the surplus below them",
                ),
                Arguments.of(
                    Named.of(
                        "spacedByPlacesTheWholeGroupAtTheAlignmentItIsGiven",
                        Arrangement.spacedBy(GAP, Alignment.Bottom),
                    ),
                    columnRows(160, 210, 260),
                    "Arrangement.spacedBy must keep its gap and put the group as a whole where its alignment says",
                ),
                Arguments.of(
                    Named.of(
                        "alignedPlacesTheChildrenTogetherWhereItsAlignmentSays",
                        Arrangement.aligned(Alignment.CenterVertically),
                    ),
                    columnRows(90, 130, 170),
                    "Arrangement.aligned must keep the children edge to edge and place the group at its alignment",
                ),
                Arguments.of(
                    Named.of(
                        "absoluteSpacedByVerticalPlacesGroupInColumn",
                        Arrangement.Absolute.spacedBy(GAP, Alignment.Bottom),
                    ),
                    columnRows(160, 210, 260),
                    "Arrangement.Absolute.spacedBy on vertical axis must place group at bottom",
                ),
            )

        @JvmStatic
        fun rowStartAndEndArrangements(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of("startPacksTheChildrenAgainstTheLeadingEdgeOfTheRow", Arrangement.Start),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(0, 50, 100),
                    "Arrangement.Start must pack the children against the leading edge, leaving the surplus after",
                ),
                Arguments.of(
                    Named.of("startPacksTheChildrenAgainstTheRightEdgeOfARightToLeftRow", Arrangement.Start),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    rowCells(250, 200, 150),
                    "Arrangement.Start must pack the children against the row's leading edge, the right one " +
                        "under a right-to-left orientation, leaving the surplus after them",
                ),
                Arguments.of(
                    Named.of("endPacksTheChildrenAgainstTheTrailingEdgeOfTheRow", Arrangement.End),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(150, 200, 250),
                    "Arrangement.End must pack the children against the trailing edge, leaving the surplus before",
                ),
                Arguments.of(
                    Named.of("endPacksTheChildrenAgainstTheLeftEdgeOfARightToLeftRow", Arrangement.End),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    rowCells(100, 50, 0),
                    "Arrangement.End must pack the children against the row's trailing edge, the left one " +
                        "under a right-to-left orientation, leaving the surplus before them",
                ),
            )

        @JvmStatic
        fun rowSpaceBetweenArrangements(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of("aRowSplitsItsSurplusIntoTheGapsBetweenChildren", Arrangement.SpaceBetween),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(0, 125, 250),
                    "an arrangement that serves either axis must share the surplus out along the row as well",
                ),
                Arguments.of(
                    Named.of(
                        "aRightToLeftRowSplitsItsSurplusIntoTheGapsBetweenChildrenInMirroredOrder",
                        Arrangement.SpaceBetween,
                    ),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    rowCells(250, 125, 0),
                    "Arrangement.SpaceBetween must share the surplus out between the children in their " +
                        "declaration order read from the row's leading edge, the right one under a " +
                        "right-to-left orientation",
                ),
            )

        @JvmStatic
        fun rowSpacedAndAlignedArrangements(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of("aRowHoldsTheFixedGapItsArrangementDeclares", Arrangement.spacedBy(GAP)),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(0, 60, 120),
                    "Arrangement.spacedBy must hold its gap between the children and leave the surplus after them",
                ),
                Arguments.of(
                    Named.of(
                        "aRowSpacedByPlacesTheWholeGroupAtTheAlignmentItIsGiven",
                        Arrangement.spacedBy(GAP, Alignment.End),
                    ),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(130, 190, 250),
                    "Arrangement.spacedBy must keep its gap and put the group as a whole where its horizontal " +
                        "alignment says",
                ),
                Arguments.of(
                    Named.of(
                        "aRowAlignedPlacesTheChildrenTogetherWhereItsAlignmentSays",
                        Arrangement.aligned(Alignment.CenterHorizontally),
                    ),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(75, 125, 175),
                    "Arrangement.aligned must keep the children edge to edge and place the group at its " +
                        "horizontal alignment",
                ),
                Arguments.of(
                    Named.of("aRightToLeftRowHoldsTheFixedGapItsArrangementDeclares", Arrangement.spacedBy(GAP)),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    rowCells(250, 190, 130),
                    "Arrangement.spacedBy must hold its gap between the children and pack the group against " +
                        "the row's leading edge, the right one under a right-to-left orientation",
                ),
                Arguments.of(
                    Named.of(
                        "aRightToLeftRowSpacedByPlacesTheWholeGroupAtTheAlignmentItIsGiven",
                        Arrangement.spacedBy(GAP, Alignment.End),
                    ),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    rowCells(120, 60, 0),
                    "Arrangement.spacedBy must keep its gap and put the group as a whole where its horizontal " +
                        "alignment says, resolved against the row's right-to-left orientation",
                ),
            )

        @JvmStatic
        fun rowAbsoluteEdgeAndCenterArrangements(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of("absoluteLeftPacksTheChildrenAgainstTheLeftEdge", Arrangement.Absolute.Left),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(0, 50, 100),
                    "Arrangement.Absolute.Left must pack the children against the left edge",
                ),
                Arguments.of(
                    Named.of(
                        "absoluteLeftPacksTheChildrenAgainstTheLeftEdgeOfARightToLeftRowToo",
                        Arrangement.Absolute.Left,
                    ),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    rowCells(0, 50, 100),
                    "Arrangement.Absolute.Left must pack the children against the left edge even under a " +
                        "right-to-left orientation, unlike the mirroring Arrangement.Start",
                ),
                Arguments.of(
                    Named.of("absoluteRightPacksTheChildrenAgainstTheRightEdge", Arrangement.Absolute.Right),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(150, 200, 250),
                    "Arrangement.Absolute.Right must pack the children against the right edge",
                ),
                Arguments.of(
                    Named.of(
                        "absoluteRightPacksTheChildrenAgainstTheRightEdgeOfARightToLeftRowToo",
                        Arrangement.Absolute.Right,
                    ),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    rowCells(150, 200, 250),
                    "Arrangement.Absolute.Right must pack the children against the right edge even under a " +
                        "right-to-left orientation, unlike the mirroring Arrangement.End",
                ),
                Arguments.of(
                    Named.of("absoluteCenterCentersTheChildren", Arrangement.Absolute.Center),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(75, 125, 175),
                    "Arrangement.Absolute.Center must place the group halfway across the row",
                ),
            )

        @JvmStatic
        fun rowAbsoluteDistributedSpaceArrangements(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of(
                        "absoluteSpaceBetweenSplitsTheSurplusInDeclarationOrder",
                        Arrangement.Absolute.SpaceBetween,
                    ),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(0, 125, 250),
                    "Arrangement.Absolute.SpaceBetween must share the surplus out in declaration order",
                ),
                Arguments.of(
                    Named.of(
                        "absoluteSpaceBetweenSplitsTheSurplusInDeclarationOrderOnARightToLeftRowToo",
                        Arrangement.Absolute.SpaceBetween,
                    ),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    rowCells(0, 125, 250),
                    "Arrangement.Absolute.SpaceBetween must share the surplus out in declaration order even " +
                        "under a right-to-left orientation, unlike the mirroring Arrangement.SpaceBetween",
                ),
                Arguments.of(
                    Named.of("absoluteSpaceAroundGivesEachChildAnEqualGap", Arrangement.Absolute.SpaceAround),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(25, 125, 225),
                    "Arrangement.Absolute.SpaceAround must give each child an equal gap",
                ),
                Arguments.of(
                    Named.of("absoluteSpaceEvenlySplitsTheSurplus", Arrangement.Absolute.SpaceEvenly),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(38, 125, 213),
                    "Arrangement.Absolute.SpaceEvenly must distribute equal gaps",
                ),
            )

        @JvmStatic
        fun rowAbsoluteSpacedAndAlignedArrangements(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of("absoluteSpacedByPacksTheGroupAgainstTheLeftEdge", Arrangement.Absolute.spacedBy(GAP)),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(0, 60, 120),
                    "Arrangement.Absolute.spacedBy must hold its gap and pack the group against the left edge",
                ),
                Arguments.of(
                    Named.of(
                        "absoluteSpacedByPacksTheGroupAgainstTheLeftEdgeOfARightToLeftRowToo",
                        Arrangement.Absolute.spacedBy(GAP),
                    ),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    rowCells(0, 60, 120),
                    "Arrangement.Absolute.spacedBy must hold its gap and pack the group against the left edge " +
                        "even under a right-to-left orientation, unlike the mirroring Arrangement.spacedBy",
                ),
                Arguments.of(
                    Named.of(
                        "absoluteSpacedByWithAlignmentPlacesGroup",
                        Arrangement.Absolute.spacedBy(GAP, Alignment.End),
                    ),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(130, 190, 250),
                    "Arrangement.Absolute.spacedBy with alignment must place the group where alignment says",
                ),
                Arguments.of(
                    Named.of(
                        "absoluteAlignedPlacesChildrenTogether",
                        Arrangement.Absolute.aligned(Alignment.CenterHorizontally),
                    ),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    rowCells(75, 125, 175),
                    "Arrangement.Absolute.aligned must keep children edge to edge",
                ),
            )
    }
}

/** The gap a `spacedBy` fixture holds between two adjacent children. */
private const val GAP = 10

/** The extent a fixture container is given along the axis it arranges its children on. */
private const val CONTAINER_MAIN = 300

/** The extent a fixture container is given across that axis, wider than any child asks for. */
private const val CONTAINER_CROSS = 100

/** A column with more height than its three children ask for, so it has surplus to place. */
@Composable
private fun ArrangedColumn(arrangement: Arrangement.Vertical) {
    Column(
        modifier = SwingModifier.containerTag().styleable(containerStyle(CONTAINER_CROSS, CONTAINER_MAIN)),
        verticalArrangement = arrangement,
    ) {
        repeat(CHILD_COUNT) { SizedChild(it) }
    }
}

/** The same column with a single child, the case an arrangement has no gap between children to fill. */
@Composable
private fun ArrangedLoneColumn(arrangement: Arrangement.Vertical) {
    Column(
        modifier = SwingModifier.containerTag().styleable(containerStyle(CONTAINER_CROSS, CONTAINER_MAIN)),
        verticalArrangement = arrangement,
    ) {
        SizedChild(0)
    }
}

/** A row with more width than its three children ask for, so it has surplus to place. */
@Composable
private fun ArrangedRow(
    arrangement: Arrangement.Horizontal,
    orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
) {
    Row(
        modifier = SwingModifier.containerTag().styleable(containerStyle(CONTAINER_MAIN, CONTAINER_CROSS, orientation)),
        horizontalArrangement = arrangement,
    ) {
        repeat(CHILD_COUNT) { SizedChild(it) }
    }
}

/**
 * A right-to-left row with a single child, the case an arrangement has no gap between children to fill
 * and the leading edge is the right one.
 */
@Composable
private fun ArrangedLoneRightToLeftRow(arrangement: Arrangement.Horizontal) {
    Row(
        modifier =
            SwingModifier.containerTag().styleable(
                containerStyle(CONTAINER_MAIN, CONTAINER_CROSS, ComponentOrientation.RIGHT_TO_LEFT),
            ),
        horizontalArrangement = arrangement,
    ) {
        SizedChild(0)
    }
}

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An arrangement decides where the space a container's children leave along its axis goes. The
 * children keep the extent they asked for either way, so the whole of an arrangement's effect is the
 * position each child ends up at, which is what every test here reads back.
 *
 * The container is always given more room along its axis than its children ask for, so there is space
 * left over to place; the fixture children are three, one, and none, because an arrangement that
 * shares space out between children has to answer for the cases where there are no gaps to share it
 * into.
 */
class RowColumnArrangementTest {
    @Test
    fun topPacksTheChildrenAgainstTheTopOfTheColumn() = runComposeSwingTest {
        setContent { ArrangedColumn(Arrangement.Top) }

        assertEquals(
            columnRows(0, 40, 80),
            childBounds(),
            "Arrangement.Top must pack the children against the top, leaving the surplus below them",
        )
    }

    @Test
    fun bottomPacksTheChildrenAgainstTheBottomOfTheColumn() = runComposeSwingTest {
        setContent { ArrangedColumn(Arrangement.Bottom) }

        assertEquals(
            columnRows(180, 220, 260),
            childBounds(),
            "Arrangement.Bottom must pack the children against the bottom, leaving the surplus above them",
        )
    }

    @Test
    fun centerKeepsTheChildrenTogetherHalfwayDownTheColumn() = runComposeSwingTest {
        setContent { ArrangedColumn(Arrangement.Center) }

        assertEquals(
            columnRows(90, 130, 170),
            childBounds(),
            "Arrangement.Center must keep the children together with half the surplus on either side",
        )
    }

    @Test
    fun spaceBetweenSplitsTheSurplusIntoTheGapsBetweenChildren() = runComposeSwingTest {
        setContent { ArrangedColumn(Arrangement.SpaceBetween) }

        assertEquals(
            columnRows(0, 130, 260),
            childBounds(),
            "Arrangement.SpaceBetween must put the surplus between the children and none at the edges",
        )
    }

    @Test
    fun spaceAroundGivesEveryChildAGapOfItsOwn() = runComposeSwingTest {
        setContent { ArrangedColumn(Arrangement.SpaceAround) }

        assertEquals(
            columnRows(30, 130, 230),
            childBounds(),
            "Arrangement.SpaceAround must give each child an equal gap, halved where it meets an edge",
        )
    }

    @Test
    fun spaceEvenlySplitsTheSurplusIntoEqualGapsIncludingTheEdges() = runComposeSwingTest {
        setContent { ArrangedColumn(Arrangement.SpaceEvenly) }

        assertEquals(
            columnRows(45, 130, 215),
            childBounds(),
            "Arrangement.SpaceEvenly must make the gaps between the children and at both edges equal",
        )
    }

    @Test
    fun spacedByHoldsAFixedGapAndPacksTheGroupAgainstTheTop() = runComposeSwingTest {
        setContent { ArrangedColumn(Arrangement.spacedBy(GAP)) }

        assertEquals(
            columnRows(0, 50, 100),
            childBounds(),
            "Arrangement.spacedBy must hold its gap between the children and leave the surplus below them",
        )
    }

    @Test
    fun spacedByPlacesTheWholeGroupAtTheAlignmentItIsGiven() = runComposeSwingTest {
        setContent { ArrangedColumn(Arrangement.spacedBy(GAP, Alignment.Bottom)) }

        assertEquals(
            columnRows(160, 210, 260),
            childBounds(),
            "Arrangement.spacedBy must keep its gap and put the group as a whole where its alignment says",
        )
    }

    @Test
    fun alignedPlacesTheChildrenTogetherWhereItsAlignmentSays() = runComposeSwingTest {
        setContent { ArrangedColumn(Arrangement.aligned(Alignment.CenterVertically)) }

        assertEquals(
            columnRows(90, 130, 170),
            childBounds(),
            "Arrangement.aligned must keep the children edge to edge and place the group at its alignment",
        )
    }

    @Test
    fun startPacksTheChildrenAgainstTheLeadingEdgeOfTheRow() = runComposeSwingTest {
        setContent { ArrangedRow(Arrangement.Start) }

        assertEquals(
            rowCells(0, 50, 100),
            childBounds(),
            "Arrangement.Start must pack the children against the leading edge, leaving the surplus after",
        )
    }

    @Test
    fun endPacksTheChildrenAgainstTheTrailingEdgeOfTheRow() = runComposeSwingTest {
        setContent { ArrangedRow(Arrangement.End) }

        assertEquals(
            rowCells(150, 200, 250),
            childBounds(),
            "Arrangement.End must pack the children against the trailing edge, leaving the surplus before",
        )
    }

    @Test
    fun aRowSplitsItsSurplusIntoTheGapsBetweenChildren() = runComposeSwingTest {
        setContent { ArrangedRow(Arrangement.SpaceBetween) }

        assertEquals(
            rowCells(0, 125, 250),
            childBounds(),
            "an arrangement that serves either axis must share the surplus out along the row as well",
        )
    }

    @Test
    fun aRowHoldsTheFixedGapItsArrangementDeclares() = runComposeSwingTest {
        setContent { ArrangedRow(Arrangement.spacedBy(GAP)) }

        assertEquals(
            rowCells(0, 60, 120),
            childBounds(),
            "Arrangement.spacedBy must hold its gap between the children and leave the surplus after them",
        )
    }

    @Test
    fun aRowSpacedByPlacesTheWholeGroupAtTheAlignmentItIsGiven() = runComposeSwingTest {
        setContent { ArrangedRow(Arrangement.spacedBy(GAP, Alignment.End)) }

        assertEquals(
            rowCells(130, 190, 250),
            childBounds(),
            "Arrangement.spacedBy must keep its gap and put the group as a whole where its horizontal " +
                "alignment says",
        )
    }

    @Test
    fun aRowAlignedPlacesTheChildrenTogetherWhereItsAlignmentSays() = runComposeSwingTest {
        setContent { ArrangedRow(Arrangement.aligned(Alignment.CenterHorizontally)) }

        assertEquals(
            rowCells(75, 125, 175),
            childBounds(),
            "Arrangement.aligned must keep the children edge to edge and place the group at its " +
                "horizontal alignment",
        )
    }

    @Test
    fun aLoneChildGoesWhereEachArrangementPutsIt() = runComposeSwingTest {
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
    fun anEmptyColumnPlacesNothingAndAsksForNoSpace() = runComposeSwingTest {
        setContent {
            Column(
                modifier = SwingModifier.testTag(CONTAINER_TAG),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {}
        }

        assertEquals(emptyList(), childBounds(), "a column with no children has nothing to place")
        assertEquals(
            Dimension(0, 0),
            containerPreferredSize(),
            "a column with no children must ask for no space of its own",
        )
    }

    @Test
    fun anEmptyRowPlacesNothingAndAsksForNoGap() = runComposeSwingTest {
        setContent {
            Row(
                modifier = SwingModifier.testTag(CONTAINER_TAG),
                horizontalArrangement = Arrangement.spacedBy(GAP),
            ) {}
        }

        assertEquals(emptyList(), childBounds(), "a row with no children has nothing to place")
        assertEquals(
            Dimension(0, 0),
            containerPreferredSize(),
            "a row with no children holds no gap, so it must ask for no space of its own",
        )
    }

    @Test
    fun aColumnAsksForTheGapItsArrangementHoldsBetweenTheChildren() = runComposeSwingTest {
        setContent {
            Column(
                modifier = SwingModifier.testTag(CONTAINER_TAG),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                repeat(CHILD_COUNT) { SizedChild(it) }
            }
        }

        assertEquals(
            Dimension(CHILD_WIDTH, CHILD_COUNT * CHILD_HEIGHT + GAP * (CHILD_COUNT - 1)),
            containerPreferredSize(),
            "a column must ask for its children's heights plus the gap its arrangement holds between them",
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
        modifier = containerModifier(CONTAINER_CROSS, CONTAINER_MAIN),
        verticalArrangement = arrangement,
    ) {
        repeat(CHILD_COUNT) { SizedChild(it) }
    }
}

/** The same column with a single child, the case an arrangement has no gap between children to fill. */
@Composable
private fun ArrangedLoneColumn(arrangement: Arrangement.Vertical) {
    Column(
        modifier = containerModifier(CONTAINER_CROSS, CONTAINER_MAIN),
        verticalArrangement = arrangement,
    ) {
        SizedChild(0)
    }
}

/** A row with more width than its three children ask for, so it has surplus to place. */
@Composable
private fun ArrangedRow(arrangement: Arrangement.Horizontal) {
    Row(
        modifier = containerModifier(CONTAINER_MAIN, CONTAINER_CROSS),
        horizontalArrangement = arrangement,
    ) {
        repeat(CHILD_COUNT) { SizedChild(it) }
    }
}

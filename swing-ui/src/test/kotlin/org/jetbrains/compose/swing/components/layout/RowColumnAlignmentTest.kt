package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An alignment decides where a child sits across the axis its container arranges children along. The
 * child keeps the extent it asked for across that axis too, so the whole of an alignment's effect is
 * the offset the child ends up at, which is what every test here reads back.
 *
 * The container is always wider (a column) or taller (a row) than its children ask for, so each child
 * has room across the axis to be placed in. A child that names an alignment of its own is placed by
 * that one, and its siblings are untouched.
 */
class RowColumnAlignmentTest {
    @Test
    fun startPutsAChildAgainstTheLeadingEdgeOfTheColumn() = runComposeSwingTest {
        setContent { AlignedColumn(Alignment.Start) }

        assertEquals(
            listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
            childBounds(),
            "Alignment.Start must put the child against the column's leading edge",
        )
    }

    @Test
    fun centerHorizontallyPutsAChildHalfwayAcrossTheColumn() = runComposeSwingTest {
        setContent { AlignedColumn(Alignment.CenterHorizontally) }

        assertEquals(
            listOf(Rectangle(75, 0, CHILD_WIDTH, CHILD_HEIGHT)),
            childBounds(),
            "Alignment.CenterHorizontally must leave equal width on either side of the child",
        )
    }

    @Test
    fun endPutsAChildAgainstTheTrailingEdgeOfTheColumn() = runComposeSwingTest {
        setContent { AlignedColumn(Alignment.End) }

        assertEquals(
            listOf(Rectangle(150, 0, CHILD_WIDTH, CHILD_HEIGHT)),
            childBounds(),
            "Alignment.End must put the child against the column's trailing edge",
        )
    }

    @Test
    fun topPutsAChildAgainstTheTopOfTheRow() = runComposeSwingTest {
        setContent { AlignedRow(Alignment.Top) }

        assertEquals(
            listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
            childBounds(),
            "Alignment.Top must put the child against the row's top edge",
        )
    }

    @Test
    fun centerVerticallyPutsAChildHalfwayDownTheRow() = runComposeSwingTest {
        setContent { AlignedRow(Alignment.CenterVertically) }

        assertEquals(
            listOf(Rectangle(0, 80, CHILD_WIDTH, CHILD_HEIGHT)),
            childBounds(),
            "Alignment.CenterVertically must leave equal height above and below the child",
        )
    }

    @Test
    fun bottomPutsAChildAgainstTheBottomOfTheRow() = runComposeSwingTest {
        setContent { AlignedRow(Alignment.Bottom) }

        assertEquals(
            listOf(Rectangle(0, 160, CHILD_WIDTH, CHILD_HEIGHT)),
            childBounds(),
            "Alignment.Bottom must put the child against the row's bottom edge",
        )
    }

    @Test
    fun aChildOfAColumnIsPlacedByTheAlignmentItNamesForItself() = runComposeSwingTest {
        setContent {
            Column(
                modifier = containerModifier(ACROSS_EXTENT, ALONG_EXTENT),
                horizontalAlignment = Alignment.Start,
            ) {
                SizedChild(0)
                SizedChild(1, SwingModifier.align(Alignment.End))
            }
        }

        assertEquals(
            listOf(
                Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                Rectangle(150, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
            ),
            childBounds(),
            "align must place the child that names it and leave its siblings on the column's alignment",
        )
    }

    @Test
    fun aChildOfARowIsPlacedByTheAlignmentItNamesForItself() = runComposeSwingTest {
        setContent {
            Row(
                modifier = containerModifier(ALONG_EXTENT, ACROSS_EXTENT),
                verticalAlignment = Alignment.Top,
            ) {
                SizedChild(0)
                SizedChild(1, SwingModifier.align(Alignment.Bottom))
            }
        }

        assertEquals(
            listOf(
                Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                Rectangle(CHILD_WIDTH, 160, CHILD_WIDTH, CHILD_HEIGHT),
            ),
            childBounds(),
            "align must place the child that names it and leave its siblings on the row's alignment",
        )
    }
}

/** The extent a fixture container is given across its axis, far wider than a child asks for. */
private const val ACROSS_EXTENT = 200

/** The extent a fixture container is given along its axis, enough for the children it declares. */
private const val ALONG_EXTENT = 120

/** A column wider than its child, so the width the child leaves is there for an alignment to use. */
@Composable
private fun AlignedColumn(alignment: Alignment.Horizontal) {
    Column(
        modifier = containerModifier(ACROSS_EXTENT, ALONG_EXTENT),
        horizontalAlignment = alignment,
    ) {
        SizedChild(0)
    }
}

/** A row taller than its child, so the height the child leaves is there for an alignment to use. */
@Composable
private fun AlignedRow(alignment: Alignment.Vertical) {
    Row(
        modifier = containerModifier(ALONG_EXTENT, ACROSS_EXTENT),
        verticalAlignment = alignment,
    ) {
        SizedChild(0)
    }
}

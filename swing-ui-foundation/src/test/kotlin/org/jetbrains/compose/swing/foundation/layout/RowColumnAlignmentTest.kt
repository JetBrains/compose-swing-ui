package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.ComponentOrientation
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An alignment decides where a child sits across the axis its container arranges children along. The
 * child keeps the extent it asked for across that axis too, so the whole of an alignment's effect is
 * the offset the child ends up at, which is what every test here reads back.
 *
 * The container is always wider (a column) or taller (a row) than its children ask for, so each child
 * has space across the axis to be placed in - except where a row asks for its own height, which is what
 * the children on its shared baseline decide between them. A child that names an alignment of its own
 * is placed by that one, and its siblings are untouched.
 */
class RowColumnAlignmentTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("columnAlignments")
    fun aColumnPutsItsChildWhereItsHorizontalAlignmentSays(
        alignment: Alignment.Horizontal,
        orientation: ComponentOrientation,
        expected: Rectangle,
        message: String,
    ) = runComposeSwingTest {
        setContent { AlignedColumn(alignment, orientation) }
        assertEquals(listOf(expected), childBounds(), message)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rowAlignments")
    fun aRowPutsItsChildWhereItsVerticalAlignmentSays(
        alignment: Alignment.Vertical,
        expected: Rectangle,
        message: String,
    ) = runComposeSwingTest {
        setContent { AlignedRow(alignment) }
        assertEquals(listOf(expected), childBounds(), message)
    }

    @Test
    fun aChildOfAColumnIsPlacedByTheAlignmentItNamesForItself() =
        runComposeSwingTest {
            setContent {
                Column(
                    modifier = ContainerModifier.styleable(containerStyle(ACROSS_EXTENT, ALONG_EXTENT)),
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
    fun aChildOfARowIsPlacedByTheAlignmentItNamesForItself() =
        runComposeSwingTest {
            setContent {
                Row(
                    modifier = ContainerModifier.styleable(containerStyle(ALONG_EXTENT, ACROSS_EXTENT)),
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

    @Test
    fun aRowChildKeepsTheLastAlignmentItDeclares() =
        runComposeSwingTest {
            setContent {
                Row(modifier = ContainerModifier.styleable(containerStyle(ALONG_EXTENT, ACROSS_EXTENT))) {
                    SizedChild(0, SwingModifier.align(Alignment.Top).align(Alignment.Bottom))
                }
            }

            assertEquals(
                listOf(Rectangle(0, ACROSS_EXTENT - CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "a modifier is folded in declaration order, so the last alignment declared wins",
            )
        }

    @Test
    fun aColumnChildKeepsTheLastAlignmentItDeclares() =
        runComposeSwingTest {
            setContent {
                Column(modifier = ContainerModifier.styleable(containerStyle(ACROSS_EXTENT, ALONG_EXTENT))) {
                    SizedChild(0, SwingModifier.align(Alignment.Start).align(Alignment.End))
                }
            }

            assertEquals(
                listOf(Rectangle(ACROSS_EXTENT - CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "a modifier is folded in declaration order, so the last alignment declared wins",
            )
        }

    @Test
    fun anAlignAppendedAfterTheCallersModifierArgumentStillWins() =
        runComposeSwingTest {
            setContent {
                Row(modifier = ContainerModifier.styleable(containerStyle(ALONG_EXTENT, ACROSS_EXTENT))) {
                    BottomAlignedChild(modifier = SwingModifier.align(Alignment.Top))
                }
            }

            assertEquals(
                listOf(Rectangle(0, ACROSS_EXTENT - CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "align is folded in declaration order wherever it is chained from, so the align a component " +
                    "appends after the modifier its caller passed in still wins over one already on that modifier",
            )
        }

    @Test
    fun childrenOnTheSharedBaselineLineUpOnIt() =
        runComposeSwingTest {
            setContent {
                BaselineRow {
                    BaselineChild(30, SwingModifier.alignByBaseline())
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 20, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "the shallower child must drop by the difference between the two baselines, so that both " +
                    "baselines fall on one line",
            )
        }

    @Test
    fun childrenOnTheSharedBaselineOfARightToLeftRowLineUpOnIt() =
        runComposeSwingTest {
            setContent {
                BaselineRow(orientation = ComponentOrientation.RIGHT_TO_LEFT) {
                    BaselineChild(30, SwingModifier.alignByBaseline())
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                listOf(0, 20),
                childBounds().map { it.y },
                "a right-to-left orientation mirrors a row along its width only, so its children must line up " +
                    "on the shared baseline measured from the top",
            )
        }

    @Test
    fun aBaselineStandsInForTheRowsVerticalAlignment() =
        runComposeSwingTest {
            setContent {
                BaselineRow(Alignment.Bottom) {
                    BaselineChild(30, SwingModifier.alignByBaseline())
                    SizedChild(1)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, ACROSS_EXTENT - CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "the baseline must place the child that declared it, and leave its sibling on the row's " +
                    "own alignment",
            )
        }

    @Test
    fun aChildReportingNoBaselineSitsAgainstTheTopOfTheRow() =
        runComposeSwingTest {
            setContent {
                BaselineRow(Alignment.Bottom) {
                    BaselineChild(NO_BASELINE, SwingModifier.alignByBaseline())
                    BaselineChild(30, SwingModifier.alignByBaseline())
                    SizedChild(2)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH * 2, ACROSS_EXTENT - CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "a child whose component reports no baseline must sit at the row's top edge rather than on " +
                    "the row's own alignment",
            )
        }

    @Test
    fun fillMaxHeightComposesWithTheBaselineAChildAlsoDeclares() =
        runComposeSwingTest {
            setContent {
                BaselineRow {
                    FlexibleBaselineChild(30, SwingModifier.fillMaxHeight().alignByBaseline())
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, ACROSS_EXTENT),
                    Rectangle(CHILD_WIDTH, 20, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "fillMaxHeight must size the first child while alignByBaseline independently places both " +
                    "children on the shared line",
            )
        }

    @Test
    fun aBaselineDeclaredAfterAnAlignmentPlacesTheChild() =
        runComposeSwingTest {
            setContent {
                BaselineRow {
                    BaselineChild(30, SwingModifier.alignByBaseline())
                    BaselineChild(10, SwingModifier.align(Alignment.Bottom).alignByBaseline())
                }
            }

            assertEquals(
                Rectangle(CHILD_WIDTH, 20, CHILD_WIDTH, CHILD_HEIGHT),
                childBounds()[1],
                "the baseline declared last must place the child, in place of the alignment before it",
            )
        }

    @Test
    fun anAlignmentDeclaredAfterABaselinePlacesTheChild() =
        runComposeSwingTest {
            setContent {
                BaselineRow {
                    BaselineChild(30, SwingModifier.alignByBaseline())
                    BaselineChild(10, SwingModifier.alignByBaseline().align(Alignment.Bottom))
                }
            }

            assertEquals(
                Rectangle(CHILD_WIDTH, ACROSS_EXTENT - CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                childBounds()[1],
                "the alignment declared last must place the child, in place of the baseline before it",
            )
        }

    companion object {
        @JvmStatic
        fun columnAlignments(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of("startPutsAChildAgainstTheLeadingEdgeOfTheColumn", Alignment.Start),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    "Alignment.Start must put the child against the column's leading edge",
                ),
                Arguments.of(
                    Named.of("startPutsAChildAgainstTheRightEdgeOfARightToLeftColumn", Alignment.Start),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    Rectangle(150, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    "Alignment.Start must put the child against the column's leading edge, the right one under " +
                        "a right-to-left orientation",
                ),
                Arguments.of(
                    Named.of("centerHorizontallyPutsAChildHalfwayAcrossTheColumn", Alignment.CenterHorizontally),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    Rectangle(75, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    "Alignment.CenterHorizontally must leave equal width on either side of the child",
                ),
                Arguments.of(
                    Named.of(
                        "centerHorizontallyPutsAChildHalfwayAcrossARightToLeftColumn",
                        Alignment.CenterHorizontally,
                    ),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    Rectangle(75, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    "Alignment.CenterHorizontally must leave equal width on either side of the child under a " +
                        "right-to-left orientation too",
                ),
                Arguments.of(
                    Named.of("endPutsAChildAgainstTheTrailingEdgeOfTheColumn", Alignment.End),
                    ComponentOrientation.LEFT_TO_RIGHT,
                    Rectangle(150, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    "Alignment.End must put the child against the column's trailing edge",
                ),
                Arguments.of(
                    Named.of("endPutsAChildAgainstTheLeftEdgeOfARightToLeftColumn", Alignment.End),
                    ComponentOrientation.RIGHT_TO_LEFT,
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    "Alignment.End must put the child against the column's trailing edge, the left one under a " +
                        "right-to-left orientation",
                ),
            )

        @JvmStatic
        fun rowAlignments(): List<Arguments> =
            listOf(
                Arguments.of(
                    Named.of("topPutsAChildAgainstTheTopOfTheRow", Alignment.Top),
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    "Alignment.Top must put the child against the row's top edge",
                ),
                Arguments.of(
                    Named.of("centerVerticallyPutsAChildHalfwayDownTheRow", Alignment.CenterVertically),
                    Rectangle(0, 80, CHILD_WIDTH, CHILD_HEIGHT),
                    "Alignment.CenterVertically must leave equal height above and below the child",
                ),
                Arguments.of(
                    Named.of("bottomPutsAChildAgainstTheBottomOfTheRow", Alignment.Bottom),
                    Rectangle(0, 160, CHILD_WIDTH, CHILD_HEIGHT),
                    "Alignment.Bottom must put the child against the row's bottom edge",
                ),
            )
    }
}

/** The extent a fixture container is given across its axis, far wider than a child asks for. */
private const val ACROSS_EXTENT = 200

/** The extent a fixture container is given along its axis, enough for the children it declares. */
private const val ALONG_EXTENT = 120

/** A column wider than its child, so the width the child leaves is there for an alignment to use. */
@Composable
private fun AlignedColumn(
    alignment: Alignment.Horizontal,
    orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
) {
    Column(
        modifier = ContainerModifier.styleable(containerStyle(ACROSS_EXTENT, ALONG_EXTENT, orientation)),
        horizontalAlignment = alignment,
    ) {
        SizedChild(0)
    }
}

/** A row taller than its child, so the height the child leaves is there for an alignment to use. */
@Composable
private fun AlignedRow(alignment: Alignment.Vertical) {
    Row(
        modifier = ContainerModifier.styleable(containerStyle(ALONG_EXTENT, ACROSS_EXTENT)),
        verticalAlignment = alignment,
    ) {
        SizedChild(0)
    }
}

/**
 * A child aligned to the row's bottom by an align appended, following the "modifier passed first"
 * convention, after the [modifier] its caller passed in - rather than one built fresh from it.
 */
@Composable
private fun RowScope.BottomAlignedChild(modifier: SwingModifier) {
    SizedChild(0, modifier.align(Alignment.Bottom))
}

/** A row wide enough for every child a test here declares, and taller than any of them asks for. */
@Composable
private fun BaselineRow(
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = ContainerModifier.styleable(containerStyle(CHILD_WIDTH * CHILD_COUNT, ACROSS_EXTENT, orientation)),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

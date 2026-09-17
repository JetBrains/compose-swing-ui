package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
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
    @Test
    fun startPutsAChildAgainstTheLeadingEdgeOfTheColumn() =
        runComposeSwingTest {
            setContent { AlignedColumn(Alignment.Start) }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "Alignment.Start must put the child against the column's leading edge",
            )
        }

    @Test
    fun startPutsAChildAgainstTheRightEdgeOfARightToLeftColumn() =
        runComposeSwingTest {
            setContent { AlignedColumn(Alignment.Start, ComponentOrientation.RIGHT_TO_LEFT) }

            assertEquals(
                listOf(Rectangle(150, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "Alignment.Start must put the child against the column's leading edge, the right one under " +
                    "a right-to-left orientation",
            )
        }

    @Test
    fun centerHorizontallyPutsAChildHalfwayAcrossTheColumn() =
        runComposeSwingTest {
            setContent { AlignedColumn(Alignment.CenterHorizontally) }

            assertEquals(
                listOf(Rectangle(75, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "Alignment.CenterHorizontally must leave equal width on either side of the child",
            )
        }

    @Test
    fun centerHorizontallyPutsAChildHalfwayAcrossARightToLeftColumn() =
        runComposeSwingTest {
            setContent { AlignedColumn(Alignment.CenterHorizontally, ComponentOrientation.RIGHT_TO_LEFT) }

            assertEquals(
                listOf(Rectangle(75, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "Alignment.CenterHorizontally must leave equal width on either side of the child under a " +
                    "right-to-left orientation too",
            )
        }

    @Test
    fun endPutsAChildAgainstTheTrailingEdgeOfTheColumn() =
        runComposeSwingTest {
            setContent { AlignedColumn(Alignment.End) }

            assertEquals(
                listOf(Rectangle(150, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "Alignment.End must put the child against the column's trailing edge",
            )
        }

    @Test
    fun endPutsAChildAgainstTheLeftEdgeOfARightToLeftColumn() =
        runComposeSwingTest {
            setContent { AlignedColumn(Alignment.End, ComponentOrientation.RIGHT_TO_LEFT) }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "Alignment.End must put the child against the column's trailing edge, the left one under a " +
                    "right-to-left orientation",
            )
        }

    @Test
    fun topPutsAChildAgainstTheTopOfTheRow() =
        runComposeSwingTest {
            setContent { AlignedRow(Alignment.Top) }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "Alignment.Top must put the child against the row's top edge",
            )
        }

    @Test
    fun centerVerticallyPutsAChildHalfwayDownTheRow() =
        runComposeSwingTest {
            setContent { AlignedRow(Alignment.CenterVertically) }

            assertEquals(
                listOf(Rectangle(0, 80, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "Alignment.CenterVertically must leave equal height above and below the child",
            )
        }

    @Test
    fun bottomPutsAChildAgainstTheBottomOfTheRow() =
        runComposeSwingTest {
            setContent { AlignedRow(Alignment.Bottom) }

            assertEquals(
                listOf(Rectangle(0, 160, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "Alignment.Bottom must put the child against the row's bottom edge",
            )
        }

    @Test
    fun aChildOfAColumnIsPlacedByTheAlignmentItNamesForItself() =
        runComposeSwingTest {
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
    fun aChildOfARowIsPlacedByTheAlignmentItNamesForItself() =
        runComposeSwingTest {
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

    @Test
    fun aRowChildKeepsTheLastAlignmentItDeclares() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(ALONG_EXTENT, ACROSS_EXTENT)) {
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
                Column(modifier = containerModifier(ACROSS_EXTENT, ALONG_EXTENT)) {
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
                Row(modifier = containerModifier(ALONG_EXTENT, ACROSS_EXTENT)) {
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

    @Test
    fun aRowAtItsOwnHeightHoldsTheDeepestBaselineAndTheDeepestRemainder() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    BaselineChild(30, SwingModifier.alignByBaseline())
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH * 2, 60),
                containerPreferredSize(),
                "a row asking for its own height must hold the deepest baseline of its children above the " +
                    "shared line and the deepest remainder below it, which is more than any one child asks for",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 20, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "both children must fit within the height the row asked for, on one baseline",
            )
        }

    @Test
    fun aLineALayoutChildsPolicyProvidesAlignsItButDoesNotEnterTheRowsPreferredHeight() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Layout(
                        measurePolicy = { _, _ ->
                            layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to 10)) {}
                        },
                        modifier = SwingModifier.alignByBaseline(),
                    )
                    BaselineChild(30, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH * 2, CHILD_HEIGHT),
                containerPreferredSize(),
                "the row's preferred height must not hold a line only the child's measure pass provides",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 20, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "the measured row must still place the child on the shared line by its policy's line",
            )
        }

    /** The line comes from a state the child's policy reads while measuring; the child's size never changes. */
    @Test
    fun aLineALayoutChildReadsFromStateRealignsTheRowWhenItChanges() =
        runComposeSwingTest {
            var line by mutableIntStateOf(10)
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Layout(
                        measurePolicy = { _, _ ->
                            layout(CHILD_WIDTH, CHILD_HEIGHT, mapOf(FirstBaseline to line)) {}
                        },
                        modifier = SwingModifier.alignByBaseline(),
                    )
                    BaselineChild(30, SwingModifier.alignByBaseline())
                }
            }

            line = 20
            awaitIdle()

            assertEquals(
                listOf(
                    Rectangle(0, 10, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "the row must place the child by the line its policy now provides",
            )
        }

    @Test
    fun aRowAtItsOwnHeightHoldsTheBaselineOfAWeightedChild() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    FlexibleBaselineChild(30, SwingModifier.weight(1f).alignByBaseline())
                    SwingNode(
                        factory = { JPanel() },
                        modifier = SwingModifier.weight(1f).alignByBaseline().preferredSize(CHILD_WIDTH, CHILD_HEIGHT),
                    )
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                60,
                containerPreferredSize().height,
                "a weighted child's baseline must enter the row's height like any other child's, and a weighted " +
                    "child without one must take no part in it",
            )
        }

    @Test
    fun aRowAtItsOwnHeightHoldsTheBaselineOfAChildMeasuredThroughACustomLayoutNode() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    BaselineChild(30, SwingModifier.alignByBaseline() then PassThroughNodeElement)
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH * 2, 60),
                containerPreferredSize(),
                "a row asking for its own height must read a baseline through a custom layout node, as through a " +
                    "built-in modifier",
            )
        }

    @Test
    fun aRowAtItsOwnHeightHoldsTheBaselineOfAPaddedChild() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    BaselineChild(30, SwingModifier.padding(top = 5).alignByBaseline())
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH * 2, 5 + 30 + CHILD_HEIGHT - 10),
                containerPreferredSize(),
                "a row asking for its own height must read the baseline of a child measured through a padding, " +
                    "below the space the padding reserves",
            )
        }

    @Test
    fun aRowsIntrinsicBaselineUsesAChildsCappedWidth() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    CappedBaselineChild(
                        30,
                        SwingModifier.maximumSize(CAPPED_BASELINE_WIDTH, CHILD_HEIGHT).alignByBaseline(),
                    )
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                Dimension(
                    CAPPED_BASELINE_WIDTH + CHILD_WIDTH,
                    60,
                ),
                containerPreferredSize(),
                "a row must ask its capped child for the baseline at the width it can occupy, so the child " +
                    "still contributes its deeper baseline to the row's intrinsic height",
            )
        }

    @Test
    fun aRowsIntrinsicBaselineUsesAChildsCappedHeight() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    CappedCrossBaselineChild(
                        SwingModifier.maximumSize(CHILD_WIDTH, CAPPED_BASELINE_HEIGHT).alignByBaseline(),
                    )
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                Dimension(
                    CHILD_WIDTH * 2,
                    CAPPED_CROSS_DEEP_BASELINE + CHILD_HEIGHT - 10,
                ),
                containerPreferredSize(),
                "a row must ask its capped child for the baseline at the height it can occupy, then calculate " +
                    "the below-baseline remainder from that same capped height",
            )
        }

    @Test
    fun aRowsIntrinsicHeightCapsAWeightedChildsMainAxisAtItsMaximumWidth() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    CappedBaselineChild(
                        30,
                        SwingModifier.weight(1f).alignByBaseline().maximumSize(CAPPED_BASELINE_WIDTH, CHILD_HEIGHT),
                    )
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                Dimension(CAPPED_BASELINE_WIDTH + CHILD_WIDTH, 60),
                containerPreferredSize(),
                "a row must cap a weighted child's unbounded main-axis share at its own maximum width before " +
                    "asking for its intrinsic height, so the capped child still contributes its baseline to the " +
                    "row's own height",
            )
        }

    @Test
    fun aRowsIntrinsicHeightCapsAnUnweightedChildsCrossAxisBoundBeforeAskingItsMainAxis() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SwingNode(
                        factory = { WidthAsBaselinePanel() },
                        modifier =
                            SwingModifier
                                .alignByBaseline()
                                .aspectRatio(1f)
                                // 10_000: wide enough the main axis never binds, exercising only the cross-axis cap.
                                .maximumSize(10_000, CAPPED_ASPECT_CROSS_AXIS),
                    )
                    BaselineChild(10, SwingModifier.alignByBaseline())
                }
            }

            assertEquals(
                Dimension(CAPPED_ASPECT_CROSS_AXIS + CHILD_WIDTH, 60),
                containerPreferredSize(),
                "a row must cap the cross-axis bound an unweighted child's own maximum height allows before " +
                    "asking its intrinsic main axis, so a child whose width follows that bound still contributes " +
                    "its capped baseline to the row's own height",
            )
        }

    @Test
    fun aRowMeasuredByItsParentStillHoldsTheDeepestBaselineAndTheDeepestRemainder() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.preferredSize(200, 200)) {
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                        BaselineChild(30, SwingModifier.alignByBaseline())
                        BaselineChild(10, SwingModifier.alignByBaseline())
                    }
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH * 2, 60),
                containerSize(),
                "a row its column measures settles on the same height it would ask for: the deepest baseline " +
                    "above the shared line and the deepest remainder below it, not merely its tallest child",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 20, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "so both children still fit within it, on one baseline",
            )
        }

    @Test
    fun aRowAtItsOwnHeightHoldsTheLinesItsAlignByChildrenWorkOut() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 20, 60, SwingModifier.alignBy { it.measuredHeight })
                    Child(1, 20, 60, SwingModifier.alignBy { 0 })
                }
            }

            assertEquals(
                Dimension(2 * 20, 2 * 60),
                containerPreferredSize(),
                "a row asking for its own height must hold the deepest line its children work out above the " +
                    "shared line and the deepest remainder below it",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 20, 60), Rectangle(20, 60, 20, 60)),
                childBounds(),
                "both children must fit within the height the row asked for, on one line",
            )
        }

    @Test
    fun aColumnAtItsOwnWidthHoldsTheLinesItsAlignByChildrenWorkOut() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 60, 20, SwingModifier.alignBy { it.measuredWidth })
                    Child(1, 60, 20, SwingModifier.alignBy { 0 })
                }
            }

            assertEquals(
                Dimension(2 * 60, 2 * 20),
                containerPreferredSize(),
                "a column asking for its own width must work each child's line out at the width the child " +
                    "takes, and hold the furthest line from its leading edge and the furthest remainder past it",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 60, 20), Rectangle(60, 20, 60, 20)),
                childBounds(),
                "both children must fit within the width the column asked for, on one line",
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
        modifier = containerModifier(ACROSS_EXTENT, ALONG_EXTENT, orientation),
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

/**
 * A child aligned to the row's bottom by an align appended, following the "modifier passed first"
 * convention, after the [modifier] its caller passed in - rather than one built fresh from it.
 */
@Composable
private fun RowScope.BottomAlignedChild(modifier: SwingModifier) {
    SizedChild(0, modifier.align(Alignment.Bottom))
}

/** The capped width at which [CappedBaselinePanel] reports its baseline. */
private const val CAPPED_BASELINE_WIDTH = 30

/** The capped height at which [CappedCrossBaselinePanel] reports its baseline. */
private const val CAPPED_BASELINE_HEIGHT = 30

/** The baseline [CappedCrossBaselinePanel] reports at [CAPPED_BASELINE_HEIGHT]. */
private const val CAPPED_CROSS_DEEP_BASELINE = 20

/** The cross-axis maximum [WidthAsBaselinePanel] is held to through its `aspectRatio`. */
private const val CAPPED_ASPECT_CROSS_AXIS = 30

/** What a component reports when it has no baseline at all, as `java.awt.Component` defines it. */
private const val NO_BASELINE = -1

/** A row wide enough for every child a test here declares, and taller than any of them asks for. */
@Composable
private fun BaselineRow(
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = containerModifier(CHILD_WIDTH * CHILD_COUNT, ACROSS_EXTENT, orientation),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** A layout node that places its content where it is. */
private data object PassThroughNodeElement : LayoutModifierNodeElement<PassThroughNode>() {
    override fun create(): PassThroughNode = PassThroughNode()

    override fun update(node: PassThroughNode): Unit = Unit
}

private class PassThroughNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/** A child of the fixture's own size whose component reports [baseline] wherever it is asked. */
@Composable
private fun BaselineChild(
    baseline: Int,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { BaselinePanel(baseline) },
        modifier = modifier.preferredSize(CHILD_WIDTH, CHILD_HEIGHT),
    )
}

/** A child whose component reports [baseline] for every positive extent it can occupy. */
@Composable
private fun FlexibleBaselineChild(
    baseline: Int,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { FlexibleBaselinePanel(baseline) },
        modifier = modifier.preferredSize(CHILD_WIDTH, CHILD_HEIGHT),
    )
}

/** A child whose component reports [baseline] only when the row asks it at its explicit capped width. */
@Composable
private fun CappedBaselineChild(
    baseline: Int,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { CappedBaselinePanel(baseline) },
        modifier = modifier.preferredSize(CHILD_WIDTH, CHILD_HEIGHT),
    )
}

/** A baseline child that reports only when the row asks it at its explicit capped height. */
@Composable
private fun CappedCrossBaselineChild(modifier: SwingModifier = SwingModifier) {
    SwingNode(
        factory = { CappedCrossBaselinePanel() },
        modifier = modifier.preferredSize(CHILD_WIDTH, CHILD_HEIGHT),
    )
}

/**
 * A component carrying the baseline the test chose for it, and none at all when it is asked at any size
 * other than the one it occupies - so a row asking the wrong question gets no baseline to place it by.
 */
private class BaselinePanel(
    private val reported: Int,
) : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = if (width == CHILD_WIDTH && height == CHILD_HEIGHT) reported else NO_BASELINE
}

/** A component that retains its baseline when a layout modifier changes its extent. */
private class FlexibleBaselinePanel(
    private val reported: Int,
) : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = if (width > 0 && height > 0) reported else NO_BASELINE
}

/** A component whose baseline is available only at [CAPPED_BASELINE_WIDTH]. */
private class CappedBaselinePanel(
    private val reported: Int,
) : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = if (width == CAPPED_BASELINE_WIDTH && height == CHILD_HEIGHT) reported else NO_BASELINE
}

/** A component whose baseline is available only at [CAPPED_BASELINE_HEIGHT]. */
private class CappedCrossBaselinePanel : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = if (width == CHILD_WIDTH && height == CAPPED_BASELINE_HEIGHT) CAPPED_CROSS_DEEP_BASELINE else NO_BASELINE
}

/** A component whose baseline equals whatever width it is asked to report itself at. */
private class WidthAsBaselinePanel : JPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = width
}

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A box stacks every child in the same place: it asks for the largest size among the children that do
 * not match its own, and lays each of them out at the size it prefers, where its own alignment or the
 * box's puts it. A child declaring `matchParentSize` is given the whole of the box instead, and is
 * passed over while the box's size is worked out.
 *
 * A case that androidx `foundation-layout`'s own `BoxTest` makes keeps that test's name, so the two
 * files read side by side. A case that test cannot make - a hidden child, a constraint refused - is
 * named for what it pins, as is the one case this tree settles the other way round: where androidx
 * gives a chain of alignments to the first declared, here the last wins, so
 * [aChildKeepsTheLastAlignmentItDeclares] stands in for `testBox_outermostGravityWins` under a name
 * that states what it asserts. The order the box stacks its children in is [BoxStackOrderTest], and a
 * case reachable only at a degenerate declaration is [BoxEdgeCaseTest].
 *
 * One case here per case in that suite, in its order and under its names, so the two can be read
 * against each other and a case dropped in translation shows up as a gap. That correspondence is what
 * holds the class together and what the size suppression below protects: it is as large as its source
 * rather than a class that grew by accretion, and splitting it would break the reading it exists for.
 */
@Suppress("LargeClass")
class BoxTest {
    @Test
    fun testBox() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.align(Alignment.BottomEnd))
                    Child(
                        index = 1,
                        width = BOX_WIDTH,
                        height = BOX_HEIGHT,
                        modifier =
                            SwingModifier
                                .matchParentSize()
                                .maximumSize(CHILD_WIDTH - 2 * INSET, CHILD_HEIGHT - 2 * INSET),
                    )
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "the box must ask for the child that does not match it, however large the matching one is",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, CHILD_WIDTH - 2 * INSET, CHILD_HEIGHT - 2 * INSET),
                ),
                stackedChildBounds(),
                "the matching child must take the box, held to the maximum size it declares",
            )
        }

    @Test
    fun testBox_withMultipleAlignedChildren() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.align(Alignment.BottomEnd))
                    Child(1, BOX_WIDTH, BOX_HEIGHT, SwingModifier.align(Alignment.BottomEnd))
                }
            }

            assertEquals(
                Dimension(BOX_WIDTH, BOX_HEIGHT),
                containerPreferredSize(),
                "the box must ask for the largest of its children along either axis",
            )
            assertEquals(
                listOf(
                    Rectangle(BOX_WIDTH - CHILD_WIDTH, BOX_HEIGHT - CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, BOX_WIDTH, BOX_HEIGHT),
                ),
                stackedChildBounds(),
                "each child must sit at the alignment it declares, in the box the largest of them sized",
            )
        }

    @Test
    fun testBox_withStretchChildren() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, BOX_WIDTH, BOX_HEIGHT)
                    Child(1, BOX_WIDTH, BOX_HEIGHT, matchingUpTo(BOX_WIDTH - INSET, BOX_HEIGHT))
                    Child(2, BOX_WIDTH, BOX_HEIGHT, matchingUpTo(BOX_WIDTH, BOX_HEIGHT - INSET))
                    Child(3, BOX_WIDTH, BOX_HEIGHT, matchingUpTo(BOX_WIDTH - INSET, BOX_HEIGHT - INSET))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, BOX_WIDTH, BOX_HEIGHT),
                    Rectangle(0, 0, BOX_WIDTH - INSET, BOX_HEIGHT),
                    Rectangle(0, 0, BOX_WIDTH, BOX_HEIGHT - INSET),
                    Rectangle(0, 0, BOX_WIDTH - INSET, BOX_HEIGHT - INSET),
                ),
                stackedChildBounds(),
                "a matching child must take the box on each axis its own maximum size leaves free",
            )
        }

    @Test
    fun eachAlignmentPutsAChildInItsOwnCornerOfTheBox() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(STACKED * CHILD_WIDTH, STACKED * CHILD_HEIGHT)) {
                    ALIGNMENT_GRID.forEachIndexed { index, alignment ->
                        SizedChild(index, SwingModifier.align(alignment))
                    }
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(2 * CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(2 * CHILD_WIDTH, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 2 * CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 2 * CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(2 * CHILD_WIDTH, 2 * CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "each of the nine alignments must put its child in the corner or edge it names",
            )
        }

    @Test
    fun testBox_Rtl() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier =
                        containerModifier(
                            STACKED * CHILD_WIDTH,
                            STACKED * CHILD_HEIGHT,
                            ComponentOrientation.RIGHT_TO_LEFT,
                        ),
                ) {
                    ALIGNMENT_GRID.forEachIndexed { index, alignment ->
                        SizedChild(index, SwingModifier.align(alignment))
                    }
                }
            }

            assertEquals(
                listOf(
                    Rectangle(2 * CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(2 * CHILD_WIDTH, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(2 * CHILD_WIDTH, 2 * CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 2 * CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 2 * CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "a right-to-left box must mirror the horizontal alignments and leave the vertical ones alone",
            )
        }

    @Test
    fun testBox_expanded() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.matchParentSize())
                    Child(
                        index = 1,
                        width = BOX_WIDTH / 2,
                        height = BOX_HEIGHT / 2,
                        modifier = SwingModifier.align(Alignment.BottomEnd),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, BOX_WIDTH, BOX_HEIGHT),
                    Rectangle(BOX_WIDTH / 2, BOX_HEIGHT / 2, BOX_WIDTH / 2, BOX_HEIGHT / 2),
                ),
                stackedChildBounds(),
                "a matching child must take a box its own parent sized, and its siblings must be placed in it",
            )
        }

    @Test
    fun aBoxDoesNotPassItsMinimumExtentToContentByDefault() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT)) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "the default Box policy must relax its incoming minimum before measuring content",
            )
        }

    @Test
    fun aBoxCanPassItsMinimumExtentToContent() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT),
                    propagateMinConstraints = true,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, BOX_WIDTH, BOX_HEIGHT)),
                stackedChildBounds(),
                "propagating Box constraints must measure content at the box's incoming minimum",
            )
        }

    @Test
    fun anEmptyBoxUsesTheDedicatedEmptyPolicy() =
        runComposeSwingTest {
            setContent { Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) }

            assertEquals(Dimension(0, 0), containerPreferredSize(), "an empty Box asks for no content extent")
            assertEquals(emptyList(), stackedChildBounds(), "the content-less overload must compose no children")
            assertSame(
                EmptyBoxMeasurePolicy,
                (box().layout as PolicyLayout).policy,
                "the modifier-only overload must not allocate a content Box measure policy",
            )
        }

    @Test
    fun aBoxReusesCachedPoliciesAcrossRecompositions() =
        runComposeSwingTest {
            var alignment by mutableStateOf(Alignment.TopStart)
            setContent {
                Box(modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT), contentAlignment = alignment) {
                    SizedChild(0)
                }
            }

            val topStartPolicy = (box().layout as PolicyLayout).policy
            assertSame(
                maybeCachedBoxMeasurePolicy(Alignment.TopStart, propagateMinConstraints = false),
                topStartPolicy,
                "the default Box policy must come from the standard-alignment cache",
            )

            alignment = Alignment.BottomEnd
            awaitIdle()
            assertSame(
                maybeCachedBoxMeasurePolicy(Alignment.BottomEnd, propagateMinConstraints = false),
                (box().layout as PolicyLayout).policy,
                "a new standard alignment must replace the policy with its cached counterpart",
            )

            alignment = Alignment.TopStart
            awaitIdle()
            assertSame(
                topStartPolicy,
                (box().layout as PolicyLayout).policy,
                "returning to a prior standard alignment must reuse its original policy",
            )
        }

    @Test
    fun testBox_alignmentParameter() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(BOX_WIDTH - CHILD_WIDTH, BOX_HEIGHT - CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "a child declaring no alignment of its own must sit where the box's alignment puts it",
            )
        }

    @Test
    fun testBox_childAffectsBoxSize() =
        runComposeSwingTest {
            var width by mutableStateOf(CHILD_WIDTH)
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) { Child(0, width, CHILD_HEIGHT) }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "the box must ask for the size its child prefers",
            )

            width = BOX_WIDTH
            awaitIdle()

            assertEquals(
                Dimension(BOX_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "a child that comes to prefer another size must carry the box's own size with it",
            )
            assertEquals(
                listOf(Rectangle(0, 0, BOX_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "and must be laid out at the size it now prefers",
            )
        }

    @Test
    fun testBox_hasCorrectIntrinsicMeasurements() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, BOX_HEIGHT, SwingModifier.minimumSize(SMALL_WIDTH, CHILD_HEIGHT))
                    Child(1, BOX_WIDTH, CHILD_HEIGHT, SwingModifier.minimumSize(CHILD_WIDTH, SMALL_HEIGHT))
                    Child(
                        index = 2,
                        width = 2 * BOX_WIDTH,
                        height = 2 * BOX_HEIGHT,
                        modifier = SwingModifier.matchParentSize().minimumSize(2 * BOX_WIDTH, 2 * BOX_HEIGHT),
                    )
                }
            }

            assertEquals(
                Dimension(BOX_WIDTH, BOX_HEIGHT),
                containerPreferredSize(),
                "the box must prefer the largest child on each axis, and nothing of a matching child",
            )
            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                box().minimumSize,
                "and its minimum must be the largest minimum on each axis, the matching child passed over again",
            )
            assertEquals(
                Dimension(Int.MAX_VALUE, Int.MAX_VALUE),
                box().maximumSize,
                "and must take any extent it is offered, however large",
            )
        }

    @Test
    fun testBox_hasCorrectIntrinsicMeasurements_withNoAlignedChildren() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG).border(EmptyBorder(TOP, LEFT, BOTTOM, RIGHT))) {
                    Child(0, BOX_WIDTH, BOX_HEIGHT, SwingModifier.matchParentSize())
                }
            }

            val insetsAlone = Dimension(LEFT + RIGHT, TOP + BOTTOM)
            assertEquals(
                insetsAlone,
                containerPreferredSize(),
                "a box whose children all match it must ask for its insets alone",
            )
            assertEquals(
                insetsAlone,
                box().minimumSize,
                "and must ask for no more than that as its minimum",
            )
        }

    @Test
    fun aMatchingChildTakesTheBoxInsideItsInsets() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT).border(EmptyBorder(TOP, LEFT, BOTTOM, RIGHT))) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.matchParentSize())
                }
            }

            assertEquals(
                listOf(Rectangle(LEFT, TOP, BOX_WIDTH - LEFT - RIGHT, BOX_HEIGHT - TOP - BOTTOM)),
                stackedChildBounds(),
                "a matching child must take the box inside its insets, not the box itself",
            )
        }

    @Test
    fun aChildIsAlignedInsideTheBoxsInsets() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT).border(EmptyBorder(TOP, LEFT, BOTTOM, RIGHT)),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        BOX_WIDTH - RIGHT - CHILD_WIDTH,
                        BOX_HEIGHT - BOTTOM - CHILD_HEIGHT,
                        CHILD_WIDTH,
                        CHILD_HEIGHT,
                    ),
                ),
                stackedChildBounds(),
                "an alignment must be resolved against the box inside its insets, not against the box itself",
            )
        }

    @Test
    fun aCenteredChildOnAnOddExtentRoundsHalfAPixelUp() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(BOX_WIDTH + 1, BOX_HEIGHT + 1),
                    contentAlignment = Alignment.Center,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(76, 61, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "an offset of 75.5 by 60.5 must round up to 76 by 61, the odd pixel of leftover going before the child",
            )
        }

    @Test
    fun aMatchingChildHeldBelowTheBoxIsAlignedInWhatIsLeftOver() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Child(0, BOX_WIDTH, BOX_HEIGHT, matchingUpTo(CHILD_WIDTH, CHILD_HEIGHT))
                    Child(
                        index = 1,
                        width = BOX_WIDTH,
                        height = BOX_HEIGHT,
                        modifier = matchingUpTo(CHILD_WIDTH, CHILD_HEIGHT).align(Alignment.BottomEnd),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        (BOX_WIDTH - CHILD_WIDTH) / 2,
                        (BOX_HEIGHT - CHILD_HEIGHT) / 2,
                        CHILD_WIDTH,
                        CHILD_HEIGHT,
                    ),
                    Rectangle(BOX_WIDTH - CHILD_WIDTH, BOX_HEIGHT - CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "a matching child a maximum size holds back must sit where its own or the box's alignment puts it",
            )
        }

    @Test
    fun aMatchingChildDoesNotDragTheBoxToTheExtentItsParentOffers() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.preferredSize(BOX_WIDTH, BOX_HEIGHT)) {
                    Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                        Child(0, CHILD_WIDTH, CHILD_HEIGHT)
                        Child(1, BOX_WIDTH, BOX_HEIGHT, SwingModifier.matchParentSize())
                    }
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerSize(),
                "a box offered more space than it needs is sized by the children that do not match it, so a " +
                    "matching child measured against what its parent offered would take the box with it",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "and the matching child takes the extent its siblings settled",
            )
        }

    @Test
    fun aChildsOwnAlignmentPlacesItOnBothAxes() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    SizedChild(0, SwingModifier.align(Alignment.TopCenter))
                }
            }

            assertEquals(
                listOf(Rectangle((BOX_WIDTH - CHILD_WIDTH) / 2, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "an alignment a child declares must place it on both axes, leaving neither to the box",
            )
        }

    @Test
    fun aChildKeepsTheLastAlignmentItDeclares() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT)) {
                    SizedChild(0, SwingModifier.align(Alignment.BottomEnd).align(Alignment.TopStart))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "a modifier is folded in declaration order, so the last alignment declared wins on both axes",
            )
        }

    @Test
    fun aBoxFollowsTheAlignmentItIsDeclaredWith() =
        runComposeSwingTest {
            var alignment by mutableStateOf(Alignment.TopStart)
            setContent {
                Box(modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT), contentAlignment = alignment) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "the alignment the box is declared with",
            )

            alignment = Alignment.BottomEnd
            awaitIdle()

            assertEquals(
                listOf(Rectangle(BOX_WIDTH - CHILD_WIDTH, BOX_HEIGHT - CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "the alignment declared on the next pass",
            )
        }

    @Test
    fun aChildFollowsTheAlignmentItDeclaresForItself() =
        runComposeSwingTest {
            var alignment by mutableStateOf<Alignment?>(Alignment.TopEnd)
            setContent {
                Box(
                    modifier = containerModifier(BOX_WIDTH, BOX_HEIGHT),
                    contentAlignment = Alignment.TopStart,
                ) {
                    SizedChild(0, alignment?.let { SwingModifier.align(it) } ?: SwingModifier)
                }
            }

            assertEquals(
                listOf(Rectangle(BOX_WIDTH - CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "the alignment the child names for itself",
            )

            alignment = Alignment.TopCenter
            awaitIdle()
            assertEquals(
                listOf(Rectangle((BOX_WIDTH - CHILD_WIDTH) / 2, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "the alignment the child names on the next pass",
            )

            alignment = null
            awaitIdle()
            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                stackedChildBounds(),
                "with its own alignment dropped the child takes the box's",
            )
        }

    @Test
    fun aChildThatStopsMatchingTheBoxGoesBackToTheSizeItPrefers() =
        runComposeSwingTest {
            var matching by mutableStateOf(true)
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT)
                    Child(1, BOX_WIDTH, BOX_HEIGHT, if (matching) SwingModifier.matchParentSize() else SwingModifier)
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerSize(),
                "the matching child asks the box for nothing, so the box is sized by its sibling alone",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "and takes the whole of that box in place of the size it prefers",
            )

            matching = false
            awaitIdle()

            assertEquals(
                Dimension(BOX_WIDTH, BOX_HEIGHT),
                containerSize(),
                "with the match dropped the child is measured again and the box grows to what it prefers",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, BOX_WIDTH, BOX_HEIGHT),
                ),
                stackedChildBounds(),
                "and the child is laid out at that size rather than at the box's",
            )
        }

    @Test
    fun anInvisibleChildIsMeasuredAndPlacedLikeAnyOther() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT)
                    Child(1, BOX_WIDTH, BOX_HEIGHT, SwingModifier.visible(false))
                }
            }

            assertEquals(
                Dimension(BOX_WIDTH, BOX_HEIGHT),
                containerPreferredSize(),
                "the box asks for what the child it hides prefers, the largest of the two",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, BOX_WIDTH, BOX_HEIGHT),
                ),
                stackedChildBounds(),
                "and places the hidden one at the extent it asked for, alongside the child it shows",
            )
        }

    @Test
    fun aBoxDoesNotClaimOptimizedDrawing() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) { SizedChild(0) }
            }

            assertFalse(
                box().isOptimizedDrawingEnabled,
                "children of a box overlap, so painting one must repaint what it overlaps",
            )
        }

    @Test
    fun aChildFillingOneAxisKeepsTheExtentItPrefersOnTheOther() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, BOX_WIDTH, BOX_HEIGHT)
                    Child(1, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.fillMaxWidth())
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, BOX_WIDTH, BOX_HEIGHT),
                    Rectangle(0, 0, BOX_WIDTH, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "the filling child should take the box's width and the height it prefers",
            )
        }

    /**
     * The difference between a fill and `matchParentSize`: a filling child is still measured, so the box
     * asks for the extent that child prefers along the axis it does not fill.
     */
    @Test
    fun aFillingChildStillSetsTheBoxsExtentOnTheAxisItDoesNotFill() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, CHILD_WIDTH, CHILD_HEIGHT, SwingModifier.fillMaxWidth())
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                box().preferredSize,
                "a box holding one filling child should ask for the extent that child prefers",
            )
        }

    @Test
    fun aChainDeclaringToTheScopesOfTwoContainersIsRefused() {
        val weightFirst =
            with(BoxScopeInstance) { with(RowScopeInstance) { SwingModifier.weight(1f) }.matchParentSize() }
        val alignFirst =
            with(RowScopeInstance) { with(BoxScopeInstance) { SwingModifier.align(Alignment.CenterEnd) }.weight(1f) }

        for (declared in listOf(weightFirst, alignFirst)) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    runComposeSwingTest {
                        setContent { Box { Child(0, CHILD_WIDTH, CHILD_HEIGHT, declared) } }
                    }
                }

            assertTrue(
                "incompatible layout families" in failure.message.orEmpty(),
                "the refusal should identify the incompatible parent-data families: ${failure.message}",
            )
        }
    }

    @Test
    fun aBoxsLayoutManagerRefusesAConstraintOfAnotherKind() {
        val box = JPanel(PolicyLayout(BoxMeasurePolicy(Alignment.TopStart)))

        val failure = assertFailsWith<IllegalArgumentException> { box.add("North", JLabel("dropped")) }

        assertTrue(
            "can carry no layout constraint" in failure.message.orEmpty(),
            "the manager should refuse a constraint it reads nothing of: ${failure.message}",
        )
    }

    @Test
    fun testAlignInspectableValue() {
        val declared = with(BoxScopeInstance) { SwingModifier.align(Alignment.Center) }

        assertEquals("align", declared.lastElement().name, "align must report itself under its own name")
        assertEquals(
            mapOf("alignment" to Alignment.Center),
            declared.lastElement().declaredValues,
            "and must report the alignment it was declared with",
        )
    }

    @Test
    fun testZIndexInspectableValue() {
        val declared = with(BoxScopeInstance) { SwingModifier.zIndex(2f) }

        assertEquals("zIndex", declared.lastElement().name, "zIndex must report itself under its own name")
        assertEquals(
            mapOf("zIndex" to 2f),
            declared.lastElement().declaredValues,
            "and must report the value it was declared with",
        )
    }

    @Test
    fun testMatchParentSizeInspectableValue() {
        val declared = with(BoxScopeInstance) { SwingModifier.matchParentSize() }

        assertEquals(
            "matchParentSize",
            declared.lastElement().name,
            "matchParentSize must report itself under its own name",
        )
        assertEquals(
            emptyMap<String, Any?>(),
            declared.lastElement().declaredValues,
            "and declares no value of its own",
        )
    }

    private companion object {
        /** The extent a box under test is given, wide and tall enough to place a fixture child within. */
        const val BOX_WIDTH = 200
        const val BOX_HEIGHT = 160

        /** How far a matching child's own maximum size holds it back from the box's extent. */
        const val INSET = 10

        // A minimum below the fixture child's own on one axis each, so the largest minimum along an
        // axis is one child's and neither axis sums to it.
        const val SMALL_WIDTH = 20
        const val SMALL_HEIGHT = 15

        /** How many fixture children fit along either axis of the box the alignment grid is placed in. */
        const val STACKED = 3

        // The four insets of the border a box is given, each different, so no two can be mistaken.
        const val TOP = 5
        const val LEFT = 10
        const val BOTTOM = 15
        const val RIGHT = 20
    }
}

/**
 * The nine alignments a box can be declared with, read row by row: the three tops, then the three
 * halfway down, then the three bottoms. A box three fixture children wide and tall places one child per
 * cell of that grid, in this order.
 */
private val ALIGNMENT_GRID =
    listOf(
        Alignment.TopStart,
        Alignment.TopCenter,
        Alignment.TopEnd,
        Alignment.CenterStart,
        Alignment.Center,
        Alignment.CenterEnd,
        Alignment.BottomStart,
        Alignment.BottomCenter,
        Alignment.BottomEnd,
    )

/** A child taking the box's extent, held to [width] by [height] of it by a maximum size of its own. */
private fun BoxScope.matchingUpTo(
    width: Int,
    height: Int,
): SwingModifier = SwingModifier.matchParentSize().maximumSize(width, height)

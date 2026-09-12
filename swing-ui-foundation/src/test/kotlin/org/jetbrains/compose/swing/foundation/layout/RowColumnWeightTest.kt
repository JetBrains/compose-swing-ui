package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A weight is how a child asks for the space its container has left over once every child that asked
 * for none has taken the extent it prefers. Weighted children share that space in proportion to their
 * weights; a child that declares a maximum of its own takes no more than that maximum allows, and a
 * child that does not fill takes only as much of its share as it prefers.
 *
 * A container nobody imposed a size on asks for room enough that the share each weighted child is
 * granted covers the extent that child can occupy - what it prefers, or its own maximum where that is
 * smaller.
 *
 * Each test reads back the extent and position the container assigned, which is the whole of what a
 * caller can observe of a weight.
 */
class RowColumnWeightTest {
    @Test
    fun aWeightedChildTakesTheHeightTheOtherChildrenLeave() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, COLUMN_EXTENT)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, COLUMN_EXTENT - CHILD_HEIGHT),
                ),
                childBounds(),
                "a single weighted child must take the whole height the column has left over",
            )
        }

    @Test
    fun twoWeightedChildrenSplitTheLeftoverHeightInProportion() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, SPLIT_COLUMN_EXTENT)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(1f))
                    SizedChild(2, SwingModifier.weight(2f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 40, CHILD_WIDTH, 100),
                    Rectangle(0, 140, CHILD_WIDTH, 200),
                ),
                childBounds(),
                "children weighted 1f and 2f must take a third and two thirds of the 300px left over",
            )
        }

    @Test
    fun aWeightedChildThatDoesNotFillKeepsTheHeightItPrefers() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, COLUMN_EXTENT)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(1f, fill = false))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "a child that does not fill must take only what it prefers of the height it was granted",
            )
        }

    @Test
    fun aWeightedChildNeverGrowsPastTheMaximumItDeclares() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, CAPPED_COLUMN_EXTENT)) {
                    SizedChild(0, SwingModifier.weight(1f).maximumSize(CHILD_WIDTH, MAXIMUM_EXTENT))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, MAXIMUM_EXTENT)),
                childBounds(),
                "a weighted child must stop at the maximum it declares, leaving the rest of its share empty",
            )
            assertEquals(
                CAPPED_COLUMN_EXTENT,
                containerSize().height,
                "the column keeps the height it was given, so the height its child refused stays empty",
            )
        }

    @Test
    fun anUnweightedChildInARowNeverGrowsPastTheMaximumItDeclares() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(CAPPED_ROW_EXTENT, CROSS_EXTENT)) {
                    SizedChild(0, SwingModifier.maximumSize(MAXIMUM_EXTENT, CHILD_HEIGHT))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, MAXIMUM_EXTENT, CHILD_HEIGHT)),
                childBounds(),
                "an unweighted child in a row must be measured no wider than the maximum it declares",
            )
        }

    @Test
    fun anUnweightedChildInAColumnNeverGrowsPastTheMaximumItDeclares() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(CROSS_EXTENT, CAPPED_COLUMN_EXTENT)) {
                    SizedChild(0, SwingModifier.maximumSize(CHILD_WIDTH, MAXIMUM_EXTENT))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, MAXIMUM_EXTENT)),
                childBounds(),
                "an unweighted child in a column must be measured no taller than the maximum it declares",
            )
        }

    @Test
    fun anUnweightedChildsMaximumBoundsItsRowsPreferredWidth() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.maximumSize(MAXIMUM_EXTENT, CHILD_HEIGHT))
                }
            }

            assertEquals(
                Dimension(MAXIMUM_EXTENT, CHILD_HEIGHT),
                containerPreferredSize(),
                "a row must not ask for width its unweighted child explicitly refuses",
            )
        }

    @Test
    fun anUnweightedChildsMaximumBoundsItsColumnsPreferredHeight() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.maximumSize(CHILD_WIDTH, MAXIMUM_EXTENT))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, MAXIMUM_EXTENT),
                containerPreferredSize(),
                "a column must not ask for height its unweighted child explicitly refuses",
            )
        }

    @Test
    fun anUnweightedChildsMaximumBoundsItsRowsPreferredHeight() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.maximumSize(CHILD_WIDTH, MAXIMUM_EXTENT))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, MAXIMUM_EXTENT),
                containerPreferredSize(),
                "a row must not ask for height its unweighted child explicitly refuses",
            )
        }

    @Test
    fun anUnweightedChildsMaximumBoundsItsColumnsPreferredWidth() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.maximumSize(MAXIMUM_EXTENT, CHILD_HEIGHT))
                }
            }

            assertEquals(
                Dimension(MAXIMUM_EXTENT, CHILD_HEIGHT),
                containerPreferredSize(),
                "a column must not ask for width its unweighted child explicitly refuses",
            )
        }

    @Test
    fun aWeightedCrossFilledAspectRatioUsesTheFeasibleSizeFromItsMaximumOffer() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(
                        0,
                        SwingModifier
                            .maximumSize(RATIO_MAXIMUM_WIDTH, RATIO_MAXIMUM_HEIGHT)
                            .weight(1f)
                            .fillMaxHeight()
                            .aspectRatio(RATIO),
                    )
                }
            }

            assertEquals(
                Dimension(RATIO_FEASIBLE_WIDTH, RATIO_MAXIMUM_HEIGHT),
                containerPreferredSize(),
                "a row must ask for the feasible 3px by 30px ratio size its weighted, cross-filled child " +
                    "resolves from its 10px by 30px maximum offer",
            )
            assertEquals(
                listOf(Rectangle(0, 0, RATIO_FEASIBLE_WIDTH, RATIO_MAXIMUM_HEIGHT)),
                childBounds(),
                "the row's actual preferred-size pass must agree with its intrinsic measurement",
            )
        }

    @Test
    fun aWeightedCrossFilledAspectRatioEscapesAnImpossibleExactOffer() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(RATIO_MAXIMUM_WIDTH, RATIO_MAXIMUM_HEIGHT)) {
                    SizedChild(
                        0,
                        SwingModifier
                            .maximumSize(RATIO_MAXIMUM_WIDTH, RATIO_MAXIMUM_HEIGHT)
                            .weight(1f)
                            .fillMaxHeight()
                            .aspectRatio(RATIO),
                    )
                }
            }

            assertEquals(
                Dimension(RATIO_MAXIMUM_WIDTH, RATIO_MAXIMUM_HEIGHT),
                containerSize(),
                "the parent must keep the row at the exact 10px by 30px extent it imposed",
            )
            assertEquals(
                listOf(
                    Rectangle(
                        0,
                        (RATIO_MAXIMUM_HEIGHT - RATIO_ESCAPED_HEIGHT) / 2,
                        RATIO_MAXIMUM_WIDTH,
                        RATIO_ESCAPED_HEIGHT,
                    ),
                ),
                childBounds(),
                "under an exact 10px by 30px offer no 0.1 ratio fits, so aspectRatio must escape to the " +
                    "10px by 100px size its documented fallback selects, centered along the cross axis",
            )
        }

    @Test
    fun twoWeightedChildrenSplitTheLeftoverWidthInProportion() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(SPLIT_ROW_EXTENT, CROSS_EXTENT)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(1f))
                    SizedChild(2, SwingModifier.weight(2f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(50, 0, 100, CHILD_HEIGHT),
                    Rectangle(150, 0, 200, CHILD_HEIGHT),
                ),
                childBounds(),
                "children weighted 1f and 2f must take a third and two thirds of the 300px left over",
            )
        }

    @Test
    fun fractionalWeightsSplitAnOddLeftoverWidthWithoutRoundingTheWeights() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(FRACTIONAL_ROW_EXTENT, CROSS_EXTENT)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(0.5f))
                    SizedChild(2, SwingModifier.weight(1.5f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, 1, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH + 1, 0, 4, CHILD_HEIGHT),
                ),
                childBounds(),
                "weights 0.5f and 1.5f must divide all 5px in their one-to-three ratio; rounding the " +
                    "weights first would incorrectly grant 2px and 3px",
            )
        }

    @Test
    fun fractionalWeightsUseAndroidXsFloatAccumulatorAndRoundingOrder() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(FLOAT_ACCUMULATOR_ROW_EXTENT, CROSS_EXTENT)) {
                    SizedChild(0, SwingModifier.weight(8f))
                    SizedChild(1, SwingModifier.weight(75f / 41f))
                    SizedChild(2, SwingModifier.weight(7f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, 11, CHILD_HEIGHT),
                    Rectangle(11, 0, 2, CHILD_HEIGHT),
                    Rectangle(13, 0, 10, CHILD_HEIGHT),
                ),
                childBounds(),
                "the Float accumulator must give AndroidX's 11px, 2px, 10px split rather than a " +
                    "Double accumulator's 10px, 3px, 10px split",
            )
        }

    @Test
    fun twoChildrenAskingForEverythingKeepAndroidXsFloatOverflowRounding() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(SPLIT_ROW_EXTENT, CROSS_EXTENT)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(Float.POSITIVE_INFINITY))
                    SizedChild(2, SwingModifier.weight(Float.POSITIVE_INFINITY))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(50, 0, 1, CHILD_HEIGHT),
                    Rectangle(51, 0, 1, CHILD_HEIGHT),
                ),
                childBounds(),
                "two Float.MAX_VALUE weights overflow the upstream Float total, so AndroidX's remainder " +
                    "pass grants its first two 1px units rather than using Double arithmetic to split 300px",
            )
        }

    @Test
    fun aRowAtItsOwnPreferredWidthHoldsTheWidthAWeightedSpacerAsksFor() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0)
                    Label("", modifier = SwingModifier.weight(1f).preferredSize(SPACER_WIDTH, CHILD_HEIGHT))
                    SizedChild(1)
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH * 2 + SPACER_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "a row asking for its own width must hold the width its weighted spacer asks for",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, SPACER_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH + SPACER_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                ),
                childBounds(),
                "the spacer must keep the labels apart rather than collapse between them",
            )
        }

    @Test
    fun aRowAtItsOwnPreferredWidthCutsNoUnequallyWeightedChildShort() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(1f))
                    SizedChild(2, SwingModifier.weight(2f))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH + CHILD_WIDTH + CHILD_WIDTH * 2, CHILD_HEIGHT),
                containerPreferredSize(),
                "the child weighted 1f asks for 50px of a width split one share to two, so the two " +
                    "weighted children need 150px between them",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH * 2, 0, CHILD_WIDTH * 2, CHILD_HEIGHT),
                ),
                childBounds(),
                "neither weighted child may be cut below the width it prefers",
            )
        }

    @Test
    fun aRowAtItsOwnPreferredWidthHoldsNoMoreThanACappedChildCanOccupy() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(1f).maximumSize(MAXIMUM_EXTENT, CHILD_HEIGHT))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH + MAXIMUM_EXTENT, CHILD_HEIGHT),
                containerPreferredSize(),
                "a row must ask for the width its weighted child can occupy, not the wider width that child " +
                    "prefers and its maximum refuses",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, MAXIMUM_EXTENT, CHILD_HEIGHT),
                ),
                childBounds(),
                "and at that width the capped child is granted no share it has to leave empty",
            )
        }

    @Test
    fun aRowWithTwoInfiniteWeightsKeepsAndroidXsRoundedIntrinsicWidth() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Label(
                        "",
                        modifier =
                            SwingModifier
                                .weight(
                                    Float.POSITIVE_INFINITY,
                                ).preferredSize(Int.MAX_VALUE, CHILD_HEIGHT),
                    )
                    Label(
                        "",
                        modifier =
                            SwingModifier
                                .weight(
                                    Float.POSITIVE_INFINITY,
                                ).preferredSize(Int.MAX_VALUE, CHILD_HEIGHT),
                    )
                }
            }

            assertEquals(
                Dimension(0, CHILD_HEIGHT),
                containerPreferredSize(),
                "two weights clamped from infinity each round their sub-pixel one-unit width to zero before " +
                    "the finite total scales it, as AndroidX's intrinsic rounding order requires",
            )
        }

    @Test
    fun aChildKeepsTheLastWeightItDeclares() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(SPLIT_ROW_EXTENT, CROSS_EXTENT)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(2f, fill = false).weight(1f, fill = true))
                    SizedChild(2, SwingModifier.weight(2f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(50, 0, 100, CHILD_HEIGHT),
                    Rectangle(150, 0, 200, CHILD_HEIGHT),
                ),
                childBounds(),
                "a modifier is folded in declaration order, so the last weight declared - share and fill " +
                    "alike - wins",
            )
        }

    @Test
    fun aWeightThatWouldGrantNoSpaceIsRejected() =
        runComposeSwingTest {
            val error =
                assertFailsWith<IllegalArgumentException> {
                    setContent {
                        Column { SizedChild(0, SwingModifier.weight(0f)) }
                    }
                    awaitIdle()
                }

            assertTrue(
                "greater than zero" in error.message.orEmpty(),
                "the error must say what a weight has to be, but was: ${error.message}",
            )
        }

    @Test
    fun aNegativeWeightIsRejected() =
        runComposeSwingTest {
            val error =
                assertFailsWith<IllegalArgumentException> {
                    setContent {
                        Column { SizedChild(0, SwingModifier.weight(-1f)) }
                    }
                    awaitIdle()
                }

            assertTrue(
                "greater than zero" in error.message.orEmpty(),
                "the error must say what a weight has to be, but was: ${error.message}",
            )
        }

    @Test
    fun aWeightThatIsNotANumberIsRejected() =
        runComposeSwingTest {
            val error =
                assertFailsWith<IllegalArgumentException> {
                    setContent {
                        Column { SizedChild(0, SwingModifier.weight(Float.NaN)) }
                    }
                    awaitIdle()
                }

            assertTrue(
                "greater than zero" in error.message.orEmpty(),
                "a weight there is no share to compute from must be refused like a zero one, but the " +
                    "error was: ${error.message}",
            )
        }

    private companion object {
        // Wider than a child asks for, so the cross axis never interferes with what a weight does.
        const val CROSS_EXTENT = 100

        // Narrower than a fixture child, so the width the spacer holds is unmistakably its own.
        const val SPACER_WIDTH = 20

        // 40px of the column goes to the child that claims no share; the rest is what a weight takes.
        const val COLUMN_EXTENT = 300
        const val SPLIT_COLUMN_EXTENT = 340
        const val SPLIT_ROW_EXTENT = 350

        // Leaves exactly 5px after the fixture child, exposing fractional-weight rounding.
        const val FRACTIONAL_ROW_EXTENT = CHILD_WIDTH + 5

        // This 23px offer sits on a Float rounding boundary that differs from Double accumulation.
        const val FLOAT_ACCUMULATOR_ROW_EXTENT = 23

        // More height than the capped child could ever accept, so the cap is what decides its extent.
        const val CAPPED_COLUMN_EXTENT = 400

        // Wider than a capped child, so the child's maximum rather than the row's offer decides its width.
        const val CAPPED_ROW_EXTENT = 100

        // Below what a fixture child prefers along either axis, so a cap holds its child back from the
        // extent it prefers as well as from the share it was granted.
        const val MAXIMUM_EXTENT = 30

        // A 0.1 width-to-height ratio resolves within a 10px by 30px maximum offer to 3px by 30px.
        const val RATIO_MAXIMUM_WIDTH = 10
        const val RATIO_MAXIMUM_HEIGHT = 30
        const val RATIO = 0.1f
        const val RATIO_FEASIBLE_WIDTH = 3
        const val RATIO_ESCAPED_HEIGHT = 100
    }
}

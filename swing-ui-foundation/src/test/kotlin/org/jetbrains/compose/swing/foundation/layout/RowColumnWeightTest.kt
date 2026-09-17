package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.composed
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
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
 * A container nobody imposed a size on asks for space enough that the share each weighted child is
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
                Column(modifier = containerModifier(100, 300)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.weight(1f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, 260),
                ),
                childBounds(),
                "a single weighted child must take the whole height the column has left over",
            )
        }

    @Test
    fun aChangedWeightMeasuresTheChildAgainThroughTheModifiersItDeclares() =
        runComposeSwingTest {
            var weight by mutableFloatStateOf(1f)
            setContent {
                Row(modifier = containerModifier(100, 10)) {
                    Box(modifier = SwingModifier.testTag("changed").weight(weight).absoluteOffset(2, 3))
                    Box(modifier = SwingModifier.weight(1f))
                }
            }
            val changed = onNodeWithTag("changed").fetch<JComponent>()
            assertEquals(Rectangle(2, 3, 50, 0), changed.bounds)

            weight = 3f
            awaitIdle()

            assertEquals(Rectangle(2, 3, 75, 0), changed.bounds)
        }

    @Test
    fun aWeightAComposedEntryDeclaresIsHonored() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(100, 300)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.composed { weight(1f) })
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, 260),
                ),
                childBounds(),
                "a weight a composed entry returns is read by the column like one declared in its place",
            )
        }

    @Test
    fun twoWeightedChildrenSplitTheLeftoverHeightInProportion() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(100, 340)) {
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
                Column(modifier = containerModifier(100, 300)) {
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
                Column(modifier = containerModifier(100, 400)) {
                    SizedChild(0, SwingModifier.weight(1f).maximumSize(CHILD_WIDTH, 30))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, 30)),
                childBounds(),
                "a weighted child must stop at the maximum it declares, leaving the rest of its share empty",
            )
            assertEquals(
                400,
                containerSize().height,
                "the column keeps the height it was given, so the height its child refused stays empty",
            )
        }

    @Test
    fun anUnweightedChildInARowNeverGrowsPastTheMaximumItDeclares() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(100, 100)) {
                    SizedChild(0, SwingModifier.maximumSize(30, CHILD_HEIGHT))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 30, CHILD_HEIGHT)),
                childBounds(),
                "an unweighted child in a row must be measured no wider than the maximum it declares",
            )
        }

    @Test
    fun anUnweightedChildInAColumnNeverGrowsPastTheMaximumItDeclares() =
        runComposeSwingTest {
            setContent {
                Column(modifier = containerModifier(100, 400)) {
                    SizedChild(0, SwingModifier.maximumSize(CHILD_WIDTH, 30))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, CHILD_WIDTH, 30)),
                childBounds(),
                "an unweighted child in a column must be measured no taller than the maximum it declares",
            )
        }

    @Test
    fun anUnweightedChildsMaximumBoundsItsRowsPreferredWidth() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.maximumSize(30, CHILD_HEIGHT))
                }
            }

            assertEquals(
                Dimension(30, CHILD_HEIGHT),
                containerPreferredSize(),
                "a row must not ask for width its unweighted child explicitly refuses",
            )
        }

    @Test
    fun anUnweightedChildsMaximumBoundsItsColumnsPreferredHeight() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.maximumSize(CHILD_WIDTH, 30))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, 30),
                containerPreferredSize(),
                "a column must not ask for height its unweighted child explicitly refuses",
            )
        }

    @Test
    fun anUnweightedChildsMaximumBoundsItsRowsPreferredHeight() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.maximumSize(CHILD_WIDTH, 30))
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, 30),
                containerPreferredSize(),
                "a row must not ask for height its unweighted child explicitly refuses",
            )
        }

    @Test
    fun anUnweightedChildsMaximumBoundsItsColumnsPreferredWidth() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.maximumSize(30, CHILD_HEIGHT))
                }
            }

            assertEquals(
                Dimension(30, CHILD_HEIGHT),
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
                            .maximumSize(10, 30)
                            .weight(1f)
                            .fillMaxHeight()
                            .aspectRatio(0.1f),
                    )
                }
            }

            assertEquals(
                Dimension(3, 30),
                containerPreferredSize(),
                "a row must ask for the feasible 3px by 30px ratio size its weighted, cross-filled child " +
                    "resolves from its 10px by 30px maximum offer",
            )
            assertEquals(
                listOf(Rectangle(0, 0, 3, 30)),
                childBounds(),
                "the row's actual preferred-size pass must agree with its intrinsic measurement",
            )
        }

    @Test
    fun aWeightedCrossFilledAspectRatioEscapesAnImpossibleExactOffer() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(10, 30)) {
                    SizedChild(
                        0,
                        SwingModifier
                            .maximumSize(10, 30)
                            .weight(1f)
                            .fillMaxHeight()
                            .aspectRatio(0.1f),
                    )
                }
            }

            assertEquals(
                Dimension(10, 30),
                containerSize(),
                "the parent must keep the row at the exact 10px by 30px extent it imposed",
            )
            assertEquals(
                listOf(
                    Rectangle(
                        0,
                        -35,
                        10,
                        100,
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
                Row(modifier = containerModifier(350, 100)) {
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
                // Leaves exactly 5px after the fixture child, exposing fractional-weight rounding.
                Row(modifier = containerModifier(55, 100)) {
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
                // This 23px offer sits on a Float rounding boundary that differs from Double accumulation.
                Row(modifier = containerModifier(23, 100)) {
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
                Row(modifier = containerModifier(350, 100)) {
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
                    // Narrower than a fixture child, so the width the spacer holds is unmistakably its own.
                    Label("", modifier = SwingModifier.weight(1f).preferredSize(20, CHILD_HEIGHT))
                    SizedChild(1)
                }
            }

            assertEquals(
                Dimension(120, CHILD_HEIGHT),
                containerPreferredSize(),
                "a row asking for its own width must hold the width its weighted spacer asks for",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, 20, CHILD_HEIGHT),
                    Rectangle(70, 0, CHILD_WIDTH, CHILD_HEIGHT),
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
                    SizedChild(1, SwingModifier.weight(1f).maximumSize(30, CHILD_HEIGHT))
                }
            }

            assertEquals(
                Dimension(80, CHILD_HEIGHT),
                containerPreferredSize(),
                "a row must ask for the width its weighted child can occupy, not the wider width that child " +
                    "prefers and its maximum refuses",
            )
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(CHILD_WIDTH, 0, 30, CHILD_HEIGHT),
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
                Row(modifier = containerModifier(350, 100)) {
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
}

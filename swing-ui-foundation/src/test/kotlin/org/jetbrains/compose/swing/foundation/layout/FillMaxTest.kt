package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The `fillMax*` layout modifiers fix each bounded axis they name to a fraction of its offered maximum.
 * They are ordinary ordered layout modifiers, so every policy layout gets them through
 * [ConstrainedScope] and their order relative to padding remains observable.
 */
class FillMaxTest {
    @Test
    fun fillMaxWidthAndHeightUseOnlyTheAxisTheyName() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT)) {
                    SizedChild(0, SwingModifier.fillMaxWidth())
                    SizedChild(1, SwingModifier.fillMaxHeight())
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CONTAINER_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, CHILD_WIDTH, CONTAINER_HEIGHT),
                ),
                stackedChildBounds(),
                "each fill must fix only its named axis and leave the other at the child's preferred extent",
            )
        }

    @Test
    fun widthAndHeightFillsUseTheirRequestedFractions() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT)) {
                    SizedChild(0, SwingModifier.fillMaxWidth(QUARTER))
                    SizedChild(1, SwingModifier.fillMaxHeight(QUARTER))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CONTAINER_WIDTH / 4, CHILD_HEIGHT),
                    Rectangle(0, 0, CHILD_WIDTH, CONTAINER_HEIGHT / 4),
                ),
                stackedChildBounds(),
                "each one-axis fill must apply its fraction only to the bounded axis it names",
            )
        }

    @Test
    fun fillMaxSizeUsesTheRequestedFractionOnBothAxes() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(ODD_WIDTH, ODD_HEIGHT)) {
                    SizedChild(0, SwingModifier.fillMaxSize(HALF))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 151, 101)),
                stackedChildBounds(),
                "a half fill must round each odd maximum to the nearest integer extent",
            )
        }

    @Test
    fun aFillFractionCannotShrinkBelowTheIncomingMinimum() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.fillMaxSize(0f)) },
                    measurePolicy = { measurables, _ ->
                        val placeable =
                            measurables.single().measure(
                                Constraints(
                                    minWidth = MINIMUM,
                                    maxWidth = CONTAINER_WIDTH,
                                    minHeight = MINIMUM,
                                    maxHeight = CONTAINER_HEIGHT,
                                ),
                            )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                )
            }

            assertEquals(
                listOf(Rectangle(0, 0, MINIMUM, MINIMUM)),
                stackedChildBounds(),
                "a zero fraction must still honor a nonzero minimum in the policy's offer",
            )
        }

    @Test
    fun aFillFractionUsesTheMaximumSizeCappedOffer() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT)) {
                    SizedChild(
                        0,
                        SwingModifier
                            .maximumSize(MAXIMUM_WIDTH, CONTAINER_HEIGHT)
                            .fillMaxWidth(HALF),
                    )
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, MAXIMUM_WIDTH / 2, CHILD_HEIGHT)),
                stackedChildBounds(),
                "fillMaxWidth must take its fraction after Box caps the offer at Swing maximumSize",
            )
        }

    @Test
    fun anExactMatchParentOfferCannotBeReducedByAFillFraction() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT)) {
                    SizedChild(0)
                    SizedChild(1, SwingModifier.matchParentSize().fillMaxWidth(HALF))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, 0, CONTAINER_WIDTH, CONTAINER_HEIGHT),
                ),
                stackedChildBounds(),
                "matchParentSize supplies an exact minimum and maximum, so fillMaxWidth must preserve that offer",
            )
        }

    @Test
    fun anUnboundedAxisIsLeftAtTheExtentTheChildPrefers() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SizedChild(0, SwingModifier.fillMaxSize())
                }
            }

            assertEquals(
                Dimension(CHILD_WIDTH, CHILD_HEIGHT),
                containerPreferredSize(),
                "an intrinsic query offers no bounded maximum, so fillMaxSize must keep the preferred extent",
            )
        }

    @Test
    fun fillMaxSizeFillsABoundedAxisAndLeavesAnUnboundedAxisPreferred() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.fillMaxSize(HALF)) },
                    measurePolicy = { measurables, _ ->
                        val placeable =
                            measurables.single().measure(
                                Constraints(maxWidth = CONTAINER_WIDTH),
                            )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                )
            }

            assertEquals(
                listOf(Rectangle(0, 0, CONTAINER_WIDTH / 2, CHILD_HEIGHT)),
                stackedChildBounds(),
                "fillMaxSize must fill the bounded width while leaving the unbounded height preferred",
            )
        }

    @Test
    fun fillAndPaddingApplyInDeclarationOrder() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT)) {
                    SizedChild(0, SwingModifier.fillMaxWidth(HALF).padding(PADDING))
                    SizedChild(1, SwingModifier.padding(PADDING).fillMaxWidth(HALF))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(PADDING, PADDING, 80, CHILD_HEIGHT),
                    Rectangle(PADDING, PADDING, 90, CHILD_HEIGHT),
                ),
                stackedChildBounds(),
                "padding inside a half fill receives its fixed width, while padding outside reduces the maximum first",
            )
        }

    @Test
    fun everyFillBuilderReportsItsNameAndFraction() {
        with(ConstrainedScopeImpl) {
            val declarations =
                listOf(
                    SwingModifier.fillMaxWidth(HALF) to "fillMaxWidth",
                    SwingModifier.fillMaxHeight(HALF) to "fillMaxHeight",
                    SwingModifier.fillMaxSize(HALF) to "fillMaxSize",
                )

            for ((modifier, name) in declarations) {
                assertEquals(name, modifier.lastElement().name, "$name must report its public name")
                assertEquals(
                    mapOf("fraction" to HALF),
                    modifier.lastElement().declaredValues,
                    "$name must report the fraction it was declared with",
                )
            }
        }
    }

    @Test
    fun fractionsOutsideThePublicRangeAreRejected() {
        val invalid = listOf(-0.1f, 1.1f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)

        with(ConstrainedScopeImpl) {
            val builders =
                mapOf<String, (Float) -> SwingModifier>(
                    "fillMaxWidth" to { SwingModifier.fillMaxWidth(it) },
                    "fillMaxHeight" to { SwingModifier.fillMaxHeight(it) },
                    "fillMaxSize" to { SwingModifier.fillMaxSize(it) },
                )

            for ((name, builder) in builders) {
                for (fraction in invalid) {
                    val failure = assertFailsWith<IllegalArgumentException> { builder(fraction) }
                    assertTrue(
                        "between zero and one" in failure.message.orEmpty(),
                        "$name must state the accepted range, but was: ${failure.message}",
                    )
                }
            }
        }
    }

    private companion object {
        const val CONTAINER_WIDTH = 200
        const val CONTAINER_HEIGHT = 120
        const val ODD_WIDTH = 301
        const val ODD_HEIGHT = 201
        const val MINIMUM = 24
        const val MAXIMUM_WIDTH = 160
        const val PADDING = 10
        const val HALF = 0.5f
        const val QUARTER = 0.25f
    }
}

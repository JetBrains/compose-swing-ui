package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Numeric size and wrap-content modifiers use the same constraint transformation as Compose
 * Foundation. In particular, preferred sizes remain inside a parent's offer, while required sizes
 * retain their raw extent and are centered when that extent overflows the reported one.
 */
class SizeModifierTest {
    @Test
    fun preferredAndRequiredSizesDifferWhenTheParentIsSmaller() =
        runComposeSwingTest {
            setContent {
                FixedOfferLayout(Constraints.fixed(CONTAINER_WIDTH, CONTAINER_HEIGHT)) {
                    Child(0, ASKED_WIDTH, ASKED_HEIGHT, SwingModifier.size(PREFERRED_WIDTH, PREFERRED_HEIGHT))
                    Child(1, ASKED_WIDTH, ASKED_HEIGHT, SwingModifier.requiredSize(REQUIRED_WIDTH, REQUIRED_HEIGHT))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CONTAINER_WIDTH, CONTAINER_HEIGHT),
                    Rectangle(
                        (CONTAINER_WIDTH - REQUIRED_WIDTH) / 2,
                        (CONTAINER_HEIGHT - REQUIRED_HEIGHT) / 2,
                        REQUIRED_WIDTH,
                        REQUIRED_HEIGHT,
                    ),
                ),
                stackedChildBounds(),
                "a preferred size must obey a smaller offer, while a required size keeps its extent and " +
                    "is centered in the apparent extent the parent receives",
            )
        }

    @Test
    fun rangedSizesPreserveUnspecifiedBoundsAndPreferTheSpecifiedOnes() =
        runComposeSwingTest {
            setContent {
                FixedOfferLayout(Constraints(maxWidth = LARGE_WIDTH, maxHeight = LARGE_HEIGHT)) {
                    Child(
                        0,
                        ASKED_WIDTH,
                        ASKED_HEIGHT,
                        SwingModifier.sizeIn(minWidth = RANGE_WIDTH, minHeight = RANGE_HEIGHT),
                    )
                    Child(1, ASKED_WIDTH, ASKED_HEIGHT, SwingModifier.requiredSizeIn())
                }
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, RANGE_WIDTH, RANGE_HEIGHT),
                    Rectangle(0, 0, ASKED_WIDTH, ASKED_HEIGHT),
                ),
                stackedChildBounds(),
                "a range must raise only its declared bounds, and omitted required bounds must leave the " +
                    "incoming constraints unchanged",
            )
        }

    @Test
    fun wrapContentRelaxesTheSelectedAxisAndPlacesWithinTheReportedWrapper() =
        runComposeSwingTest {
            setContent {
                FixedOfferLayout(
                    Constraints(
                        minWidth = WRAP_WIDTH,
                        maxWidth = WRAP_WIDTH,
                        minHeight = WRAP_HEIGHT,
                        maxHeight = WRAP_HEIGHT,
                    ),
                ) {
                    Child(0, ASKED_WIDTH, ASKED_HEIGHT, SwingModifier.wrapContentWidth())
                }
            }

            assertEquals(
                listOf(Rectangle((WRAP_WIDTH - ASKED_WIDTH) / 2, 0, ASKED_WIDTH, WRAP_HEIGHT)),
                stackedChildBounds(),
                "wrapping width must ignore an offered width minimum and center the desired child width " +
                    "inside the wrapper",
            )
        }

    @Test
    fun unboundedWrapContentCanOverflowAndHonorItsAlignment() =
        runComposeSwingTest {
            setContent {
                FixedOfferLayout(Constraints(maxWidth = WRAP_MAXIMUM, maxHeight = WRAP_HEIGHT)) {
                    Child(
                        0,
                        OVERFLOW_WIDTH,
                        ASKED_HEIGHT,
                        SwingModifier.wrapContentWidth(align = Alignment.End, unbounded = true),
                    )
                }
            }

            assertEquals(
                listOf(Rectangle(WRAP_MAXIMUM - OVERFLOW_WIDTH, 0, OVERFLOW_WIDTH, ASKED_HEIGHT)),
                stackedChildBounds(),
                "an unbounded wrapped width must let the child exceed the offer and align it inside the " +
                    "reported maximum-width wrapper",
            )
        }

    @Test
    fun wrapContentHeightCentersDesiredHeightInsideWrapper() =
        runComposeSwingTest {
            setContent {
                FixedOfferLayout(
                    Constraints(
                        minWidth = WRAP_WIDTH,
                        maxWidth = WRAP_WIDTH,
                        minHeight = WRAP_OFFER_HEIGHT,
                        maxHeight = WRAP_OFFER_HEIGHT,
                    ),
                ) {
                    Child(0, ASKED_WIDTH, ASKED_HEIGHT, SwingModifier.wrapContentHeight())
                }
            }

            assertEquals(
                listOf(Rectangle(0, (WRAP_OFFER_HEIGHT - ASKED_HEIGHT) / 2, WRAP_WIDTH, ASKED_HEIGHT)),
                stackedChildBounds(),
                "wrapping height must ignore an offered height minimum and center the desired child height " +
                    "inside the wrapper",
            )
        }

    @Test
    fun unboundedWrapContentHeightCanOverflowAndHonorItsAlignment() =
        runComposeSwingTest {
            setContent {
                FixedOfferLayout(Constraints(maxWidth = WRAP_WIDTH, maxHeight = WRAP_MAXIMUM)) {
                    Child(
                        0,
                        ASKED_WIDTH,
                        OVERFLOW_HEIGHT,
                        SwingModifier.wrapContentHeight(align = Alignment.Bottom, unbounded = true),
                    )
                }
            }

            assertEquals(
                listOf(Rectangle(0, WRAP_MAXIMUM - OVERFLOW_HEIGHT, ASKED_WIDTH, OVERFLOW_HEIGHT)),
                stackedChildBounds(),
                "an unbounded wrapped height must let the child exceed the offer and align it inside the " +
                    "reported maximum-height wrapper",
            )
        }

    @Test
    fun wrapContentSizeCentersDesiredSizeInsideWrapper() =
        runComposeSwingTest {
            setContent {
                FixedOfferLayout(
                    Constraints(
                        minWidth = WRAP_WIDTH,
                        maxWidth = WRAP_WIDTH,
                        minHeight = WRAP_OFFER_HEIGHT,
                        maxHeight = WRAP_OFFER_HEIGHT,
                    ),
                ) {
                    Child(0, ASKED_WIDTH, ASKED_HEIGHT, SwingModifier.wrapContentSize(Alignment.BottomEnd))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        WRAP_WIDTH - ASKED_WIDTH,
                        WRAP_OFFER_HEIGHT - ASKED_HEIGHT,
                        ASKED_WIDTH,
                        ASKED_HEIGHT,
                    ),
                ),
                stackedChildBounds(),
                "wrapping size must ignore offered minimums and align the desired child size inside the wrapper",
            )
        }

    @Test
    fun unboundedWrapContentSizeCanOverflowAndHonorItsAlignment() =
        runComposeSwingTest {
            setContent {
                FixedOfferLayout(Constraints(maxWidth = WRAP_MAXIMUM, maxHeight = WRAP_MAXIMUM)) {
                    Child(
                        0,
                        OVERFLOW_WIDTH,
                        OVERFLOW_HEIGHT,
                        SwingModifier.wrapContentSize(align = Alignment.BottomEnd, unbounded = true),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        WRAP_MAXIMUM - OVERFLOW_WIDTH,
                        WRAP_MAXIMUM - OVERFLOW_HEIGHT,
                        OVERFLOW_WIDTH,
                        OVERFLOW_HEIGHT,
                    ),
                ),
                stackedChildBounds(),
                "an unbounded wrapped size must let the child exceed the offer and align it inside the wrapper",
            )
        }

    @Test
    fun sizeBuildersReportTheirNameAndTheBoundsTheyDeclare() {
        with(ConstrainedScopeImpl) {
            val declarations =
                listOf(
                    SwingModifier.sizeIn(minWidth = 1, minHeight = 2, maxWidth = 3, maxHeight = 4) to "sizeIn",
                    SwingModifier.requiredWidthIn(min = 1, max = 3) to "requiredWidthIn",
                    SwingModifier.height(2) to "height",
                )
            val bounds =
                listOf(
                    mapOf("minWidth" to 1, "minHeight" to 2, "maxWidth" to 3, "maxHeight" to 4),
                    mapOf("minWidth" to 1, "minHeight" to null, "maxWidth" to 3, "maxHeight" to null),
                    mapOf("minWidth" to null, "minHeight" to 2, "maxWidth" to null, "maxHeight" to 2),
                )

            for ((declared, expected) in declarations.zip(bounds)) {
                val (modifier, name) = declared
                assertEquals(name, modifier.lastElement().name, "$name must report its public name")
                assertEquals(expected, modifier.lastElement().declaredValues, "$name must report what it bounds")
            }
        }
    }

    @Test
    fun everyWrapContentBuilderReportsItsNameAlignmentAndUnboundedness() {
        with(ConstrainedScopeImpl) {
            val declarations =
                listOf(
                    SwingModifier.wrapContentWidth(Alignment.End, unbounded = true) to
                        mapOf("align" to Alignment.End, "unbounded" to true),
                    SwingModifier.wrapContentHeight(Alignment.Bottom) to
                        mapOf("align" to Alignment.Bottom, "unbounded" to false),
                    SwingModifier.wrapContentSize(Alignment.TopStart) to
                        mapOf("align" to Alignment.TopStart, "unbounded" to false),
                )

            for ((modifier, expected) in declarations) {
                val name = modifier.lastElement().name
                assertEquals(
                    expected,
                    modifier.lastElement().declaredValues,
                    "$name must report its alignment and unboundedness",
                )
            }
            assertEquals(
                listOf("wrapContentWidth", "wrapContentHeight", "wrapContentSize"),
                declarations.map { it.first.lastElement().name },
                "each wrap-content builder must report its own name",
            )
        }
    }

    /**
     * androidx `Size.kt:800-807` coerces `minWidth` into `0..maxWidth` before checking whether the result names
     * the unspecified sentinel, so an `Int.MAX_VALUE` minimum above a finite maximum is coerced down to that
     * maximum and kept, not answered as zero.
     */
    @Test
    fun widthInCoercesAnOutOfRangeMinimumIntoRangeBeforeItChecksForTheUnboundedSentinel() =
        runComposeSwingTest {
            val naturalHeight = 20
            val coercedMaxWidth = 50
            setContent {
                FixedOfferLayout(Constraints(maxWidth = LARGE_WIDTH, maxHeight = LARGE_HEIGHT)) {
                    Child(0, 10, naturalHeight, SwingModifier.widthIn(min = Int.MAX_VALUE, max = coercedMaxWidth))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, coercedMaxWidth, naturalHeight)),
                stackedChildBounds(),
                "an unbounded minimum above a finite maximum must be coerced down to that maximum and raise the " +
                    "child to it, not fall back to zero",
            )
        }

    @Composable
    private fun FixedOfferLayout(
        offer: Constraints,
        content: @Composable ConstrainedScope.() -> Unit,
    ) {
        Layout(
            content = content,
            modifier = SwingModifier.testTag(CONTAINER_TAG),
            measurePolicy = { measurables, _ ->
                val placeables = measurables.map { it.measure(offer) }
                layout(
                    placeables.maxOfOrNull { it.width } ?: 0,
                    placeables.maxOfOrNull { it.height } ?: 0,
                ) {
                    placeables.forEach { it.place(0, 0) }
                }
            },
        )
    }

    /**
     * androidx `Size.kt:877-878` asks the child for its intrinsic width at the incoming height, unchanged by a
     * preferred `heightIn` bounding only its own maximum height. `AspectRatio.kt:127-128` answers that width as
     * the incoming height times the ratio.
     */
    @Test
    fun heightInAsksTheChildForItsMinIntrinsicWidthAtTheIncomingHeight() =
        runComposeSwingTest {
            var minIntrinsicWidth = -1
            setContent {
                Layout(measurePolicy = { measurables, _ ->
                    minIntrinsicWidth = measurables[0].minIntrinsicWidth(100)
                    layout(0, 0) {}
                }) {
                    SwingNode(factory = { JPanel() }, modifier = SwingModifier.heightIn(max = 30).aspectRatio(2f))
                }
            }

            assertEquals(
                200,
                minIntrinsicWidth,
                "a preferred heightIn must pass the incoming height to the child unchanged, not its own bounded " +
                    "maximum height",
            )
        }

    /**
     * androidx `Size.kt:916-917` asks the child for its intrinsic height at the incoming width, held to a
     * required `requiredWidthIn`'s own bounds. `AspectRatio.kt:157-158` answers that height as the width
     * divided by the ratio.
     */
    @Test
    fun requiredWidthInAsksTheChildForItsMaxIntrinsicHeightAtTheIncomingWidth() =
        runComposeSwingTest {
            var maxIntrinsicHeight = -1
            setContent {
                Layout(measurePolicy = { measurables, _ ->
                    maxIntrinsicHeight = measurables[0].maxIntrinsicHeight(100)
                    layout(0, 0) {}
                }) {
                    SwingNode(
                        factory = { JPanel() },
                        modifier = SwingModifier.requiredWidthIn(max = 200).aspectRatio(2f),
                    )
                }
            }

            assertEquals(
                50,
                maxIntrinsicHeight,
                "a required widthIn must hold the incoming width to its own bounds before asking the child",
            )
        }

    /**
     * androidx `Size.kt:1079-1080` coerces an unspecified minimum into `0..maxWidth` before it can name the
     * unbounded sentinel, so a minimum of `Int.MAX_VALUE` under a finite incoming maximum resolves to that
     * maximum.
     */
    @Test
    fun defaultMinSizeResolvesAnUnboundedMinimumToTheFiniteIncomingMaximum() =
        runComposeSwingTest {
            setContent {
                FixedOfferLayout(Constraints(maxWidth = LARGE_WIDTH, maxHeight = LARGE_HEIGHT)) {
                    Child(0, ASKED_WIDTH, ASKED_HEIGHT, SwingModifier.defaultMinSize(minWidth = Int.MAX_VALUE))
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, LARGE_WIDTH, ASKED_HEIGHT)),
                stackedChildBounds(),
                "an unbounded minimum must be coerced down to the finite incoming maximum, not left unbounded",
            )
        }

    private companion object {
        const val ASKED_WIDTH = 20
        const val ASKED_HEIGHT = 30
        const val CONTAINER_WIDTH = 40
        const val CONTAINER_HEIGHT = 50
        const val PREFERRED_WIDTH = 60
        const val PREFERRED_HEIGHT = 70
        const val REQUIRED_WIDTH = 60
        const val REQUIRED_HEIGHT = 70
        const val LARGE_WIDTH = 100
        const val LARGE_HEIGHT = 100
        const val RANGE_WIDTH = 40
        const val RANGE_HEIGHT = 50
        const val WRAP_WIDTH = 80
        const val WRAP_HEIGHT = 30
        const val WRAP_OFFER_HEIGHT = 60
        const val WRAP_MAXIMUM = 50
        const val OVERFLOW_WIDTH = 90
        const val OVERFLOW_HEIGHT = 80
    }
}

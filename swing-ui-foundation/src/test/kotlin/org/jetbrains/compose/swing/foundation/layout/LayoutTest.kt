package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A container written as a [MeasurePolicy] rather than as a layout manager. The policy is asked the
 * same questions [Row], [Column] and [Box] are: an extent to name when nothing constrains it, and an
 * extent plus a placement when its container has one.
 *
 * A child inside it is an ordinary child - it declares its own layout modifiers and its own constraint
 * to the policy, and a container nested under it is asked a constrained question like any other.
 */
class LayoutTest {
    @Test
    fun aPolicyPlacesEachChildWhereItSays() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                        SizedChild(2)
                    },
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                    measurePolicy = stackedRows(),
                )
            }

            assertEquals(
                columnRows(0, CHILD_HEIGHT, 2 * CHILD_HEIGHT),
                childBounds(),
                "each child must be placed where the policy placed it",
            )
        }

    @Test
    fun aPolicyGivesLaterChildrenOnlyTheHeightLeftInTheContainer() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                    },
                    modifier = containerModifier(CROSS_EXTENT, CHILD_HEIGHT),
                    measurePolicy = stackedRows(),
                )
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, 0),
                ),
                childBounds(),
                "a later child must be measured into the height left instead of extending beyond the container",
            )
        }

    @Test
    fun anAspectRatioChildThatHasNoHeightLeftIsGivenNoSpaceToEscapeTheContainer() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1, SwingModifier.aspectRatio(2f))
                    },
                    modifier = containerModifier(CROSS_EXTENT, CHILD_HEIGHT),
                    measurePolicy = stackedRows(),
                )
            }

            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, 0, 0),
                ),
                childBounds(),
                "an aspect-ratio child that escapes a zero-height offer must not be placed outside the stack",
            )
        }

    @Test
    fun aPolicyNamesWhatTheContainerAsksItsOwnParentFor() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                    },
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    measurePolicy = stackedRows(),
                )
            }

            assertEquals(
                Dimension(CHILD_WIDTH, 2 * CHILD_HEIGHT),
                containerPreferredSize(),
                "the extent the policy names with nothing to constrain it is what the container prefers",
            )
        }

    @Test
    fun aChildTakesTheExtentThePolicyOffersIt() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = { SizedChild(0) },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints(GRANTED, GRANTED, GRANTED, GRANTED))
                        layout(GRANTED, GRANTED) { placeable.place(0, 0) }
                    },
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                )
            }

            assertEquals(
                listOf(Rectangle(0, 0, GRANTED, GRANTED)),
                childBounds(),
                "a child measured under a fixed extent occupies it rather than the extent it prefers",
            )
        }

    /**
     * The content receiver is [ConstrainedScope], so a child declares the modifiers that stand between
     * the policy's offer and its own measure. The padding narrows what reaches the child and states the
     * child plus its own space as what the policy placed.
     */
    @Test
    fun aChildsOwnLayoutModifiersStandBetweenThePolicyAndTheChild() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.padding(PADDING)) },
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                    measurePolicy = stackedRows(),
                )
            }

            assertEquals(
                listOf(Rectangle(PADDING, PADDING, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "a padded child sits inside the space its padding reserved",
            )
        }

    @Test
    fun aPolicyReadsBackWhatAChildDeclaredToIt() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.layoutConstraint(TRAILING)) },
                    measurePolicy = { measurables, constraints ->
                        val placeables =
                            measurables.map {
                                val placeable = it.measure(Constraints(maxWidth = constraints.maxWidth))
                                placeable to (it.parentData == TRAILING)
                            }
                        layout(constraints.maxWidth, CHILD_HEIGHT) {
                            for ((placeable, trailing) in placeables) {
                                placeable.place(if (trailing) constraints.maxWidth - placeable.width else 0, 0)
                            }
                        }
                    },
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                )
            }

            assertEquals(
                listOf(Rectangle(CROSS_EXTENT - CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "the policy must place the child by the constraint the child declared to it",
            )
        }

    @Test
    fun aPolicyHandedOnALaterPassLaysTheContainerOutAgain() =
        runComposeSwingTest {
            var spaced by mutableStateOf(false)
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                    },
                    measurePolicy = if (spaced) stackedRows(CHILD_HEIGHT) else stackedRows(),
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                )
            }

            assertEquals(columnRows(0, CHILD_HEIGHT), childBounds(), "the container starts under the first policy")

            spaced = true
            awaitIdle()

            assertEquals(
                columnRows(0, 2 * CHILD_HEIGHT),
                childBounds(),
                "and is laid out again by the policy the later pass handed it",
            )
        }

    @Test
    fun aLeafLayoutUsesItsMeasurePolicyWithoutComposingChildren() =
        runComposeSwingTest {
            setContent {
                Layout(
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    measurePolicy = { _, constraints ->
                        layout(constraints.minWidth, constraints.minHeight) {}
                    },
                )
            }

            val layout = onNodeWithTag(CONTAINER_TAG).fetch<JPanel>()
            assertEquals(0, layout.componentCount, "the leaf overload must not compose an empty child lambda")
            assertEquals(
                Dimension(0, 0),
                layout.preferredSize,
                "the leaf policy must define the panel's preferred size",
            )
        }

    /**
     * A [Layout] answers the constrained question its own parent asks, so a container nested inside one
     * is measured against the extent the policy offered rather than against the extent it prefers.
     *
     * The offer is a ceiling with no floor, which is what makes the reading a measure rather than a
     * placement: a row handed a fixed extent would occupy it either way, because its own bounds are
     * written by the pass that places it. What only a measure settles is the row's weighted child,
     * which takes the width the row was granted.
     */
    @Test
    fun aContainerNestedInsideALayoutIsAskedTheConstrainedQuestion() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        Row {
                            SizedChild(0, SwingModifier.weight(1f))
                        }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable =
                            measurables.single().measure(
                                Constraints(maxWidth = GRANTED, maxHeight = GRANTED),
                            )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                    modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                )
            }

            assertEquals(
                listOf(Rectangle(0, 0, GRANTED, GRANTED)),
                childBounds(),
                "the nested row must settle for the ceiling the policy offered, not the extent it prefers",
            )
            assertEquals(
                listOf(Rectangle(0, 0, GRANTED, GRANTED)),
                nestedRowChildBounds(),
                "and must have measured under it, since its weighted child takes the width it was granted",
            )
        }

    private companion object {
        /** The extent the fixture container is given across the axis its policy stacks children on. */
        const val CROSS_EXTENT = 200

        /** The extent the fixture container is given along that axis. */
        const val MAIN_EXTENT = 300

        /** The extent a policy grants a child outright, smaller than the container and than the child. */
        const val GRANTED = 30

        /** The space a padded child reserves around itself. */
        const val PADDING = 8

        /** What a child declares to a policy that places by a constraint of its own. */
        const val TRAILING = "trailing"

        /** The bounds the one row nested inside the container under test assigned its own children. */
        fun ComposeSwingTest.nestedRowChildBounds(): List<Rectangle> =
            (onNodeWithTag(CONTAINER_TAG).fetch<JPanel>().getComponent(0) as JPanel)
                .components
                .map { it.bounds }

        /**
         * A policy that stacks its children down the container at the width the offer allows, [gap]
         * apart, and asks for as much space as the stack occupies within that offer.
         */
        fun stackedRows(gap: Int = 0): MeasurePolicy =
            MeasurePolicy { measurables, constraints ->
                var remainingHeight = constraints.maxHeight
                val placeables =
                    measurables.mapIndexed { index, measurable ->
                        val measured =
                            measurable.measure(
                                Constraints(maxWidth = constraints.maxWidth, maxHeight = remainingHeight),
                            )
                        val placeable =
                            if (measured.measuredWidth <= constraints.maxWidth &&
                                measured.measuredHeight <= remainingHeight
                            ) {
                                measured
                            } else {
                                measurable.measure(Constraints(maxWidth = 0, maxHeight = 0))
                            }
                        remainingHeight = (remainingHeight - placeable.height).coerceAtLeast(0)
                        if (index < measurables.lastIndex) {
                            remainingHeight = (remainingHeight - gap.coerceAtLeast(0)).coerceAtLeast(0)
                        }
                        placeable
                    }
                val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
                val height = constraints.constrainHeight(constraints.maxHeight - remainingHeight)
                layout(width, height) {
                    var y = 0
                    for (placeable in placeables) {
                        placeable.place(0, y)
                        y += placeable.height + gap
                    }
                }
            }
    }
}

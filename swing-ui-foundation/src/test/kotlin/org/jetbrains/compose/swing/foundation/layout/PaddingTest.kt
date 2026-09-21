package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A padding reserves space along a child's edges: it takes that space out of what the child is measured
 * under, states the child plus the space as the extent it occupies, and places the child inside it.
 * `padding` reserves its start before the child and its end after it along the reading order, so the
 * two swap edges under a right-to-left orientation, while `absolutePadding` reserves the same left and
 * right under either.
 *
 * Ported from androidx `foundation-layout`'s own `PaddingTest`, whose case names are kept so the two
 * files read side by side. `PaddingValues` in a case name is the four-argument `padding` this library
 * spells the same insets with. Three of that test's cases have no counterpart here: a
 * horizontal-and-vertical padding here delegates to the four sides rather than describing itself by
 * the two, which is what `testInspectableParameterWith2Parameters` reads,
 * `testInspectableParameterWithSameOverallValue` reads the single value an inspector shows in place of
 * the four sides, and `paddingAbsolutePaddingValuesAppliedToChild` reaches the same builder
 * [absolutePaddingAppliedToChild] already covers. [intrinsicMeasurements] keeps the rows androidx
 * asserts under an unbounded extent: what a container asks for is the whole of the intrinsic surface
 * here, and it takes no extent to answer against.
 *
 * [aChainIsMeasuredOutermostFirst] has no counterpart there, where the order of a modifier chain is
 * the framework's own business rather than this tree's.
 */
class PaddingTest {
    @Test
    fun allEqualToAbsoluteWithExplicitSides() {
        assertEquals(
            with(BoxScopeInstance) {
                SwingModifier.padding(UNIFORM_PADDING, UNIFORM_PADDING, UNIFORM_PADDING, UNIFORM_PADDING)
            },
            with(BoxScopeInstance) { SwingModifier.padding(UNIFORM_PADDING) },
            "a padding declared for every edge at once must equal the same four sides named one by one",
        )
    }

    @Test
    fun symmetricEqualToAbsoluteWithExplicitSides() {
        assertEquals(
            with(BoxScopeInstance) {
                SwingModifier.padding(HORIZONTAL_PADDING, VERTICAL_PADDING, HORIZONTAL_PADDING, VERTICAL_PADDING)
            },
            with(BoxScopeInstance) { SwingModifier.padding(HORIZONTAL_PADDING, VERTICAL_PADDING) },
            "a padding declared for a pair of edges at once must equal the same four sides named one by one",
        )
    }

    @Test
    fun negativeStartPadding_throws() {
        with(BoxScopeInstance) {
            assertFailsWith<IllegalArgumentException>("no space can be reserved before a child") {
                SwingModifier.padding(start = -1)
            }
        }
    }

    @Test
    fun negativeTopPadding_throws() {
        with(BoxScopeInstance) {
            assertFailsWith<IllegalArgumentException>("no space can be reserved above a child") {
                SwingModifier.padding(top = -1)
            }
        }
    }

    @Test
    fun negativeEndPadding_throws() {
        with(BoxScopeInstance) {
            assertFailsWith<IllegalArgumentException>("no space can be reserved after a child") {
                SwingModifier.padding(end = -1)
            }
        }
    }

    @Test
    fun negativeBottomPadding_throws() {
        with(BoxScopeInstance) {
            assertFailsWith<IllegalArgumentException>("no space can be reserved below a child") {
                SwingModifier.padding(bottom = -1)
            }
        }
    }

    @Test
    fun aNegativeAbsolutePaddingIsRefusedToo() {
        with(BoxScopeInstance) {
            assertFailsWith<IllegalArgumentException>(
                "an absolute padding reserves the same space a padding does, so it refuses the same values",
            ) {
                SwingModifier.absolutePadding(left = -1)
            }
        }
    }

    @Test
    fun paddingAllAppliedToChild() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(BOX_EXTENT, BOX_EXTENT)) {
                    SizedChild(0, SwingModifier.matchParentSize().padding(UNIFORM_PADDING))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        UNIFORM_PADDING,
                        UNIFORM_PADDING,
                        BOX_EXTENT - 2 * UNIFORM_PADDING,
                        BOX_EXTENT - 2 * UNIFORM_PADDING,
                    ),
                ),
                childBounds(),
                "a padded child that fills its box must be measured into what the padding leaves of it, and " +
                    "sit that far in from each edge",
            )
        }

    @Test
    fun paddingPaddingValuesAppliedToChild() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(BOX_EXTENT, BOX_EXTENT)) {
                    SizedChild(
                        0,
                        SwingModifier.matchParentSize().padding(INSET_START, INSET_TOP, INSET_END, INSET_BOTTOM),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        INSET_START,
                        INSET_TOP,
                        BOX_EXTENT - INSET_START - INSET_END,
                        BOX_EXTENT - INSET_TOP - INSET_BOTTOM,
                    ),
                ),
                childBounds(),
                "a padding naming four different sides must take each of them out of the child's own extent " +
                    "and move the child in by the two it leads with",
            )
        }

    @Test
    fun absolutePaddingAppliedToChild() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(BOX_EXTENT, BOX_EXTENT)) {
                    SizedChild(
                        0,
                        SwingModifier
                            .matchParentSize()
                            .absolutePadding(ABSOLUTE_LEFT, ABSOLUTE_TOP, ABSOLUTE_RIGHT, ABSOLUTE_BOTTOM),
                    )
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        ABSOLUTE_LEFT,
                        ABSOLUTE_TOP,
                        BOX_EXTENT - ABSOLUTE_LEFT - ABSOLUTE_RIGHT,
                        BOX_EXTENT - ABSOLUTE_TOP - ABSOLUTE_BOTTOM,
                    ),
                ),
                childBounds(),
                "an absolute padding must reserve its four sides the same way a padding does while the " +
                    "orientation is left to right",
            )
        }

    @Test
    fun insufficientSpaceAvailable() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(BOX_EXTENT, BOX_EXTENT)) {
                    SizedChild(0, SwingModifier.matchParentSize().padding(OVERSIZE_PADDING))
                }
            }

            assertEquals(
                listOf(Rectangle(OVERSIZE_PADDING, OVERSIZE_PADDING, 0, 0)),
                childBounds(),
                "a padding wider than the space it is given must still be reserved: the child is left nothing " +
                    "to occupy and is placed past the leading padding all the same",
            )
        }

    @Test
    fun intrinsicMeasurements() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Child(0, 0, 0, SwingModifier.padding(RATIO_PADDING).aspectRatio(2f))
                }
            }

            assertEquals(
                Dimension(2 * RATIO_PADDING, 2 * RATIO_PADDING),
                containerPreferredSize(),
                "a padding measured under an unbounded extent must leave that extent unbounded, so the ratio " +
                    "inside it finds no extent to take a size from and the padding alone is asked for",
            )
        }

    @Test
    fun aChainIsMeasuredOutermostFirst() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CHAIN_EXTENT, CHAIN_EXTENT)) {
                    Child(0, CHAIN_EXTENT, CHAIN_EXTENT, SwingModifier.padding(CHAIN_PADDING).aspectRatio(2f))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(
                        CHAIN_PADDING,
                        CHAIN_PADDING,
                        CHAIN_EXTENT - 2 * CHAIN_PADDING,
                        (CHAIN_EXTENT - 2 * CHAIN_PADDING) / 2,
                    ),
                ),
                childBounds(),
                "the padding declared first must narrow the box's offer before the ratio takes a size from " +
                    "it, which halves what the padding left rather than what the box offered",
            )
        }

    @Test
    fun testPadding_rtl() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(ROW_WIDTH, SIZE, ComponentOrientation.RIGHT_TO_LEFT)) {
                    Child(0, SIZE, SIZE, SwingModifier.padding(PADDING_1, 0, PADDING_2, 0))
                    Child(1, SIZE, SIZE, SwingModifier.padding(0, 0, PADDING_3, 0))
                    Child(2, SIZE, SIZE, SwingModifier.padding(PADDING_1, 0, 0, 0))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(ROW_WIDTH - PADDING_1 - SIZE, 0, SIZE, SIZE),
                    Rectangle(ROW_WIDTH - PADDING_1 - PADDING_2 - 2 * SIZE, 0, SIZE, SIZE),
                    Rectangle(ROW_WIDTH - PADDING_1 * 2 - PADDING_2 - PADDING_3 - 3 * SIZE, 0, SIZE, SIZE),
                ),
                childBounds(),
                "under a right-to-left orientation a padding's start must be reserved to the right of the " +
                    "child and its end to the left, the two having swapped edges",
            )
        }

    @Test
    fun testAbsolutePadding_rtl() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(ROW_WIDTH, SIZE, ComponentOrientation.RIGHT_TO_LEFT)) {
                    Child(0, SIZE, SIZE, SwingModifier.absolutePadding(PADDING_1, 0, PADDING_2, 0))
                    Child(1, SIZE, SIZE, SwingModifier.absolutePadding(0, 0, PADDING_3, 0))
                }
            }

            assertEquals(
                listOf(
                    Rectangle(ROW_WIDTH - PADDING_2 - SIZE, 0, SIZE, SIZE),
                    Rectangle(ROW_WIDTH - PADDING_1 - PADDING_2 - PADDING_3 - 2 * SIZE, 0, SIZE, SIZE),
                ),
                childBounds(),
                "under a right-to-left orientation an absolute padding must keep its left to the left of the " +
                    "child and its right to the right, where a padding would have swapped them",
            )
        }

    @Test
    fun testPaddingValuesRtl() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(TIGHT_EXTENT, TIGHT_EXTENT, ComponentOrientation.RIGHT_TO_LEFT)) {
                    SizedChild(0, SwingModifier.matchParentSize().padding(TIGHT_START, 0, TIGHT_END, 0))
                }
            }

            assertEquals(
                listOf(Rectangle(TIGHT_END, 0, TIGHT_EXTENT - TIGHT_START - TIGHT_END, TIGHT_EXTENT)),
                childBounds(),
                "a box with space for its child and the padding either side of it must measure the child into " +
                    "what is left, under a right-to-left orientation as under any other",
            )
        }

    @Test
    fun testInspectableParameter() {
        val declared =
            with(BoxScopeInstance) {
                SwingModifier.padding(INSPECTED_START, INSPECTED_TOP, INSPECTED_END, INSPECTED_BOTTOM)
            }

        assertEquals("padding", declared.lastElement().name, "padding must report itself under its own name")
        assertEquals(
            mapOf(
                "start" to INSPECTED_START,
                "top" to INSPECTED_TOP,
                "end" to INSPECTED_END,
                "bottom" to INSPECTED_BOTTOM,
            ),
            declared.lastElement().declaredValues,
            "and must report the four sides it was declared with, each under the edge it reserves",
        )
    }

    @Test
    fun testInspectableParameterForAbsolute() {
        val declared =
            with(BoxScopeInstance) {
                SwingModifier.absolutePadding(INSPECTED_START, INSPECTED_TOP, INSPECTED_END, INSPECTED_BOTTOM)
            }

        assertEquals(
            "absolutePadding",
            declared.lastElement().name,
            "absolutePadding must report itself under its own name",
        )
        assertEquals(
            mapOf(
                "left" to INSPECTED_START,
                "top" to INSPECTED_TOP,
                "right" to INSPECTED_END,
                "bottom" to INSPECTED_BOTTOM,
            ),
            declared.lastElement().declaredValues,
            "and must name its sides left and right, the edges it reserves whatever the orientation",
        )
    }

    private companion object {
        /** The extent a box is given where it has space for both the child and the padding around it. */
        const val BOX_EXTENT = 50

        /** The space a padding declared for every edge at once reserves. */
        const val UNIFORM_PADDING = 10

        /** The space a padding declared for a pair of edges at once reserves, one value per axis. */
        const val HORIZONTAL_PADDING = 10
        const val VERTICAL_PADDING = 20

        /** A box for the chain whose two elements reserve and then take a size from what is left. */
        const val CHAIN_EXTENT = 100
        const val CHAIN_PADDING = 20

        /** The four sides a padding reserves where each must be told apart from the other three. */
        const val INSET_START = 1
        const val INSET_TOP = 3
        const val INSET_END = 6
        const val INSET_BOTTOM = 10

        /** The same for an absolute padding, whose sides are named for the edges they never leave. */
        const val ABSOLUTE_LEFT = 10
        const val ABSOLUTE_TOP = 15
        const val ABSOLUTE_RIGHT = 20
        const val ABSOLUTE_BOTTOM = 30

        /** A padding reserving more than [BOX_EXTENT] holds, so the child is left nothing to occupy. */
        const val OVERSIZE_PADDING = 30

        /** The padding around the child whose ratio reads the unbounded extent the padding narrowed. */
        const val RATIO_PADDING = 100

        /** The extent each child of the right-to-left row asks for on either axis. */
        const val SIZE = 100

        /** A row wide enough to hold all three padded children with space to spare after them. */
        const val ROW_WIDTH = 400

        /** The three paddings the right-to-left cases reserve, each a different width. */
        const val PADDING_1 = 5
        const val PADDING_2 = 10
        const val PADDING_3 = 15

        /** A box holding exactly its child and the padding either side of it, and those two paddings. */
        const val TIGHT_EXTENT = 10
        const val TIGHT_START = 1
        const val TIGHT_END = 2

        /** The sides a padding is declared with where only what it reports about itself is read. */
        const val INSPECTED_START = 10
        const val INSPECTED_TOP = 20
        const val INSPECTED_END = 30
        const val INSPECTED_BOTTOM = 40
    }
}

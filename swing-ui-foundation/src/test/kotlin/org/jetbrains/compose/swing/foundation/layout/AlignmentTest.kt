package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coverage for the entry points [Alignment] offers beyond its fifteen named constants: [BiasAlignment]
 * built with an arbitrary bias, [Alignment.Horizontal] and [Alignment.Vertical] composed with `plus`,
 * [AbsoluteAlignment] ignoring orientation, and a caller's own [Alignment] implementation.
 */
class AlignmentTest {
    @Test
    fun anArbitraryBiasPlacesAChildWhereTheArithmeticSays() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT),
                    contentAlignment = BiasAlignment(0.6f, -0.6f),
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(120, 22, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "a bias of 0.6 across a leftover width of 150 must place the child 80% of the way through it, " +
                    "and -0.6 across a leftover height of 110 must place it 20% of the way through that",
            )
        }

    @Test
    fun plusOnAHorizontalComposesWithAVerticalIntoTheEquivalentNamedAlignment() {
        assertEquals(
            Alignment.BottomStart,
            Alignment.Start + Alignment.Bottom,
            "Alignment.Start + Alignment.Bottom must equal Alignment.BottomStart, not merely place a child " +
                "the same way, so a Box re-declared with it is not re-laid-out for no reason",
        )
    }

    @Test
    fun plusOnAVerticalComposesWithAHorizontalIntoTheEquivalentNamedAlignment() {
        assertEquals(
            Alignment.BottomStart,
            Alignment.Bottom + Alignment.Start,
            "Alignment.Bottom + Alignment.Start must equal Alignment.BottomStart too, the same as the reverse",
        )
    }

    @Test
    fun biasAlignmentsHaveComposeValueSemantics() {
        assertEquals(BiasAlignment(0.25f, -0.5f), BiasAlignment(0.25f, -0.5f))
        assertEquals(BiasAlignment.Horizontal(0.25f), BiasAlignment.Horizontal(0.25f))
        assertEquals(BiasAlignment.Vertical(-0.5f), BiasAlignment.Vertical(-0.5f))
    }

    @Test
    fun absoluteBiasAlignmentIgnoresComponentOrientation() {
        val size = Dimension(CHILD_WIDTH, CHILD_HEIGHT)
        val space = Dimension(CONTAINER_WIDTH, CONTAINER_HEIGHT)
        val expected = Point(CONTAINER_WIDTH - CHILD_WIDTH, CONTAINER_HEIGHT - CHILD_HEIGHT)

        assertEquals(
            expected,
            BiasAbsoluteAlignment(1f, 1f).align(size, space, ComponentOrientation.LEFT_TO_RIGHT),
        )
        assertEquals(
            expected,
            BiasAbsoluteAlignment(1f, 1f).align(size, space, ComponentOrientation.RIGHT_TO_LEFT),
        )
        assertEquals(
            BiasAbsoluteAlignment(1f, 1f),
            Alignment.Bottom + BiasAbsoluteAlignment.Horizontal(1f),
        )
    }

    @Test
    fun plusComposesCallerWrittenAxisAlignmentsInEitherOrder() {
        val horizontal =
            Alignment.Horizontal { size, space, orientation ->
                if (orientation.isLeftToRight) 7 else space - size - 7
            }
        val vertical = Alignment.Vertical { _, _ -> 11 }
        val child = Dimension(CHILD_WIDTH, CHILD_HEIGHT)
        val container = Dimension(CONTAINER_WIDTH, CONTAINER_HEIGHT)
        val expected = Point(CONTAINER_WIDTH - CHILD_WIDTH - 7, 11)

        assertEquals(
            expected,
            (horizontal + vertical).align(child, container, ComponentOrientation.RIGHT_TO_LEFT),
            "a caller-written horizontal plus vertical alignment must preserve both axis implementations",
        )
        assertEquals(
            expected,
            (vertical + horizontal).align(child, container, ComponentOrientation.RIGHT_TO_LEFT),
            "composing the same caller-written axes in reverse order must produce the same placement",
        )
    }

    @Test
    fun plusOnAHorizontalComposesWithAVerticalIntoAnAlignmentThatPlacesAChildTheSameWay() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT),
                    contentAlignment = Alignment.Start + Alignment.Bottom,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(0, CONTAINER_HEIGHT - CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "Alignment.Start + Alignment.Bottom must place a child exactly where Alignment.BottomStart does",
            )
        }

    @Test
    fun anAbsoluteAlignmentPlacesAChildAtTheRightUnderALeftToRightOrientation() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT),
                    contentAlignment = AbsoluteAlignment.TopRight,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(CONTAINER_WIDTH - CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "AbsoluteAlignment.TopRight must put the child at the right under a left-to-right orientation",
            )
        }

    @Test
    fun anAbsoluteAlignmentPlacesAChildAtTheSameRightUnderARightToLeftOrientation() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT, ComponentOrientation.RIGHT_TO_LEFT),
                    contentAlignment = AbsoluteAlignment.TopRight,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(CONTAINER_WIDTH - CHILD_WIDTH, 0, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "AbsoluteAlignment.TopRight must stay at that same right under a right-to-left orientation too, " +
                    "unlike Alignment.TopEnd which would mirror to the left",
            )
        }

    @Test
    fun aCallerWrittenAlignmentIsHonoredByBox() =
        runComposeSwingTest {
            val placeAtAFixedOffset = Alignment { _, _, _ -> Point(20, 15) }

            setContent {
                Box(
                    modifier = containerModifier(CONTAINER_WIDTH, CONTAINER_HEIGHT),
                    contentAlignment = placeAtAFixedOffset,
                ) {
                    SizedChild(0)
                }
            }

            assertEquals(
                listOf(Rectangle(20, 15, CHILD_WIDTH, CHILD_HEIGHT)),
                childBounds(),
                "a plain lambda satisfies Alignment as a fun interface, and Box must place a child by whatever " +
                    "offset it returns",
            )
        }

    private companion object {
        /** The extent a fixture container is given, wide and tall enough to leave space for a bias to place a child. */
        const val CONTAINER_WIDTH = 200
        const val CONTAINER_HEIGHT = 150
    }
}

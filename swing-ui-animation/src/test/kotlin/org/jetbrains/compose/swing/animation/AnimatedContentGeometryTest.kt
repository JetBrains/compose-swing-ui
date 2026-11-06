package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope.SlideDirection
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.ComponentOrientation
import java.awt.Container
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where content sliding in from a container's own edge starts, for each direction crossed with the
 * leading, center and trailing alignment the container places content by.
 *
 * A container measures 200x100 over the content it is left and 40x20 over the content arriving into it,
 * so each alignment leaves the arriving content a different distance from the edge it comes in over. The
 * offset is read off that content's own placement inside the panel holding it, which is where a slide
 * puts it: the panel stands at the content's own size, so the alignment inside it offsets nothing and
 * what is left is the slide.
 */
class AnimatedContentGeometryTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("slideDirections")
    fun `content starts a full slide off the opposite edge`(case: DirectionCase) =
        runComposeSwingTest {
            assertEquals(
                case.expected,
                slideIn(case.alignments.map { SlideCase(case.direction, alignment = it) }),
                case.message,
            )
        }

    companion object {
        @JvmStatic
        fun slideDirections(): List<Named<DirectionCase>> =
            listOf(
                DirectionCase(
                    SlideDirection.Left,
                    HorizontalAlignments,
                    listOf(Point(200, 0), Point(120, 0), Point(40, 0)),
                    "content coming in over the right edge did not start the width the alignment leaves it",
                ),
                DirectionCase(
                    SlideDirection.Right,
                    HorizontalAlignments,
                    listOf(Point(-40, 0), Point(-120, 0), Point(-200, 0)),
                    "content coming in over the left edge did not start the width the alignment leaves it",
                ),
                DirectionCase(
                    SlideDirection.Up,
                    VerticalAlignments,
                    listOf(Point(0, 100), Point(0, 60), Point(0, 20)),
                    "content coming in over the bottom edge did not start the height the alignment leaves it",
                ),
                DirectionCase(
                    SlideDirection.Down,
                    VerticalAlignments,
                    listOf(Point(0, -20), Point(0, -60), Point(0, -100)),
                    "content coming in over the top edge did not start the height the alignment leaves it",
                ),
            ).map { Named.of(it.direction.toString(), it) }
    }

    @Test
    fun `a right-to-left container sends the leading slide the way a left-to-right one sends the trailing`() =
        runComposeSwingTest {
            val (leadingUnderRightToLeft, trailingUnderLeftToRight) =
                slideIn(
                    listOf(
                        SlideCase(SlideDirection.Start, orientation = ComponentOrientation.RIGHT_TO_LEFT),
                        SlideCase(SlideDirection.End, orientation = ComponentOrientation.LEFT_TO_RIGHT),
                    ),
                )
            assertEquals(
                trailingUnderLeftToRight,
                leadingUnderRightToLeft,
                "a right-to-left container sent the leading slide a different distance than a left-to-right " +
                    "one sends the trailing slide, which is the placement mirrored twice",
            )
            assertEquals(
                Point(-40, 0),
                leadingUnderRightToLeft,
                "the leading slide did not start the content its own width short of where it rests",
            )
        }

    /**
     * Where the content arriving in each of [cases] starts.
     *
     * One container per case is composed side by side, all driven by the same state, so a single
     * transition drives the whole crossing.
     */
    private fun ComposeSwingTest.slideIn(cases: List<SlideCase>): List<Point> {
        var state by mutableStateOf("a")
        setContent {
            for (case in cases) {
                AnimatedContent(
                    targetState = state,
                    modifier = SwingModifier.containerOrientation(case.orientation),
                    transitionSpec = {
                        slideIntoContainer(case.towards, animationSpec = HeldOffset) togetherWith
                            ExitTransition.None
                    },
                    contentAlignment = case.alignment,
                ) {
                    Body(state = it)
                }
            }
        }

        mainClock.autoAdvance = false
        state = "b"
        // The arriving content is measured one pass after it is mounted and the slide is set up from that
        // measurement; the spec holds its initial offset far longer than the frames that takes.
        repeat(3) { mainClock.advanceTimeByFrame() }
        // The slide is where the content stands past the place the alignment gives it in the container.
        return cases.mapIndexed { index, case ->
            val container = root.getComponent(index) as Container
            val content = onRoot().onChildAt(index).onChildAt(0).fetch<Container>()
            val aligned = case.alignment.align(content.size, container.size, ComponentOrientation.LEFT_TO_RIGHT)
            Point(content.x - aligned.x, content.y - aligned.y)
        }
    }
}

class DirectionCase(
    val direction: SlideDirection,
    val alignments: List<Alignment>,
    val expected: List<Point>,
    val message: String,
)

/** One container of the crossing: which way its content slides in, and what it is placed by. */
private class SlideCase(
    val towards: SlideDirection,
    val alignment: Alignment = Alignment.TopStart,
    val orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
)

/** The content of each state, sized so that the container is larger than what arrives into it. */
@Composable
private fun Body(state: String) {
    val width = if (state == "a") 200 else 40
    val height = if (state == "a") 100 else 20
    Label(text = "", style = { preferredSize(width = width, height = height) })
}

/**
 * A slide that holds its initial offset for the whole test: the delay outlasts the frames the test
 * sends, so what is read back is the offset the slide was set up with rather than a sample of a curve,
 * and the expected value is a number a reader can check against the container's own arithmetic.
 */
private val HeldOffset: FiniteAnimationSpec<Point> = tween(durationMillis = 320, delayMillis = 320)

/** The leading, center and trailing placement across a container's width, each held at its top. */
private val HorizontalAlignments = listOf(Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd)

/** The leading, center and trailing placement down a container's height, each held at its leading edge. */
private val VerticalAlignments = listOf(Alignment.TopStart, Alignment.CenterStart, Alignment.BottomStart)

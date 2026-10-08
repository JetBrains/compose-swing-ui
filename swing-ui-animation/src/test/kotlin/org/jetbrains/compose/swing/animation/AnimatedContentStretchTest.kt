package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope.SlideDirection
import org.jetbrains.compose.swing.animation.Stretch.NARROW
import org.jetbrains.compose.swing.animation.Stretch.ParentCase
import org.jetbrains.compose.swing.animation.Stretch.ResizeCase
import org.jetbrains.compose.swing.animation.Stretch.TransformCase
import org.jetbrains.compose.swing.animation.Stretch.recordEntering
import org.jetbrains.compose.swing.animation.Stretch.replacing
import org.jetbrains.compose.swing.animation.Stretch.resizedFrames
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Point
import kotlin.test.assertEquals

/** How an [AnimatedContent] directly under a stock parent lays its content out while its content changes. */
internal class AnimatedContentStretchTest {
    /** Unscoped content is measured within the container's size on every frame of a size change. */
    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("stockParentsAndTransforms")
    fun `replacement content is measured within the container on every frame under each stock parent protocol`(
        parent: ParentCase,
        transform: TransformCase,
    ) {
        val entering = recordEntering(parent.parent, replacing(transform.transform))

        assertContentFitsContainer(entering.frames)
        assertEquals(emptyList(), entering.taken.shrinkages(), "the content shrank while the container grew")
        assertEquals(parent.settled, entering.settled)
        assertLayoutsSettle(entering.frames)
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("parentsAssigningRoomFromFirstFrameAndTransforms")
    fun `a stock parent that assigns its room from the first frame leaves replacement content one size`(
        parent: ParentCase,
        transform: TransformCase,
    ) {
        val entering = recordEntering(parent.parent, replacing(transform.transform))

        assertEquals(listOf(parent.settled), entering.taken)
    }

    /**
     * Content resized part-way through a change is measured within the container on every frame, so it holds the ratio
     * at the room the container has, and takes the new width once the container has grown to it.
     */
    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("resizesAndTransforms")
    fun `under a stock parent that grants the preferred size resized content is measured within the container`(
        resize: ResizeCase,
        transform: TransformCase,
    ) {
        val resized = resizedFrames(resize, { content -> replacing(transform.transform, content) })

        assertContentFitsContainer(resized.afterResize)
        assertEquals(resize.settled, resized.afterResize.last().content)
        assertLayoutsSettle(resized.all)
    }

    /**
     * A container that a `BorderLayout` center assigns less than the arriving content prefers slides the content it
     * leaves by the natural size of the arriving content, not by the assigned room it is measured within.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("slidesOut")
    fun `under a stock parent a slide out travels the natural size of the arriving content`(slide: SlideOutCase) =
        runComposeSwingTest {
            val assigned = Dimension(80, 60)
            var state by mutableStateOf("a")
            setContent {
                Panel(PanelLayout.Flow()) {
                    Panel(
                        PanelLayout.Border(),
                        modifier = SwingModifier.preferredSize(assigned.width, assigned.height),
                    ) {
                        AnimatedContent(
                            targetState = state,
                            modifier = SwingModifier.center().componentOrientation(slide.orientation),
                            contentAlignment = slide.alignment,
                            transitionSpec = {
                                fadeIn(tween(800)) togetherWith
                                    (
                                        slideOutOfContainer(slide.direction, tween(160)) +
                                            ExitTransition.KeepUntilTransitionsFinished
                                    )
                            },
                        ) {
                            val preferred = if (it == "a") Dimension(40, 20) else Dimension(NARROW, ARRIVING_HEIGHT)
                            Label(it, modifier = SwingModifier.preferredSize(preferred.width, preferred.height))
                        }
                    }
                }
            }
            mainClock.autoAdvance = false
            state = "b"
            // The exit ends within these frames and the enter outlasts them.
            repeat(20) { driveOneFrame() }

            val leaving = onNodeWithText("a").fetch().parent
            val arriving = onNodeWithText("b").fetch().parent
            assertEquals(assigned, arriving.size, "the arriving content was not measured within the assigned room")
            val aligned = slide.alignment.align(leaving.size, arriving.size, ComponentOrientation.LEFT_TO_RIGHT)
            assertEquals(
                slide.offset,
                Point(leaving.x - arriving.x - aligned.x, leaving.y - arriving.y - aligned.y),
                "how far the content being left slid from where its alignment puts it",
            )
        }

    /** A slide out in [direction] of content aligned by [alignment] in a container of [orientation], by [offset]. */
    class SlideOutCase(
        val name: String,
        val direction: SlideDirection,
        val orientation: ComponentOrientation,
        val alignment: Alignment,
        val offset: Point,
    ) {
        override fun toString(): String = name
    }

    private companion object {
        const val ARRIVING_HEIGHT = 100

        @JvmStatic
        fun stockParentsAndTransforms(): List<Arguments> = withTransforms(Stretch.StockParents)

        @JvmStatic
        fun parentsAssigningRoomFromFirstFrameAndTransforms(): List<Arguments> =
            withTransforms(Stretch.StockParents.filter { it.assignsRoomFromFirstFrame })

        @JvmStatic
        fun resizesAndTransforms(): List<Arguments> =
            Stretch.ResizeCases.flatMap { resize ->
                Stretch.Transforms.map { transform -> Arguments.of(resize, transform) }
            }

        @JvmStatic
        fun slidesOut(): List<SlideOutCase> =
            listOf(
                SlideOutCase(
                    "right",
                    SlideDirection.Right,
                    ComponentOrientation.LEFT_TO_RIGHT,
                    Alignment.TopStart,
                    Point(NARROW, 0),
                ),
                SlideOutCase(
                    "trailing edge",
                    SlideDirection.End,
                    ComponentOrientation.LEFT_TO_RIGHT,
                    Alignment.TopStart,
                    Point(NARROW, 0),
                ),
                SlideOutCase(
                    "leading edge, right to left",
                    SlideDirection.Start,
                    ComponentOrientation.RIGHT_TO_LEFT,
                    Alignment.TopStart,
                    Point(NARROW, 0),
                ),
                SlideOutCase(
                    "bottom",
                    SlideDirection.Down,
                    ComponentOrientation.LEFT_TO_RIGHT,
                    Alignment.TopStart,
                    Point(0, ARRIVING_HEIGHT),
                ),
                SlideOutCase(
                    "left from the trailing edge",
                    SlideDirection.Left,
                    ComponentOrientation.LEFT_TO_RIGHT,
                    Alignment.TopEnd,
                    Point(-NARROW, 0),
                ),
            )

        private fun withTransforms(parents: List<ParentCase>): List<Arguments> =
            parents.flatMap { parent -> Stretch.Transforms.map { transform -> Arguments.of(parent, transform) } }
    }
}

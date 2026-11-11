// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope.SlideDirection
import org.jetbrains.compose.swing.animation.Stretch.NARROW
import org.jetbrains.compose.swing.animation.Stretch.ParentCase
import org.jetbrains.compose.swing.animation.Stretch.ResizeCase
import org.jetbrains.compose.swing.animation.Stretch.TRANSITION_MILLIS
import org.jetbrains.compose.swing.animation.Stretch.WideContent
import org.jetbrains.compose.swing.animation.Stretch.recordEntering
import org.jetbrains.compose.swing.animation.Stretch.resizedFrames
import org.jetbrains.compose.swing.animation.core.keyframes
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.componentOrientation
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Point
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How an [AnimatedContent] directly under a stock parent lays its content out while its content changes. */
internal class AnimatedContentStretchTest {
    /** Unscoped content under a parent that grants its room is measured within the container's size on every frame. */
    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("stretchingParentsAndTransforms")
    fun `replacement content is measured within the container on every frame under each stretching stock parent`(
        parent: ParentCase,
        transform: TransformCase,
    ) {
        val entering = recordEntering(parent.parent, replacing(transform.transform))

        assertContentFitsContainer(entering.frames)
        assertEquals(emptyList(), entering.taken.shrinkages(), "the content shrank while the container grew")
        assertEquals(parent.settled, entering.settled)
        assertLayoutsSettle(entering.frames)
    }

    /**
     * Under a parent that sizes the container to the size it requests, the container is part-way to the size the
     * content settles at while the content already has that size, and the container clips it. Where the container is
     * held at the width the slot sets, the content takes its rest width while the hold lasts.
     */
    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("followingParentsAndTransforms")
    fun `replacement content keeps its settled size on every frame under each following stock parent`(
        parent: ParentCase,
        transform: TransformCase,
    ) {
        val entering = recordEntering(parent.parent, replacing(transform.transform))

        val expected =
            if (transform.heldAtSlot) {
                listOf(parent.settled, Dimension(320, 180), parent.settled)
            } else {
                listOf(parent.settled)
            }
        assertEquals(expected, entering.taken, "the sizes the content took")
        val firstContainer = entering.frames.first().container
        assertTrue(
            firstContainer.height < parent.settled.height,
            "the container was not part-way to the size the content settles at",
        )
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
     * Content resized part-way through a change keeps the size it rests at under a parent that grants the preferred
     * size: it never takes less than the size it settles at, takes more only to the room the container has, and takes
     * the new size at the end, while the container is part-way on the first frame.
     */
    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("resizesAndTransforms")
    fun `under a stock parent that grants the preferred size resized content keeps the size it rests at`(
        resize: ResizeCase,
        transform: TransformCase,
    ) {
        val resized = resizedFrames(resize, { content -> replacing(transform.transform, content) })
        val frames = resized.afterResize

        assertEquals(resize.settled, frames.first().content, "the content on the first frame after the resize")
        val firstContainer = frames.first().container
        assertTrue(
            firstContainer.width < resize.settled.width,
            "the container was not part-way to the size the content settles at",
        )
        assertEquals(
            emptyList(),
            frames
                .filter {
                    it.content.width < resize.settled.width ||
                        it.content.height < resize.settled.height ||
                        it.content.width > maxOf(it.container.width, resize.settled.width) ||
                        it.content.height > maxOf(it.container.height, resize.settled.height)
                }.map { it.content },
            "the content that took less than the size it rests at, or more than the room the container had",
        )
        assertEquals(resize.settled, frames.last().content)
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
                        style = { preferredSize(assigned.width, assigned.height) },
                    ) {
                        AnimatedContent(
                            targetState = state,
                            modifier = SwingModifier.center().styleable { componentOrientation(slide.orientation) },
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
                            Label(it, style = { preferredSize(preferred.width, preferred.height) })
                        }
                    }
                }
            }
            mainClock.autoAdvance = false
            state = "b"
            // The exit ends within these frames and the enter outlasts them.
            repeat(20) { mainClock.advanceTimeByFrame() }

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

    /** A change of [AnimatedContent]'s content. */
    class TransformCase(
        val name: String,
        val transform: ContentTransform,
        val heldAtSlot: Boolean = false,
    ) {
        override fun toString(): String = name
    }

    private companion object {
        const val ARRIVING_HEIGHT = 100

        val Transforms: List<TransformCase> =
            listOf(
                TransformCase("fade", fadeIn(tween(TRANSITION_MILLIS)) togetherWith fadeOut(tween(TRANSITION_MILLIS))),
                TransformCase(
                    "expand and shrink",
                    expandIn(tween(TRANSITION_MILLIS)) togetherWith shrinkOut(tween(TRANSITION_MILLIS)),
                ),
            )

        @JvmStatic
        fun stretchingParentsAndTransforms(): List<Arguments> = withTransforms(Stretch.StretchingParents)

        @JvmStatic
        fun followingParentsAndTransforms(): List<Arguments> =
            withTransforms(Stretch.FollowingParents + Stretch.NorthSlot) +
                Arguments.of(
                    Stretch.NorthSlot,
                    TransformCase(
                        "width held at the slot's while the height changes",
                        ContentTransform(
                            fadeIn(tween(TRANSITION_MILLIS)),
                            fadeOut(tween(TRANSITION_MILLIS)),
                            sizeTransform =
                                SizeTransform { initial, target ->
                                    keyframes {
                                        durationMillis = TRANSITION_MILLIS
                                        Dimension(NARROW, initial.height) at TRANSITION_MILLIS / 4
                                        Dimension(NARROW, target.height) at TRANSITION_MILLIS * 3 / 4
                                    }
                                },
                        ),
                        heldAtSlot = true,
                    ),
                )

        @JvmStatic
        fun parentsAssigningRoomFromFirstFrameAndTransforms(): List<Arguments> =
            withTransforms(Stretch.StockParents.filter { it.assignsRoomFromFirstFrame })

        @JvmStatic
        fun resizesAndTransforms(): List<Arguments> =
            Stretch.ResizeCases.flatMap { resize ->
                Transforms.map { transform -> Arguments.of(resize, transform) }
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
            parents.flatMap { parent -> Transforms.map { transform -> Arguments.of(parent, transform) } }
    }
}

/** An [AnimatedContent] that replaces a short label with [content] by [transform]. */
private fun replacing(
    transform: ContentTransform,
    content: TaggedContent = WideContent,
): EnteringContainer =
    { modifier, shown, contentModifier ->
        AnimatedContent(shown, modifier, transitionSpec = { transform }) { wide ->
            if (wide) {
                content(contentModifier)
            } else {
                Label("Other", style = { preferredSize(NARROW / 4, 20) })
            }
        }
    }

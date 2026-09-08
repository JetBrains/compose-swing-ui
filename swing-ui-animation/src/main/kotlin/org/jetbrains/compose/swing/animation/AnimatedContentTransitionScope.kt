/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.compose.swing.animation

import androidx.collection.MutableScatterMap
import androidx.collection.mutableScatterMapOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.spring
import org.jetbrains.compose.swing.foundation.layout.Alignment
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Point

// Derived from androidx.compose.animation.AnimatedContentTransitionScope and its implementation, with
// java.awt.Point standing in for IntOffset.

/**
 * The receiver of an [AnimatedContent]'s `transitionSpec`: a segment of the container's transition, plus
 * slides into and out of the container's edges, which need the container's size.
 *
 * ```
 * AnimatedContent(
 *     targetState = page,
 *     transitionSpec = {
 *         slideIntoContainer(SlideDirection.Left) togetherWith slideOutOfContainer(SlideDirection.Left)
 *     },
 * ) { Page(it) }
 * ```
 */
public sealed interface AnimatedContentTransitionScope<S> : Transition.Segment<S> {
    /**
     * Replaces the [SizeTransform] of this content transform. `using null` turns off the size animation.
     *
     * @param sizeTransform how the container's size animates, or `null` for no size animation.
     * @return this same content transform.
     */
    public infix fun ContentTransform.using(sizeTransform: SizeTransform?): ContentTransform

    /**
     * Which way content travels in [slideIntoContainer] and out in [slideOutOfContainer].
     *
     * [Start] and [End] are the leading and trailing edges, resolved against the reading order the
     * container was last placed under while settled. The other four are fixed edges.
     */
    @Immutable
    @JvmInline
    public value class SlideDirection private constructor(
        private val value: Int,
    ) {
        override fun toString(): String =
            when (this) {
                Left -> "Left"
                Right -> "Right"
                Up -> "Up"
                Down -> "Down"
                Start -> "Start"
                End -> "End"
                else -> "Invalid"
            }

        /** The six directions content can travel. */
        public companion object {
            /** Towards the left edge, whatever the reading order. */
            @JvmStatic
            public val Left: SlideDirection = SlideDirection(0)

            /** Towards the right edge, whatever the reading order. */
            @JvmStatic
            public val Right: SlideDirection = SlideDirection(1)

            /** Towards the top edge. */
            @JvmStatic
            public val Up: SlideDirection = SlideDirection(2)

            /** Towards the bottom edge. */
            @JvmStatic
            public val Down: SlideDirection = SlideDirection(3)

            /** Towards the leading edge: the left under a left-to-right reading order. */
            @JvmStatic
            public val Start: SlideDirection = SlideDirection(4)

            /** Towards the trailing edge: the right under a left-to-right reading order. */
            @JvmStatic
            public val End: SlideDirection = SlideDirection(5)
        }
    }

    /**
     * Slides the arriving content in from the container's edge, traveling [towards].
     *
     * The full slide is computed from the container's current size and [contentAlignment] and passed to
     * [initialOffset]. [slideInHorizontally] and [slideInVertically] cannot express this, since they know
     * only the content's size.
     *
     * [towards] is resolved against the reading order when the animation is set up, so a
     * `ComponentOrientation` change during the transition does not redirect a running slide.
     *
     * @param towards which way the content travels.
     * @param animationSpec how the offset travels, a medium stiffness spring by default.
     * @param initialOffset the offset the content starts at, given the full slide; the full slide
     *     itself by default.
     * @return the enter transition.
     */
    public fun slideIntoContainer(
        towards: SlideDirection,
        animationSpec: FiniteAnimationSpec<Point> = spring(visibilityThreshold = pointVisibilityThreshold()),
        initialOffset: (offsetForFullSlide: Int) -> Int = { it },
    ): EnterTransition

    /**
     * Slides the old content out to the container's edge, traveling [towards].
     *
     * The full slide is computed from the arriving content's size and [contentAlignment] and passed to
     * [targetOffset]; see [slideIntoContainer].
     *
     * @param towards which way the content travels.
     * @param animationSpec how the offset travels, a medium stiffness spring by default.
     * @param targetOffset the offset the content ends at, given the full slide; the full slide
     *     itself by default.
     * @return the exit transition.
     */
    public fun slideOutOfContainer(
        towards: SlideDirection,
        animationSpec: FiniteAnimationSpec<Point> = spring(visibilityThreshold = pointVisibilityThreshold()),
        targetOffset: (offsetForFullSlide: Int) -> Int = { it },
    ): ExitTransition

    /**
     * An exit transition that keeps the old content composed until the whole transition has finished,
     * instead of removing it when its own exit ends.
     *
     * It animates nothing, so combine it with an exit:
     * `slideOutOfContainer(SlideDirection.Left) + KeepUntilTransitionsFinished`, in either order.
     *
     * Give both contents a [ContentTransform.targetContentZIndex] when using it: content interrupted on
     * its way in and sent back out is held over the content arriving after it, and only the z-index
     * decides which paints on top.
     */
    @Suppress("VariableNaming") // Named as the constant it is, the way ExitTransition.None is.
    public val ExitTransition.Companion.KeepUntilTransitionsFinished: ExitTransition
        get() = HoldingExit

    /** Where the container places content inside itself; the [AnimatedContent]'s own parameter. */
    public val contentAlignment: Alignment
}

/**
 * The one [AnimatedContentTransitionScope], which is also what an [AnimatedContent] keeps its per-content
 * bookkeeping in.
 *
 * @property transition the container's transition, whose segment this scope reports.
 * @param contentAlignment where the container places content inside itself, until a settled composition hands another.
 */
internal class AnimatedContentTransitionScopeImpl<S>(
    val transition: Transition<S>,
    contentAlignment: Alignment,
) : AnimatedContentTransitionScope<S> {
    // Snapshot state, so the measure pass reading it measures again when a settled container is handed another.
    override var contentAlignment: Alignment by mutableStateOf(contentAlignment)

    /** The reading order the container was last placed under while settled, which `Start` and `End` resolve by. */
    var isLeftToRight: Boolean = true

    override val initialState: S
        get() = transition.segment.initialState

    override val targetState: S
        get() = transition.segment.targetState

    override infix fun ContentTransform.using(sizeTransform: SizeTransform?): ContentTransform =
        apply { this.sizeTransform = sizeTransform }

    override fun slideIntoContainer(
        towards: AnimatedContentTransitionScope.SlideDirection,
        animationSpec: FiniteAnimationSpec<Point>,
        initialOffset: (offsetForFullSlide: Int) -> Int,
    ): EnterTransition =
        when {
            towards.isLeft -> {
                slideInHorizontally(animationSpec) {
                    initialOffset(currentSize.width - calculateOffset(it, currentSize).x)
                }
            }

            towards.isRight -> {
                slideInHorizontally(animationSpec) {
                    initialOffset(-calculateOffset(it, currentSize).x - it)
                }
            }

            towards == AnimatedContentTransitionScope.SlideDirection.Up -> {
                slideInVertically(animationSpec) {
                    initialOffset(currentSize.height - calculateOffset(it, currentSize).y)
                }
            }

            towards == AnimatedContentTransitionScope.SlideDirection.Down -> {
                slideInVertically(animationSpec) {
                    initialOffset(-calculateOffset(it, currentSize).y - it)
                }
            }

            else -> {
                EnterTransition.None
            }
        }

    private val AnimatedContentTransitionScope.SlideDirection.isLeft: Boolean
        get() =
            this == AnimatedContentTransitionScope.SlideDirection.Left ||
                (this == AnimatedContentTransitionScope.SlideDirection.Start && isLeftToRight) ||
                (this == AnimatedContentTransitionScope.SlideDirection.End && !isLeftToRight)

    private val AnimatedContentTransitionScope.SlideDirection.isRight: Boolean
        get() =
            this == AnimatedContentTransitionScope.SlideDirection.Right ||
                (this == AnimatedContentTransitionScope.SlideDirection.Start && !isLeftToRight) ||
                (this == AnimatedContentTransitionScope.SlideDirection.End && isLeftToRight)

    /** Where content of extent [fullSize] on both axes is placed inside [currentSize]. */
    private fun calculateOffset(
        fullSize: Int,
        currentSize: Dimension,
    ): Point = contentAlignment.align(Dimension(fullSize, fullSize), currentSize, ComponentOrientation.LEFT_TO_RIGHT)

    override fun slideOutOfContainer(
        towards: AnimatedContentTransitionScope.SlideDirection,
        animationSpec: FiniteAnimationSpec<Point>,
        targetOffset: (offsetForFullSlide: Int) -> Int,
    ): ExitTransition =
        when {
            // The target size is zero for content that composes nothing.
            towards.isLeft -> {
                slideOutHorizontally(animationSpec) {
                    val targetSize = targetSizeMap[transition.targetState]?.value ?: Dimension(0, 0)
                    targetOffset(-calculateOffset(it, targetSize).x - it)
                }
            }

            towards.isRight -> {
                slideOutHorizontally(animationSpec) {
                    val targetSize = targetSizeMap[transition.targetState]?.value ?: Dimension(0, 0)
                    targetOffset(-calculateOffset(it, targetSize).x + targetSize.width)
                }
            }

            towards == AnimatedContentTransitionScope.SlideDirection.Up -> {
                slideOutVertically(animationSpec) {
                    val targetSize = targetSizeMap[transition.targetState]?.value ?: Dimension(0, 0)
                    targetOffset(-calculateOffset(it, targetSize).y - it)
                }
            }

            towards == AnimatedContentTransitionScope.SlideDirection.Down -> {
                slideOutVertically(animationSpec) {
                    val targetSize = targetSizeMap[transition.targetState]?.value ?: Dimension(0, 0)
                    targetOffset(-calculateOffset(it, targetSize).y + targetSize.height)
                }
            }

            else -> {
                ExitTransition.None
            }
        }

    /** The container's measurement over the contents it counts, written by its measure policy. */
    var measuredSize: Dimension by mutableStateOf(Dimension(0, 0))

    /** The size each content on screen measured at, registered by that content and dropped as it leaves. */
    val targetSizeMap: MutableScatterMap<S, State<Dimension>> = mutableScatterMapOf()

    /** Whether the last measure pass measured content showing the transition's target state. */
    var isTargetMeasured: Boolean = true

    /** The size animation's value while one runs, or `null`. */
    var animatedSize: State<Dimension>? = null

    /** The size the container stands at: the animated size while one runs, and its measurement otherwise. */
    private val currentSize: Dimension
        get() = animatedSize?.value ?: measuredSize
}

/**
 * A scope reporting a different change than its [delegate], so a transition spec can be resolved against a
 * change the transition has not started yet.
 *
 * The content of an [AnimatedContent]'s pending target state is composed under it: a deferred phase leaves
 * the transition settled, so the delegate's segment names one state twice, and a spec that reads both
 * states, such as a directional slide, would resolve against nothing.
 *
 * @param delegate the scope everything but the reported change is taken from.
 * @param overrideInitialState the state the reported change starts from.
 * @param overrideTargetState the state the reported change arrives at.
 */
internal class PendingAnimatedContentTransitionScope<S>(
    delegate: AnimatedContentTransitionScope<S>,
    private val overrideInitialState: S,
    private val overrideTargetState: S,
) : AnimatedContentTransitionScope<S> by delegate {
    override val initialState: S
        get() = overrideInitialState

    override val targetState: S
        get() = overrideTargetState
}

/**
 * The one transition carrying the hold flag, behind
 * [AnimatedContentTransitionScope.KeepUntilTransitionsFinished].
 */
private val HoldingExit: ExitTransition = ExitTransitionImpl(EnterExitTransitionConfig(hold = true))

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

import androidx.compose.runtime.Stable
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.Spring
import org.jetbrains.compose.swing.animation.core.spring
import java.awt.Dimension
import java.awt.Point

// Derived from androidx.compose.animation's slideIn/slideOut family: the parameter names, order and
// defaults below are upstream's, with java.awt.Point standing in for IntOffset.

/**
 * Slides the content in, from [initialOffset] to its place in the container.
 *
 * The offset is relative to where the content is laid out: a positive x starts it to the right, a
 * positive y starts it below. Content past the container's edge is clipped.
 *
 * For one axis, use [slideInHorizontally] or [slideInVertically].
 *
 * @param animationSpec how the offset travels, a medium-low stiffness spring by default.
 * @param initialOffset the offset the content starts at, given its full size.
 * @return the enter transition.
 */
@Stable
public fun slideIn(
    animationSpec: FiniteAnimationSpec<Point> = DEFAULT_SLIDE_SPRING,
    initialOffset: (fullSize: Dimension) -> Point,
): EnterTransition = EnterTransitionImpl(EnterExitTransitionConfig(slide = SlideConfig(initialOffset, animationSpec)))

/**
 * Slides the content out, from its place in the container to [targetOffset].
 *
 * The offset is relative to where the content is laid out: a positive x moves it right, a positive y
 * moves it down. Content past the container's edge is clipped.
 *
 * For one axis, use [slideOutHorizontally] or [slideOutVertically].
 *
 * @param animationSpec how the offset travels, a medium-low stiffness spring by default.
 * @param targetOffset the offset the content ends at, given its full size.
 * @return the exit transition.
 */
@Stable
public fun slideOut(
    animationSpec: FiniteAnimationSpec<Point> = DEFAULT_SLIDE_SPRING,
    targetOffset: (fullSize: Dimension) -> Point,
): ExitTransition = ExitTransitionImpl(EnterExitTransitionConfig(slide = SlideConfig(targetOffset, animationSpec)))

/**
 * Slides the content in horizontally, from [initialOffsetX] to its place in the container.
 *
 * @param animationSpec how the offset travels, a medium-low stiffness spring by default.
 * @param initialOffsetX the horizontal offset the content starts at, given its full width; half that
 *     width to the left by default.
 * @return the enter transition.
 */
@Stable
public fun slideInHorizontally(
    animationSpec: FiniteAnimationSpec<Point> = DEFAULT_SLIDE_SPRING,
    initialOffsetX: (fullWidth: Int) -> Int = { -it / 2 },
): EnterTransition = slideIn(animationSpec) { Point(initialOffsetX(it.width), 0) }

/**
 * Slides the content in vertically, from [initialOffsetY] to its place in the container.
 *
 * @param animationSpec how the offset travels, a medium-low stiffness spring by default.
 * @param initialOffsetY the vertical offset the content starts at, given its full height; half that
 *     height up by default.
 * @return the enter transition.
 */
@Stable
public fun slideInVertically(
    animationSpec: FiniteAnimationSpec<Point> = DEFAULT_SLIDE_SPRING,
    initialOffsetY: (fullHeight: Int) -> Int = { -it / 2 },
): EnterTransition = slideIn(animationSpec) { Point(0, initialOffsetY(it.height)) }

/**
 * Slides the content out horizontally, from its place in the container to [targetOffsetX].
 *
 * @param animationSpec how the offset travels, a medium-low stiffness spring by default.
 * @param targetOffsetX the horizontal offset the content ends at, given its full width; half that width
 *     to the left by default.
 * @return the exit transition.
 */
@Stable
public fun slideOutHorizontally(
    animationSpec: FiniteAnimationSpec<Point> = DEFAULT_SLIDE_SPRING,
    targetOffsetX: (fullWidth: Int) -> Int = { -it / 2 },
): ExitTransition = slideOut(animationSpec) { Point(targetOffsetX(it.width), 0) }

/**
 * Slides the content out vertically, from its place in the container to [targetOffsetY].
 *
 * @param animationSpec how the offset travels, a medium-low stiffness spring by default.
 * @param targetOffsetY the vertical offset the content ends at, given its full height; half that height
 *     up by default.
 * @return the exit transition.
 */
@Stable
public fun slideOutVertically(
    animationSpec: FiniteAnimationSpec<Point> = DEFAULT_SLIDE_SPRING,
    targetOffsetY: (fullHeight: Int) -> Int = { -it / 2 },
): ExitTransition = slideOut(animationSpec) { Point(0, targetOffsetY(it.height)) }

// The default spec of every slide factory. The threshold is explicit because the vendored engine's own
// threshold map covers only Int and Float.
private val DEFAULT_SLIDE_SPRING: FiniteAnimationSpec<Point> =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = pointVisibilityThreshold())

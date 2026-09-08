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
import org.jetbrains.compose.swing.foundation.layout.Alignment
import java.awt.Dimension

// Derived from androidx.compose.animation's expandIn/shrinkOut family: the parameter names, order and
// defaults below are upstream's, with java.awt.Dimension standing in for IntSize.

/**
 * Expands the space the content is given, from [initialSize] to the content's full size.
 *
 * The content keeps its own size and is aligned inside that space, so the corner [expandFrom] names
 * stays in place. Content outside the space is clipped when [clip] is true.
 *
 * The container reports that space as its preferred size. The clip and the alignment follow the space,
 * not the bounds the parent assigns, so a parent that stretches the container shows the same animation.
 * For one axis, use [expandHorizontally] or [expandVertically].
 *
 * @param animationSpec how the space travels, a medium-low stiffness spring by default.
 * @param expandFrom the corner the space grows from, the bottom trailing one by default.
 * @param clip whether to clip content to the animated space; true by default.
 * @param initialSize the space the content starts in, given its full size; empty by default.
 * @return the enter transition.
 */
@Stable
public fun expandIn(
    animationSpec: FiniteAnimationSpec<Dimension> = DEFAULT_SIZE_SPRING,
    expandFrom: Alignment = Alignment.BottomEnd,
    clip: Boolean = true,
    initialSize: (fullSize: Dimension) -> Dimension = { Dimension(0, 0) },
): EnterTransition =
    EnterTransitionImpl(
        EnterExitTransitionConfig(changeSize = ChangeSizeConfig(expandFrom, initialSize, animationSpec, clip)),
    )

/**
 * Shrinks the space the content is given, from the content's full size to [targetSize].
 *
 * The content keeps its own size and is aligned inside that space, so the corner [shrinkTowards] names
 * stays in place. Content outside the space is clipped when [clip] is true; see [expandIn].
 *
 * For one axis, use [shrinkHorizontally] or [shrinkVertically].
 *
 * @param animationSpec how the space travels, a medium-low stiffness spring by default.
 * @param shrinkTowards the corner the space shrinks towards, the bottom trailing one by default.
 * @param clip whether to clip content to the animated space; true by default.
 * @param targetSize the space the content ends in, given its full size; empty by default.
 * @return the exit transition.
 */
@Stable
public fun shrinkOut(
    animationSpec: FiniteAnimationSpec<Dimension> = DEFAULT_SIZE_SPRING,
    shrinkTowards: Alignment = Alignment.BottomEnd,
    clip: Boolean = true,
    targetSize: (fullSize: Dimension) -> Dimension = { Dimension(0, 0) },
): ExitTransition =
    ExitTransitionImpl(
        EnterExitTransitionConfig(changeSize = ChangeSizeConfig(shrinkTowards, targetSize, animationSpec, clip)),
    )

/**
 * Expands the width the content is given, from [initialWidth] to the content's full width, keeping the
 * full height. The content is centered vertically.
 *
 * @param animationSpec how the width travels, a medium-low stiffness spring by default.
 * @param expandFrom the edge the width grows from, the trailing one by default. Any alignment other than
 *     [Alignment.Start] and [Alignment.End] centers the content horizontally.
 * @param clip whether to clip content to the animated space; true by default.
 * @param initialWidth the width the content starts in, given its full width; `0` by default.
 * @return the enter transition.
 */
@Stable
public fun expandHorizontally(
    animationSpec: FiniteAnimationSpec<Dimension> = DEFAULT_SIZE_SPRING,
    expandFrom: Alignment.Horizontal = Alignment.End,
    clip: Boolean = true,
    initialWidth: (fullWidth: Int) -> Int = { 0 },
): EnterTransition =
    expandIn(animationSpec, expandFrom.toAlignment(), clip) {
        Dimension(initialWidth(it.width), it.height)
    }

/**
 * Expands the height the content is given, from [initialHeight] to the content's full height, keeping
 * the full width. The content is centered horizontally.
 *
 * @param animationSpec how the height travels, a medium-low stiffness spring by default.
 * @param expandFrom the edge the height grows from, the bottom by default. Any alignment other than
 *     [Alignment.Top] and [Alignment.Bottom] centers the content vertically.
 * @param clip whether to clip content to the animated space; true by default.
 * @param initialHeight the height the content starts in, given its full height; `0` by default.
 * @return the enter transition.
 */
@Stable
public fun expandVertically(
    animationSpec: FiniteAnimationSpec<Dimension> = DEFAULT_SIZE_SPRING,
    expandFrom: Alignment.Vertical = Alignment.Bottom,
    clip: Boolean = true,
    initialHeight: (fullHeight: Int) -> Int = { 0 },
): EnterTransition =
    expandIn(animationSpec, expandFrom.toAlignment(), clip) {
        Dimension(it.width, initialHeight(it.height))
    }

/**
 * Shrinks the width the content is given, from the content's full width to [targetWidth], keeping the
 * full height. The content is centered vertically.
 *
 * @param animationSpec how the width travels, a medium-low stiffness spring by default.
 * @param shrinkTowards the edge the width shrinks towards, the trailing one by default. Any alignment
 *     other than [Alignment.Start] and [Alignment.End] centers the content horizontally.
 * @param clip whether to clip content to the animated space; true by default.
 * @param targetWidth the width the content ends in, given its full width; `0` by default.
 * @return the exit transition.
 */
@Stable
public fun shrinkHorizontally(
    animationSpec: FiniteAnimationSpec<Dimension> = DEFAULT_SIZE_SPRING,
    shrinkTowards: Alignment.Horizontal = Alignment.End,
    clip: Boolean = true,
    targetWidth: (fullWidth: Int) -> Int = { 0 },
): ExitTransition =
    shrinkOut(animationSpec, shrinkTowards.toAlignment(), clip) { Dimension(targetWidth(it.width), it.height) }

/**
 * Shrinks the height the content is given, from the content's full height to [targetHeight], keeping the
 * full width. The content is centered horizontally.
 *
 * @param animationSpec how the height travels, a medium-low stiffness spring by default.
 * @param shrinkTowards the edge the height shrinks towards, the bottom by default. Any alignment other
 *     than [Alignment.Top] and [Alignment.Bottom] centers the content vertically.
 * @param clip whether to clip content to the animated space; true by default.
 * @param targetHeight the height the content ends in, given its full height; `0` by default.
 * @return the exit transition.
 */
@Stable
public fun shrinkVertically(
    animationSpec: FiniteAnimationSpec<Dimension> = DEFAULT_SIZE_SPRING,
    shrinkTowards: Alignment.Vertical = Alignment.Bottom,
    clip: Boolean = true,
    targetHeight: (fullHeight: Int) -> Int = { 0 },
): ExitTransition =
    shrinkOut(animationSpec, shrinkTowards.toAlignment(), clip) { Dimension(it.width, targetHeight(it.height)) }

/** The edge [expandHorizontally] and [shrinkHorizontally] anchor to, centered on the other axis. */
private fun Alignment.Horizontal.toAlignment(): Alignment =
    when (this) {
        Alignment.Start -> Alignment.CenterStart
        Alignment.End -> Alignment.CenterEnd
        else -> Alignment.Center
    }

/** The edge [expandVertically] and [shrinkVertically] anchor to, centered on the other axis. */
private fun Alignment.Vertical.toAlignment(): Alignment =
    when (this) {
        Alignment.Top -> Alignment.TopCenter
        Alignment.Bottom -> Alignment.BottomCenter
        else -> Alignment.Center
    }

// The default spec of every size factory. The threshold is explicit because the vendored engine's own threshold
// map covers only Int and Float.
private val DEFAULT_SIZE_SPRING: FiniteAnimationSpec<Dimension> =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = dimensionVisibilityThreshold())

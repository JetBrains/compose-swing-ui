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
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin

// Derived from androidx.compose.animation's scaleIn and scaleOut: the parameter names, order and defaults
// below are upstream's.

/**
 * Scales the content in, from [initialScale] to its own size, around [transformOrigin].
 *
 * A scale changes how the content is painted, not its layout size. Combine it with [expandIn] to animate the
 * layout size too. Under a Foundation parent, content scaled above `1f` paints past the container unless the
 * transition also changes its size with an [expandIn] or [shrinkOut] that clips, or an [AnimatedContent] clips it
 * with its [SizeTransform]. Under any other parent, the container's bounds clip it.
 *
 * @param animationSpec how the scale travels, a medium-low stiffness spring by default.
 * @param initialScale the scale the content starts at, `0f` by default. Any value is accepted; a
 *     negative one mirrors the content around [transformOrigin].
 * @param transformOrigin the point the scale is applied around, the center of the content by default.
 * @return the enter transition.
 */
@Stable
public fun scaleIn(
    animationSpec: FiniteAnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow),
    initialScale: Float = 0f,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
): EnterTransition =
    EnterTransitionImpl(
        EnterExitTransitionConfig(scale = ScaleConfig(initialScale, transformOrigin, animationSpec)),
    )

/**
 * Scales the content out, from its own size to [targetScale], around [transformOrigin].
 *
 * A scale changes how the content is painted, not its layout size; see [scaleIn].
 *
 * @param animationSpec how the scale travels, a medium-low stiffness spring by default.
 * @param targetScale the scale the content ends at, `0f` by default. Any value is accepted; a negative
 *     one mirrors the content around [transformOrigin].
 * @param transformOrigin the point the scale is applied around, the center of the content by default.
 * @return the exit transition.
 */
@Stable
public fun scaleOut(
    animationSpec: FiniteAnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow),
    targetScale: Float = 0f,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
): ExitTransition =
    ExitTransitionImpl(
        EnterExitTransitionConfig(scale = ScaleConfig(targetScale, transformOrigin, animationSpec)),
    )

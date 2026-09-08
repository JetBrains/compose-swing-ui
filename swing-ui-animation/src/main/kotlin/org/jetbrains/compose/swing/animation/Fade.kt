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

// Derived from androidx.compose.animation's fadeIn and fadeOut: the parameter names, order and defaults
// below are upstream's.

/**
 * Fades the content in, from [initialAlpha] to full opacity.
 *
 * @param animationSpec how the opacity travels, a medium-low stiffness spring by default.
 * @param initialAlpha the opacity the content starts at, fully transparent `0f` by default.
 * @return the enter transition.
 */
@Stable
public fun fadeIn(
    animationSpec: FiniteAnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow),
    initialAlpha: Float = 0f,
): EnterTransition = EnterTransitionImpl(EnterExitTransitionConfig(fade = FadeConfig(initialAlpha, animationSpec)))

/**
 * Fades the content out, from full opacity to [targetAlpha].
 *
 * @param animationSpec how the opacity travels, a medium-low stiffness spring by default.
 * @param targetAlpha the opacity the content ends at, fully transparent `0f` by default.
 * @return the exit transition.
 */
@Stable
public fun fadeOut(
    animationSpec: FiniteAnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow),
    targetAlpha: Float = 0f,
): ExitTransition = ExitTransitionImpl(EnterExitTransitionConfig(fade = FadeConfig(targetAlpha, animationSpec)))

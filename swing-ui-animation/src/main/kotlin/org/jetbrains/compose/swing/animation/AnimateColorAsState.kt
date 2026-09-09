/*
 * Copyright 2019 The Android Open Source Project
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.annotation.RememberInComposition
import org.jetbrains.compose.swing.animation.core.Animatable
import org.jetbrains.compose.swing.animation.core.AnimationSpec
import org.jetbrains.compose.swing.animation.core.AnimationVector4D
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.InfiniteRepeatableSpec
import org.jetbrains.compose.swing.animation.core.InfiniteTransition
import org.jetbrains.compose.swing.animation.core.RepeatMode
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.animateValue
import org.jetbrains.compose.swing.animation.core.animateValueAsState
import org.jetbrains.compose.swing.animation.core.spring
import java.awt.Color

// Derived from androidx.compose.animation's animateColorAsState, Transition.animateColor,
// InfiniteTransition.animateColor and Animatable(Color), with java.awt.Color standing in for Color.

/**
 * Animates towards [targetValue] whenever it changes. A running animation continues from its current
 * value towards the new target instead of restarting.
 *
 * The color travels through Oklab, the space [ColorToVector] describes.
 *
 * The animation runs for as long as this call stays in the composition and cannot be stopped from
 * outside it; use [Animatable] for an animation the caller can cancel.
 *
 * @param targetValue the color to animate towards.
 * @param animationSpec how the value moves through time; a [spring] by default.
 * @param label names this animation in a tool that inspects a composition.
 * @param finishedListener called with the target color once the animation arrives; `null` by default.
 * @return a state holding the color of the current frame.
 */
@Composable
public fun animateColorAsState(
    targetValue: Color,
    animationSpec: AnimationSpec<Color> = colorDefaultSpring,
    label: String = "ColorAnimation",
    finishedListener: ((Color) -> Unit)? = null,
): State<Color> =
    animateValueAsState(
        targetValue = targetValue,
        typeConverter = ColorToVector,
        animationSpec = animationSpec,
        label = label,
        finishedListener = finishedListener,
    )

/**
 * Animates a color as part of this [Transition], which owns the animation's lifetime.
 *
 * [targetValueByState] is composable, so it may read state or a composition local. A new color it
 * returns for a state the transition has already reached is also animated to.
 *
 * @param transitionSpec how the value moves through time between a given pair of states; a [spring]
 *   for every pair by default.
 * @param label names this animation among the others of the same transition.
 * @param targetValueByState the color each state of the transition stands for.
 * @return a state holding the color of the current frame.
 */
@Composable
public fun <S> Transition<S>.animateColor(
    transitionSpec: @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<Color> = { spring() },
    label: String = "ColorAnimation",
    targetValueByState: @Composable (state: S) -> Color,
): State<Color> = animateValue(ColorToVector, transitionSpec, label, targetValueByState)

/**
 * Animates a color from [initialValue] to [targetValue] and repeats for as long as this
 * [InfiniteTransition] stays in the composition, restarting or reversing on each iteration according
 * to the [RepeatMode] of [animationSpec].
 *
 * Changing [initialValue] or [targetValue] restarts the animation at the new endpoints.
 *
 * @param initialValue the color each iteration starts from.
 * @param targetValue the color each iteration runs to.
 * @param animationSpec how the value moves through time, and how each iteration follows the last.
 * @param label names this animation among the others of the same transition.
 * @return a state holding the color of the current frame.
 */
@Composable
public fun InfiniteTransition.animateColor(
    initialValue: Color,
    targetValue: Color,
    animationSpec: InfiniteRepeatableSpec<Color>,
    label: String = "ColorAnimation",
): State<Color> = animateValue(initialValue, targetValue, ColorToVector, animationSpec, label)

/**
 * Holds a color that animates through Oklab, the space [ColorToVector] describes.
 *
 * A new animation cancels the running one and starts from the color it reached.
 *
 * @param initialValue the color the holder starts at.
 */
@RememberInComposition
public fun Animatable(initialValue: Color): Animatable<Color, AnimationVector4D> =
    Animatable(initialValue, ColorToVector)

private val colorDefaultSpring = spring<Color>()

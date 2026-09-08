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
import java.awt.Color

// Derived from androidx.compose.animation's unveilIn and veilOut: the parameter names, order and defaults
// below are upstream's, with java.awt.Color standing in for Color.

/**
 * Unveils the content as it enters: a scrim of [initialColor] over it animates to fully transparent.
 *
 * The scrim is filled in the container's own space, so a fade or a scale running with it moves the
 * content under the scrim, never the scrim itself.
 *
 * @param animationSpec how the scrim's color travels, a medium-low stiffness spring by default.
 * @param initialColor the color the scrim starts at, half-transparent black by default.
 * @param matchParentSize whether the scrim covers the container's whole box instead of only the
 *     content's rectangle; `false` by default.
 * @return the enter transition.
 */
@ExperimentalAnimationApi
@Stable
public fun unveilIn(
    animationSpec: FiniteAnimationSpec<Color> = spring(stiffness = Spring.StiffnessMediumLow),
    initialColor: Color = DefaultVeilColor,
    matchParentSize: Boolean = false,
): EnterTransition =
    EnterTransitionImpl(
        EnterExitTransitionConfig(
            veil = VeilConfig(initialColor, initialColor.fullyTransparent(), animationSpec, matchParentSize),
        ),
    )

/**
 * Veils the content as it leaves: a scrim over it animates from fully transparent to [targetColor].
 *
 * The scrim is filled over the content; see [unveilIn].
 *
 * @param animationSpec how the scrim's color travels, a medium-low stiffness spring by default.
 * @param targetColor the color the scrim ends at, half-transparent black by default.
 * @param matchParentSize whether the scrim covers the container's whole box instead of only the
 *     content's rectangle; `false` by default.
 * @return the exit transition.
 */
@ExperimentalAnimationApi
@Stable
public fun veilOut(
    animationSpec: FiniteAnimationSpec<Color> = spring(stiffness = Spring.StiffnessMediumLow),
    targetColor: Color = DefaultVeilColor,
    matchParentSize: Boolean = false,
): ExitTransition =
    ExitTransitionImpl(
        EnterExitTransitionConfig(
            veil = VeilConfig(targetColor.fullyTransparent(), targetColor, animationSpec, matchParentSize),
        ),
    )

/** The default scrim of both factories. */
private val DefaultVeilColor: Color = Color(0f, 0f, 0f, 0.5f)

/** This color at zero alpha, the end a veil clears to or starts from. */
private fun Color.fullyTransparent(): Color = Color(red, green, blue, 0)

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

import org.jetbrains.compose.swing.animation.core.AnimationVector2D
import org.jetbrains.compose.swing.animation.core.TwoWayConverter
import java.awt.Dimension
import java.awt.Point
import kotlin.math.roundToInt

// Derived from androidx.compose.animation.core's IntSize and IntOffset converters and visibility thresholds,
// with java.awt.Dimension and java.awt.Point standing in for IntSize and IntOffset.

/**
 * Animates a [Dimension] as a two-dimensional vector of its width and height.
 *
 * Each frame is rounded to whole units and floored at zero, because a spring overshoots below its target
 * and a component cannot have a negative size. Every frame is a new [Dimension]:
 * `Component.setPreferredSize` stores the instance it is passed, so a reused one would let a later frame
 * change the value a component holds.
 */
public val DimensionToVector: TwoWayConverter<Dimension, AnimationVector2D> =
    TwoWayConverter(
        convertToVector = { AnimationVector2D(it.width.toFloat(), it.height.toFloat()) },
        convertFromVector = {
            Dimension(it.v1.roundToInt().coerceAtLeast(0), it.v2.roundToInt().coerceAtLeast(0))
        },
    )

/**
 * Animates a [Point] as a two-dimensional vector of its x and y coordinates.
 *
 * Each frame is rounded to whole units and may be negative. Every frame is a new [Point], for the reason
 * given on [DimensionToVector].
 */
public val PointToVector: TwoWayConverter<Point, AnimationVector2D> =
    TwoWayConverter(
        convertToVector = { AnimationVector2D(it.x.toFloat(), it.y.toFloat()) },
        convertFromVector = { Point(it.v1.roundToInt(), it.v2.roundToInt()) },
    )

/**
 * The size change below which a [Dimension] animation is considered finished: one unit on each axis.
 *
 * Returns a new instance on each call: an animation spec stores the threshold and reads it for each
 * animation it starts, so a `setSize` on a shared instance would change every animation built from it.
 */
public fun dimensionVisibilityThreshold(): Dimension = Dimension(1, 1)

/**
 * The offset change below which a [Point] animation is considered finished: one unit on each axis.
 *
 * Returns a new instance on each call, for the reason given on [dimensionVisibilityThreshold].
 */
public fun pointVisibilityThreshold(): Point = Point(1, 1)

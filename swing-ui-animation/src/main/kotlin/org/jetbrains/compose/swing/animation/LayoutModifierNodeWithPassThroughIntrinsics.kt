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

import org.jetbrains.compose.swing.foundation.layout.IntrinsicMeasurable
import org.jetbrains.compose.swing.foundation.layout.IntrinsicMeasureScope
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNode
import org.jetbrains.compose.swing.foundation.layout.Placeable

// Derived from androidx.compose.animation.LayoutModifierNodeWithPassThroughIntrinsics.

/**
 * A [LayoutModifierNode] whose intrinsic measurements and intrinsic alignment lines delegate to `measurable` instead
 * of running [org.jetbrains.compose.swing.foundation.layout.LayoutModifier.measure] under intrinsic constraints.
 *
 * A node whose `measure` starts or retargets an animation must not run it from an intrinsic query: a
 * validate cycle asks for a container's preferred size and minimum size and then lays it out, so the
 * node would set up one target per query instead of one.
 */
internal abstract class LayoutModifierNodeWithPassThroughIntrinsics : LayoutModifierNode() {
    final override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.minIntrinsicWidth(height)

    final override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.maxIntrinsicWidth(height)

    final override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.minIntrinsicHeight(width)

    final override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.maxIntrinsicHeight(width)

    final override fun IntrinsicMeasureScope.intrinsicPlaceable(
        measurable: IntrinsicMeasurable,
        width: Int,
        height: Int,
    ): Placeable? = measurable.intrinsicPlaceable(width, height)
}

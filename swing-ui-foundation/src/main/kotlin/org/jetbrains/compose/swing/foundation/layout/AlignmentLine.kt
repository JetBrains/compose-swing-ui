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
 *
 * Adapted from androidx.compose.ui.layout.AlignmentLine in AndroidX's ui; see this module's
 * META-INF/NOTICE for the synced version.
 */

@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import kotlin.math.min

/**
 * An offset line a measured layout exposes to its parent for alignment.
 *
 * A child provides the lines its policy and its layout modifiers name in [MeasureResult.alignmentLines], and
 * [FirstBaseline] where its component reports a baseline. A container also puts each line its children put, where
 * it places them. Where several children put one line, the line's merger picks its position: the highest one for
 * [FirstBaseline].
 */
public sealed class AlignmentLine(
    internal val merger: (Int, Int) -> Int,
) {
    /** A line no layout supplied. */
    public companion object {
        /** Value indicating an unspecified alignment line position. */
        public const val UNSPECIFIED: Int = Int.MIN_VALUE
    }
}

/** [coordinate] held to the range AWT can represent, above the [AlignmentLine.UNSPECIFIED] no real line takes. */
internal fun saturateLineCoordinate(coordinate: Long): Int =
    coordinate.coerceIn(AlignmentLine.UNSPECIFIED.toLong() + 1, Int.MAX_VALUE.toLong()).toInt()

/** A line whose position is measured from the left or right edge. */
public class VerticalAlignmentLine(
    merger: (Int, Int) -> Int,
) : AlignmentLine(merger)

/** A line whose position is measured from the top or bottom edge, such as a text baseline. */
public class HorizontalAlignmentLine(
    merger: (Int, Int) -> Int,
) : AlignmentLine(merger)

/**
 * The baseline of a child's first line of text: where `java.awt.Component.getBaseline` puts it, or where
 * a [MeasurePolicy] or a [LayoutModifierNode] names it in [MeasureResult.alignmentLines].
 */
public val FirstBaseline: HorizontalAlignmentLine = HorizontalAlignmentLine(::min)

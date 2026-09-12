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

@file:JvmMultifileClass
@file:JvmName("FoundationLayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier

/** Adapted from AndroidX's foundation-layout; see this module's META-INF/NOTICE for the synced version. */
@PublishedApi
internal val DefaultColumnMeasurePolicy: MeasurePolicy =
    ColumnMeasurePolicy(verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.Start)

@PublishedApi
@Composable
internal fun columnMeasurePolicy(
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal,
): MeasurePolicy =
    if (verticalArrangement == Arrangement.Top && horizontalAlignment == Alignment.Start) {
        DefaultColumnMeasurePolicy
    } else {
        remember(verticalArrangement, horizontalAlignment) {
            ColumnMeasurePolicy(verticalArrangement, horizontalAlignment)
        }
    }

/**
 * A composable that arranges its [content] vertically, top to bottom.
 *
 * An explicit `maximumSize` caps the offer and normally the extent each child takes on either axis.
 * A layout modifier whose own contract permits escape from an impossible offer, such as
 * [ConstrainedScope.aspectRatio], may report an extent outside that maximum. The height the column has
 * left over is placed by [verticalArrangement] - above the children, below them, between them, or as a
 * fixed gap through [Arrangement.spacedBy]. Across the column each child sits where
 * [horizontalAlignment] puts it.
 *
 * A child claims a share of the leftover height with `weight`, or names its own horizontal placement
 * with `align`, through [ColumnScope]:
 *
 * ```
 * Column(verticalArrangement = Arrangement.spacedBy(8), horizontalAlignment = Alignment.CenterHorizontally) {
 *     Label(text = "Title")
 *     Panel(PanelLayout.Flow(), modifier = SwingModifier.weight(1f)) { Body() }
 *     Button(text = "Close", onClick = ::close)
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param verticalArrangement where the children, and the height left over, go along the column; the
 *   default [Arrangement.Top] packs them against the top and leaves the rest of the height below them
 * @param horizontalAlignment where each child sits across the column; the default [Alignment.Start]
 *   puts each at the leading edge - the left under a left-to-right orientation
 * @param content the composable content of the column; see [ColumnScope]
 */
@Composable
public inline fun Column(
    modifier: SwingModifier = SwingModifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    crossinline content: @Composable ColumnScope.() -> Unit,
) {
    Layout(
        measurePolicy = columnMeasurePolicy(verticalArrangement, horizontalAlignment),
        modifier = modifier,
        parentDataProtocol = LinearParentDataProtocol,
        content = { ColumnScopeInstance.content() },
    )
}

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
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier

/** Adapted from AndroidX's foundation-layout; see this module's META-INF/NOTICE for the synced version. */
@PublishedApi
internal val DefaultRowMeasurePolicy: MeasurePolicy =
    RowMeasurePolicy(horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top)

@PublishedApi
@Composable
internal fun rowMeasurePolicy(
    horizontalArrangement: Arrangement.Horizontal,
    verticalAlignment: Alignment.Vertical,
): MeasurePolicy =
    if (horizontalArrangement == Arrangement.Start && verticalAlignment == Alignment.Top) {
        DefaultRowMeasurePolicy
    } else {
        remember(horizontalArrangement, verticalAlignment) {
            RowMeasurePolicy(horizontalArrangement, verticalAlignment)
        }
    }

/**
 * A composable that arranges its [content] horizontally, along the panel's reading order.
 *
 * An explicit `maximumSize` caps the offer and normally the extent each child takes on either axis.
 * A layout modifier whose own contract permits escape from an impossible offer, such as [aspectRatio],
 * may report an extent outside that maximum. The width the row has left over is placed by
 * [horizontalArrangement] - before the children, after them, between them, or as a fixed gap through
 * [Arrangement.spacedBy]. Across the row each child sits where [verticalAlignment] puts it.
 *
 * A child claims a share of the leftover width with `weight`, or names its own vertical placement with
 * `align`, through [RowScope]:
 *
 * ```
 * Row(horizontalArrangement = Arrangement.spacedBy(8), verticalAlignment = Alignment.CenterVertically) {
 *     Label(text = "Status")
 *     Panel(PanelLayout.Flow(), modifier = SwingModifier.weight(1f)) { Details() }
 *     Button(text = "Close", onClick = ::close)
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param horizontalArrangement where the children, and the width left over, go along the row; the
 *   default [Arrangement.Start] packs them against the leading edge and leaves the rest of the width
 *   after them
 * @param verticalAlignment where each child sits across the row; the default [Alignment.Top] puts each
 *   at the top of the height available to it
 * @param content the composable content of the row; see [RowScope]
 */
@Composable
public inline fun Row(
    modifier: SwingModifier = SwingModifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    crossinline content: @Composable RowScope.() -> Unit,
) {
    Layout(
        measurePolicy = rowMeasurePolicy(horizontalArrangement, verticalAlignment),
        modifier = modifier,
        parentDataProtocol = LinearParentDataProtocol,
        content = { RowScopeInstance.content() },
    )
}

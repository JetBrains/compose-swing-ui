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
 *
 * Adapted from androidx.compose.foundation.layout.Box in AndroidX's foundation-layout; see this
 * module's META-INF/NOTICE for the synced version. The policy is adapted to Swing's component
 * sizes and alignments, and retains this project's public API and parent-data model.
 */

@file:JvmMultifileClass
@file:JvmName("FoundationLayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension

/**
 * A composable that stacks its [content] in one place, one child over another.
 *
 * The box asks for the largest size among the children that do not match its own, plus its insets. Each
 * child keeps the size it prefers, capped at the box's inner extent, and sits where [contentAlignment]
 * puts it. The children stack in declaration order: the last child declared paints over the ones before
 * it, and takes a mouse event at a point they share. A child naming a `zIndex` rises over every sibling
 * declaring a smaller one, wherever the two are declared.
 *
 * By default the box gives content a zero minimum, so a minimum imposed on the box need not enlarge its
 * children. Set [propagateMinConstraints] to pass that incoming minimum through instead, such as when
 * content cannot receive a modifier directly.
 *
 * A child names its own placement with `align`, takes the box's whole extent with `matchParentSize`, or
 * names where in the stack it sits with `zIndex`, through [BoxScope]:
 *
 * ```
 * Box(contentAlignment = Alignment.Center) {
 *     ProgressBar(value = 40, modifier = SwingModifier.matchParentSize())
 *     Label(text = "Loading")
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param contentAlignment where each child sits in the box; the default [Alignment.TopStart] puts each at
 *   the top of the leading edge of the extent available to it
 * @param propagateMinConstraints whether the box's incoming minimum extent is passed through to its
 *   content. The default `false` keeps the minimum on the box alone.
 * @param content the composable content of the box; see [BoxScope]
 */
@Composable
public inline fun Box(
    modifier: SwingModifier = SwingModifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    crossinline content: @Composable BoxScope.() -> Unit,
) {
    Layout(
        measurePolicy = maybeCachedBoxMeasurePolicy(contentAlignment, propagateMinConstraints),
        modifier = modifier,
        parentDataProtocol = BoxParentDataProtocol,
        content = { BoxScopeInstance.content() },
    )
}

/**
 * A box with no content that can participate in layout, drawing, and input through its [modifier].
 *
 * @param modifier the [SwingModifier] applied to the panel.
 */
@Composable
public fun Box(modifier: SwingModifier = SwingModifier) {
    Layout(measurePolicy = EmptyBoxMeasurePolicy, modifier = modifier)
}

private fun cacheFor(propagateMinConstraints: Boolean): Map<Alignment, MeasurePolicy> =
    mapOf(
        Alignment.TopStart to BoxMeasurePolicy(Alignment.TopStart, propagateMinConstraints),
        Alignment.TopCenter to BoxMeasurePolicy(Alignment.TopCenter, propagateMinConstraints),
        Alignment.TopEnd to BoxMeasurePolicy(Alignment.TopEnd, propagateMinConstraints),
        Alignment.CenterStart to BoxMeasurePolicy(Alignment.CenterStart, propagateMinConstraints),
        Alignment.Center to BoxMeasurePolicy(Alignment.Center, propagateMinConstraints),
        Alignment.CenterEnd to BoxMeasurePolicy(Alignment.CenterEnd, propagateMinConstraints),
        Alignment.BottomStart to BoxMeasurePolicy(Alignment.BottomStart, propagateMinConstraints),
        Alignment.BottomCenter to BoxMeasurePolicy(Alignment.BottomCenter, propagateMinConstraints),
        Alignment.BottomEnd to BoxMeasurePolicy(Alignment.BottomEnd, propagateMinConstraints),
    )

private val PropagatingBoxMeasurePolicies: Map<Alignment, MeasurePolicy> = cacheFor(propagateMinConstraints = true)
private val RelaxedBoxMeasurePolicies: Map<Alignment, MeasurePolicy> = cacheFor(propagateMinConstraints = false)

@PublishedApi
internal fun maybeCachedBoxMeasurePolicy(
    alignment: Alignment,
    propagateMinConstraints: Boolean,
): MeasurePolicy {
    val cache = if (propagateMinConstraints) PropagatingBoxMeasurePolicies else RelaxedBoxMeasurePolicies
    return cache[alignment] ?: BoxMeasurePolicy(alignment, propagateMinConstraints)
}

@PublishedApi
@Composable
internal fun rememberBoxMeasurePolicy(
    alignment: Alignment,
    propagateMinConstraints: Boolean,
): MeasurePolicy =
    if (alignment == Alignment.TopStart && !propagateMinConstraints) {
        DefaultBoxMeasurePolicy
    } else {
        remember(alignment, propagateMinConstraints) {
            BoxMeasurePolicy(alignment, propagateMinConstraints)
        }
    }

private val DefaultBoxMeasurePolicy: MeasurePolicy =
    BoxMeasurePolicy(Alignment.TopStart, propagateMinConstraints = false)

internal val EmptyBoxMeasurePolicy: MeasurePolicy =
    MeasurePolicy { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {}
    }

/** The foundation Box policy, adapted to Swing's component sizes and alignments. */
internal data class BoxMeasurePolicy(
    private val alignment: Alignment,
    private val propagateMinConstraints: Boolean = false,
) : MeasurePolicy,
    ParentDataPolicy,
    ParentAlignmentPolicy {
    override val parentAlignmentChild: ParentAlignmentChild = ParentAlignmentChild.Topmost

    override fun validateParentData(
        component: Component,
        parentData: Any?,
    ) {
        require(parentData == null || parentData is BoxConstraint) {
            "A Box places a child by the alignment it is declared with, and by align() / " +
                "matchParentSize() / zIndex() on the child's own modifier, so '$component' can carry no " +
                "layout constraint, but it was added under '$parentData'."
        }
    }

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult =
        when (measurables.size) {
            0 -> layout(constraints.minWidth, constraints.minHeight) {}
            1 -> measureSingle(measurables.single(), constraints)
            else -> measureMultiple(measurables, constraints)
        }

    private fun MeasureScope.measureSingle(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val contentConstraints = constraints.forContent(propagateMinConstraints)
        val placeable: Placeable
        val boxSize: Dimension
        if (measurable.matchesParentSize) {
            boxSize = Dimension(constraints.minWidth, constraints.minHeight)
            placeable = measurable.measure(fixedForBox(boxSize.width, boxSize.height, measurable))
        } else {
            placeable = measurable.measure(contentConstraints.forChild(measurable))
            boxSize =
                Dimension(
                    maxOf(constraints.minWidth, placeable.width),
                    maxOf(constraints.minHeight, placeable.height),
                )
        }
        return layout(boxSize.width, boxSize.height) {
            placeInBox(placeable, measurable, boxSize, alignment, orientation)
        }
    }

    private fun MeasureScope.measureMultiple(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val contentConstraints = constraints.forContent(propagateMinConstraints)
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var hasMatchParentSizeChildren = false
        var boxWidth = constraints.minWidth
        var boxHeight = constraints.minHeight
        measurables.forEachIndexed { index, measurable ->
            if (!measurable.matchesParentSize) {
                val placeable = measurable.measure(contentConstraints.forChild(measurable))
                placeables[index] = placeable
                boxWidth = maxOf(boxWidth, placeable.width)
                boxHeight = maxOf(boxHeight, placeable.height)
            } else {
                hasMatchParentSizeChildren = true
            }
        }

        if (hasMatchParentSizeChildren) {
            val matchParentSizeConstraints =
                Constraints(
                    minWidth = if (boxWidth != Int.MAX_VALUE) boxWidth else 0,
                    minHeight = if (boxHeight != Int.MAX_VALUE) boxHeight else 0,
                    maxWidth = boxWidth,
                    maxHeight = boxHeight,
                )
            measurables.forEachIndexed { index, measurable ->
                if (measurable.matchesParentSize) {
                    placeables[index] =
                        measurable.measure(
                            matchParentSizeConstraints.forChild(measurable, fixed = true),
                        )
                }
            }
        }

        val boxSize = Dimension(boxWidth, boxHeight)
        return layout(boxSize.width, boxSize.height) {
            placeables.forEachIndexed { index, placeable ->
                placeInBox(checkNotNull(placeable), measurables[index], boxSize, alignment, orientation)
            }
        }
    }
}

private val Measurable.matchesParentSize: Boolean
    get() = (parentData as? BoxConstraint)?.matchesParentSize == true

private val Measurable.boxAlignment: Alignment?
    get() = (parentData as? BoxConstraint)?.alignment

private fun PlacementScope.placeInBox(
    placeable: Placeable,
    measurable: Measurable,
    boxSize: Dimension,
    alignment: Alignment,
    orientation: ComponentOrientation,
) {
    val position =
        (measurable.boxAlignment ?: alignment).align(
            Dimension(placeable.width, placeable.height),
            boxSize,
            orientation,
        )
    placeable.place(position.x, position.y)
}

private fun Constraints.forChild(
    measurable: Measurable,
    fixed: Boolean = false,
): Constraints {
    val maximum =
        measurable
            .componentOrNull
            ?.takeIf { it.isMaximumSizeSet }
            ?.maximumSize
    val maxWidth = ceiling(maxWidth, maximum?.width)
    val maxHeight = ceiling(maxHeight, maximum?.height)
    return Constraints(
        minWidth = if (fixed && (hasFixedWidth || maximum != null)) maxWidth else minWidth.coerceAtMost(maxWidth),
        maxWidth = maxWidth,
        minHeight = if (fixed && (hasFixedHeight || maximum != null)) maxHeight else minHeight.coerceAtMost(maxHeight),
        maxHeight = maxHeight,
    )
}

private fun Constraints.forContent(propagateMinConstraints: Boolean): Constraints =
    if (propagateMinConstraints) this else copy(minWidth = 0, minHeight = 0)

private fun fixedForBox(
    width: Int,
    height: Int,
    measurable: Measurable,
): Constraints = Constraints(width, width, height, height).forChild(measurable, fixed = true)

private fun ceiling(
    available: Int,
    maximum: Int?,
): Int = if (maximum == null) available else minOf(available, maximum.coerceAtLeast(0))

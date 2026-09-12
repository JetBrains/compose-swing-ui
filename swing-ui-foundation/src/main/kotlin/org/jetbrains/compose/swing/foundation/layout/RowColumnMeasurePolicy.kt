/*
 * Copyright 2022 The Android Open Source Project
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

// Suppressed: This file consolidates AndroidX's foundation-layout Row and Column measurement
// algorithms, intrinsic sizing passes, and saturating arithmetic to preserve verbatim upstream synchronization.
// See this module's META-INF/NOTICE for the synced version.
@file:Suppress("TooManyFunctions")

package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component
import java.awt.Dimension
import kotlin.math.roundToInt
import kotlin.math.sign

/** The shared AndroidX RowColumnMeasurePolicy shape, with Swing placement adaptations. */
internal interface RowColumnMeasurePolicy :
    MeasurePolicy,
    ParentDataPolicy {
    val arrangementSpacing: Int

    fun Placeable.mainAxisSize(): Int

    fun Placeable.crossAxisSize(): Int

    fun createConstraints(
        mainAxisMin: Int,
        crossAxisMin: Int,
        mainAxisMax: Int,
        crossAxisMax: Int,
    ): Constraints

    fun componentMainAxisMaximum(component: Dimension): Int

    fun componentCrossAxisMaximum(component: Dimension): Int

    @Suppress("LongParameterList")
    fun MeasureScope.layoutResult(
        mainAxisLayoutSize: Int,
        crossAxisLayoutSize: Int,
        placeables: Array<Placeable?>,
        childrenMainAxisSize: IntArray,
        measurables: List<Measurable>,
        beforeCrossAxisAlignmentLine: Int,
    ): MeasureResult

    override fun validateParentData(
        component: Component,
        parentData: Any?,
    ) {
        require(parentData == null || parentData is LinearConstraint) { foreignConstraint(component, parentData) }
    }
}

/**
 * Measures a Row or Column with AndroidX foundation-layout's two-pass algorithm (see this
 * module's META-INF/NOTICE for the synced version). The only differences are Swing's
 * maximum-size ceiling, integer arrangements, baseline bridge, and saturating coordinate
 * arithmetic.
 *
 * Keep the coupled upstream hot-path algorithm together: splitting it allocates state and lambdas,
 * and risks drifting from AndroidX's weight-rounding semantics.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
internal fun RowColumnMeasurePolicy.measureRowColumn(
    measureScope: MeasureScope,
    mainAxisMin: Int,
    crossAxisMin: Int,
    mainAxisMax: Int,
    crossAxisMax: Int,
    arrangementSpacing: Int,
    measurables: List<Measurable>,
): MeasureResult {
    val placeables = arrayOfNulls<Placeable>(measurables.size)
    var totalWeight = 0f
    var fixedSpace = 0L
    var crossAxisSpace = 0
    var weightedChildrenCount = 0
    var anyBaseline = false
    val childrenMainAxisSize = IntArray(measurables.size)
    var beforeCrossAxisAlignmentLine = 0
    var afterCrossAxisAlignmentLine = 0
    var spaceAfterLastNoWeight = 0

    for (index in measurables.indices) {
        val child = measurables[index]
        val parentData = child.linearConstraint
        val weight = parentData?.weight
        anyBaseline = anyBaseline || parentData?.alignment == BaselineAxisAlignment
        if (weight != null) {
            totalWeight += weight.weight
            weightedChildrenCount++
            continue
        }
        val remaining = saturatedInt(mainAxisMax.toLong() - fixedSpace).coerceAtLeast(0)
        val placeable =
            child.measure(
                child.constraintsWithMaximumSize(
                    createConstraints(
                        mainAxisMin = 0,
                        crossAxisMin = 0,
                        mainAxisMax = if (mainAxisMax == Int.MAX_VALUE) Int.MAX_VALUE else remaining,
                        crossAxisMax = crossAxisMax,
                    ),
                ),
            )
        val mainAxisSize = placeable.mainAxisSize()
        childrenMainAxisSize[index] = mainAxisSize
        spaceAfterLastNoWeight = minOf(arrangementSpacing, (remaining - mainAxisSize).coerceAtLeast(0))
        fixedSpace = saturatedLong(fixedSpace + mainAxisSize + spaceAfterLastNoWeight)
        crossAxisSpace = maxOf(crossAxisSpace, placeable.crossAxisSize())
        placeables[index] = placeable
    }

    var weightedSpace = 0L
    if (weightedChildrenCount == 0) {
        fixedSpace = saturatedLong(fixedSpace - spaceAfterLastNoWeight)
    } else {
        val targetSpace = if (mainAxisMax != Int.MAX_VALUE) mainAxisMax else mainAxisMin
        val arrangementSpacingTotal = arrangementSpacing.toLong() * (weightedChildrenCount - 1)
        val remainingToTarget =
            saturatedInt(
                targetSpace.toLong() - fixedSpace - arrangementSpacingTotal,
            ).coerceAtLeast(0)
        val weightUnitSpace = remainingToTarget / totalWeight
        var remainder = remainingToTarget
        for (index in measurables.indices) {
            val weight = measurables[index].linearConstraint?.weight ?: continue
            remainder -= (weightUnitSpace * weight.weight).roundToInt()
        }
        for (index in measurables.indices) {
            if (placeables[index] != null) continue
            val child = measurables[index]
            val weight = checkNotNull(child.linearConstraint?.weight)
            val remainderUnit = remainder.sign
            remainder -= remainderUnit
            val allocated =
                saturatedInt(
                    (weightUnitSpace * weight.weight).roundToInt().toLong() + remainderUnit,
                ).coerceAtLeast(0)
            val childMainAxisSize = child.cappedMainAxisSize(allocated, this)
            val placeable =
                child.measure(
                    child.constraintsWithMaximumSize(
                        createConstraints(
                            mainAxisMin = if (weight.fill) childMainAxisSize else 0,
                            crossAxisMin = 0,
                            mainAxisMax = childMainAxisSize,
                            crossAxisMax = crossAxisMax,
                        ),
                    ),
                )
            childrenMainAxisSize[index] = placeable.mainAxisSize()
            weightedSpace = saturatedLong(weightedSpace + placeable.mainAxisSize())
            crossAxisSpace = maxOf(crossAxisSpace, placeable.crossAxisSize())
            placeables[index] = placeable
        }
        weightedSpace =
            saturatedLong(weightedSpace + arrangementSpacingTotal)
                .coerceIn(0L, (mainAxisMax.toLong() - fixedSpace).coerceAtLeast(0L))
    }

    if (anyBaseline) {
        for (index in measurables.indices) {
            val child = measurables[index]
            if (child.linearConstraint?.alignment != BaselineAxisAlignment) continue
            val placeable = checkNotNull(placeables[index])
            val alignmentLine = child.baseline(placeable.width, placeable.height)
            if (alignmentLine >= 0) {
                beforeCrossAxisAlignmentLine = maxOf(beforeCrossAxisAlignmentLine, alignmentLine)
                afterCrossAxisAlignmentLine =
                    maxOf(afterCrossAxisAlignmentLine, placeable.crossAxisSize() - alignmentLine)
            }
        }
    }

    val mainAxisLayoutSize =
        saturatedInt(
            fixedSpace + weightedSpace,
        ).coerceAtLeast(0).coerceIn(mainAxisMin, mainAxisMax)
    val crossAxisLayoutSize =
        maxOf(
            crossAxisSpace,
            crossAxisMin,
            beforeCrossAxisAlignmentLine + afterCrossAxisAlignmentLine,
        ).coerceIn(crossAxisMin, crossAxisMax)
    return with(measureScope) {
        layoutResult(
            mainAxisLayoutSize,
            crossAxisLayoutSize,
            placeables,
            childrenMainAxisSize,
            measurables,
            beforeCrossAxisAlignmentLine,
        )
    }
}

/** AndroidX's intrinsic main-axis calculation, retaining Swing's saturating coordinate arithmetic. */
private fun RowColumnMeasurePolicy.intrinsicMainAxisSize(
    measurables: List<IntrinsicMeasurable>,
    mainAxisSize: (IntrinsicMeasurable) -> Int,
): Int {
    var fixedSpace = 0L
    var totalWeight = 0f
    var weightUnitSpace = 0
    for (child in measurables) {
        val childMainAxisSize = child.cappedIntrinsicMainAxisSize(mainAxisSize(child), this)
        val weight = child.linearConstraint?.weight
        if (weight == null) {
            fixedSpace = saturatedLong(fixedSpace + childMainAxisSize)
        } else {
            totalWeight += weight.weight
            weightUnitSpace = maxOf(weightUnitSpace, (childMainAxisSize / weight.weight).roundToInt())
        }
    }
    fixedSpace = saturatedLong(fixedSpace + (weightUnitSpace * totalWeight).roundToIntOrZero())
    if (measurables.isNotEmpty()) {
        fixedSpace =
            saturatedLong(fixedSpace + arrangementSpacing.toLong() * (measurables.size - 1))
    }
    return saturatedInt(fixedSpace).coerceAtLeast(0)
}

/** AndroidX's intrinsic cross-axis calculation, retaining Swing's maximum-size ceiling. */
@Suppress("CyclomaticComplexMethod")
private fun RowColumnMeasurePolicy.intrinsicCrossAxisSize(
    measurables: List<IntrinsicMeasurable>,
    mainAxisSize: (IntrinsicMeasurable, Int) -> Int,
    crossAxisSize: (IntrinsicMeasurable, Int) -> Int,
    mainAxisAvailable: Int,
): Int {
    if (measurables.isEmpty()) return 0
    var fixedSpace = minOf((measurables.size - 1) * arrangementSpacing, mainAxisAvailable)
    var crossAxisMaximum = 0
    var totalWeight = 0f
    var beforeCrossAxisAlignmentLine = 0
    var afterCrossAxisAlignmentLine = 0
    for (child in measurables) {
        val weight = child.linearConstraint?.weight?.weight ?: 0f
        if (weight == 0f) {
            val remaining = if (mainAxisAvailable == Int.MAX_VALUE) Int.MAX_VALUE else mainAxisAvailable - fixedSpace
            val childMainAxisSize = minOf(mainAxisSize(child, Int.MAX_VALUE), remaining)
            fixedSpace += childMainAxisSize
            val childCrossAxisSize = child.cappedIntrinsicCrossAxisSize(crossAxisSize(child, childMainAxisSize), this)
            crossAxisMaximum = maxOf(crossAxisMaximum, childCrossAxisSize)
            if (child.linearConstraint?.alignment == BaselineAxisAlignment) {
                val baseline = child.intrinsicBaseline(childMainAxisSize, childCrossAxisSize)
                if (baseline >= 0) {
                    beforeCrossAxisAlignmentLine = maxOf(beforeCrossAxisAlignmentLine, baseline)
                    afterCrossAxisAlignmentLine = maxOf(afterCrossAxisAlignmentLine, childCrossAxisSize - baseline)
                }
            }
        } else if (weight > 0f) {
            totalWeight += weight
        }
    }
    val weightUnitSpace =
        when {
            totalWeight == 0f -> 0
            mainAxisAvailable == Int.MAX_VALUE -> Int.MAX_VALUE
            else -> ((mainAxisAvailable - fixedSpace).coerceAtLeast(0) / totalWeight).roundToIntOrZero()
        }
    for (child in measurables) {
        val weight = child.linearConstraint?.weight?.weight ?: 0f
        if (weight > 0f) {
            val childMainAxisSize =
                if (weightUnitSpace == Int.MAX_VALUE) {
                    Int.MAX_VALUE
                } else {
                    (weightUnitSpace * weight).roundToIntOrZero()
                }
            val childCrossAxisSize = child.cappedIntrinsicCrossAxisSize(crossAxisSize(child, childMainAxisSize), this)
            crossAxisMaximum = maxOf(crossAxisMaximum, childCrossAxisSize)
            if (child.linearConstraint?.alignment == BaselineAxisAlignment) {
                val baseline = child.intrinsicBaseline(childMainAxisSize, childCrossAxisSize)
                if (baseline >= 0) {
                    beforeCrossAxisAlignmentLine = maxOf(beforeCrossAxisAlignmentLine, baseline)
                    afterCrossAxisAlignmentLine = maxOf(afterCrossAxisAlignmentLine, childCrossAxisSize - baseline)
                }
            }
        }
    }
    return maxOf(crossAxisMaximum, beforeCrossAxisAlignmentLine + afterCrossAxisAlignmentLine)
}

/** Compose's fast rounding maps NaN to zero; Kotlin's [roundToInt] instead throws for it. */
private fun Float.roundToIntOrZero(): Int = if (isNaN()) 0 else roundToInt()

/** The intrinsic contract has no baseline; only Swing's live measurables can supply the bridge. */
private fun IntrinsicMeasurable.intrinsicBaseline(
    width: Int,
    height: Int,
): Int = (this as? Measurable)?.baseline(width, height) ?: -1

/**
 * Replays a live Swing child's modifier chain under its explicit maximum size, which CMP's abstract
 * intrinsic contract cannot express. This preserves the constrained bridge for aspect ratio and the
 * baseline query while non-Swing intrinsic measurables keep using their supplied answer.
 */
private fun RowColumnMeasurePolicy.measureIntrinsicChild(
    child: IntrinsicMeasurable,
    mainAxisMaximum: Int,
): Placeable? {
    val measurable = child as? ChildMeasurable ?: return null
    val component = measurable.component
    val maximum = component.maximumSize
    val mainMaximum =
        if (component.isMaximumSizeSet) {
            minOf(mainAxisMaximum, componentMainAxisMaximum(maximum).coerceAtLeast(0))
        } else {
            mainAxisMaximum
        }
    val crossMaximum =
        if (component.isMaximumSizeSet) componentCrossAxisMaximum(maximum).coerceAtLeast(0) else Int.MAX_VALUE
    return measurable.measure(createConstraints(0, 0, mainMaximum, crossMaximum))
}

private fun RowColumnMeasurePolicy.intrinsicMainAxisSize(
    child: IntrinsicMeasurable,
    fallback: () -> Int,
): Int = measureIntrinsicChild(child, Int.MAX_VALUE)?.mainAxisSize() ?: fallback()

private fun RowColumnMeasurePolicy.intrinsicCrossAxisSize(
    child: IntrinsicMeasurable,
    mainAxisMaximum: Int,
    fallback: () -> Int,
): Int = measureIntrinsicChild(child, mainAxisMaximum)?.crossAxisSize() ?: fallback()

/** AndroidX's row policy adapted to Swing's integer arrangements and physical component orientation. */
internal data class RowMeasurePolicy(
    private val horizontalArrangement: Arrangement.Horizontal,
    private val verticalAlignment: Alignment.Vertical,
) : RowColumnMeasurePolicy {
    override val arrangementSpacing: Int get() = horizontalArrangement.spacing

    override fun Placeable.mainAxisSize(): Int = width

    override fun Placeable.crossAxisSize(): Int = height

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult =
        measureRowColumn(
            this,
            constraints.minWidth,
            constraints.minHeight,
            constraints.maxWidth,
            constraints.maxHeight,
            horizontalArrangement.spacing,
            measurables,
        )

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int =
        intrinsicMainAxisSize(measurables) { child ->
            intrinsicMainAxisSize(child) { child.minIntrinsicWidth(height) }
        }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int =
        intrinsicMainAxisSize(measurables) { child ->
            intrinsicMainAxisSize(child) { child.maxIntrinsicWidth(height) }
        }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int =
        intrinsicCrossAxisSize(
            measurables,
            mainAxisSize = { child, height -> intrinsicMainAxisSize(child) { child.maxIntrinsicWidth(height) } },
            crossAxisSize = {
                child,
                childWidth,
                ->
                intrinsicCrossAxisSize(child, childWidth) { child.minIntrinsicHeight(childWidth) }
            },
            mainAxisAvailable = width,
        )

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int =
        intrinsicCrossAxisSize(
            measurables,
            mainAxisSize = { child, height -> intrinsicMainAxisSize(child) { child.maxIntrinsicWidth(height) } },
            crossAxisSize = {
                child,
                childWidth,
                ->
                intrinsicCrossAxisSize(child, childWidth) { child.maxIntrinsicHeight(childWidth) }
            },
            mainAxisAvailable = width,
        )

    override fun createConstraints(
        mainAxisMin: Int,
        crossAxisMin: Int,
        mainAxisMax: Int,
        crossAxisMax: Int,
    ): Constraints = Constraints(mainAxisMin, mainAxisMax, crossAxisMin, crossAxisMax)

    override fun componentMainAxisMaximum(component: Dimension): Int = component.width

    override fun componentCrossAxisMaximum(component: Dimension): Int = component.height

    override fun MeasureScope.layoutResult(
        mainAxisLayoutSize: Int,
        crossAxisLayoutSize: Int,
        placeables: Array<Placeable?>,
        childrenMainAxisSize: IntArray,
        measurables: List<Measurable>,
        beforeCrossAxisAlignmentLine: Int,
    ): MeasureResult =
        layout(mainAxisLayoutSize, crossAxisLayoutSize) {
            val positions = IntArray(childrenMainAxisSize.size)
            horizontalArrangement.arrange(mainAxisLayoutSize, childrenMainAxisSize, orientation, positions)
            placeables.forEachIndexed { index, placeable ->
                val measured = checkNotNull(placeable)
                val crossAxisPosition =
                    if (measurables[index].linearConstraint?.alignment == BaselineAxisAlignment) {
                        measurables[index]
                            .baseline(measured.width, measured.height)
                            .takeIf { it >= 0 }
                            ?.let { beforeCrossAxisAlignmentLine - it } ?: 0
                    } else {
                        (measurables[index].linearConstraint?.alignment ?: VerticalAxisAlignment(verticalAlignment))
                            .align(measured.height, crossAxisLayoutSize, orientation)
                    }
                measured.place(positions[index], crossAxisPosition)
            }
        }
}

/** AndroidX's column policy adapted to Swing's integer arrangements and physical component orientation. */
internal data class ColumnMeasurePolicy(
    private val verticalArrangement: Arrangement.Vertical,
    private val horizontalAlignment: Alignment.Horizontal,
) : RowColumnMeasurePolicy {
    override val arrangementSpacing: Int get() = verticalArrangement.spacing

    override fun Placeable.mainAxisSize(): Int = height

    override fun Placeable.crossAxisSize(): Int = width

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult =
        measureRowColumn(
            this,
            constraints.minHeight,
            constraints.minWidth,
            constraints.maxHeight,
            constraints.maxWidth,
            verticalArrangement.spacing,
            measurables,
        )

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int =
        intrinsicCrossAxisSize(
            measurables,
            mainAxisSize = { child, width -> intrinsicMainAxisSize(child) { child.maxIntrinsicHeight(width) } },
            crossAxisSize = {
                child,
                childHeight,
                ->
                intrinsicCrossAxisSize(child, childHeight) { child.minIntrinsicWidth(childHeight) }
            },
            mainAxisAvailable = height,
        )

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int =
        intrinsicCrossAxisSize(
            measurables,
            mainAxisSize = { child, width -> intrinsicMainAxisSize(child) { child.maxIntrinsicHeight(width) } },
            crossAxisSize = {
                child,
                childHeight,
                ->
                intrinsicCrossAxisSize(child, childHeight) { child.maxIntrinsicWidth(childHeight) }
            },
            mainAxisAvailable = height,
        )

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int =
        intrinsicMainAxisSize(measurables) { child ->
            intrinsicMainAxisSize(child) { child.minIntrinsicHeight(width) }
        }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int =
        intrinsicMainAxisSize(measurables) { child ->
            intrinsicMainAxisSize(child) { child.maxIntrinsicHeight(width) }
        }

    override fun createConstraints(
        mainAxisMin: Int,
        crossAxisMin: Int,
        mainAxisMax: Int,
        crossAxisMax: Int,
    ): Constraints = Constraints(crossAxisMin, crossAxisMax, mainAxisMin, mainAxisMax)

    override fun componentMainAxisMaximum(component: Dimension): Int = component.height

    override fun componentCrossAxisMaximum(component: Dimension): Int = component.width

    override fun MeasureScope.layoutResult(
        mainAxisLayoutSize: Int,
        crossAxisLayoutSize: Int,
        placeables: Array<Placeable?>,
        childrenMainAxisSize: IntArray,
        measurables: List<Measurable>,
        beforeCrossAxisAlignmentLine: Int,
    ): MeasureResult =
        layout(crossAxisLayoutSize, mainAxisLayoutSize) {
            val positions = IntArray(childrenMainAxisSize.size)
            verticalArrangement.arrange(mainAxisLayoutSize, childrenMainAxisSize, positions)
            placeables.forEachIndexed { index, placeable ->
                val measured = checkNotNull(placeable)
                val crossAxisPosition =
                    (measurables[index].linearConstraint?.alignment ?: HorizontalAxisAlignment(horizontalAlignment))
                        .align(measured.width, crossAxisLayoutSize, orientation)
                measured.place(crossAxisPosition, positions[index])
            }
        }
}

/** What a child declared to a Row or Column. */
private val IntrinsicMeasurable.linearConstraint: LinearConstraint? get() = parentData as? LinearConstraint

/** Applies a component's explicit Swing maximum size to a physical constraint offer. */
private fun Measurable.constraintsWithMaximumSize(constraints: Constraints): Constraints {
    val component = component
    if (!component.isMaximumSizeSet) return constraints
    val maximum = component.maximumSize
    val maxWidth = minOf(constraints.maxWidth, maximum.width.coerceAtLeast(0))
    val maxHeight = minOf(constraints.maxHeight, maximum.height.coerceAtLeast(0))
    return Constraints(
        minWidth = minOf(constraints.minWidth, maxWidth),
        maxWidth = maxWidth,
        minHeight = minOf(constraints.minHeight, maxHeight),
        maxHeight = maxHeight,
    )
}

/** The weight allocation held to the child's explicit Swing maximum size along this policy's main axis. */
private fun Measurable.cappedMainAxisSize(
    allocated: Int,
    policy: RowColumnMeasurePolicy,
): Int =
    if (component.isMaximumSizeSet) {
        minOf(
            allocated,
            policy.componentMainAxisMaximum(component.maximumSize).coerceAtLeast(0),
        )
    } else {
        allocated
    }

/** Applies the same Swing maximum-size ceiling to a direct child's intrinsic axis query. */
private fun IntrinsicMeasurable.cappedIntrinsicMainAxisSize(
    size: Int,
    policy: RowColumnMeasurePolicy,
): Int =
    componentOrNull
        ?.takeIf { it.isMaximumSizeSet }
        ?.let { minOf(size, policy.componentMainAxisMaximum(it.maximumSize).coerceAtLeast(0)) }
        ?: size

/** Applies the same Swing maximum-size ceiling to a direct child's intrinsic cross-axis query. */
private fun IntrinsicMeasurable.cappedIntrinsicCrossAxisSize(
    size: Int,
    policy: RowColumnMeasurePolicy,
): Int =
    componentOrNull
        ?.takeIf { it.isMaximumSizeSet }
        ?.let { minOf(size, policy.componentCrossAxisMaximum(it.maximumSize).coerceAtLeast(0)) }
        ?: size

/** Holds arithmetic at Swing's representable signed coordinate range rather than letting it wrap. */
private fun saturatedLong(value: Long): Long = value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())

/** Holds an extent at Swing's representable signed range before the caller applies its own lower bound. */
private fun saturatedInt(value: Long): Int = saturatedLong(value).toInt()

/** The message refusing a constraint a Row or Column cannot interpret. */
private fun foreignConstraint(
    child: Component,
    constraint: Any?,
): String =
    "A Row or Column places a child by the arrangement and alignment it is declared with, and by " +
        "weight() / align() on the child's own modifier, so '$child' can carry no layout constraint, " +
        "but it was added under '$constraint'."

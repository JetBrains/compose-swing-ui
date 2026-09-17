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
 *
 * Adapted from androidx.compose.foundation.layout.RowColumnMeasurePolicy in AndroidX's
 * foundation-layout; see this module's META-INF/NOTICE for the synced version.
 * intrinsicMainAxisSize/intrinsicCrossAxisSize (from RowColumnImpl.kt, 2019) and
 * RowMeasurePolicy/ColumnMeasurePolicy (from Row.kt/Column.kt, 2020) are upstream's too.
 */

// Suppressed: every function here is a step of the one Row and Column measurement algorithm, with its intrinsic
// passes and the saturating arithmetic they share.
@file:Suppress("TooManyFunctions")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.util.fastForEach
import java.awt.ComponentOrientation
import java.awt.Dimension
import kotlin.math.roundToInt
import kotlin.math.sign

/** The shared AndroidX RowColumnMeasurePolicy shape, with Swing placement adaptations. */
internal interface RowColumnMeasurePolicy : MeasurePolicy {
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

    /**
     * [IntrinsicMeasurable.intrinsicPlaceable] naming this policy's axes: main by cross for a Row, cross by main
     * for a Column.
     */
    fun IntrinsicMeasurable.intrinsicPlaceableAt(
        main: Int,
        cross: Int,
    ): Placeable?

    @Suppress("LongParameterList")
    fun MeasureScope.layoutResult(
        mainAxisLayoutSize: Int,
        crossAxisLayoutSize: Int,
        placeables: Array<Placeable?>,
        childrenMainAxisSize: IntArray,
        measurables: List<Measurable>,
        beforeCrossAxisAlignmentLine: Int,
    ): MeasureResult
}

/**
 * Measures a Row or Column with AndroidX foundation-layout's two-pass algorithm (see this
 * module's META-INF/NOTICE for the synced version). The only differences are Swing's
 * maximum-size ceiling, integer arrangements, and saturating coordinate arithmetic.
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
    measurables: List<Measurable>,
): MeasureResult {
    val placeables = arrayOfNulls<Placeable>(measurables.size)
    var totalWeight = 0f
    var fixedSpace = 0L
    var crossAxisSpace = 0
    var weightedChildrenCount = 0
    var anyAlignBy = false
    val childrenMainAxisSize = IntArray(measurables.size)
    var beforeCrossAxisAlignmentLine = 0
    var afterCrossAxisAlignmentLine = 0
    var spaceAfterLastNoWeight = 0

    for (index in measurables.indices) {
        val child = measurables[index]
        val parentData = child.linearConstraint
        val weight = parentData?.weight
        anyAlignBy = anyAlignBy || parentData?.alignment?.isRelative == true
        if (weight != null) {
            totalWeight += weight.weight
            weightedChildrenCount++
            continue
        }
        val remaining = saturateLayoutCoordinate(mainAxisMax.toLong() - fixedSpace).coerceAtLeast(0)
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
        fixedSpace = saturateLayoutCoordinate(fixedSpace + mainAxisSize + spaceAfterLastNoWeight).toLong()
        crossAxisSpace = maxOf(crossAxisSpace, placeable.crossAxisSize())
        placeables[index] = placeable
    }

    var weightedSpace = 0L
    if (weightedChildrenCount == 0) {
        fixedSpace = saturateLayoutCoordinate(fixedSpace - spaceAfterLastNoWeight).toLong()
    } else {
        val targetSpace = if (mainAxisMax != Int.MAX_VALUE) mainAxisMax else mainAxisMin
        val arrangementSpacingTotal = arrangementSpacing.toLong() * (weightedChildrenCount - 1)
        val remainingToTarget =
            saturateLayoutCoordinate(
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
                saturateLayoutCoordinate(
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
            weightedSpace = saturateLayoutCoordinate(weightedSpace + placeable.mainAxisSize()).toLong()
            crossAxisSpace = maxOf(crossAxisSpace, placeable.crossAxisSize())
            placeables[index] = placeable
        }
        weightedSpace =
            saturateLayoutCoordinate(weightedSpace + arrangementSpacingTotal)
                .toLong()
                .coerceIn(0L, (mainAxisMax.toLong() - fixedSpace).coerceAtLeast(0L))
    }

    if (anyAlignBy) {
        for (index in measurables.indices) {
            val placeable = checkNotNull(placeables[index])
            val alignmentLinePosition =
                measurables[index].linearConstraint?.alignment?.linePosition(placeable) ?: AlignmentLine.UNSPECIFIED
            if (alignmentLinePosition != AlignmentLine.UNSPECIFIED) {
                beforeCrossAxisAlignmentLine = maxOf(beforeCrossAxisAlignmentLine, alignmentLinePosition)
                afterCrossAxisAlignmentLine =
                    maxOf(afterCrossAxisAlignmentLine, placeable.crossAxisSize() - alignmentLinePosition)
            }
        }
    }

    val mainAxisLayoutSize = saturateLayoutCoordinate(fixedSpace + weightedSpace).coerceAtLeast(mainAxisMin)
    val crossAxisLayoutSize =
        maxOf(crossAxisSpace, crossAxisMin, beforeCrossAxisAlignmentLine + afterCrossAxisAlignmentLine)
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
    crossAxisAvailable: Int,
    mainAxisSize: (IntrinsicMeasurable, Int) -> Int,
): Int {
    var fixedSpace = 0L
    var totalWeight = 0f
    var weightUnitSpace = 0
    measurables.fastForEach { child ->
        val crossAxisSize = child.cappedCrossAxisSize(crossAxisAvailable, this)
        val childMainAxisSize = child.cappedMainAxisSize(mainAxisSize(child, crossAxisSize), this)
        val weight = child.linearConstraint?.weight
        if (weight == null) {
            fixedSpace = saturateLayoutCoordinate(fixedSpace + childMainAxisSize).toLong()
        } else {
            totalWeight += weight.weight
            weightUnitSpace = maxOf(weightUnitSpace, (childMainAxisSize / weight.weight).roundToInt())
        }
    }
    fixedSpace = saturateLayoutCoordinate(fixedSpace + (weightUnitSpace * totalWeight).roundToIntOrZero()).toLong()
    if (measurables.isNotEmpty()) {
        fixedSpace =
            saturateLayoutCoordinate(fixedSpace + arrangementSpacing.toLong() * (measurables.size - 1)).toLong()
    }
    return saturateLayoutCoordinate(fixedSpace).coerceAtLeast(0)
}

/** AndroidX's intrinsic cross-axis calculation, with Swing's maximum-size ceiling and alignment-line extent. */
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
    measurables.fastForEach { child ->
        val weight = child.linearConstraint?.weight?.weight ?: 0f
        if (weight == 0f) {
            val remaining = if (mainAxisAvailable == Int.MAX_VALUE) Int.MAX_VALUE else mainAxisAvailable - fixedSpace
            val crossAxisAvailable = child.cappedCrossAxisSize(Int.MAX_VALUE, this)
            val childMainAxisSize =
                minOf(child.cappedMainAxisSize(mainAxisSize(child, crossAxisAvailable), this), remaining)
            fixedSpace += childMainAxisSize
            val childCrossAxisSize = child.cappedCrossAxisSize(crossAxisSize(child, childMainAxisSize), this)
            crossAxisMaximum = maxOf(crossAxisMaximum, childCrossAxisSize)
            val line = alignmentLineOrUnspecified(child, childMainAxisSize, childCrossAxisSize)
            if (line != AlignmentLine.UNSPECIFIED) {
                beforeCrossAxisAlignmentLine = maxOf(beforeCrossAxisAlignmentLine, line)
                afterCrossAxisAlignmentLine = maxOf(afterCrossAxisAlignmentLine, childCrossAxisSize - line)
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
    measurables.fastForEach { child ->
        val weight = child.linearConstraint?.weight?.weight ?: 0f
        if (weight > 0f) {
            val weightedMainAxisSize =
                if (weightUnitSpace == Int.MAX_VALUE) {
                    Int.MAX_VALUE
                } else {
                    (weightUnitSpace * weight).roundToIntOrZero()
                }
            val childMainAxisSize = child.cappedMainAxisSize(weightedMainAxisSize, this)
            val childCrossAxisSize = child.cappedCrossAxisSize(crossAxisSize(child, childMainAxisSize), this)
            crossAxisMaximum = maxOf(crossAxisMaximum, childCrossAxisSize)
            val line = alignmentLineOrUnspecified(child, childMainAxisSize, childCrossAxisSize)
            if (line != AlignmentLine.UNSPECIFIED) {
                beforeCrossAxisAlignmentLine = maxOf(beforeCrossAxisAlignmentLine, line)
                afterCrossAxisAlignmentLine = maxOf(afterCrossAxisAlignmentLine, childCrossAxisSize - line)
            }
        }
    }
    return maxOf(crossAxisMaximum, beforeCrossAxisAlignmentLine + afterCrossAxisAlignmentLine)
}

/**
 * [child]'s alignment line at [mainAxisSize] by [crossAxisSize], or [AlignmentLine.UNSPECIFIED] where it aligns by
 * extent.
 */
private fun RowColumnMeasurePolicy.alignmentLineOrUnspecified(
    child: IntrinsicMeasurable,
    mainAxisSize: Int,
    crossAxisSize: Int,
): Int {
    val alignment = child.linearConstraint?.alignment
    return if (alignment?.isRelative == true) {
        intrinsicAlignmentLine(child, alignment, mainAxisSize, crossAxisSize)
    } else {
        AlignmentLine.UNSPECIFIED
    }
}

/** androidx's fast rounding maps NaN to zero; Kotlin's [roundToInt] instead throws for it. */
private fun Float.roundToIntOrZero(): Int = if (isNaN()) 0 else roundToInt()

/**
 * Where [alignment] puts the child's line when it is [mainAxisSize] along the main axis and [crossAxisSize] across it,
 * read from the child's [stand-in][IntrinsicMeasurable.intrinsicPlaceable], since the intrinsic contract has no
 * alignment line; [AlignmentLine.UNSPECIFIED] where the child has no stand-in.
 */
private fun RowColumnMeasurePolicy.intrinsicAlignmentLine(
    child: IntrinsicMeasurable,
    alignment: CrossAxisAlignment,
    mainAxisSize: Int,
    crossAxisSize: Int,
): Int {
    val placeable = child.intrinsicPlaceableAt(mainAxisSize, crossAxisSize) ?: return AlignmentLine.UNSPECIFIED
    return alignment.linePosition(placeable)
}

/** AndroidX's row policy adapted to Swing's integer arrangements and physical component orientation. */
internal data class RowMeasurePolicy(
    private val horizontalArrangement: Arrangement.Horizontal,
    private val verticalAlignment: Alignment.Vertical,
) : RowColumnMeasurePolicy {
    override val arrangementSpacing: Int get() = horizontalArrangement.spacing

    private val crossAxisAlignment: CrossAxisAlignment = VerticalAxisAlignment(verticalAlignment)

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
            measurables,
        )

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int =
        intrinsicMainAxisSize(measurables, height) { child, crossAxisSize ->
            child.minIntrinsicWidth(crossAxisSize)
        }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int =
        intrinsicMainAxisSize(measurables, height) { child, crossAxisSize ->
            child.maxIntrinsicWidth(crossAxisSize)
        }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int =
        intrinsicCrossAxisSize(
            measurables,
            mainAxisSize = { child, height -> child.maxIntrinsicWidth(height) },
            crossAxisSize = {
                child,
                childWidth,
                ->
                child.minIntrinsicHeight(childWidth)
            },
            mainAxisAvailable = width,
        )

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int =
        intrinsicCrossAxisSize(
            measurables,
            mainAxisSize = { child, height -> child.maxIntrinsicWidth(height) },
            crossAxisSize = {
                child,
                childWidth,
                ->
                child.maxIntrinsicHeight(childWidth)
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

    override fun IntrinsicMeasurable.intrinsicPlaceableAt(
        main: Int,
        cross: Int,
    ): Placeable? = intrinsicPlaceable(main, cross)

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
                    (measurables[index].linearConstraint?.alignment ?: crossAxisAlignment).align(
                        measured.height,
                        crossAxisLayoutSize,
                        ComponentOrientation.LEFT_TO_RIGHT,
                        measured,
                        beforeCrossAxisAlignmentLine,
                    )
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

    private val crossAxisAlignment: CrossAxisAlignment = HorizontalAxisAlignment(horizontalAlignment)

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
            measurables,
        )

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int =
        intrinsicCrossAxisSize(
            measurables,
            mainAxisSize = { child, width -> child.maxIntrinsicHeight(width) },
            crossAxisSize = {
                child,
                childHeight,
                ->
                child.minIntrinsicWidth(childHeight)
            },
            mainAxisAvailable = height,
        )

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int =
        intrinsicCrossAxisSize(
            measurables,
            mainAxisSize = { child, width -> child.maxIntrinsicHeight(width) },
            crossAxisSize = {
                child,
                childHeight,
                ->
                child.maxIntrinsicWidth(childHeight)
            },
            mainAxisAvailable = height,
        )

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int =
        intrinsicMainAxisSize(measurables, width) { child, crossAxisSize ->
            child.minIntrinsicHeight(crossAxisSize)
        }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int =
        intrinsicMainAxisSize(measurables, width) { child, crossAxisSize ->
            child.maxIntrinsicHeight(crossAxisSize)
        }

    override fun createConstraints(
        mainAxisMin: Int,
        crossAxisMin: Int,
        mainAxisMax: Int,
        crossAxisMax: Int,
    ): Constraints = Constraints(crossAxisMin, crossAxisMax, mainAxisMin, mainAxisMax)

    override fun componentMainAxisMaximum(component: Dimension): Int = component.height

    override fun componentCrossAxisMaximum(component: Dimension): Int = component.width

    override fun IntrinsicMeasurable.intrinsicPlaceableAt(
        main: Int,
        cross: Int,
    ): Placeable? = intrinsicPlaceable(cross, main)

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
                    (measurables[index].linearConstraint?.alignment ?: crossAxisAlignment).align(
                        measured.width,
                        crossAxisLayoutSize,
                        orientation,
                        measured,
                        beforeCrossAxisAlignmentLine,
                    )
                measured.place(crossAxisPosition, positions[index])
            }
        }
}

/** What a child declared to a Row or Column. */
private val IntrinsicMeasurable.linearConstraint: LinearConstraint? get() = parentData as? LinearConstraint

/** Applies a child's explicit Swing maximum size to a constraint offer. */
private fun Measurable.constraintsWithMaximumSize(constraints: Constraints): Constraints {
    val maximum = maximumSize() ?: return constraints
    val maxWidth = minOf(constraints.maxWidth, maximum.width.coerceAtLeast(0))
    val maxHeight = minOf(constraints.maxHeight, maximum.height.coerceAtLeast(0))
    return Constraints(
        minWidth = minOf(constraints.minWidth, maxWidth),
        maxWidth = maxWidth,
        minHeight = minOf(constraints.minHeight, maxHeight),
        maxHeight = maxHeight,
    )
}

/** [size] held to the child's explicit Swing maximum size along this policy's main axis. */
private fun IntrinsicMeasurable.cappedMainAxisSize(
    size: Int,
    policy: RowColumnMeasurePolicy,
): Int = maximumSize()?.let { minOf(size, policy.componentMainAxisMaximum(it).coerceAtLeast(0)) } ?: size

/** [size] held to the child's explicit Swing maximum size across this policy's main axis. */
private fun IntrinsicMeasurable.cappedCrossAxisSize(
    size: Int,
    policy: RowColumnMeasurePolicy,
): Int = maximumSize()?.let { minOf(size, policy.componentCrossAxisMaximum(it).coerceAtLeast(0)) } ?: size

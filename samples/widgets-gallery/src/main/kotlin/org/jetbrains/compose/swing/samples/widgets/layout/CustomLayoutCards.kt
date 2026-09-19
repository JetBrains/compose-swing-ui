package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.BoxScope
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.foundation.layout.Measurable
import org.jetbrains.compose.swing.foundation.layout.MeasurePolicy
import org.jetbrains.compose.swing.foundation.layout.MeasureResult
import org.jetbrains.compose.swing.foundation.layout.MeasureScope
import org.jetbrains.compose.swing.foundation.layout.Placeable
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.onPlaced
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle

@Preview
@Composable
internal fun LayoutMechanicsSection() {
    SectionColumn {
        SectionHeading("Layout mechanics")
        CustomLayoutCards()
    }
}

@Composable
internal fun ColumnScope.CustomLayoutCards() {
    MeasurePolicyCard()
    DirectionAwareModifierCard()
    ConstraintSizingCard()
}

@Composable
private fun ColumnScope.MeasurePolicyCard() {
    ExampleCard("Layout + MeasurePolicy (relative and physical placement)") {
        var gap by remember { mutableStateOf(CUSTOM_GAPS.first()) }
        var rightToLeft by remember { mutableStateOf(false) }
        var relativeBounds by remember { mutableStateOf<Rectangle?>(null) }
        var physicalBounds by remember { mutableStateOf<Rectangle?>(null) }

        Row(
            modifier = SwingModifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBox(
                text = "Right-to-left placement",
                checked = rightToLeft,
                onCheckedChange = { rightToLeft = it },
            )
            Label(
                "placeRelative ${boundsText(relativeBounds)} · place ${boundsText(physicalBounds)}",
                modifier = SwingModifier.weight(1f),
            )
        }
        Layout(
            content = {
                InteractiveLayoutSwatch(
                    text = "<html><center>placeRelative(0,y)<br>gap=$gap · click</center></html>",
                    color = LayoutSampleColors.Blue,
                    onClick = { gap = CUSTOM_GAPS[(CUSTOM_GAPS.indexOf(gap) + 1) % CUSTOM_GAPS.size] },
                    modifier =
                        SwingModifier
                            .preferredSize(CUSTOM_SWATCH_SIZE)
                            .accessibleName("placeRelative, gap $gap; activate to cycle")
                            .toolTip("Follows reading order; click to cycle the gap")
                            .onPlaced { relativeBounds = it },
                )
                LayoutSwatch(
                    "<html><center>place(width -<br>child.width,y)</center></html>",
                    LayoutSampleColors.Green,
                    SwingModifier
                        .preferredSize(CUSTOM_SWATCH_SIZE)
                        .toolTip("Uses physical coordinates and stays on the right")
                        .onPlaced { physicalBounds = it },
                )
            },
            modifier =
                SwingModifier
                    .layoutTrack()
                    .preferredSize(CUSTOM_LAYOUT_SIZE)
                    .componentOrientation(
                        if (rightToLeft) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT,
                    ),
            measurePolicy = stackedPlacementPolicy(gap),
        )
    }
}

@Composable
private fun ColumnScope.DirectionAwareModifierCard() {
    ExampleCard("padding / offset vs absolutePadding / absoluteOffset") {
        var rightToLeft by remember { mutableStateOf(false) }
        var paddingBounds by remember { mutableStateOf<Rectangle?>(null) }
        var absolutePaddingBounds by remember { mutableStateOf<Rectangle?>(null) }
        var offsetBounds by remember { mutableStateOf<Rectangle?>(null) }
        var absoluteOffsetBounds by remember { mutableStateOf<Rectangle?>(null) }
        Button(
            text = "Orientation: ${if (rightToLeft) "RTL" else "LTR"}",
            onClick = { rightToLeft = !rightToLeft },
            modifier = SwingModifier.toolTip("Logical declarations mirror; absolute declarations stay physical"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8)) {
            DirectionalSample(rightToLeft, DirectionalModifier.Padding, paddingBounds) { paddingBounds = it }
            DirectionalSample(rightToLeft, DirectionalModifier.AbsolutePadding, absolutePaddingBounds) {
                absolutePaddingBounds = it
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8)) {
            DirectionalSample(rightToLeft, DirectionalModifier.Offset, offsetBounds) { offsetBounds = it }
            DirectionalSample(rightToLeft, DirectionalModifier.AbsoluteOffset, absoluteOffsetBounds) {
                absoluteOffsetBounds = it
            }
        }
    }
}

@Composable
private fun ColumnScope.ConstraintSizingCard() {
    ExampleCard("aspectRatio / defaultMinSize under one changing parent") {
        var compactParent by remember { mutableStateOf(false) }
        var aspectRatioIndex by remember { mutableStateOf(ASPECT_RATIOS.lastIndex) }
        var aspectBounds by remember { mutableStateOf<Rectangle?>(null) }
        var minimumBounds by remember { mutableStateOf<Rectangle?>(null) }
        val parentSize = if (compactParent) COMPACT_PARENT_SIZE else ROOMY_PARENT_SIZE
        val aspectRatio = ASPECT_RATIOS[aspectRatioIndex]
        val aspectRatioText = aspectRatio.toString().removeSuffix(".0")

        Row(
            modifier = SwingModifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                text = "Parent: ${parentSize.width} × ${parentSize.height}",
                onClick = { compactParent = !compactParent },
                modifier =
                    SwingModifier
                        .preferredSize(SIZE_CONTROL_SIZE)
                        .toolTip(if (compactParent) "Expand both parents" else "Tighten both parents"),
            )
            Label(
                "aspectRatio($aspectRatioText): ${boundsText(aspectBounds)} · " +
                    "defaultMinSize: ${boundsText(minimumBounds)}",
                modifier = SwingModifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8)) {
            Box(
                modifier = SwingModifier.layoutTrack().preferredSize(parentSize),
                contentAlignment = Alignment.TopCenter,
            ) {
                InteractiveLayoutSwatch(
                    text = aspectRatioText,
                    color = LayoutSampleColors.Pink,
                    onClick = { aspectRatioIndex = (aspectRatioIndex + 1) % ASPECT_RATIOS.size },
                    modifier =
                        SwingModifier
                            .aspectRatio(aspectRatio)
                            .accessibleName("aspectRatio ${aspectRatioText}f")
                            .toolTip("aspectRatio(${aspectRatioText}f): click to cycle")
                            .onPlaced { aspectBounds = it },
                )
            }
            Box(
                modifier = SwingModifier.layoutTrack().preferredSize(parentSize),
                contentAlignment = Alignment.TopCenter,
            ) {
                LayoutSwatch(
                    "<html><center>defaultMinSize<br>(120, 64)</center></html>",
                    LayoutSampleColors.Yellow,
                    SwingModifier
                        .defaultMinSize(MINIMUM_SIZE.width, MINIMUM_SIZE.height)
                        .onPlaced { minimumBounds = it },
                )
            }
        }
    }
}

@Composable
private fun RowScope.DirectionalSample(
    rightToLeft: Boolean,
    kind: DirectionalModifier,
    bounds: Rectangle?,
    onPlace: (Rectangle) -> Unit,
) {
    val orientation = if (rightToLeft) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT
    Box(
        modifier = SwingModifier.layoutTrack().preferredSize(DIRECTION_SAMPLE_SIZE).componentOrientation(orientation),
        contentAlignment = Alignment.TopCenter,
    ) {
        DirectionalSwatch(kind, bounds, onPlace)
    }
}

@Composable
private fun BoxScope.DirectionalSwatch(
    kind: DirectionalModifier,
    bounds: Rectangle?,
    onPlaced: (Rectangle) -> Unit,
) {
    val modifier =
        when (kind) {
            DirectionalModifier.Padding -> {
                SwingModifier.padding(start = DIRECTION_SHIFT, end = DIRECTION_GUTTER)
            }

            DirectionalModifier.AbsolutePadding -> {
                SwingModifier.absolutePadding(left = DIRECTION_SHIFT, right = DIRECTION_GUTTER)
            }

            DirectionalModifier.Offset -> {
                SwingModifier.offset(x = DIRECTION_SHIFT)
            }

            DirectionalModifier.AbsoluteOffset -> {
                SwingModifier.absoluteOffset(x = DIRECTION_SHIFT)
            }
        }.preferredSize(DIRECTION_SWATCH_SIZE)
            .toolTip(if (kind.relative) "Mirrors under RTL" else "Stays in the physical direction")
            .onPlaced(onPlaced)
    val x = bounds?.x?.toString() ?: "-"
    LayoutSwatch(
        "<html><center>${kind.title}<br>${kind.arguments}<br>x=$x</center></html>",
        if (kind.relative) LayoutSampleColors.Teal else LayoutSampleColors.Orange,
        modifier,
    )
}

private fun stackedPlacementPolicy(gap: Int): MeasurePolicy =
    MeasurePolicy { measurables, constraints ->
        measureStack(measurables, constraints, gap)
    }

private fun MeasureScope.measureStack(
    measurables: List<Measurable>,
    constraints: Constraints,
    gap: Int,
): MeasureResult {
    var remainingHeight = constraints.maxHeight
    val positioned =
        measurables.mapIndexed { index, measurable ->
            val placeable =
                measurable.measure(
                    Constraints(
                        maxWidth = constraints.maxWidth,
                        maxHeight = remainingHeight,
                    ),
                )
            val y = constraints.maxHeight - remainingHeight
            remainingHeight = (remainingHeight - placeable.height).coerceAtLeast(0)
            if (index < measurables.lastIndex) {
                remainingHeight = (remainingHeight - gap).coerceAtLeast(0)
            }
            PositionedPlaceable(placeable, y)
        }
    val width = constraints.constrainWidth(positioned.maxOfOrNull { it.placeable.width } ?: 0)
    val height = constraints.constrainHeight(constraints.maxHeight - remainingHeight)
    return layout(width, height) {
        positioned.forEachIndexed { index, child ->
            if (index == 0) {
                child.placeable.placeRelative(0, child.y)
            } else {
                child.placeable.place((width - child.placeable.width).coerceAtLeast(0), child.y)
            }
        }
    }
}

private fun boundsText(bounds: Rectangle?): String =
    bounds?.let { "x=${it.x}, y=${it.y}, ${it.width} × ${it.height}" } ?: "waiting for placement"

private data class PositionedPlaceable(
    val placeable: Placeable,
    val y: Int,
)

private enum class DirectionalModifier(
    val title: String,
    val arguments: String,
    val relative: Boolean,
) {
    Padding(
        title = "padding",
        arguments = "start 24, end 6",
        relative = true,
    ),
    AbsolutePadding(
        title = "absolutePadding",
        arguments = "left 24, right 6",
        relative = false,
    ),
    Offset(
        title = "offset",
        arguments = "x 24",
        relative = true,
    ),
    AbsoluteOffset(
        title = "absoluteOffset",
        arguments = "x 24",
        relative = false,
    ),
}

private val CUSTOM_GAPS = listOf(8, 18, 28)
private val ASPECT_RATIOS = listOf(0.5f, 1f, 2f)
private val CUSTOM_LAYOUT_SIZE = Dimension(320, 116)
private val CUSTOM_SWATCH_SIZE = Dimension(144, 40)
private val DIRECTION_SAMPLE_SIZE = Dimension(210, 64)
private val DIRECTION_SWATCH_SIZE = Dimension(170, 52)
private val SIZE_CONTROL_SIZE = Dimension(180, 30)
private val ROOMY_PARENT_SIZE = Dimension(200, 96)
private val COMPACT_PARENT_SIZE = Dimension(96, 48)
private val MINIMUM_SIZE = Dimension(120, 64)
private const val DIRECTION_SHIFT = 24
private const val DIRECTION_GUTTER = 6

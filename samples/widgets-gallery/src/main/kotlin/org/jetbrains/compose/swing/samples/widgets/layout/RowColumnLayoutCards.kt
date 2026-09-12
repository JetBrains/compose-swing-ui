package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.Color

@Composable
internal fun ColumnScope.RowCard() {
    ExampleCard("Row (fixed children + weight)") {
        var rowWidth by remember { mutableIntStateOf(0) }
        var weightedWidth by remember { mutableIntStateOf(0) }
        var fixedWidth by remember { mutableIntStateOf(88) }
        var weight by remember { mutableFloatStateOf(1f) }
        Label("Activate the fixed or weighted child to cycle its value.")
        Label("Row: $rowWidth px; weighted child: $weightedWidth px")
        Row(
            modifier =
                SwingModifier
                    .layoutTrack()
                    .fillMaxWidth()
                    .componentListener(onComponentResized = { rowWidth = it.component.width }),
            horizontalArrangement = Arrangement.spacedBy(8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InteractiveLayoutSwatch(
                "fixed · $fixedWidth px",
                LayoutSampleColors.Blue,
                onClick = {
                    fixedWidth = FIXED_WIDTHS[(FIXED_WIDTHS.indexOf(fixedWidth) + 1) % FIXED_WIDTHS.size]
                },
                modifier =
                    SwingModifier
                        .preferredSize(fixedWidth, 32)
                        .toolTip("Activate to cycle the fixed width"),
            )
            InteractiveLayoutSwatch(
                weight.label(),
                LayoutSampleColors.Orange,
                onClick = {
                    weight = WEIGHT_CHOICES[(WEIGHT_CHOICES.indexOf(weight) + 1) % WEIGHT_CHOICES.size]
                },
                modifier =
                    SwingModifier
                        .weight(weight)
                        .preferredSize(120, 40)
                        .componentListener(onComponentResized = { weightedWidth = it.component.width })
                        .toolTip("Activate to cycle the weight"),
            )
            LayoutSwatch(
                "peer · weight(1f)",
                LayoutSampleColors.Green,
                SwingModifier.weight(1f).preferredSize(88, 32),
            )
        }
    }
}

private val WEIGHT_CHOICES = listOf(0.5f, 1f, 1.5f, 2f, 3f)
private val FIXED_WIDTHS = listOf(64, 88, 120)

private fun Float.label(): String = "weight(${toString().removeSuffix(".0")}f)"

@Composable
internal fun ColumnScope.RowArrangementCard() {
    ExampleCard("Row playground") {
        var selected by remember { mutableIntStateOf(0) }
        var selectedAlignment by remember { mutableIntStateOf(1) }
        LayoutParameterSelector("horizontalArrangement", ROW_ARRANGEMENTS, selected) { selected = it }
        LayoutParameterSelector("verticalAlignment", ROW_VERTICAL_ALIGNMENTS, selectedAlignment) {
            selectedAlignment = it
        }
        RowArrangementPreview(
            ROW_ARRANGEMENTS[selected].second,
            ROW_VERTICAL_ALIGNMENTS[selectedAlignment].second,
        )
    }
}

@Composable
private fun ColumnScope.RowArrangementPreview(
    horizontalArrangement: Arrangement.Horizontal,
    verticalAlignment: Alignment.Vertical,
) {
    var first by remember { mutableStateOf(ChildPosition()) }
    var middle by remember { mutableStateOf(ChildPosition()) }
    var last by remember { mutableStateOf(ChildPosition()) }
    Label(
        "Child positions: A (${first.x}, ${first.y}), " +
            "B (${middle.x}, ${middle.y}), C (${last.x}, ${last.y}) px",
    )
    Row(
        modifier = SwingModifier.layoutTrack().fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
    ) {
        PositionedSwatch("A · 72 px", LayoutSampleColors.Blue, 72, 32) { first = it }
        PositionedSwatch("B · 96 px", LayoutSampleColors.Orange, 96, 40) { middle = it }
        PositionedSwatch("C · 64 px", LayoutSampleColors.Green, 64, 28) { last = it }
    }
}

@Composable
internal fun ColumnScope.ColumnCard() {
    ExampleCard("Column (alignment + spacing)") {
        var centeredX by remember { mutableIntStateOf(0) }
        var endX by remember { mutableIntStateOf(0) }
        val childAlignments =
            listOf(
                "Start" to Alignment.Start,
                "Center" to Alignment.CenterHorizontally,
                "End" to Alignment.End,
            )
        var childAlignment by remember { mutableIntStateOf(2) }
        Label("Activate the green child to cycle its own align override")
        Label("Centered child x: $centeredX px; end-aligned child x: $endX px")
        Column(
            modifier = SwingModifier.layoutTrack().preferredSize(320, 144),
            verticalArrangement = Arrangement.spacedBy(6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LayoutSwatch(
                "Centered · 176 px",
                LayoutSampleColors.Blue,
                modifier =
                    SwingModifier
                        .preferredSize(176, 32)
                        .componentListener(onComponentMoved = { centeredX = it.component.x }),
            )
            LayoutSwatch("Centered · 112 px", LayoutSampleColors.Orange, SwingModifier.preferredSize(112, 32))
            InteractiveLayoutSwatch(
                "align(${childAlignments[childAlignment].first}) · 144 px",
                LayoutSampleColors.Green,
                onClick = { childAlignment = (childAlignment + 1) % childAlignments.size },
                SwingModifier
                    .align(childAlignments[childAlignment].second)
                    .preferredSize(144, 32)
                    .componentListener(onComponentMoved = { endX = it.component.x })
                    .toolTip("Activate to cycle the child alignment"),
            )
        }
    }
}

@Composable
internal fun ColumnScope.ColumnArrangementCard() {
    ExampleCard("Column playground") {
        var selected by remember { mutableIntStateOf(0) }
        var selectedAlignment by remember { mutableIntStateOf(1) }
        LayoutParameterSelector("verticalArrangement", COLUMN_ARRANGEMENTS, selected) { selected = it }
        LayoutParameterSelector("horizontalAlignment", COLUMN_HORIZONTAL_ALIGNMENTS, selectedAlignment) {
            selectedAlignment = it
        }
        ColumnArrangementPreview(
            COLUMN_ARRANGEMENTS[selected].second,
            COLUMN_HORIZONTAL_ALIGNMENTS[selectedAlignment].second,
        )
    }
}

@Composable
private fun ColumnArrangementPreview(
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal,
) {
    var first by remember { mutableStateOf(ChildPosition()) }
    var middle by remember { mutableStateOf(ChildPosition()) }
    var last by remember { mutableStateOf(ChildPosition()) }
    Label(
        "Child positions: A (${first.x}, ${first.y}), " +
            "B (${middle.x}, ${middle.y}), C (${last.x}, ${last.y}) px",
    )
    Column(
        modifier = SwingModifier.layoutTrack().preferredSize(240, 176),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
    ) {
        PositionedSwatch("A · 32 px", LayoutSampleColors.Blue, 168, 32) { first = it }
        PositionedSwatch("B · 40 px", LayoutSampleColors.Orange, 136, 40) { middle = it }
        PositionedSwatch("C · 28 px", LayoutSampleColors.Green, 104, 28) { last = it }
    }
}

@Composable
private fun PositionedSwatch(
    label: String,
    color: Color,
    width: Int,
    height: Int,
    onPositionChange: (ChildPosition) -> Unit,
) {
    LayoutSwatch(
        label,
        color,
        SwingModifier
            .preferredSize(width, height)
            .componentListener(
                onComponentMoved = {
                    onPositionChange(ChildPosition(it.component.x, it.component.y))
                },
            ),
    )
}

private data class ChildPosition(
    val x: Int = 0,
    val y: Int = 0,
)

private val ROW_ARRANGEMENTS: List<Pair<String, Arrangement.Horizontal>> =
    listOf(
        "Start" to Arrangement.Start,
        "Center" to Arrangement.Center,
        "End" to Arrangement.End,
        "SpaceBetween" to Arrangement.SpaceBetween,
        "SpaceAround" to Arrangement.SpaceAround,
        "SpaceEvenly" to Arrangement.SpaceEvenly,
        "Aligned to end" to Arrangement.aligned(Alignment.End),
    )

private val ROW_VERTICAL_ALIGNMENTS =
    listOf(
        "Top edge" to Alignment.Top,
        "Center vertically" to Alignment.CenterVertically,
        "Bottom edge" to Alignment.Bottom,
    )

private val COLUMN_ARRANGEMENTS: List<Pair<String, Arrangement.Vertical>> =
    listOf(
        "Top" to Arrangement.Top,
        "Center" to Arrangement.Center,
        "Bottom" to Arrangement.Bottom,
        "SpaceBetween" to Arrangement.SpaceBetween,
        "SpaceAround" to Arrangement.SpaceAround,
        "SpaceEvenly" to Arrangement.SpaceEvenly,
        "Aligned to bottom" to Arrangement.aligned(Alignment.Bottom),
    )

private val COLUMN_HORIZONTAL_ALIGNMENTS =
    listOf(
        "Start edge" to Alignment.Start,
        "Center horizontally" to Alignment.CenterHorizontally,
        "End edge" to Alignment.End,
    )

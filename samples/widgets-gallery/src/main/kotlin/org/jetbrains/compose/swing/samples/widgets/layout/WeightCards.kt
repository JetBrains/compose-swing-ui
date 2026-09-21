package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.Dimension

// weight() cards: each one hands a share of a row's width to a colored swatch and prints the width the
// layout granted it. A gray outline on the row makes the extent that share comes out of legible, and the
// printed width names what the outline cannot - the part of a share a child declines to take, or is
// capped out of, is empty space indistinguishable from space no child ever claimed.
@Composable
internal fun ColumnScope.WeightCards() {
    WeightSharesCard()
    WeightFillCard()
    WeightMaximumSizeCard()
}

// Every child of this row claims a share, so between them they divide the whole width the row is offered
// and the row spans its parent. Sliding the second swatch's weight up moves the boundary between the two
// swatches, not the row's own edge: what one swatch gains the other gives up. A swatch's preferred size
// stands for its height alone, since a share fills the whole width it is granted.
@Composable
internal fun ColumnScope.WeightSharesCard() {
    ExampleCard("weight (shares divide the row's width)") {
        var weightStep by remember { mutableIntStateOf(30) }
        val weight = weightStep / 10f
        var rowWidth by remember { mutableIntStateOf(0) }
        var lightWidth by remember { mutableIntStateOf(0) }
        var heavyWidth by remember { mutableIntStateOf(0) }
        LayoutSlider(
            label = "Second swatch weight",
            valueText = "${weight.toString().removeSuffix(".0")}f",
            value = weightStep,
            onValueChange = { weightStep = it },
            min = 1,
            max = 50,
        )
        Label("Shares granted: $lightWidth px and $heavyWidth px of the row's $rowWidth px")
        Row(
            modifier =
                SwingModifier.layoutTrack().componentListener(onComponentResized = {
                    rowWidth =
                        it.component.width
                }),
        ) {
            LayoutSwatch(
                "1f",
                LayoutSampleColors.Green,
                SwingModifier
                    .weight(1f)
                    .preferredSize(Dimension(50, 28))
                    .componentListener(onComponentResized = { lightWidth = it.component.width }),
            )
            LayoutSwatch(
                "${weight.toString().removeSuffix(".0")}f",
                LayoutSampleColors.Orange,
                SwingModifier
                    .weight(weight)
                    .preferredSize(Dimension(50, 28))
                    .componentListener(onComponentResized = { heavyWidth = it.component.width }),
            )
        }
    }
}

@Composable
internal fun ColumnScope.CrossAxisFillCard() {
    ExampleCard("fillMax fractions across Row, Column, and Box") {
        var fillStep by remember { mutableIntStateOf(10) }
        val fillFraction = fillStep / 10f
        val fillFractionText = "${fillStep / 10}.${fillStep % 10}"
        LayoutSlider(
            label = "fillMax fraction",
            valueText = fillFractionText,
            value = fillStep,
            onValueChange = { fillStep = it },
            min = 0,
            max = 10,
        )
        Label("Row cross axis: fillMaxHeight($fillFractionText)")
        Row(modifier = SwingModifier.layoutTrack().preferredSize(260, 52)) {
            LayoutSwatch(
                "fills $fillFractionText of available height",
                LayoutSampleColors.Blue,
                SwingModifier.preferredSize(128, 28).fillMaxHeight(fillFraction),
            )
        }
        Label("Column cross axis: fillMaxWidth($fillFractionText)")
        Column(modifier = SwingModifier.layoutTrack().preferredSize(260, 52)) {
            LayoutSwatch(
                "fills $fillFractionText of available width",
                LayoutSampleColors.Green,
                SwingModifier.preferredSize(128, 28).fillMaxWidth(fillFraction),
            )
        }
        Label("Box axes: fillMaxSize($fillFractionText)")
        Box(modifier = SwingModifier.layoutTrack().preferredSize(260, 52)) {
            LayoutSwatch(
                "fills $fillFractionText of the box",
                LayoutSampleColors.Orange,
                SwingModifier.preferredSize(128, 28).fillMaxSize(fillFraction),
            )
        }
    }
}

// Both children claim an equal 1f share of a row fixed at 300 px. A swatch that does not fill keeps only
// the width it prefers and leaves the rest of its share empty inside the outline; the one beside it takes
// the whole share it was granted, and the two printed widths are equal only once both fill.
@Composable
internal fun ColumnScope.WeightFillCard() {
    ExampleCard("weight (fill vs. fill = false)") {
        var fill by remember { mutableStateOf(false) }
        var rowWidth by remember { mutableIntStateOf(0) }
        var leftWidth by remember { mutableIntStateOf(0) }
        var rightWidth by remember { mutableIntStateOf(0) }
        CheckBox(text = "Left swatch fills its share", checked = fill, onCheckedChange = { fill = it })
        Label("Shares: left swatch $leftWidth px, right swatch $rightWidth px of the row's $rowWidth px")
        Row(
            modifier =
                SwingModifier
                    .layoutTrack()
                    .preferredSize(Dimension(300, 32))
                    .componentListener(onComponentResized = { rowWidth = it.component.width }),
        ) {
            LayoutSwatch(
                if (fill) "fill" else "no fill",
                LayoutSampleColors.Purple,
                SwingModifier
                    .weight(1f, fill = fill)
                    .preferredSize(Dimension(80, 28))
                    .componentListener(onComponentResized = { leftWidth = it.component.width }),
            )
            LayoutSwatch(
                "fill",
                LayoutSampleColors.Teal,
                SwingModifier
                    .weight(1f)
                    .preferredSize(Dimension(80, 28))
                    .componentListener(onComponentResized = { rightWidth = it.component.width }),
            )
        }
    }
}

// The swatch is the row's only child and would take the row's whole 300 px, but a maximumSize caps how
// much of that share it occupies. What the cap refuses stays empty inside the outline rather than going
// to another child, since there is none.
@Composable
internal fun ColumnScope.WeightMaximumSizeCard() {
    ExampleCard("weight + maximumSize") {
        var capped by remember { mutableStateOf(true) }
        var rowWidth by remember { mutableIntStateOf(0) }
        var swatchWidth by remember { mutableIntStateOf(0) }
        CheckBox(text = "Cap the swatch at 80 px", checked = capped, onCheckedChange = { capped = it })
        Label("Share taken: $swatchWidth px of the row's $rowWidth px")
        Row(
            modifier =
                SwingModifier
                    .layoutTrack()
                    .preferredSize(Dimension(300, 32))
                    .componentListener(onComponentResized = { rowWidth = it.component.width }),
        ) {
            val share =
                SwingModifier
                    .weight(1f)
                    .componentListener(onComponentResized = { swatchWidth = it.component.width })
            LayoutSwatch(
                if (capped) "capped" else "uncapped",
                LayoutSampleColors.Pink,
                if (capped) share.maximumSize(Dimension(80, 28)) else share,
            )
        }
    }
}

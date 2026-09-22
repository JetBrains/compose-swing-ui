package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.AbsoluteAlignment
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Font

@Preview
@Composable
internal fun LinearLayoutsSection() {
    SectionColumn {
        SectionHeading("Linear layouts")
        RowCard()
        RowArrangementCard()
        ColumnCard()
        ColumnArrangementCard()
        CrossAxisFillCard()
        WeightCards()
        ArrangementAbsoluteCard()
        AbsoluteAlignmentCard()
        AlignByBaselineCard()
    }
}

// Arrangement.End reads the row's ComponentOrientation and mirrors under right-to-left; Absolute.Right
// always packs against the physical right edge. Both rows are given the same orientation, so flipping it
// moves the leading child of the first row and leaves the second row's exactly where it was.
@Composable
internal fun ColumnScope.ArrangementAbsoluteCard() {
    ExampleCard("Arrangement.End vs Arrangement.Absolute.Right") {
        var rightToLeft by remember { mutableStateOf(false) }
        var endX by remember { mutableIntStateOf(0) }
        var absoluteX by remember { mutableIntStateOf(0) }
        CheckBox(
            text = "Right-to-left orientation (arrangement)",
            checked = rightToLeft,
            onCheckedChange = { rightToLeft = it },
        )
        val orientation = if (rightToLeft) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT
        Label("End: leading child at x = $endX px")
        Row(
            modifier = SwingModifier.layoutTrack().fillMaxWidth().componentOrientation(orientation),
            horizontalArrangement = Arrangement.End,
        ) {
            LayoutSwatch(
                "One",
                LayoutSampleColors.Blue,
                SwingModifier
                    .preferredSize(72, 28)
                    .componentListener(onComponentMoved = { endX = it.component.x }),
            )
            LayoutSwatch("Two", LayoutSampleColors.Orange, SwingModifier.preferredSize(72, 28))
        }
        Label("Absolute.Right: leading child at x = $absoluteX px")
        Row(
            modifier = SwingModifier.layoutTrack().fillMaxWidth().componentOrientation(orientation),
            horizontalArrangement = Arrangement.Absolute.Right,
        ) {
            LayoutSwatch(
                "One",
                LayoutSampleColors.Blue,
                SwingModifier
                    .preferredSize(72, 28)
                    .componentListener(onComponentMoved = { absoluteX = it.component.x }),
            )
            LayoutSwatch("Two", LayoutSampleColors.Orange, SwingModifier.preferredSize(72, 28))
        }
    }
}

// The same contrast for cross-axis alignment: Alignment.Start mirrors to the column's trailing edge under
// right-to-left, AbsoluteAlignment.Left never does.
@Composable
internal fun ColumnScope.AbsoluteAlignmentCard() {
    ExampleCard("Alignment.Start vs AbsoluteAlignment.Left") {
        var rightToLeft by remember { mutableStateOf(false) }
        var startX by remember { mutableIntStateOf(0) }
        var leftX by remember { mutableIntStateOf(0) }
        CheckBox(
            text = "Right-to-left orientation (alignment)",
            checked = rightToLeft,
            onCheckedChange = { rightToLeft = it },
        )
        val orientation = if (rightToLeft) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT
        Label("Start: child at x = $startX px")
        Column(
            modifier =
                SwingModifier
                    .preferredSize(Dimension(220, 28))
                    .layoutTrack()
                    .componentOrientation(orientation),
            horizontalAlignment = Alignment.Start,
        ) {
            LayoutSwatch(
                "Start child",
                LayoutSampleColors.Blue,
                SwingModifier
                    .preferredSize(96, 28)
                    .componentListener(onComponentMoved = { startX = it.component.x }),
            )
        }
        Label("AbsoluteAlignment.Left: child at x = $leftX px")
        Column(
            modifier =
                SwingModifier
                    .preferredSize(Dimension(220, 28))
                    .layoutTrack()
                    .componentOrientation(orientation),
            horizontalAlignment = AbsoluteAlignment.Left,
        ) {
            LayoutSwatch(
                "Left child",
                LayoutSampleColors.Orange,
                SwingModifier
                    .preferredSize(96, 28)
                    .componentListener(onComponentMoved = { leftX = it.component.x }),
            )
        }
    }
}

// The case alignByBaseline exists for: a label and a field at different font sizes, whose text only lines
// up when both sit on the row's shared baseline instead of at its own verticalAlignment. Turning it off
// drops the label onto the row's Bottom alignment, which its printed y follows.
@Composable
internal fun ColumnScope.AlignByBaselineCard() {
    ExampleCard("RowScope.alignByBaseline") {
        var baseline by remember { mutableStateOf(true) }
        var labelY by remember { mutableIntStateOf(0) }
        var fontSize by remember { mutableIntStateOf(28) }
        CheckBox(text = "Align by baseline", checked = baseline, onCheckedChange = { baseline = it })
        Label("Align by baseline: ${if (baseline) "on" else "off"}, label sits at y = $labelY px")
        LayoutSlider(
            label = "Field font",
            valueText = "$fontSize pt",
            value = fontSize,
            onValueChange = { fontSize = it },
            accessibleName = "Field font size",
            min = 12,
            max = 72,
        )
        var text by remember { mutableStateOf("Text field") }
        Row(
            modifier = SwingModifier.layoutTrack().fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8),
            verticalAlignment = Alignment.Bottom,
        ) {
            Label(
                text = "Label:",
                modifier =
                    SwingModifier
                        .font(Font(Font.SANS_SERIF, Font.PLAIN, 12))
                        .componentListener(onComponentMoved = { labelY = it.component.y })
                        .then(if (baseline) SwingModifier.alignByBaseline() else SwingModifier),
            )
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier =
                    SwingModifier
                        .font(Font(Font.SANS_SERIF, Font.PLAIN, fontSize))
                        .then(if (baseline) SwingModifier.alignByBaseline() else SwingModifier),
                columns = 12,
            )
        }
    }
}

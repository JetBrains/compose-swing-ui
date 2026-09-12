package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Glue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.RigidArea
import org.jetbrains.compose.swing.components.layout.Spacer
import org.jetbrains.compose.swing.components.layout.Strut
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.SwingConstants

@Composable
internal fun ColumnScope.FlowLayoutCard() {
    ExampleCard("PanelLayout.Flow playground") {
        val alignments =
            listOf(
                "Leading" to FlowLayout.LEADING,
                "Center" to FlowLayout.CENTER,
                "Trailing" to FlowLayout.TRAILING,
            )
        var alignment by remember { mutableIntStateOf(0) }
        var gap by remember { mutableIntStateOf(8) }
        LayoutParameterSelector("alignment", alignments, alignment) { alignment = it }
        LayoutSlider(
            label = "Horizontal and vertical gap",
            valueText = "$gap px",
            value = gap,
            onValueChange = { gap = it },
            accessibleName = "Flow gaps",
            min = 0,
            max = 24,
        )
        Panel(
            PanelLayout.Flow(
                alignment = alignments[alignment].second,
                hgap = gap,
                vgap = gap,
            ),
            modifier = SwingModifier.layoutTrack().preferredSize(300, 104),
        ) {
            LayoutSwatch("A", LayoutSampleColors.Blue, SwingModifier.preferredSize(110, 28))
            LayoutSwatch("B", LayoutSampleColors.Orange, SwingModifier.preferredSize(110, 28))
            LayoutSwatch("C", LayoutSampleColors.Green, SwingModifier.preferredSize(110, 28))
        }
    }
}

@Composable
internal fun ColumnScope.BorderCompassCard() {
    ExampleCard("PanelLayout.Border (compass regions)") {
        var gap by remember { mutableIntStateOf(4) }
        LayoutSlider(
            label = "Horizontal and vertical gap",
            valueText = "$gap px",
            value = gap,
            onValueChange = { gap = it },
            accessibleName = "Border gaps",
            min = 0,
            max = 16,
        )
        Panel(
            PanelLayout.Border(hgap = gap, vgap = gap),
            modifier = SwingModifier.layoutTrack().preferredSize(Dimension(360, 140)),
        ) {
            LayoutRegionSwatch("north", LayoutSampleColors.Blue, SwingModifier.north())
            LayoutRegionSwatch("south", LayoutSampleColors.Green, SwingModifier.south())
            LayoutRegionSwatch("west", LayoutSampleColors.Orange, SwingModifier.west(), width = EDGE_WIDTH)
            LayoutRegionSwatch("east", LayoutSampleColors.Pink, SwingModifier.east(), width = EDGE_WIDTH)
            LayoutRegionSwatch("center", LayoutSampleColors.Gray, SwingModifier.center())
        }
    }
}

// Flipping the orientation swaps where lineStart/lineEnd resolve: lineStart is the left edge under LTR
// and the right edge under RTL, so the two edge children move live between the leading and trailing sides.
@Composable
internal fun ColumnScope.BorderOrientationCard() {
    ExampleCard("PanelLayout.Border (orientation-aware)") {
        var rtl by remember { mutableStateOf(false) }
        CheckBox(
            text = "Right-to-left orientation",
            checked = rtl,
            onCheckedChange = { rtl = it },
        )
        Panel(
            PanelLayout.Border(hgap = 4, vgap = 4),
            modifier =
                SwingModifier
                    .layoutTrack()
                    .preferredSize(Dimension(360, 120))
                    .componentOrientation(
                        if (rtl) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT,
                    ),
        ) {
            LayoutRegionSwatch("pageStart", LayoutSampleColors.Blue, SwingModifier.pageStart())
            LayoutRegionSwatch("pageEnd", LayoutSampleColors.Green, SwingModifier.pageEnd())
            LayoutRegionSwatch(
                "lineStart (leading)",
                LayoutSampleColors.Orange,
                SwingModifier.lineStart(),
                width = EDGE_WIDTH,
            )
            LayoutRegionSwatch(
                "lineEnd (trailing)",
                LayoutSampleColors.Pink,
                SwingModifier.lineEnd(),
                width = EDGE_WIDTH,
            )
            LayoutRegionSwatch("center", LayoutSampleColors.Gray, SwingModifier.center())
        }
    }
}

@Composable
internal fun ColumnScope.LinearLayoutCard() {
    ExampleCard("PanelLayout.Box (X/Y axis)") {
        var horizontal by remember { mutableStateOf(false) }
        CheckBox(
            text = "Arrange on the X axis (left to right)",
            checked = horizontal,
            onCheckedChange = { horizontal = it },
        )
        Label("axis = ${if (horizontal) "X_AXIS" else "Y_AXIS"}")
        Panel(
            PanelLayout.Box(axis = if (horizontal) BoxLayout.X_AXIS else BoxLayout.Y_AXIS),
            modifier = SwingModifier.layoutTrack().preferredSize(320, 128),
        ) {
            LayoutSwatch(
                "First",
                LayoutSampleColors.Blue,
                SwingModifier.preferredSize(88, 28).maximumSize(88, 28),
            )
            LayoutSwatch(
                "Second",
                LayoutSampleColors.Orange,
                SwingModifier.preferredSize(104, 32).maximumSize(104, 32),
            )
            LayoutSwatch(
                "Third",
                LayoutSampleColors.Green,
                SwingModifier.preferredSize(72, 24).maximumSize(72, 24),
            )
        }
    }
}

// RigidArea and Spacer hold a fixed gap; Strut holds a fixed size along the box's own axis and stretches
// across it; Glue takes whatever space is left over, which is what pushes the trailing label to the end.
@Composable
internal fun ColumnScope.BoxFillersCard() {
    ExampleCard("Box fillers (RigidArea, Spacer, Strut, Glue)") {
        var includeGlue by remember { mutableStateOf(true) }
        CheckBox(text = "Include Glue", checked = includeGlue, onCheckedChange = { includeGlue = it })
        Label("Purple = RigidArea 24 px; gray = Spacer 16 px; pink = Strut 32 px; green = Glue")
        Panel(
            PanelLayout.Box(axis = BoxLayout.X_AXIS),
            modifier = SwingModifier.layoutTrack().preferredSize(460, 52),
        ) {
            LayoutSwatch(
                "Start",
                LayoutSampleColors.Blue,
                SwingModifier.preferredSize(54, 32).maximumSize(Dimension(54, 32)),
            )
            RigidArea(
                width = 24,
                height = 32,
                modifier = SwingModifier.layoutSampleSurface(LayoutSampleColors.Purple),
            )
            LayoutSwatch(
                "Middle",
                LayoutSampleColors.Orange,
                SwingModifier.preferredSize(60, 32).maximumSize(Dimension(60, 32)),
            )
            Spacer(size = 16, modifier = SwingModifier.layoutSampleSurface(LayoutSampleColors.Gray))
            Strut(
                orientation = SwingConstants.HORIZONTAL,
                size = 32,
                modifier = SwingModifier.layoutSampleSurface(LayoutSampleColors.Pink),
            )
            if (includeGlue) {
                Glue(modifier = SwingModifier.layoutSampleSurface(LayoutSampleColors.Green))
            }
            LayoutSwatch(
                "Trailing",
                LayoutSampleColors.Blue,
                SwingModifier.preferredSize(62, 32).maximumSize(Dimension(62, 32)),
            )
        }
    }
}

private const val EDGE_WIDTH = 120

package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.Glue
import org.jetbrains.compose.swing.components.layout.RigidArea
import org.jetbrains.compose.swing.components.layout.Spacer
import org.jetbrains.compose.swing.components.layout.Strut
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.SwingConstants

@Composable
internal fun ColumnScope.FlowPanelCard() {
    ExampleCard("FlowPanel") {
        FlowPanel(alignment = FlowLayout.LEADING, hgap = 12, vgap = 4) {
            Button("One", onClick = { })
            Button("Two", onClick = { })
            Button("Three", onClick = { })
        }
    }
}

@Composable
internal fun ColumnScope.BorderCompassCard() {
    ExampleCard("BorderPanel (compass regions)") {
        BorderPanel(modifier = SwingModifier.preferredSize(Dimension(360, 140)), hgap = 4, vgap = 4) {
            RegionLabel("north", Color(0xBB, 0xDE, 0xFB), SwingModifier.north())
            RegionLabel("south", Color(0xC8, 0xE6, 0xC9), SwingModifier.south())
            RegionLabel("west", Color(0xFF, 0xE0, 0xB2), SwingModifier.west(), width = EDGE_WIDTH)
            RegionLabel("east", Color(0xF8, 0xBB, 0xD0), SwingModifier.east(), width = EDGE_WIDTH)
            RegionLabel("center", Color(0xE0, 0xE0, 0xE0), SwingModifier.center())
        }
    }
}

// Flipping the orientation swaps where lineStart/lineEnd resolve: lineStart is the left edge under LTR
// and the right edge under RTL, so the two edge children move live between the leading and trailing sides.
@Composable
internal fun ColumnScope.BorderOrientationCard() {
    ExampleCard("BorderPanel (orientation-aware)") {
        var rtl by remember { mutableStateOf(false) }
        CheckBox(
            text = "Right-to-left orientation",
            checked = rtl,
            onCheckedChange = { rtl = it },
        )
        BorderPanel(
            modifier =
                SwingModifier
                    .preferredSize(Dimension(360, 120))
                    .componentOrientation(
                        if (rtl) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT,
                    ),
            hgap = 4,
            vgap = 4,
        ) {
            RegionLabel("pageStart", Color(0xBB, 0xDE, 0xFB), SwingModifier.pageStart())
            RegionLabel("pageEnd", Color(0xC8, 0xE6, 0xC9), SwingModifier.pageEnd())
            RegionLabel("lineStart (leading)", Color(0xFF, 0xE0, 0xB2), SwingModifier.lineStart(), width = EDGE_WIDTH)
            RegionLabel("lineEnd (trailing)", Color(0xF8, 0xBB, 0xD0), SwingModifier.lineEnd(), width = EDGE_WIDTH)
            RegionLabel("center", Color(0xE0, 0xE0, 0xE0), SwingModifier.center())
        }
    }
}

@Composable
internal fun ColumnScope.BoxPanelCard() {
    ExampleCard("BoxPanel (Y axis)") {
        BoxPanel(axis = BoxLayout.Y_AXIS) {
            Label("First")
            Label("Second")
            Label("Third")
        }
    }
}

// RigidArea and Spacer hold a fixed gap; Strut holds a fixed size along the box's own axis and stretches
// across it; Glue takes whatever space is left over, which is what pushes the trailing label to the end.
@Composable
internal fun ColumnScope.BoxFillersCard() {
    ExampleCard("Box fillers (RigidArea, Spacer, Strut, Glue)") {
        BoxPanel(modifier = SwingModifier.fillWidth(), axis = BoxLayout.X_AXIS) {
            Label("Start")
            RigidArea(width = 24, height = 0)
            Label("+24px RigidArea")
            Spacer(size = 16)
            Label("+16px square Spacer")
            Strut(orientation = SwingConstants.HORIZONTAL, size = 40)
            Label("+40px Strut")
            Glue()
            Label("Pushed to the end by Glue")
        }
    }
}

// A filled, centered label that makes each region visible. width sets the preferred width: regions
// stretched to their parent's full width (north/south/center) leave it at 0, while the horizontal-edge
// regions (west/east, lineStart/lineEnd) need a real width to claim space.
@Composable
internal fun RegionLabel(
    text: String,
    color: Color,
    modifier: SwingModifier = SwingModifier,
    width: Int = 0,
) {
    Label(
        text = text,
        modifier =
            modifier
                .opaque(true)
                .background(color)
                .preferredSize(Dimension(width, 28))
                .horizontalAlignment(SwingConstants.CENTER),
    )
}

private const val EDGE_WIDTH = 120

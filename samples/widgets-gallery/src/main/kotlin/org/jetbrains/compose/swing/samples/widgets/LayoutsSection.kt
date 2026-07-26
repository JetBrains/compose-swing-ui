package org.jetbrains.compose.swing.samples.widgets

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
import org.jetbrains.compose.swing.components.layout.CardPanel
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.GridBagPanel
import org.jetbrains.compose.swing.components.layout.GridPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.Insets
import javax.swing.BoxLayout
import javax.swing.SwingConstants

// Every layout wrapper, including both BorderPanel region families - the absolute compass
// (north/south/...) and the orientation-aware one (lineStart/lineEnd) - plus a CardPanel whose visible
// card is driven by state.
@Composable
internal fun LayoutsSection() {
    SectionColumn {
        SectionHeading("Layouts")
        FlowPanelCard()
        BorderCompassCard()
        BorderOrientationCard()
        BoxPanelCard()
        GridPanelCard()
        GridBagPanelCard()
        CardPanelCard()
    }
}

@Composable
private fun FlowPanelCard() {
    ExampleCard("FlowPanel") {
        FlowPanel(alignment = FlowLayout.LEADING, hgap = 12, vgap = 4) {
            Button("One")
            Button("Two")
            Button("Three")
        }
    }
}

@Composable
private fun BorderCompassCard() {
    ExampleCard("BorderPanel (compass regions)") {
        BorderPanel(modifier = SwingModifier.preferredSize(Dimension(360, 140)), hgap = 4, vgap = 4) {
            north { RegionLabel("north", Color(0xBB, 0xDE, 0xFB)) }
            south { RegionLabel("south", Color(0xC8, 0xE6, 0xC9)) }
            west { RegionLabel("west", Color(0xFF, 0xE0, 0xB2), width = EDGE_WIDTH) }
            east { RegionLabel("east", Color(0xF8, 0xBB, 0xD0), width = EDGE_WIDTH) }
            center { RegionLabel("center", Color(0xE0, 0xE0, 0xE0)) }
        }
    }
}

// Flipping the orientation swaps where lineStart/lineEnd resolve: lineStart is the left edge under LTR
// and the right edge under RTL, so the two edge children move live between the leading and trailing sides.
@Composable
private fun BorderOrientationCard() {
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
                    .alignmentX(LEFT_ALIGNED)
                    .componentOrientation(
                        if (rtl) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT,
                    ),
            hgap = 4,
            vgap = 4,
        ) {
            pageStart { RegionLabel("pageStart", Color(0xBB, 0xDE, 0xFB)) }
            pageEnd { RegionLabel("pageEnd", Color(0xC8, 0xE6, 0xC9)) }
            lineStart { RegionLabel("lineStart (leading)", Color(0xFF, 0xE0, 0xB2), width = EDGE_WIDTH) }
            lineEnd { RegionLabel("lineEnd (trailing)", Color(0xF8, 0xBB, 0xD0), width = EDGE_WIDTH) }
            center { RegionLabel("center", Color(0xE0, 0xE0, 0xE0)) }
        }
    }
}

@Composable
private fun BoxPanelCard() {
    ExampleCard("BoxPanel (Y axis)") {
        BoxPanel(axis = BoxLayout.Y_AXIS) {
            Label("First")
            Label("Second")
            Label("Third")
        }
    }
}

@Composable
private fun GridPanelCard() {
    ExampleCard("GridPanel (2x3)") {
        GridPanel(rows = 2, cols = 3, hgap = 6, vgap = 6) {
            repeat(6) { index -> Button("Cell ${index + 1}") }
        }
    }
}

@Composable
private fun GridBagPanelCard() {
    ExampleCard("GridBagPanel") {
        GridBagPanel {
            item(gridx = 0, gridy = 0, anchor = GridBagConstraints.LINE_END, insets = Insets(4, 4, 4, 4)) {
                Label("Name")
            }
            item(
                gridx = 1,
                gridy = 0,
                weightx = 1.0,
                fill = GridBagConstraints.HORIZONTAL,
                insets = Insets(4, 4, 4, 4),
            ) {
                Button("Pick a name")
            }
            item(gridx = 0, gridy = 1, gridwidth = 2, fill = GridBagConstraints.HORIZONTAL) {
                Label("A row spanning both columns")
            }
        }
    }
}

@Composable
private fun CardPanelCard() {
    ExampleCard("CardPanel") {
        var shown by remember { mutableStateOf("A") }
        FlowPanel {
            Button("Show A", onClick = { shown = "A" })
            Button("Show B", onClick = { shown = "B" })
            Button("Show C", onClick = { shown = "C" })
        }
        CardPanel(selectedCard = shown, modifier = SwingModifier.preferredSize(Dimension(320, 60))) {
            card("A") { RegionLabel("Card A", Color(0xBB, 0xDE, 0xFB)) }
            card("B") { RegionLabel("Card B", Color(0xC8, 0xE6, 0xC9)) }
            card("C") { RegionLabel("Card C", Color(0xFF, 0xE0, 0xB2)) }
        }
    }
}

// A filled, centered label that makes each region visible. width sets the preferred width: regions
// stretched to their parent's full width (north/south/center) leave it at 0, while the horizontal-edge
// regions (west/east, lineStart/lineEnd) need a real width to claim space.
@Composable
private fun RegionLabel(
    text: String,
    color: Color,
    width: Int = 0,
) {
    Label(
        text = text,
        modifier =
            SwingModifier
                .opaque(true)
                .background(color)
                .preferredSize(Dimension(width, 28))
                .horizontalAlignment(SwingConstants.CENTER),
    )
}

private const val EDGE_WIDTH = 120

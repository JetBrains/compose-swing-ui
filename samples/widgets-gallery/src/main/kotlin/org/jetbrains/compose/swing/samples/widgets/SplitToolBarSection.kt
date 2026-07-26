package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.ToolBar
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Color
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JSplitPane
import javax.swing.SwingConstants

// SplitPane and ToolBar: a resizable divider whose location is hoisted into state, and a tool bar of
// buttons whose orientation and floatability flip live.
@Composable
internal fun SplitToolBarSection() {
    SectionColumn {
        SectionHeading("Split & ToolBar")
        ControlledSplitCard()
        WeightedSplitCard()
        ToolBarCard()
    }
}

@Composable
private fun ControlledSplitCard() {
    ExampleCard("SplitPane (controlled divider)") {
        var divider by remember { mutableIntStateOf(140) }
        Label("Divider location: $divider px")
        FlowPanel {
            Button("Move left", onClick = { divider = (divider - 40).coerceAtLeast(0) })
            Button("Move right", onClick = { divider += 40 })
        }
        SplitPane(
            modifier = SwingModifier.preferredSize(Dimension(360, 120)),
            orientation = JSplitPane.HORIZONTAL_SPLIT,
            dividerLocation = divider,
            onDividerLocationChange = { divider = it },
        ) {
            first { SplitPaneSide("Left side", Color(0xBB, 0xDE, 0xFB)) }
            second { SplitPaneSide("Right side", Color(0xC8, 0xE6, 0xC9)) }
        }
    }
}

// A bare Label is non-opaque, so the two sides would read as one flat surface; a distinct background
// plus an edge border on each side makes the split - and the draggable divider - plainly visible.
@Composable
private fun SplitPaneSide(
    text: String,
    color: Color,
) {
    Label(
        text = text,
        modifier =
            SwingModifier
                .opaque(true)
                .background(color)
                .border(BorderFactory.createLineBorder(Color(0x90, 0xA4, 0xAE)))
                .horizontalAlignment(SwingConstants.CENTER),
    )
}

@Composable
private fun WeightedSplitCard() {
    ExampleCard("SplitPane (vertical, resizeWeight)") {
        SplitPane(
            modifier = SwingModifier.preferredSize(Dimension(360, 160)),
            orientation = JSplitPane.VERTICAL_SPLIT,
            resizeWeight = 0.75,
        ) {
            first { SplitPaneSide("Top side (keeps 75% of extra space)", Color(0xBB, 0xDE, 0xFB)) }
            second { SplitPaneSide("Bottom side", Color(0xC8, 0xE6, 0xC9)) }
        }
    }
}

@Composable
private fun ToolBarCard() {
    ExampleCard("ToolBar") {
        var vertical by remember { mutableStateOf(false) }
        var floatable by remember { mutableStateOf(true) }
        var bold by remember { mutableStateOf(false) }
        var clicks by remember { mutableIntStateOf(0) }

        FlowPanel {
            CheckBox(
                text = "Vertical",
                checked = vertical,
                onCheckedChange = { vertical = it },
            )
            CheckBox(
                text = "Floatable",
                checked = floatable,
                onCheckedChange = { floatable = it },
            )
        }
        Label("New clicks: $clicks   Bold: ${if (bold) "on" else "off"}")
        ToolBar(
            orientation = if (vertical) SwingConstants.VERTICAL else SwingConstants.HORIZONTAL,
            floatable = floatable,
        ) {
            Button("New", onClick = { clicks++ })
            ToggleButton(text = "Bold", pressed = bold, onPressedChange = { bold = it })
        }
    }
}

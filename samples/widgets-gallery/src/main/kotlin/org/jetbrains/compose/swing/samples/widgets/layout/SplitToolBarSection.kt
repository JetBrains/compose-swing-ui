package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.ToolBar
import org.jetbrains.compose.swing.components.layout.ToolBarSeparator
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.Color
import java.awt.Dimension
import javax.swing.JSplitPane
import javax.swing.SwingConstants

// SplitPane and ToolBar: a resizable divider whose location is hoisted into state, and a tool bar of
// buttons whose orientation and floatability flip live.
@Preview
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
private fun ColumnScope.ControlledSplitCard() {
    ExampleCard("SplitPane (controlled divider)") {
        var divider by remember { mutableIntStateOf(140) }
        var oneTouch by remember { mutableStateOf(false) }
        var continuous by remember { mutableStateOf(true) }
        var dividerSize by remember { mutableIntStateOf(8) }
        Label("Divider location: $divider px")
        FlowPanel {
            Button("Move left", onClick = { divider = (divider - 40).coerceAtLeast(0) })
            Button("Move right", onClick = { divider += 40 })
        }
        FlowPanel {
            CheckBox(text = "One-touch expandable", checked = oneTouch, onCheckedChange = { oneTouch = it })
            CheckBox(text = "Continuous layout", checked = continuous, onCheckedChange = { continuous = it })
            Label("Divider size:")
            Spinner(dividerSize, onValueChange = { dividerSize = it.toInt() }, min = 4, max = 30, step = 1)
        }
        SplitPane(
            modifier = SwingModifier.preferredSize(Dimension(360, 120)),
            orientation = JSplitPane.HORIZONTAL_SPLIT,
            dividerLocation = divider,
            onDividerLocationChange = { divider = it },
            oneTouchExpandable = oneTouch,
            dividerSize = dividerSize,
            continuousLayout = continuous,
        ) {
            SplitPaneSide("Left side", Color(0xBB, 0xDE, 0xFB), SwingModifier.first())
            SplitPaneSide("Right side", Color(0xC8, 0xE6, 0xC9), SwingModifier.second())
        }
    }
}

// A bare Label is non-opaque, so the two sides would read as one flat surface; a distinct background
// plus an edge border on each side makes the split - and the draggable divider - plainly visible.
@Composable
private fun SplitPaneSide(
    text: String,
    color: Color,
    modifier: SwingModifier = SwingModifier,
) {
    Label(
        text = text,
        modifier =
            modifier
                .opaque(true)
                .background(color)
                .lineBorder(Color(0x90, 0xA4, 0xAE))
                .horizontalAlignment(SwingConstants.CENTER),
    )
}

@Composable
private fun ColumnScope.WeightedSplitCard() {
    ExampleCard("SplitPane (vertical, resizeWeight)") {
        SplitPane(
            modifier = SwingModifier.preferredSize(Dimension(360, 160)),
            orientation = JSplitPane.VERTICAL_SPLIT,
            resizeWeight = 0.75,
        ) {
            SplitPaneSide("Top side (keeps 75% of extra space)", Color(0xBB, 0xDE, 0xFB), SwingModifier.first())
            SplitPaneSide("Bottom side", Color(0xC8, 0xE6, 0xC9), SwingModifier.second())
        }
    }
}

@Composable
private fun ColumnScope.ToolBarCard() {
    ExampleCard("ToolBar") {
        var vertical by remember { mutableStateOf(false) }
        var floatable by remember { mutableStateOf(true) }
        var floating by remember { mutableStateOf(false) }
        var rollover by remember { mutableStateOf(true) }
        var bold by remember { mutableStateOf(false) }
        var clicks by remember { mutableIntStateOf(0) }

        FlowPanel {
            CheckBox(text = "Vertical", checked = vertical, onCheckedChange = { vertical = it })
            CheckBox(text = "Floatable", checked = floatable, onCheckedChange = { floatable = it })
            CheckBox(text = "Floating", checked = floating, onCheckedChange = { floating = it })
            CheckBox(text = "Rollover", checked = rollover, onCheckedChange = { rollover = it })
        }
        Label("New clicks: $clicks   Bold: ${if (bold) "on" else "off"}   Floating: $floating")
        ToolBar(
            orientation = if (vertical) SwingConstants.VERTICAL else SwingConstants.HORIZONTAL,
            floatable = floatable,
            floating = floating,
            onFloatingChange = { floating = it },
            rollover = rollover,
        ) {
            Button("New", onClick = { clicks++ })
            ToolBarSeparator()
            ToggleButton(text = "Bold", selected = bold, onSelectedChange = { bold = it })
        }
    }
}

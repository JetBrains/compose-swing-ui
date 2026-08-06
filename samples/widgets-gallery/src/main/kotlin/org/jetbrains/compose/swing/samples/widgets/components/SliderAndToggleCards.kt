package org.jetbrains.compose.swing.samples.widgets.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import javax.swing.DefaultBoundedRangeModel

@Composable
internal fun ColumnScope.SharedRangeModelCard() {
    ExampleCard("Slider & ProgressBar (shared model)") {
        // One BoundedRangeModel drives both widgets: dragging the slider repaints the bar without a
        // recomposition, since the bar renders the model it was handed rather than a value declared over it.
        val range = remember { DefaultBoundedRangeModel(30, 0, 0, 100) }
        var value by remember { mutableIntStateOf(range.value) }
        FlowPanel {
            Label("Value:")
            Slider(model = range, onValueChange = { value = it })
        }
        ProgressBar(model = range)
        Label("Value is $value")
    }
}

@Composable
internal fun ColumnScope.ToggleButtonCard() {
    ExampleCard("ToggleButton") {
        var bold by remember { mutableStateOf(false) }
        ToggleButton(text = "Bold", selected = bold, onSelectedChange = { bold = it })
        Label("Bold is ${if (bold) "on" else "off"}")
    }
}

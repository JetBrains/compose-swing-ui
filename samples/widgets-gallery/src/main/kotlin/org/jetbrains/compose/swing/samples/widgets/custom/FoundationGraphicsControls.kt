package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.preferredSize

// Sliders sharing a row split its width evenly.
@Composable
internal fun ColumnScope.SliderRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = SwingModifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

// Only the slider flexes, and the label is wide enough for every value in these ranges: changing a value
// never moves its track or neighboring controls.
@Composable
internal fun RowScope.GraphicsSlider(
    label: String,
    value: Int,
    unit: String,
    range: IntRange,
    tag: String,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    Row(modifier = SwingModifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Label(
            "$label: $value$unit",
            modifier = SwingModifier.preferredSize(108, 28).enabled(enabled),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier =
                SwingModifier
                    .weight(1f)
                    .preferredSize(80, 28)
                    .testTag(tag)
                    .accessibleName(label)
                    .enabled(enabled),
            min = range.first,
            max = range.last,
        )
    }
}

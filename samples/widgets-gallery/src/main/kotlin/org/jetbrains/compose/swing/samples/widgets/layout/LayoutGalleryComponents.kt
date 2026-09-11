package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.contentAreaFilled
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import javax.swing.SwingConstants

// Row, Column and Box paint nothing of their own, so their track is declared as a decoration.
internal fun SwingModifier.layoutTrack(): SwingModifier =
    this
        .lineBorder(LayoutSampleColors.Border)
        .background(LayoutSampleColors.Track, RectangleShape)

internal fun SwingModifier.layoutPanelTrack(): SwingModifier =
    this
        .lineBorder(LayoutSampleColors.Border)
        .background(LayoutSampleColors.Track)
        .opaque(true)

internal fun SwingModifier.layoutSampleSurface(color: Color): SwingModifier =
    this
        .lineBorder(LayoutSampleColors.Border)
        .background(color)
        .foreground(LayoutSampleColors.Text)
        .opaque(true)

@Composable
internal fun LayoutSwatch(
    text: String,
    color: Color,
    modifier: SwingModifier = SwingModifier,
) {
    Label(
        text = text,
        modifier = modifier.layoutSampleSurface(color).horizontalAlignment(SwingConstants.CENTER),
    )
}

@Composable
internal fun InteractiveLayoutSwatch(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    cursor: Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
) {
    Button(
        text = text,
        onClick = onClick,
        modifier =
            modifier
                .contentAreaFilled(false)
                .layoutSampleSurface(color)
                .cursor(cursor)
                .horizontalAlignment(SwingConstants.CENTER),
    )
}

@Composable
internal fun LayoutRegionSwatch(
    text: String,
    color: Color,
    modifier: SwingModifier = SwingModifier,
    width: Int = 0,
) {
    LayoutSwatch(text, color, modifier.preferredSize(Dimension(width, 28)))
}

@Composable
internal fun <T> LayoutParameterSelector(
    label: String,
    options: List<Pair<String, T>>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
) {
    val names = options.map { it.first }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label("$label:")
        ComboBox(
            items = names,
            selectedItem = names[selectedIndex],
            onSelectionChange = { selected ->
                val index = names.indexOf(selected)
                if (index >= 0) onSelectionChange(index)
            },
            modifier = SwingModifier.accessibleName(label),
        )
    }
}

@Composable
internal fun LayoutSlider(
    label: String,
    valueText: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    accessibleName: String = label,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label("$label: $valueText")
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = SwingModifier.accessibleName(accessibleName),
            min = min,
            max = max,
        )
    }
}

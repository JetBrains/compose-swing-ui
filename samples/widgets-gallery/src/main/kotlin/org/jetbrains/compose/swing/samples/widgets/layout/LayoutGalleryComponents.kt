package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.border
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.contentAreaFilled
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.apply
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.cursor
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.lineBorder
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import javax.swing.SwingConstants

// A track as large as its content and the 32 px its border keeps around it. The border is part of the track, so its
// content box starts inside it and a child may paint into it without leaving the track.
internal fun SwingModifier.layoutStage(): SwingModifier =
    this
        .background(LayoutSampleColors.Track, RectangleShape)
        .border(1, LayoutSampleColors.Border)

internal fun SwingComponentStyleScope.layoutPanelTrack() {
    lineBorder(LayoutSampleColors.Border)
    opaque(true)
    background(LayoutSampleColors.Track)
}

internal fun SwingComponentStyleScope.layoutSampleSurface(color: Color) {
    lineBorder(LayoutSampleColors.Border)
    opaque(true)
    background(color)
    foreground(LayoutSampleColors.Text)
}

@Composable
internal fun LayoutSwatch(
    text: String,
    color: Color,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<LabelStyleScope> = SwingStyle,
) {
    Label(
        text = text,
        modifier = modifier.horizontalAlignment(SwingConstants.CENTER),
        style = {
            layoutSampleSurface(color)
            apply(style)
        },
    )
}

@Composable
internal fun InteractiveLayoutSwatch(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ButtonStyleScope> = SwingStyle,
    cursor: Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
) {
    Button(
        text = text,
        onClick = onClick,
        modifier =
            modifier
                .contentAreaFilled(false)
                .horizontalAlignment(SwingConstants.CENTER),
        style = {
            layoutSampleSurface(color)
            cursor(cursor)
            apply(style)
        },
    )
}

@Composable
internal fun LayoutRegionSwatch(
    text: String,
    color: Color,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<LabelStyleScope> = SwingStyle,
    width: Int = 0,
) {
    LayoutSwatch(
        text,
        color,
        modifier,
        style = {
            preferredSize(Dimension(width, 28))
            apply(style)
        },
    )
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
            style = { accessibleName(label) },
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
            style = { accessibleName(accessibleName) },
            min = min,
            max = max,
        )
    }
}

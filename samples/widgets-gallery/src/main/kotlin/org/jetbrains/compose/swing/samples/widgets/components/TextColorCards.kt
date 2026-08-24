package org.jetbrains.compose.swing.samples.widgets.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.caretColor
import org.jetbrains.compose.swing.modifier.appearance.disabledTextColor
import org.jetbrains.compose.swing.modifier.appearance.selectedTextColor
import org.jetbrains.compose.swing.modifier.appearance.selectionColor
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.Color
import javax.swing.BoxLayout

@Composable
internal fun ColumnScope.TextColorsCard() {
    ExampleCard("caretColor / selectionColor / selectedTextColor / disabledTextColor") {
        var text by remember { mutableStateOf("Select part of this text to see the selection colors.") }
        var caret by remember { mutableStateOf(TEXT_COLOR_SWATCHES[0]) }
        var selection by remember { mutableStateOf(TEXT_COLOR_SWATCHES[3]) }
        var selectedText by remember { mutableStateOf(TEXT_COLOR_SWATCHES[1]) }
        var disabledText by remember { mutableStateOf(TEXT_COLOR_SWATCHES[4]) }
        var enabled by remember { mutableStateOf(true) }

        FlowPanel {
            Label("Caret:")
            ColorSwatchPicker(caret) { caret = it }
        }
        FlowPanel {
            Label("Selection:")
            ColorSwatchPicker(selection) { selection = it }
        }
        FlowPanel {
            Label("Selected text:")
            ColorSwatchPicker(selectedText) { selectedText = it }
        }
        FlowPanel {
            Label("Disabled text:")
            ColorSwatchPicker(disabledText) { disabledText = it }
        }
        CheckBox(
            text = "Disabled (shows disabledTextColor)",
            checked = !enabled,
            onCheckedChange = { enabled = !it },
        )
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier =
                SwingModifier
                    .caretColor(caret.color)
                    .selectionColor(selection.color)
                    .selectedTextColor(selectedText.color)
                    .disabledTextColor(disabledText.color)
                    .enabled(enabled),
            columns = 40,
        )
    }
}

@Composable
private fun ColorSwatchPicker(
    selected: ColorSwatch,
    onSelectionChange: (ColorSwatch) -> Unit,
) {
    RadioGroup(
        selectedIndex = TEXT_COLOR_SWATCHES.indexOf(selected),
        onSelectionChange = { onSelectionChange(TEXT_COLOR_SWATCHES[it]) },
        axis = BoxLayout.X_AXIS,
    ) {
        TEXT_COLOR_SWATCHES.forEach { option(it.name) }
    }
}

/** A named [Color], so the [TextColorsCard] radio groups label each option instead of a Color's toString. */
private data class ColorSwatch(
    val name: String,
    val color: Color,
)

private val TEXT_COLOR_SWATCHES =
    listOf(
        ColorSwatch("Red", Color.RED),
        ColorSwatch("Green", Color.GREEN),
        ColorSwatch("Blue", Color.BLUE),
        ColorSwatch("Orange", Color.ORANGE),
        ColorSwatch("Magenta", Color.MAGENTA),
    )

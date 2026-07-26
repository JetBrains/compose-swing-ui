package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.Separator
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Dimension
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

// The leaf input/display components, each wired to remember { mutableStateOf(...) } so the rendered
// value and the live state stay in lock-step. PasswordField exposes its value as a CharArray, so the
// strength echo is derived from the array length without ever interning the cleartext into a String.
@Composable
internal fun ComponentsSection() {
    SectionColumn {
        SectionHeading("Components")
        ButtonAndLabelCard()
        TextInputCard()
        PasswordCard()
        ToggleCard()
        ChoiceCard()
        RangeCard()
        ListBoxCard()
        SeparatorCard()
    }
}

@Composable
private fun ButtonAndLabelCard() {
    ExampleCard("Button & Label") {
        var counter by remember { mutableIntStateOf(0) }
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            Label("Counter: $counter")
            Button("Increment", onClick = { counter++ })
            Button("Decrement", onClick = { counter-- })
            Button("Reset", onClick = { counter = 0 })
        }
        Label("Right-aligned label", horizontalAlignment = SwingConstants.RIGHT)
    }
}

@Composable
private fun TextInputCard() {
    ExampleCard("TextField & TextArea") {
        var line by remember { mutableStateOf("Edit me") }
        var notes by remember { mutableStateOf("Multi-line\ntext area") }
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            Label("TextField:")
            TextField(value = line, onValueChange = { line = it }, columns = 24)
        }
        Label("Echo: $line")
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            Label("TextArea:")
            TextArea(value = notes, onValueChange = { notes = it }, rows = 3, columns = 30)
        }
    }
}

@Composable
private fun PasswordCard() {
    ExampleCard("PasswordField (CharArray)") {
        var secret by remember { mutableStateOf(CharArray(0)) }
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            Label("Password:")
            PasswordField(value = secret, onValueChange = { secret = it }, columns = 20)
        }
        Label("Length: ${secret.size}")
    }
}

@Composable
private fun ToggleCard() {
    ExampleCard("CheckBox & RadioButton") {
        var checked by remember { mutableStateOf(false) }
        CheckBox(
            text = "Enable feature",
            checked = checked,
            onCheckedChange = { checked = it },
        )
        Label("Feature is ${if (checked) "on" else "off"}")

        var choice by remember { mutableIntStateOf(0) }
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            RadioButton(text = "Low", selected = choice == 0, onSelect = { choice = 0 })
            RadioButton(text = "Medium", selected = choice == 1, onSelect = { choice = 1 })
            RadioButton(text = "High", selected = choice == 2, onSelect = { choice = 2 })
        }
        Label("Priority index: $choice")
    }
}

@Composable
private fun ChoiceCard() {
    ExampleCard("ComboBox") {
        // Each option carries a glyph and a name; itemContent renders an arbitrary composable cell
        // (a Row of a glyph label and a name label) rather than the item's toString.
        val options =
            listOf(
                Language("🟣", "Kotlin"),
                Language("☕", "Java"),
                Language("🔴", "Scala"),
                Language("🎸", "Groovy"),
            )
        var selected by remember { mutableIntStateOf(0) }
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            Label("Language:")
            ComboBox(
                items = options,
                selectedIndex = selected,
                onSelectionChange = { selected = it },
            ) { language ->
                FlowPanel {
                    Label(language.glyph)
                    Label(language.name)
                }
            }
        }
        Label("Selected: ${options.getOrNull(selected)?.name ?: "none"}")
    }
}

/** A choice with a leading glyph, so the [ComboBox]/[ListBox] cards can render a composable cell. */
private data class Language(
    val glyph: String,
    val name: String,
)

@Composable
private fun RangeCard() {
    ExampleCard("Slider & ProgressBar") {
        var amount by remember { mutableIntStateOf(40) }
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            Label("Amount: $amount")
            Slider(
                value = amount,
                onValueChange = { amount = it },
                min = 0,
                max = 100,
            )
        }
        ProgressBar(value = amount, min = 0, max = 100)
        Label("Indeterminate ProgressBar:")
        ProgressBar(value = 0, indeterminate = true)
    }
}

@Composable
private fun ListBoxCard() {
    ExampleCard("ListBox in a ScrollPane") {
        val rows = (1..30).map { "Row $it" }
        var selection by remember { mutableStateOf(listOf(0)) }
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(220, 120)).alignmentX(LEFT_ALIGNED)) {
            content {
                // itemContent renders each row as a composable cell: a bullet glyph plus the row text,
                // with the glyph reflecting whether that row is selected.
                ListBox(
                    items = rows,
                    selectedIndices = selection,
                    onSelectionChange = { selection = it },
                    selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                ) { row ->
                    FlowPanel {
                        Label(if (isSelected) "●" else "○")
                        Label(row)
                    }
                }
            }
        }
        Label("Selected indices: $selection")
    }
}

@Composable
private fun SeparatorCard() {
    ExampleCard("Separator") {
        Label("Above the horizontal separator")
        Separator(orientation = SwingConstants.HORIZONTAL)
        Label("Below the horizontal separator")
    }
}

package org.jetbrains.compose.swing.samples.widgets.components

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
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.Row
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.rememberDotIcon
import java.awt.Color
import java.awt.Dimension
import javax.swing.SwingConstants

// The slot a cell keeps for its leading glyph, so a name starts in the same place in every row.
private const val GLYPH_SLOT = 28

// The leaf input/display components, each wired to remember { mutableStateOf(...) } so the rendered
// value and the live state stay in lock-step. PasswordField exposes its value as a CharArray, so the
// strength echo is derived from the array length without ever interning the cleartext into a String.
@Composable
internal fun ComponentsSection() {
    SectionColumn {
        SectionHeading("Components")
        ButtonAndLabelCard()
        TextInputCard()
        TextAreaOptionsCard()
        TextColorsCard()
        PasswordCard()
        PasswordStateCard()
        ToggleCard()
        ChoiceCard()
        RangeCard()
        ListBoxCard()
        ListBoxSizingCard()
        ListBoxModelCard()
        SeparatorCard()
    }
}

@Composable
private fun ColumnScope.ButtonAndLabelCard() {
    ExampleCard("Button & Label") {
        var counter by remember { mutableIntStateOf(0) }
        FlowPanel {
            Label("Counter: $counter")
            Button("Increment", onClick = { counter++ })
            Button("Decrement", onClick = { counter-- })
            Button("Reset", onClick = { counter = 0 })
        }
        Label("Right-aligned label", modifier = SwingModifier.horizontalAlignment(SwingConstants.RIGHT))
    }
}

@Composable
private fun ColumnScope.TextInputCard() {
    ExampleCard("TextField & TextArea") {
        var line by remember { mutableStateOf("Edit me") }
        var notes by remember { mutableStateOf("Multi-line\ntext area") }
        FlowPanel {
            Label("TextField:")
            TextField(value = line, onValueChange = { line = it }, columns = 24)
        }
        Label("Echo: $line")
        FlowPanel {
            Label("TextArea:")
            TextArea(value = notes, onValueChange = { notes = it }, rows = 3, columns = 30)
        }
    }
}

@Composable
private fun ColumnScope.TextAreaOptionsCard() {
    ExampleCard("TextArea (lineWrap / wrapStyleWord / tabSize / editable)") {
        var notes by
            remember {
                mutableStateOf(
                    "A line long enough to wrap once the option below is switched on.\n\tA tab-indented line.",
                )
            }
        var lineWrap by remember { mutableStateOf(false) }
        var wrapStyleWord by remember { mutableStateOf(false) }
        var editable by remember { mutableStateOf(true) }
        var tabSize by remember { mutableIntStateOf(8) }

        FlowPanel {
            CheckBox(text = "Line wrap", checked = lineWrap, onCheckedChange = { lineWrap = it })
            CheckBox(
                text = "Wrap at word boundaries",
                checked = wrapStyleWord,
                onCheckedChange = { wrapStyleWord = it },
            )
            CheckBox(text = "Editable", checked = editable, onCheckedChange = { editable = it })
        }
        FlowPanel {
            Label("Tab size:")
            Spinner(tabSize, onValueChange = { tabSize = it.toInt() }, min = 1, max = 16, step = 1)
        }
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 100))) {
            content {
                TextArea(
                    value = notes,
                    onValueChange = { notes = it },
                    rows = 4,
                    columns = 40,
                    editable = editable,
                    lineWrap = lineWrap,
                    wrapStyleWord = wrapStyleWord,
                    tabSize = tabSize,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PasswordCard() {
    ExampleCard("PasswordField (CharArray, echoChar)") {
        var secret by remember { mutableStateOf(CharArray(0)) }
        var reveal by remember { mutableStateOf(false) }
        FlowPanel {
            Label("Password:")
            PasswordField(
                value = secret,
                onValueChange = { secret = it },
                echoChar = if (reveal) '\u0000' else null,
                columns = 20,
            )
        }
        CheckBox(text = "Reveal characters", checked = reveal, onCheckedChange = { reveal = it })
        Label("Length: ${secret.size}")
    }
}

@Composable
private fun ColumnScope.PasswordStateCard() {
    ExampleCard("PasswordField (DocumentState)") {
        val state = rememberDocumentState("hunter2")
        FlowPanel {
            Label("Password:")
            PasswordField(state = state, columns = 20)
            Button("Undo", modifier = SwingModifier.enabled(state.canUndo), onClick = state::undo)
        }
        Label("Length: ${state.text.length}")
    }
}

@Composable
private fun ColumnScope.ToggleCard() {
    ExampleCard("CheckBox & RadioButton") {
        var checked by remember { mutableStateOf(false) }
        CheckBox(
            text = "Enable feature",
            checked = checked,
            onCheckedChange = { checked = it },
        )
        Label("Feature is ${if (checked) "on" else "off"}")

        var choice by remember { mutableIntStateOf(0) }
        FlowPanel {
            RadioButton(text = "Low", selected = choice == 0, onSelectedChange = { choice = 0 })
            RadioButton(text = "Medium", selected = choice == 1, onSelectedChange = { choice = 1 })
            RadioButton(text = "High", selected = choice == 2, onSelectedChange = { choice = 2 })
        }
        Label("Priority index: $choice")
    }
}

@Composable
private fun ColumnScope.ChoiceCard() {
    ExampleCard("ComboBox") {
        // Each option carries a glyph and a name; itemContent renders an arbitrary composable cell
        // (a glyph label plus a name label) rather than the item's toString.
        val options =
            listOf(
                Language(Color(0x7F, 0x52, 0xFF), "Kotlin"),
                Language(Color(0xF8, 0x98, 0x20), "Java"),
                Language(Color(0xDC, 0x32, 0x2F), "Scala"),
                Language(Color(0x4A, 0x90, 0xD9), "Groovy"),
            )
        var selected by remember { mutableStateOf<Language?>(options.first()) }
        var editable by remember { mutableStateOf(false) }
        var typed by remember { mutableStateOf("") }
        var maxRowCount by remember { mutableIntStateOf(8) }

        FlowPanel {
            CheckBox(text = "Editable", checked = editable, onCheckedChange = { editable = it })
            Label("Max rows shown:")
            Spinner(maxRowCount, onValueChange = { maxRowCount = it.toInt() }, min = 1, max = 8, step = 1)
        }
        FlowPanel {
            Label("Language:")
            ComboBox(
                items = options,
                selectedItem = selected,
                onSelectionChange = { selected = it },
                editable = editable,
                onValueCommit = { typed = it },
                maximumRowCount = maxRowCount,
            ) { language ->
                // A cell renders one component, and its layout is what arranges the row: the glyph
                // holds a slot of its own on the leading edge, so every name starts in the same place
                // whatever glyph precedes it, and the name takes the room that is left.
                val swatch = rememberDotIcon(language.swatch)
                BorderPanel(modifier = SwingModifier.opaque(false)) {
                    west {
                        Label(
                            "",
                            modifier =
                                SwingModifier
                                    .icon(swatch)
                                    .preferredSize(GLYPH_SLOT, GLYPH_SLOT)
                                    .horizontalAlignment(SwingConstants.CENTER),
                        )
                    }
                    center { Label(language.name) }
                }
            }
        }
        Label("Selected: ${selected?.name ?: "none"}")
        // onValueCommit fires only for editable text the items don't name - selecting an option never
        // touches it, which is why it stays blank until something is typed and committed.
        if (editable) Label("Typed (unmatched by an item): ${typed.ifEmpty { "(nothing yet)" }}")
    }
}

/** A choice with a leading color swatch, so the [ComboBox] card can render a composable cell. */
private data class Language(
    val swatch: Color,
    val name: String,
)

@Composable
private fun ColumnScope.RangeCard() {
    ExampleCard("Slider & ProgressBar") {
        var amount by remember { mutableIntStateOf(40) }
        var settledAmount by remember { mutableIntStateOf(40) }
        var vertical by remember { mutableStateOf(false) }
        var inverted by remember { mutableStateOf(false) }
        var paintTicks by remember { mutableStateOf(false) }
        var paintLabels by remember { mutableStateOf(false) }
        var snapToTicks by remember { mutableStateOf(false) }
        var customString by remember { mutableStateOf(false) }
        // The label table a ticked, labeled slider paints at its major ticks.
        val labels = mapOf(0 to "Low", 50 to "Mid", 100 to "High")
        val orientation = if (vertical) SwingConstants.VERTICAL else SwingConstants.HORIZONTAL

        FlowPanel {
            CheckBox(text = "Vertical", checked = vertical, onCheckedChange = { vertical = it })
            CheckBox(text = "Inverted", checked = inverted, onCheckedChange = { inverted = it })
        }
        FlowPanel {
            CheckBox(text = "Paint ticks", checked = paintTicks, onCheckedChange = { paintTicks = it })
            CheckBox(text = "Paint labels", checked = paintLabels, onCheckedChange = { paintLabels = it })
            CheckBox(text = "Snap to ticks", checked = snapToTicks, onCheckedChange = { snapToTicks = it })
        }
        Label("Amount: $amount")
        Slider(
            value = amount,
            onValueChange = { amount = it },
            onValueSettled = { settledAmount = it },
            min = 0,
            max = 100,
            orientation = orientation,
            inverted = inverted,
            majorTickSpacing = 25,
            minorTickSpacing = 5,
            paintTicks = paintTicks,
            paintLabels = paintLabels,
            labels = labels,
            snapToTicks = snapToTicks,
        )
        // onValueChange hears every value a drag passes through; onValueSettled hears only the one it is
        // released on, which is why the two echoes move at different rates while the slider is dragged.
        Label("Settled on: $settledAmount")

        CheckBox(text = "Custom string", checked = customString, onCheckedChange = { customString = it })
        ProgressBar(
            value = amount,
            min = 0,
            max = 100,
            orientation = orientation,
            stringPainted = true,
            string = if (customString) "$amount of 100" else null,
        )
        Label("Indeterminate ProgressBar:")
        ProgressBar(value = 0, indeterminate = true)
    }
}

@Composable
private fun ColumnScope.SeparatorCard() {
    ExampleCard("Separator") {
        Label("Above the horizontal separator")
        // A horizontal separator asks for no width of its own, so it takes the card's whole width to draw.
        Separator(
            modifier = SwingModifier.fillWidth(),
            orientation = SwingConstants.HORIZONTAL,
        )
        Label("Below the horizontal separator")
        // A vertical separator likewise asks for no height of its own; fillHeight lets it match the
        // height of the row it sits in instead of collapsing to zero.
        Row {
            Label("Left of the vertical separator")
            Separator(
                modifier = SwingModifier.fillHeight(),
                orientation = SwingConstants.VERTICAL,
            )
            Label("Right of the vertical separator")
        }
    }
}

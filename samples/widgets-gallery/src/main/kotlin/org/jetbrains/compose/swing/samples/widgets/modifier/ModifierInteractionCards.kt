package org.jetbrains.compose.swing.samples.widgets.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.displayedMnemonicIndex
import org.jetbrains.compose.swing.modifier.accessibility.mnemonic
import org.jetbrains.compose.swing.modifier.interaction.actionCommand
import org.jetbrains.compose.swing.modifier.interaction.buttonGroup
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.focusRequester
import org.jetbrains.compose.swing.modifier.interaction.focusable
import org.jetbrains.compose.swing.modifier.interaction.initialFocus
import org.jetbrains.compose.swing.modifier.interaction.popupMenu
import org.jetbrains.compose.swing.modifier.interaction.rememberFocusRequester
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.event.ActionListener
import javax.swing.ButtonGroup

// The interaction-modifier cards of the modifier gallery: enablement, focusability, focus
// requesting, mnemonics, button grouping, action commands, and popup menus.

@Composable
internal fun ColumnScope.EnabledCard() {
    ExampleCard("enabled") {
        var editable by remember { mutableStateOf(true) }
        var text by remember { mutableStateOf("Editable when enabled") }
        CheckBox(text = "Field enabled", checked = editable, onCheckedChange = { editable = it })
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = SwingModifier.enabled(editable),
            columns = 28,
        )
    }
}

@Composable
internal fun ColumnScope.FocusableCard() {
    ExampleCard("focusable") {
        var canFocus by remember { mutableStateOf(true) }
        CheckBox(text = "Button is focusable", checked = canFocus, onCheckedChange = { canFocus = it })
        FlowPanel {
            Button("Tab reaches me only when focusable", onClick = { }, modifier = SwingModifier.focusable(canFocus))
        }
    }
}

@Composable
internal fun ColumnScope.FocusCard() {
    ExampleCard("rememberFocusRequester + focusRequester") {
        val requester = rememberFocusRequester()
        var text by remember { mutableStateOf("") }
        FlowPanel {
            Button("Focus the field", onClick = { requester.requestFocus() })
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = SwingModifier.focusRequester(requester),
                columns = 20,
            )
        }
    }
}

@Composable
internal fun ColumnScope.InitialFocusCard() {
    ExampleCard("initialFocus") {
        WrappedCaption("This field takes keyboard focus automatically the first time this section is shown.")
        var text by remember { mutableStateOf("") }
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = SwingModifier.initialFocus(),
            columns = 20,
        )
    }
}

@Composable
internal fun ColumnScope.ButtonGroupCard() {
    ExampleCard("buttonGroup (hand-placed RadioButtons)") {
        val group = remember { ButtonGroup() }
        var choice by remember { mutableStateOf("Small") }
        FlowPanel {
            RadioButton(
                text = "Small",
                selected = choice == "Small",
                onSelectedChange = { choice = "Small" },
                modifier = SwingModifier.buttonGroup(group),
            )
            RadioButton(
                text = "Medium",
                selected = choice == "Medium",
                onSelectedChange = { choice = "Medium" },
                modifier = SwingModifier.buttonGroup(group),
            )
            RadioButton(
                text = "Large",
                selected = choice == "Large",
                onSelectedChange = { choice = "Large" },
                modifier = SwingModifier.buttonGroup(group),
            )
        }
        Label("Selected: $choice")
    }
}

@Composable
internal fun ColumnScope.DisplayedMnemonicIndexCard() {
    ExampleCard("displayedMnemonicIndex") {
        var underlineAs by remember { mutableStateOf(false) }
        WrappedCaption("mnemonic('A') matches both letters in \"Save As\"; the index picks which one is underlined.")
        CheckBox(
            text = "Underline the A of \"As\" instead of the a of \"Save\"",
            checked = underlineAs,
            onCheckedChange = { underlineAs = it },
        )
        FlowPanel {
            Button(
                "Save As",
                onClick = { },
                modifier =
                    SwingModifier
                        .mnemonic('A')
                        .displayedMnemonicIndex(if (underlineAs) 5 else 1),
            )
        }
    }
}

@Composable
internal fun ColumnScope.ActionCommandCard() {
    ExampleCard("actionCommand") {
        var lastCommand by remember { mutableStateOf("none") }
        val listener = remember { ActionListener { event -> lastCommand = event.actionCommand } }
        FlowPanel {
            Button("Print", actionListener = listener, modifier = SwingModifier.actionCommand("print-doc"))
            Button(
                "Print Preview",
                actionListener = listener,
                modifier = SwingModifier.actionCommand("print-preview-doc"),
            )
        }
        Label("Last command: $lastCommand")
    }
}

@Composable
internal fun ColumnScope.PopupMenuCard() {
    ExampleCard("popupMenu") {
        var expanded by remember { mutableStateOf(false) }
        var lastAction by remember { mutableStateOf("none") }
        FlowPanel {
            Button(
                "Export",
                onClick = { expanded = true },
                modifier =
                    SwingModifier.popupMenu(expanded, onDismiss = { expanded = false }) {
                        MenuItem("As CSV", onClick = { lastAction = "As CSV" })
                        MenuItem("As JSON", onClick = { lastAction = "As JSON" })
                    },
            )
        }
        Label("Last export: $lastAction")
    }
}

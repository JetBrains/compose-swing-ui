package org.jetbrains.compose.swing.samples.widgets.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.documentFilter
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.inputVerifier
import org.jetbrains.compose.swing.modifier.interaction.onAccept
import org.jetbrains.compose.swing.modifier.interaction.verifyInputWhenFocusTarget
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

@Composable
internal fun ColumnScope.DigitsOnlyCard() {
    ExampleCard("TextField + documentFilter (digits only)") {
        var pin by remember { mutableStateOf("0000") }
        FlowPanel {
            Label("PIN:")
            TextField(
                value = pin,
                onValueChange = { pin = it },
                modifier = SwingModifier.documentFilter(DigitsOnlyFilter),
                columns = 12,
            )
        }
        Label("PIN is $pin")
    }
}

@Composable
internal fun ColumnScope.AcceptCard() {
    ExampleCard("onAccept (Enter commits a field)") {
        var typed by remember { mutableStateOf("Type, then press Enter") }
        var committed by remember { mutableStateOf("nothing yet") }
        FlowPanel {
            Label("Command:")
            TextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = SwingModifier.onAccept { committed = typed },
                columns = 24,
            )
        }
        Label("Last committed: $committed")
    }
}

@Composable
internal fun ColumnScope.InputVerifierCard() {
    ExampleCard("inputVerifier & verifyInputWhenFocusTarget") {
        var port by remember { mutableStateOf("8080") }
        val portValid = port.toIntOrNull() in 1..65535
        FlowPanel {
            Label("Port (1-65535):")
            TextField(
                value = port,
                onValueChange = { port = it },
                modifier = SwingModifier.inputVerifier { port.toIntOrNull() in 1..65535 },
                columns = 8,
            )
            // Bypasses the field's own verifier, so this button acts on whatever the field holds instead
            // of being held back by it.
            Button(
                "Reset",
                modifier = SwingModifier.verifyInputWhenFocusTarget(false),
                onClick = { port = "8080" },
            )
        }
        Label(if (portValid) "Port is valid" else "Port is invalid - focus is held here until it is fixed")
    }
}

@Composable
internal fun ColumnScope.DocumentStateCard() {
    ExampleCard("TextField (DocumentState)") {
        // The state and the field share one document, so there is no value to hoist: the Label's
        // length readout and the Undo button both read straight from the state the field edits.
        val state = rememberDocumentState("hello")
        FlowPanel {
            Label("Text:")
            TextField(state = state, columns = 16)
            Button("Undo", modifier = SwingModifier.enabled(state.canUndo), onClick = state::undo)
        }
        Label("Length is ${state.text.length}")
    }
}

private object DigitsOnlyFilter : DocumentFilter() {
    override fun insertString(
        fb: FilterBypass,
        offset: Int,
        text: String?,
        attrs: AttributeSet?,
    ) {
        if (text == null || text.all(Char::isDigit)) super.insertString(fb, offset, text, attrs)
    }

    override fun replace(
        fb: FilterBypass,
        offset: Int,
        length: Int,
        text: String?,
        attrs: AttributeSet?,
    ) {
        if (text == null || text.all(Char::isDigit)) super.replace(fb, offset, length, text, attrs)
    }
}

package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.rememberSpinnerState
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.documentFilter
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Dimension
import java.text.NumberFormat
import javax.swing.JFormattedTextField
import javax.swing.text.AttributeSet
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.DocumentFilter
import javax.swing.text.MaskFormatter
import javax.swing.text.NumberFormatter

// The form-input controls - Spinner, ToggleButton, FormattedTextField - plus the documentFilter seam.
// Every card binds its control to live state echoed by an adjacent Label.
@Composable
internal fun FormInputsSection() {
    SectionColumn {
        SectionHeading("Form inputs")
        IntSpinnerCard()
        DoubleSpinnerCard()
        ListSpinnerCard()
        ToggleButtonCard()
        NumberFieldCard()
        MaskFieldCard()
        DigitsOnlyCard()
        DocumentStateCard()
    }
}

@Composable
private fun IntSpinnerCard() {
    ExampleCard("Spinner (Int)") {
        val count = rememberSpinnerState(initialValue = 3, min = 0, max = 10, step = 1)
        FlowPanel {
            Label("Count:")
            Spinner(count)
        }
        Label("Count is ${count.value}")
    }
}

@Composable
private fun DoubleSpinnerCard() {
    ExampleCard("Spinner (Double)") {
        val rate = rememberSpinnerState(initialValue = 1.5, min = 0.0, max = 5.0, step = 0.5)
        FlowPanel {
            Label("Rate:")
            Spinner(
                rate,
                // The default editor is sized for short integers and would clip "1.5" to "1."; a wider
                // preferred width lets the whole fractional value show.
                modifier = SwingModifier.preferredSize(Dimension(80, 28)),
            )
        }
        Label("Rate is ${rate.value}")
    }
}

@Composable
private fun ListSpinnerCard() {
    ExampleCard("Spinner (list)") {
        val sizes = listOf("S", "M", "L", "XL")
        val size = rememberSpinnerState(items = sizes, initialSelectedIndex = 1)
        FlowPanel {
            Label("Size:")
            Spinner(size)
        }
        Label("Size is ${size.value} (index ${sizes.indexOf(size.value)})")
    }
}

@Composable
private fun ToggleButtonCard() {
    ExampleCard("ToggleButton") {
        var bold by remember { mutableStateOf(false) }
        ToggleButton(text = "Bold", pressed = bold, onPressedChange = { bold = it })
        Label("Bold is ${if (bold) "on" else "off"}")
    }
}

@Composable
private fun NumberFieldCard() {
    ExampleCard("FormattedTextField (NumberFormatter)") {
        val initialQuantity: Any = 42
        var quantity by remember { mutableStateOf<Any?>(initialQuantity) }
        val factory =
            remember {
                val formatter = NumberFormatter(NumberFormat.getIntegerInstance())
                formatter.valueClass = Int::class.javaObjectType
                DefaultFormatterFactory(formatter)
            }
        FlowPanel {
            Label("Quantity:")
            FormattedTextField(
                value = quantity,
                formatterFactory = factory,
                onValueChange = { quantity = it },
                focusLostBehavior = JFormattedTextField.COMMIT_OR_REVERT,
                columns = 8,
            )
        }
        Label("Quantity is $quantity")
    }
}

@Composable
private fun MaskFieldCard() {
    ExampleCard("FormattedTextField (MaskFormatter)") {
        var phone by remember { mutableStateOf<Any?>("123-4567") }
        val factory =
            remember {
                val mask = MaskFormatter("###-####")
                mask.placeholderCharacter = '_'
                DefaultFormatterFactory(mask)
            }
        FlowPanel {
            Label("Phone:")
            FormattedTextField(
                value = phone,
                formatterFactory = factory,
                onValueChange = { phone = it },
                focusLostBehavior = JFormattedTextField.COMMIT,
                columns = 10,
            )
        }
        Label("Phone is $phone")
    }
}

@Composable
private fun DigitsOnlyCard() {
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
private fun DocumentStateCard() {
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

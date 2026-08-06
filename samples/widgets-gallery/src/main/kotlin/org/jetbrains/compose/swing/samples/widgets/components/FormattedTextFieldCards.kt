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
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.rememberFormattedValueState
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.text.NumberFormat
import javax.swing.JFormattedTextField
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.MaskFormatter
import javax.swing.text.NumberFormatter

@Composable
internal fun ColumnScope.NumberFieldCard() {
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
internal fun ColumnScope.FormattedValueStateCard() {
    ExampleCard("FormattedTextField (FormattedValueState)") {
        // The state owns the committed value and reports whether the typed text parses; commit() takes
        // an edit the field's own focusLostBehavior has not yet settled.
        val amount = rememberFormattedValueState(10)
        val factory =
            remember {
                val formatter = NumberFormatter(NumberFormat.getIntegerInstance())
                formatter.valueClass = Int::class.javaObjectType
                DefaultFormatterFactory(formatter)
            }
        FlowPanel {
            Label("Amount:")
            FormattedTextField(
                state = amount,
                formatterFactory = factory,
                focusLostBehavior = JFormattedTextField.COMMIT_OR_REVERT,
                columns = 8,
            )
            Button("Apply", onClick = { amount.commit() })
        }
        Label("Amount is ${amount.value} · edit is ${if (amount.isEditValid) "valid" else "invalid"}")
    }
}

@Composable
internal fun ColumnScope.MaskFieldCard() {
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

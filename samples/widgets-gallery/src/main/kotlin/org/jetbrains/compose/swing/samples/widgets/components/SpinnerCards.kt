package org.jetbrains.compose.swing.samples.widgets.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.Dimension
import java.util.Calendar
import java.util.Locale

@Composable
internal fun ColumnScope.IntSpinnerCard() {
    ExampleCard("Spinner (Int)") {
        var count by remember { mutableIntStateOf(3) }
        FlowPanel {
            Label("Count:")
            Spinner(count, onValueChange = { count = it.toInt() }, min = 0, max = 10, step = 1)
        }
        Label("Count is $count")
    }
}

@Composable
internal fun ColumnScope.DoubleSpinnerCard() {
    ExampleCard("Spinner (Double)") {
        var rate by remember { mutableStateOf(1.5) }
        FlowPanel {
            Label("Rate:")
            Spinner(
                rate,
                onValueChange = { rate = it.toDouble() },
                // The default editor is sized for short integers and would clip "1.5" to "1."; a wider
                // preferred width lets the whole fractional value show.
                modifier = SwingModifier.preferredSize(Dimension(80, 28)),
                min = 0.0,
                max = 5.0,
                step = 0.5,
            )
        }
        Label("Rate is $rate")
    }
}

@Composable
internal fun ColumnScope.ListSpinnerCard() {
    ExampleCard("Spinner (list)") {
        val sizes = listOf("S", "M", "L", "XL")
        var size by remember { mutableStateOf(sizes[1]) }
        FlowPanel {
            Label("Size:")
            Spinner(items = sizes, value = size, onValueChange = { size = it })
        }
        Label("Size is $size (index ${sizes.indexOf(size)})")
    }
}

@Composable
internal fun ColumnScope.DateSpinnerCard() {
    ExampleCard("Spinner (Date)") {
        val steps = listOf("Day" to Calendar.DAY_OF_MONTH, "Month" to Calendar.MONTH, "Year" to Calendar.YEAR)
        var step by remember { mutableStateOf(steps.first().first) }
        val calendarField = steps.first { it.first == step }.second
        val today = remember { Calendar.getInstance().time }
        var date by remember { mutableStateOf(today) }
        FlowPanel {
            Label("Step by:")
            ComboBox(
                items = steps.map { it.first },
                selectedItem = step,
                onSelectionChange = { step = it ?: steps.first().first },
            )
        }
        FlowPanel {
            Label("Date:")
            Spinner(date, onValueChange = { date = it }, calendarField = calendarField)
        }
        Label("Date is $date")
    }
}

@Composable
internal fun ColumnScope.FormatSpinnerCard() {
    ExampleCard("Spinner (format)") {
        var amount by remember { mutableIntStateOf(1_000_000) }
        FlowPanel {
            Label("Amount:")
            Spinner(
                amount,
                onValueChange = { amount = it.toInt() },
                min = 0,
                max = 10_000_000,
                step = 1_000,
                format = "#,##0",
            )
        }
        Label("Amount is $amount")
    }
}

@Composable
internal fun ColumnScope.EditorSpinnerCard() {
    ExampleCard("Spinner (composed editor)") {
        var weight by remember { mutableStateOf(70.0) }
        FlowPanel {
            Label("Weight:")
            // The editor is an island of this composition, so it reads the same state the card does and
            // renders the unit beside the value it qualifies.
            Spinner(
                weight,
                onValueChange = { weight = it.toDouble() },
                min = 0.0,
                max = 200.0,
                step = 0.5,
            ) {
                FlowPanel {
                    Label(String.format(Locale.getDefault(), "%,.1f", weight))
                    Label("kg")
                }
            }
        }
        Label("Weight is $weight kg")
    }
}

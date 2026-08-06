package org.jetbrains.compose.swing.samples.widgets.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import javax.swing.BoxLayout

// RadioGroup: a set of mutually exclusive options whose selection is held in a single selectedIndex
// state, so the echo label can only ever name one choice.
@Composable
internal fun RadioGroupSection() {
    SectionColumn {
        SectionHeading("RadioGroup")
        VerticalGroupCard()
        HorizontalGroupCard()
    }
}

@Composable
private fun ColumnScope.VerticalGroupCard() {
    ExampleCard("RadioGroup (vertical)") {
        val plans = listOf("Free", "Pro", "Team", "Enterprise")
        var selected by remember { mutableIntStateOf(0) }
        RadioGroup(selectedIndex = selected, onSelectionChange = { selected = it }) {
            plans.forEach { option(it) }
        }
        Label("Selected plan: ${plans[selected]} (index $selected)")
    }
}

@Composable
private fun ColumnScope.HorizontalGroupCard() {
    ExampleCard("RadioGroup (horizontal)") {
        val sizes = listOf("Small", "Medium", "Large")
        var selected by remember { mutableIntStateOf(1) }
        RadioGroup(
            selectedIndex = selected,
            onSelectionChange = { selected = it },
            axis = BoxLayout.X_AXIS,
        ) {
            sizes.forEach { option(it) }
        }
        Label("Selected size: ${sizes[selected]}")
    }
}

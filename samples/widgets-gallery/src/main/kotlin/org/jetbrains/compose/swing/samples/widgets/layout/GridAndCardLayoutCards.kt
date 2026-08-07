package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.CardPanel
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.GridBagPanel
import org.jetbrains.compose.swing.components.layout.GridPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.Insets

@Composable
internal fun ColumnScope.GridPanelCard() {
    ExampleCard("GridPanel (2x3)") {
        GridPanel(rows = 2, cols = 3, hgap = 6, vgap = 6) {
            repeat(6) { index -> Button("Cell ${index + 1}") }
        }
    }
}

@Composable
internal fun ColumnScope.GridBagPanelCard() {
    ExampleCard("GridBagPanel") {
        GridBagPanel {
            Label(
                "Name",
                SwingModifier.item(
                    gridx = 0,
                    gridy = 0,
                    anchor = GridBagConstraints.LINE_END,
                    insets = Insets(4, 4, 4, 4),
                ),
            )
            Button(
                "Pick a name",
                SwingModifier.item(
                    gridx = 1,
                    gridy = 0,
                    weightx = 1.0,
                    fill = GridBagConstraints.HORIZONTAL,
                    insets = Insets(4, 4, 4, 4),
                ),
            )
            Label(
                "A row spanning both columns",
                SwingModifier.item(gridx = 0, gridy = 1, gridwidth = 2, fill = GridBagConstraints.HORIZONTAL),
            )
        }
    }
}

@Composable
internal fun ColumnScope.CardPanelCard() {
    ExampleCard("CardPanel") {
        var shown by remember { mutableStateOf("A") }
        FlowPanel {
            Button("Show A", onClick = { shown = "A" })
            Button("Show B", onClick = { shown = "B" })
            Button("Show C", onClick = { shown = "C" })
        }
        CardPanel(selectedCard = shown, modifier = SwingModifier.preferredSize(Dimension(320, 60))) {
            RegionLabel("Card A", Color(0xBB, 0xDE, 0xFB), SwingModifier.card("A"))
            RegionLabel("Card B", Color(0xC8, 0xE6, 0xC9), SwingModifier.card("B"))
            RegionLabel("Card C", Color(0xFF, 0xE0, 0xB2), SwingModifier.card("C"))
        }
    }
}

package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.Insets

@Composable
internal fun ColumnScope.GridLayoutCard() {
    ExampleCard("PanelLayout.Grid (equal cells)") {
        var transposed by remember { mutableStateOf(false) }
        val rows = if (transposed) 3 else 2
        val cols = if (transposed) 2 else 3
        CheckBox(
            text = "Transpose rows and columns",
            checked = transposed,
            onCheckedChange = { transposed = it },
        )
        Label("$rows rows x $cols columns")
        Panel(
            PanelLayout.Grid(rows = rows, cols = cols, hgap = 6, vgap = 6),
            modifier = SwingModifier.layoutPanelTrack().preferredSize(Dimension(320, 120)),
        ) {
            repeat(6) { index -> LayoutRegionSwatch("Cell ${index + 1}", GRID_COLORS[index]) }
        }
    }
}

@Composable
internal fun ColumnScope.GridBagLayoutCard() {
    ExampleCard("PanelLayout.GridBag") {
        var fillsRemainingWidth by remember { mutableStateOf(true) }
        var buttonWidth by remember { mutableIntStateOf(0) }
        CheckBox(
            text = "Button fills remaining width",
            checked = fillsRemainingWidth,
            onCheckedChange = { fillsRemainingWidth = it },
        )
        Label(
            "Button: ${buttonWidth}px; fill = ${if (fillsRemainingWidth) "HORIZONTAL" else "NONE"}; " +
                "weightx = ${if (fillsRemainingWidth) "1.0" else "0.0"}",
        )
        Panel(
            PanelLayout.GridBag,
            modifier = SwingModifier.layoutPanelTrack().preferredSize(Dimension(360, 128)),
        ) {
            LayoutRegionSwatch(
                "Name",
                LayoutSampleColors.Blue,
                SwingModifier.item(
                    gridx = 0,
                    gridy = 0,
                    anchor = GridBagConstraints.LINE_END,
                    insets = Insets(4, 4, 4, 4),
                ),
                width = 64,
            )
            Button(
                "Pick a name",
                onClick = { },
                modifier =
                    SwingModifier
                        .item(
                            gridx = 1,
                            gridy = 0,
                            weightx = if (fillsRemainingWidth) 1.0 else 0.0,
                            fill =
                                if (fillsRemainingWidth) {
                                    GridBagConstraints.HORIZONTAL
                                } else {
                                    GridBagConstraints.NONE
                                },
                            insets = Insets(4, 4, 4, 4),
                        ).componentListener(onComponentResized = { buttonWidth = it.component.width }),
            )
            LayoutRegionSwatch(
                "A row spanning the remaining columns",
                LayoutSampleColors.Orange,
                SwingModifier.item(
                    gridx = 0,
                    gridy = 1,
                    gridwidth = GridBagConstraints.REMAINDER,
                    weightx = 1.0,
                    fill = GridBagConstraints.HORIZONTAL,
                ),
            )
        }
    }
}

@Composable
internal fun ColumnScope.CardDeckCard() {
    ExampleCard("PanelLayout.Card") {
        var shown by remember { mutableStateOf("A") }
        Panel {
            Button("Show A", onClick = { shown = "A" })
            Button("Show B", onClick = { shown = "B" })
            Button("Show C", onClick = { shown = "C" })
        }
        Panel(
            PanelLayout.Card(selectedCard = shown),
            modifier = SwingModifier.layoutPanelTrack().preferredSize(Dimension(320, 60)),
        ) {
            LayoutRegionSwatch("Card A", LayoutSampleColors.Blue, SwingModifier.card("A"))
            LayoutRegionSwatch("Card B", LayoutSampleColors.Green, SwingModifier.card("B"))
            LayoutRegionSwatch("Card C", LayoutSampleColors.Orange, SwingModifier.card("C"))
        }
    }
}

private val GRID_COLORS =
    listOf(
        LayoutSampleColors.Blue,
        LayoutSampleColors.Green,
        LayoutSampleColors.Orange,
        LayoutSampleColors.Pink,
        LayoutSampleColors.Purple,
        LayoutSampleColors.Teal,
    )

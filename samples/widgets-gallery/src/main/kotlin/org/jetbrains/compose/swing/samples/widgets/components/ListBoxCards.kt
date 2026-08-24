package org.jetbrains.compose.swing.samples.widgets.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.components.selection.rememberListState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.ListSelectionModel

// The fixed cell size the ListBox sizing card declares in its "Fixed" mode.
private const val FIXED_CELL_WIDTH = 160
private const val FIXED_CELL_HEIGHT = 32

private val listBoxSelectionModes =
    listOf(
        "Single" to ListSelectionModel.SINGLE_SELECTION,
        "Single interval" to ListSelectionModel.SINGLE_INTERVAL_SELECTION,
        "Multiple interval" to ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    )

private val listBoxOrientations =
    listOf(
        "Vertical" to JList.VERTICAL,
        "Vertical wrap" to JList.VERTICAL_WRAP,
        "Horizontal wrap" to JList.HORIZONTAL_WRAP,
    )

@Composable
internal fun ColumnScope.ListBoxCard() {
    ExampleCard("ListBox in a ScrollPane") {
        val rows = (1..30).map { "Row $it" }
        var selection by remember { mutableStateOf(setOf(0)) }
        var selectionModeIndex by remember { mutableIntStateOf(2) }
        var orientationIndex by remember { mutableIntStateOf(0) }
        var visibleRows by remember { mutableIntStateOf(8) }

        ListBoxSelectionModeControl(selectionModeIndex) { selectionModeIndex = it }
        ListBoxLayoutControls(
            orientationIndex = orientationIndex,
            onOrientationIndexChange = { orientationIndex = it },
            visibleRows = visibleRows,
            onVisibleRowsChange = { visibleRows = it },
        )
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(220, 120))) {
            // itemContent renders each row as a composable cell: a bullet glyph plus the row text,
            // with the glyph reflecting whether that row is selected.
            ListBox(
                items = rows,
                modifier = SwingModifier.viewport(),
                selectedIndices = selection,
                onSelectionChange = { selection = it },
                selectionMode = listBoxSelectionModes[selectionModeIndex].second,
                visibleRowCount = visibleRows,
                layoutOrientation = listBoxOrientations[orientationIndex].second,
            ) { row ->
                FlowPanel(
                    modifier = SwingModifier.opaque(false),
                    alignment = FlowLayout.LEADING,
                    vgap = 0,
                ) {
                    Label(if (isSelected) "●" else "○")
                    Label(row)
                }
            }
        }
        Label("Selected indices: $selection")
    }
}

@Composable
private fun ListBoxSelectionModeControl(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    FlowPanel {
        Label("Selection mode:")
        RadioGroup(
            selectedIndex = selectedIndex,
            onSelectionChange = onSelectedIndexChange,
            axis = BoxLayout.X_AXIS,
        ) {
            listBoxSelectionModes.forEach { (label, _) -> option(label) }
        }
    }
}

@Composable
private fun ListBoxLayoutControls(
    orientationIndex: Int,
    onOrientationIndexChange: (Int) -> Unit,
    visibleRows: Int,
    onVisibleRowsChange: (Int) -> Unit,
) {
    FlowPanel {
        Label("Layout:")
        RadioGroup(
            selectedIndex = orientationIndex,
            onSelectionChange = onOrientationIndexChange,
            axis = BoxLayout.X_AXIS,
        ) {
            listBoxOrientations.forEach { (label, _) -> option(label) }
        }
        Label("Visible rows:")
        Spinner(visibleRows, onValueChange = { onVisibleRowsChange(it.toInt()) }, min = 3, max = 20, step = 1)
    }
}

@Composable
internal fun ColumnScope.ListBoxSizingCard() {
    ExampleCard("ListBox cell sizing (prototype & fixed)") {
        val rows = (1..12).map { "Item $it" }
        val sizingModes =
            listOf(
                "Auto (measure each row)",
                "Prototype (\"Item 88\")",
                "Fixed ($FIXED_CELL_WIDTH×$FIXED_CELL_HEIGHT)",
            )
        var sizingIndex by remember { mutableIntStateOf(0) }

        RadioGroup(
            selectedIndex = sizingIndex,
            onSelectionChange = { sizingIndex = it },
            axis = BoxLayout.X_AXIS,
        ) {
            sizingModes.forEach { option(it) }
        }
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(220, 120))) {
            ListBox(
                items = rows,
                modifier = SwingModifier.viewport(),
                prototypeCellValue = if (sizingIndex == 1) "Item 88" else null,
                fixedCellWidth = if (sizingIndex == 2) FIXED_CELL_WIDTH else -1,
                fixedCellHeight = if (sizingIndex == 2) FIXED_CELL_HEIGHT else -1,
            )
        }
        Label("Sizing: ${sizingModes[sizingIndex]}")
    }
}

@Composable
internal fun ColumnScope.ListBoxModelCard() {
    ExampleCard("ListBox driven by a Swing ListModel, with a ListState") {
        val model = remember { DefaultListModel<String>().apply { for (i in 1..5) addElement("Entry $i") } }
        var entryCount by remember { mutableIntStateOf(model.size()) }
        val state = rememberListState()

        FlowPanel {
            Button(
                "Add & reveal",
                onClick = {
                    model.addElement("Entry ${model.size() + 1}")
                    entryCount = model.size()
                    state.revealIndex(model.size() - 1)
                },
            )
        }
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(220, 120))) {
            ListBox(model = model, state = state, modifier = SwingModifier.viewport())
        }
        Label("Entries: $entryCount, selected: ${state.selectedIndices.size}")
        WrappedCaption("The list renders this DefaultListModel as-is; adding an entry reveals it.")
    }
}

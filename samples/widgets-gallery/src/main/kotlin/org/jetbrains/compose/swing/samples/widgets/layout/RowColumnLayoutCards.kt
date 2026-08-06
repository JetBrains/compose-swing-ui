package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Alignment
import org.jetbrains.compose.swing.components.layout.Arrangement
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.Row
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.Dimension
import javax.swing.BoxLayout

// A Row hands every child the width it asks for and gives the rest to the one child that claims it with
// weight, so the two labels stay at their own width whatever the row is given.
@Composable
internal fun ColumnScope.RowCard() {
    ExampleCard("Row (arrangement + weight)") {
        Row(
            modifier = SwingModifier.fillWidth(),
            horizontalArrangement = Arrangement.spacedBy(8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Leading")
            Button("Takes the width that is left", modifier = SwingModifier.weight(1f))
            Label("Trailing")
        }
    }
}

// A radio group switches the Row's own horizontalArrangement live, and the taller two-line label gives
// the row real height for the two buttons' own placement - one pinned to the bottom, the other filling
// the row - to show against.
@Composable
internal fun ColumnScope.RowArrangementCard() {
    ExampleCard("Row (arrangement, live)") {
        val arrangements: List<Pair<String, Arrangement.Horizontal>> =
            listOf(
                "Start" to Arrangement.Start,
                "Center" to Arrangement.Center,
                "End" to Arrangement.End,
                "SpaceBetween" to Arrangement.SpaceBetween,
                "SpaceAround" to Arrangement.SpaceAround,
                "SpaceEvenly" to Arrangement.SpaceEvenly,
                "Aligned to end" to Arrangement.aligned(Alignment.End),
            )
        var selected by remember { mutableIntStateOf(0) }
        RadioGroup(
            selectedIndex = selected,
            onSelectionChange = { selected = it },
            axis = BoxLayout.X_AXIS,
        ) {
            arrangements.forEach { (name, _) -> option(name) }
        }
        Row(
            modifier = SwingModifier.fillWidth(),
            horizontalArrangement = arrangements[selected].second,
        ) {
            Label(text = "<html>Two<br>lines</html>")
            Button("Bottom-aligned", modifier = SwingModifier.align(Alignment.Bottom))
            Button("Fills height", modifier = SwingModifier.fillHeight())
        }
    }
}

// A Column places the height it has left over by its arrangement rather than pushing it into a child,
// and a child that wants a different place across the column names its own alignment.
@Composable
internal fun ColumnScope.ColumnCard() {
    ExampleCard("Column (arrangement + alignment)") {
        Column(
            modifier = SwingModifier.preferredSize(Dimension(400, 140)),
            verticalArrangement = Arrangement.spacedBy(6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button("Centred by the column")
            Button("Centred too")
            Button("Aligned to the end", modifier = SwingModifier.align(Alignment.End))
        }
    }
}

// The vertical counterpart of RowArrangementCard: the column carries a fixed preferred height so the
// leftover space an arrangement places is visible.
@Composable
internal fun ColumnScope.ColumnArrangementCard() {
    ExampleCard("Column (arrangement, live)") {
        val arrangements: List<Pair<String, Arrangement.Vertical>> =
            listOf(
                "Top" to Arrangement.Top,
                "Center" to Arrangement.Center,
                "Bottom" to Arrangement.Bottom,
                "SpaceBetween" to Arrangement.SpaceBetween,
                "SpaceAround" to Arrangement.SpaceAround,
                "SpaceEvenly" to Arrangement.SpaceEvenly,
                "Aligned to bottom" to Arrangement.aligned(Alignment.Bottom),
            )
        var selected by remember { mutableIntStateOf(0) }
        RadioGroup(
            selectedIndex = selected,
            onSelectionChange = { selected = it },
            axis = BoxLayout.X_AXIS,
        ) {
            arrangements.forEach { (name, _) -> option(name) }
        }
        Column(
            modifier = SwingModifier.preferredSize(Dimension(200, 160)),
            verticalArrangement = arrangements[selected].second,
        ) {
            Button("One")
            Button("Two")
            Button("Three")
        }
    }
}

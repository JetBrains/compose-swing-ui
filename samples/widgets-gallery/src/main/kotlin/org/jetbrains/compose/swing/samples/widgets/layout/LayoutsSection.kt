package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.tooling.Preview

// Every panel layout, including both border-region families - the absolute compass (north/south/...)
// and the orientation-aware one (lineStart/lineEnd) - plus a card deck whose visible card is driven by
// state, a live switch among a Row's and a Column's arrangements, and the empty-space primitives
// (RigidArea, Spacer, Strut, Glue) a linear panel or a ToolBar gaps its items with.
@Preview
@Composable
internal fun LayoutsSection() {
    SectionColumn {
        SectionHeading("Layouts")
        RowCard()
        RowArrangementCard()
        ColumnCard()
        ColumnArrangementCard()
        FlowLayoutCard()
        BorderCompassCard()
        BorderOrientationCard()
        LinearLayoutCard()
        BoxFillersCard()
        GridLayoutCard()
        GridBagLayoutCard()
        CardDeckCard()
    }
}

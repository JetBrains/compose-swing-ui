package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.GridPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.rememberScrollState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.adjustmentListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.AdjustmentListener
import javax.swing.BorderFactory
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.SwingConstants

// The full ScrollPaneScope: a scrollable grid as the viewport content, a synced row header and column
// header, and a corner badge in the upper-leading slot, each child naming its own region. The scrollbar
// policies are forced always-on so every slot is visible at once. Further down: the pane's hoistable
// ScrollState, the viewport content's own scrolling behavior, its border and wheel-scrolling switch, and
// a raw JScrollBar driven through adjustmentListener.
@Preview
@Composable
internal fun ScrollPaneSection() {
    SectionColumn {
        SectionHeading("ScrollPane")
        ExampleCard("viewport + rowHeader + columnHeader + corner") {
            ScrollPane(
                modifier = SwingModifier.preferredSize(Dimension(420, 240)),
                verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS,
            ) {
                GridPanel(SwingModifier.viewport(), rows = ROWS, cols = COLS, hgap = 1, vgap = 1) {
                    repeat(ROWS * COLS) { index ->
                        Cell("R${index / COLS},C${index % COLS}", Color(0xEC, 0xEF, 0xF1))
                    }
                }
                GridPanel(SwingModifier.columnHeader(), rows = 1, cols = COLS) {
                    repeat(COLS) { col -> Cell("Col $col", Color(0xCF, 0xD8, 0xDC)) }
                }
                GridPanel(SwingModifier.rowHeader(), rows = ROWS, cols = 1) {
                    repeat(ROWS) { row -> Cell("Row $row", Color(0xCF, 0xD8, 0xDC)) }
                }
                Cell("⌗", Color(0x90, 0xA4, 0xAE), SwingModifier.corner(JScrollPane.UPPER_LEADING_CORNER))
            }
        }
        ExampleCard("Plain viewport-only ScrollPane") {
            ScrollPane(modifier = SwingModifier.preferredSize(Dimension(420, 100))) {
                Column(SwingModifier.viewport()) {
                    repeat(20) { Label("Scrollable line ${it + 1}") }
                }
            }
        }
        ScrollStateCard()
        ContentBehaviorCard()
        BorderAndWheelCard()
        AdjustmentListenerCard()
    }
}

@Composable
private fun ColumnScope.ScrollStateCard() {
    ExampleCard("ScrollPane (ScrollState)") {
        val scroll = rememberScrollState()
        FlowPanel {
            Button(
                "Scroll to start",
                onClick = {
                    scroll.x = 0
                    scroll.y = 0
                },
            )
            Button(
                "Scroll to end",
                onClick = {
                    scroll.x = scroll.maxX
                    scroll.y = scroll.maxY
                },
            )
            // The grid below has 1px hgap/vgap, so cell (col, row) starts at (col * 61, row * 25).
            Button("Reveal R9,C6", onClick = { scroll.revealRect(Rectangle(6 * 61, 9 * 25, 60, 24)) })
        }
        Label("x: ${scroll.x} (max ${scroll.maxX})   y: ${scroll.y} (max ${scroll.maxY})")
        Slider(value = scroll.x, onValueChange = { scroll.x = it }, min = 0, max = scroll.maxX)
        Slider(value = scroll.y, onValueChange = { scroll.y = it }, min = 0, max = scroll.maxY)
        Label("Viewport ${scroll.extentWidth}x${scroll.extentHeight}, content ${scroll.viewWidth}x${scroll.viewHeight}")
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(220, 120)), state = scroll) {
            GridPanel(SwingModifier.viewport(), rows = ROWS, cols = COLS, hgap = 1, vgap = 1) {
                repeat(ROWS * COLS) { index ->
                    Cell("R${index / COLS},C${index % COLS}", Color(0xE1, 0xF5, 0xFE))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ContentBehaviorCard() {
    ExampleCard("SwingModifier.viewport (increments + tracksViewport)") {
        var unitIncrement by remember { mutableIntStateOf(16) }
        var blockIncrement by remember { mutableIntStateOf(80) }
        var tracksWidth by remember { mutableStateOf(false) }
        var tracksHeight by remember { mutableStateOf(false) }

        FlowPanel {
            Label("Unit increment:")
            Spinner(unitIncrement, onValueChange = { unitIncrement = it.toInt() }, min = 1, max = 200, step = 1)
            Label("Block increment:")
            Spinner(blockIncrement, onValueChange = { blockIncrement = it.toInt() }, min = 1, max = 400, step = 10)
        }
        FlowPanel {
            CheckBox(text = "Tracks viewport width", checked = tracksWidth, onCheckedChange = { tracksWidth = it })
            CheckBox(text = "Tracks viewport height", checked = tracksHeight, onCheckedChange = { tracksHeight = it })
        }
        WrappedCaption(
            "Unchecked, the grid keeps its own cell size and a scrollbar reaches the rest; checked, it " +
                "stretches to fill the viewport on that axis instead. The increments govern how far an " +
                "arrow-button click or a page click scrolls.",
        )
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(180, 70))) {
            GridPanel(
                SwingModifier.viewport(
                    unitIncrement = unitIncrement,
                    blockIncrement = blockIncrement,
                    tracksViewportWidth = tracksWidth,
                    tracksViewportHeight = tracksHeight,
                ),
                rows = 4,
                cols = 6,
                hgap = 1,
                vgap = 1,
            ) {
                repeat(4 * 6) { index -> Cell("${index / 6},${index % 6}", Color(0xFF, 0xF3, 0xE0)) }
            }
        }
    }
}

@Composable
private fun ColumnScope.BorderAndWheelCard() {
    ExampleCard("ScrollPane (viewportBorder + wheelScrollingEnabled)") {
        var showBorder by remember { mutableStateOf(true) }
        var wheelEnabled by remember { mutableStateOf(true) }
        // A border is compared by identity, so one built inline would be a new border on every
        // recomposition and would be written to the viewport each time.
        val redOutline = remember { BorderFactory.createLineBorder(Color(0xE5, 0x39, 0x35), 3) }
        FlowPanel {
            CheckBox(text = "Viewport border", checked = showBorder, onCheckedChange = { showBorder = it })
            CheckBox(
                text = "Wheel scrolling enabled",
                checked = wheelEnabled,
                onCheckedChange = { wheelEnabled = it },
            )
        }
        ScrollPane(
            modifier = SwingModifier.preferredSize(Dimension(200, 90)),
            viewportBorder = if (showBorder) redOutline else null,
            wheelScrollingEnabled = wheelEnabled,
        ) {
            GridPanel(SwingModifier.viewport(), rows = ROWS, cols = COLS, hgap = 1, vgap = 1) {
                repeat(ROWS * COLS) { index ->
                    Cell("R${index / COLS},C${index % COLS}", Color(0xEC, 0xEF, 0xF1))
                }
            }
        }
        WrappedCaption("Try the mouse wheel over the grid above; disabling wheel scrolling leaves only the scrollbars.")
    }
}

@Composable
private fun ColumnScope.AdjustmentListenerCard() {
    ExampleCard("JScrollBar + adjustmentListener") {
        WrappedCaption(
            "A scrollbar a custom component drives itself, wrapped with SwingNode; adjustmentListener " +
                "reports every value it moves to, exactly as ScrollPane's own scrollbars do internally.",
        )
        var position by remember { mutableIntStateOf(0) }
        val listener = remember { AdjustmentListener { event -> position = event.value } }
        SwingNode(
            factory = { JScrollBar(JScrollBar.HORIZONTAL, 0, 10, 0, 100) },
            modifier = SwingModifier.preferredSize(Dimension(240, 18)).adjustmentListener(listener),
        )
        Label("Position: $position")
    }
}

private const val ROWS = 12
private const val COLS = 8

@Composable
private fun Cell(
    text: String,
    color: Color,
    modifier: SwingModifier = SwingModifier,
) {
    Label(
        text = text,
        modifier =
            modifier
                .opaque(true)
                .background(color)
                .preferredSize(Dimension(60, 24))
                .horizontalAlignment(SwingConstants.CENTER),
    )
}

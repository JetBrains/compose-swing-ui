package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.animation.animateScrollBy
import org.jetbrains.compose.swing.animation.animateScrollTo
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.rememberScrollState
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
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

// The full ScrollPaneScope: a scrollable grid as the viewport's content, the pane's own viewport and scroll
// bars styled in place, a synced row header and column header, and a corner badge in the upper-leading
// slot. The scrollbar policies are forced always-on so every part is visible at once. Further down: the
// pane's hoistable ScrollState, moving that position over time, the viewport's scroll increments, its
// border and wheel-scrolling switch, and a raw JScrollBar driven through adjustmentListener.
@Preview
@Composable
internal fun ScrollPaneSection() {
    SectionColumn {
        SectionHeading("ScrollPane")
        ExampleCard("Viewport + scroll bars + rowHeader + columnHeader + corner") {
            ScrollPane(
                modifier = SwingModifier.preferredSize(Dimension(420, 240)),
                verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS,
            ) {
                Viewport(modifier = SwingModifier.background(Color(0x90, 0xA4, 0xAE))) {
                    Panel(PanelLayout.Grid(rows = ROWS, cols = COLS, hgap = 1, vgap = 1)) {
                        repeat(ROWS * COLS) { index ->
                            Cell("R${index / COLS},C${index % COLS}", Color(0xEC, 0xEF, 0xF1))
                        }
                    }
                }
                VerticalScrollbar(modifier = SwingModifier.background(Color(0xCF, 0xD8, 0xDC)))
                HorizontalScrollbar(modifier = SwingModifier.background(Color(0xCF, 0xD8, 0xDC)))
                Panel(PanelLayout.Grid(rows = 1, cols = COLS), SwingModifier.columnHeader()) {
                    repeat(COLS) { col -> Cell("Col $col", Color(0xCF, 0xD8, 0xDC)) }
                }
                Panel(PanelLayout.Grid(rows = ROWS, cols = 1), SwingModifier.rowHeader()) {
                    repeat(ROWS) { row -> Cell("Row $row", Color(0xCF, 0xD8, 0xDC)) }
                }
                Cell("⌗", Color(0x90, 0xA4, 0xAE), SwingModifier.corner(JScrollPane.UPPER_LEADING_CORNER))
            }
        }
        ExampleCard("Plain viewport-only ScrollPane") {
            ScrollPane(modifier = SwingModifier.preferredSize(Dimension(420, 100))) {
                Viewport {
                    Column {
                        repeat(20) { Label("Scrollable line ${it + 1}") }
                    }
                }
            }
        }
        ScrollStateCard()
        AnimatedScrollCard()
        ContentBehaviorCard()
        BorderAndWheelCard()
        AdjustmentListenerCard()
    }
}

@Composable
private fun ColumnScope.ScrollStateCard() {
    ExampleCard("ScrollPane (ScrollState)") {
        val scroll = rememberScrollState()
        Panel {
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
        LayoutSlider(
            label = "Horizontal scroll",
            valueText = "${scroll.x} / ${scroll.maxX}",
            value = scroll.x,
            onValueChange = { scroll.x = it },
            min = 0,
            max = scroll.maxX,
        )
        LayoutSlider(
            label = "Vertical scroll",
            valueText = "${scroll.y} / ${scroll.maxY}",
            value = scroll.y,
            onValueChange = { scroll.y = it },
            min = 0,
            max = scroll.maxY,
        )
        Label("Viewport ${scroll.extentWidth}x${scroll.extentHeight}, content ${scroll.viewWidth}x${scroll.viewHeight}")
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(220, 120)), state = scroll) {
            Viewport {
                Panel(PanelLayout.Grid(rows = ROWS, cols = COLS, hgap = 1, vgap = 1)) {
                    repeat(ROWS * COLS) { index ->
                        Cell("R${index / COLS},C${index % COLS}", Color(0xE1, 0xF5, 0xFE))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AnimatedScrollCard() {
    ExampleCard("ScrollPane (animateScrollTo)") {
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        WrappedCaption(
            "These buttons travel to the position over time instead of jumping to it. Whatever moves the " +
                "pane next ends the travel where it stands: press two of them in a row, or grab a " +
                "scrollbar or the wheel while one is running.",
        )
        Panel {
            Button("Animate to end", onClick = { scope.launch { scroll.animateScrollTo(scroll.maxX, scroll.maxY) } })
            Button("Animate to start", onClick = { scope.launch { scroll.animateScrollTo(0, 0) } })
            Button("Nudge down", onClick = { scope.launch { scroll.animateScrollBy(dy = 75) } })
        }
        Label("x: ${scroll.x}   y: ${scroll.y}   scrolling: ${scroll.isScrollInProgress}")
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(220, 120)), state = scroll) {
            Viewport {
                Panel(PanelLayout.Grid(rows = ROWS, cols = COLS, hgap = 1, vgap = 1)) {
                    repeat(ROWS * COLS) { index ->
                        Cell("R${index / COLS},C${index % COLS}", Color(0xE8, 0xF5, 0xE9))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ContentBehaviorCard() {
    ExampleCard("Viewport (increments)") {
        var unitIncrement by remember { mutableIntStateOf(16) }
        var blockIncrement by remember { mutableIntStateOf(80) }

        Panel {
            Label("Unit increment:")
            Spinner(unitIncrement, onValueChange = { unitIncrement = it.toInt() }, min = 1, max = 200, step = 1)
            Label("Block increment:")
            Spinner(blockIncrement, onValueChange = { blockIncrement = it.toInt() }, min = 1, max = 400, step = 10)
        }
        WrappedCaption("The increments set how far an arrow-button click or a page click scrolls.")
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(180, 70))) {
            Viewport(unitIncrement = unitIncrement, blockIncrement = blockIncrement) {
                Panel(PanelLayout.Grid(rows = 4, cols = 6, hgap = 1, vgap = 1)) {
                    repeat(4 * 6) { index -> Cell("${index / 6},${index % 6}", Color(0xFF, 0xF3, 0xE0)) }
                }
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
        Panel {
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
            Viewport {
                Panel(PanelLayout.Grid(rows = ROWS, cols = COLS, hgap = 1, vgap = 1)) {
                    repeat(ROWS * COLS) { index ->
                        Cell("R${index / COLS},C${index % COLS}", Color(0xEC, 0xEF, 0xF1))
                    }
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

package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.GridPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Color
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JScrollPane
import javax.swing.SwingConstants

// The full ScrollPaneScope: a scrollable grid as the content, a synced row header and column header,
// and a corner badge in the upper-leading slot. The scrollbar policies are forced always-on so every
// slot is visible at once.
@Composable
internal fun ScrollPaneSection() {
    SectionColumn {
        SectionHeading("ScrollPane")
        ExampleCard("content + rowHeader + columnHeader + corner") {
            ScrollPane(
                modifier = SwingModifier.preferredSize(Dimension(420, 240)),
                verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS,
            ) {
                content {
                    GridPanel(rows = ROWS, cols = COLS, hgap = 1, vgap = 1) {
                        repeat(ROWS * COLS) { index ->
                            Cell("R${index / COLS},C${index % COLS}", Color(0xEC, 0xEF, 0xF1))
                        }
                    }
                }
                columnHeader {
                    GridPanel(rows = 1, cols = COLS) {
                        repeat(COLS) { col -> Cell("Col $col", Color(0xCF, 0xD8, 0xDC)) }
                    }
                }
                rowHeader {
                    GridPanel(rows = ROWS, cols = 1) {
                        repeat(ROWS) { row -> Cell("Row $row", Color(0xCF, 0xD8, 0xDC)) }
                    }
                }
                corner(JScrollPane.UPPER_LEADING_CORNER) {
                    Cell("⌗", Color(0x90, 0xA4, 0xAE))
                }
            }
        }
        ExampleCard("Plain content-only ScrollPane") {
            ScrollPane(modifier = SwingModifier.preferredSize(Dimension(420, 100))) {
                content {
                    BoxPanel(axis = BoxLayout.Y_AXIS) {
                        repeat(20) { Label("Scrollable line ${it + 1}") }
                    }
                }
            }
        }
    }
}

private const val ROWS = 12
private const val COLS = 8

@Composable
private fun Cell(
    text: String,
    color: Color,
) {
    Label(
        text = text,
        modifier =
            SwingModifier
                .opaque(true)
                .background(color)
                .preferredSize(Dimension(60, 24))
                .horizontalAlignment(SwingConstants.CENTER),
    )
}

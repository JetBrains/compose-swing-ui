package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Layer
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseEvent

// Layer wraps one live component in place - it paints over what the view paints and watches the
// mouse events that reach it - so the whole example is a single component on this page, with no window
// of its own.
@Preview
@Composable
internal fun LayerSection() {
    SectionColumn {
        SectionHeading("Layer")
        ExampleCard("Layer (painting and mouse events over a live view)") {
            WrappedCaption(
                "The list below is the layer's view: it stays a real list, selecting as ever, while " +
                    "the layer washes it with the tint from the slider and counts every mouse press " +
                    "that lands anywhere over it.",
            )

            var tint by remember { mutableIntStateOf(0) }
            var presses by remember { mutableIntStateOf(0) }

            Panel {
                Label("Tint: $tint")
                Slider(value = tint, onValueChange = { tint = it }, min = 0, max = 220)
                Label("Presses over the layer: $presses")
            }
            Layer(
                modifier = SwingModifier.preferredSize(Dimension(240, 130)),
                // The tint is painted after the view, so the list shows through it.
                onPaint = { g, width, height, paintView ->
                    paintView()
                    g.color = Color(233, 30, 99, tint)
                    g.fillRect(0, 0, width, height)
                },
                onMouseEvent = { event -> if (event.id == MouseEvent.MOUSE_PRESSED) presses++ },
            ) {
                ListBox(
                    items = listOf("Apples", "Bananas", "Cherries", "Dates", "Elderberries"),
                    modifier = SwingModifier.view(),
                )
            }
        }
    }
}

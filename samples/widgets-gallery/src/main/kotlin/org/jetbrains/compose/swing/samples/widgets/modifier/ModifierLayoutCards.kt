package org.jetbrains.compose.swing.samples.widgets.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.Row
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.appearance.margin
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.verticalAlignment
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import org.jetbrains.compose.swing.modifier.layout.alignmentY
import org.jetbrains.compose.swing.modifier.layout.height
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.modifier.layout.width
import org.jetbrains.compose.swing.modifier.layout.x
import org.jetbrains.compose.swing.modifier.layout.y
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import javax.swing.BoxLayout
import javax.swing.JLayeredPane
import javax.swing.SwingConstants

// The layout-modifier cards of the modifier gallery: size, visibility, geometry, and alignment
// within a parent's layout manager.

@Composable
internal fun ColumnScope.SizeAndVisibilityCard() {
    ExampleCard("preferredSize / visible") {
        var shown by remember { mutableStateOf(true) }
        CheckBox(text = "Show the second button", checked = shown, onCheckedChange = { shown = it })
        FlowPanel {
            Button("Wide button", modifier = SwingModifier.preferredSize(Dimension(WIDE_BUTTON, BUTTON_HEIGHT)))
            // The slot's footprint is reserved by the wrapping panel's preferredSize, so visible(false)
            // hides the button but keeps it attached and the row does not collapse - unlike conditional
            // composition (if (shown) Button(...)), which drops the child and lets the layout reflow.
            FlowPanel(
                modifier = SwingModifier.preferredSize(Dimension(120, BUTTON_HEIGHT)),
                hgap = 0,
                vgap = 0,
            ) {
                Button("Toggle me", modifier = SwingModifier.visible(shown))
            }
        }
    }
}

@Composable
internal fun ColumnScope.SizeConstraintsCard() {
    ExampleCard("minimumSize / maximumSize") {
        WrappedCaption(
            "The button claims the whole row with weight(1f), and maximumSize caps how much of that " +
                "width it actually takes, while minimumSize keeps it from collapsing.",
        )
        Row(modifier = SwingModifier.fillWidth()) {
            Button(
                "Clamped button",
                modifier =
                    SwingModifier
                        .minimumSize(Dimension(120, BUTTON_HEIGHT))
                        .maximumSize(Dimension(240, BUTTON_HEIGHT))
                        .weight(1f),
            )
        }
    }
}

@Composable
internal fun ColumnScope.GeometryCard() {
    ExampleCard("x / y / width / height (single-axis geometry; location/size are their two-axis equivalents)") {
        var frameX by remember { mutableIntStateOf(20) }
        var frameY by remember { mutableIntStateOf(20) }
        var frameWidth by remember { mutableIntStateOf(140) }
        var frameHeight by remember { mutableIntStateOf(60) }
        WrappedCaption(
            "These set a component's actual bounds directly, so they take effect only outside a managed " +
                "layout - here, a LayeredPane, which positions each child itself.",
        )
        FlowPanel {
            GeometrySpinner("x:", frameX, { frameX = it }, min = 0, max = 200)
            GeometrySpinner("y:", frameY, { frameY = it }, min = 0, max = 140)
        }
        FlowPanel {
            GeometrySpinner("width:", frameWidth, { frameWidth = it }, min = 40, max = 240)
            GeometrySpinner("height:", frameHeight, { frameHeight = it }, min = 30, max = 100)
        }
        LayeredPane(modifier = SwingModifier.preferredSize(Dimension(280, 180))) {
            Label(
                "Adjust the spinners",
                modifier =
                    SwingModifier
                        .layer(JLayeredPane.DEFAULT_LAYER)
                        .opaque(true)
                        .background(Color(0xC8, 0xE6, 0xC9))
                        .horizontalAlignment(SwingConstants.CENTER)
                        .x(frameX)
                        .y(frameY)
                        .width(frameWidth)
                        .height(frameHeight),
            )
        }
    }
}

/** One labeled bound of the geometry card, all four of which are spun the same way. */
@Composable
private fun GeometrySpinner(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
) {
    Label(label)
    Spinner(
        value,
        onValueChange = { onValueChange(it.toInt()) },
        modifier = SwingModifier.preferredSize(Dimension(64, 24)),
        min = min,
        max = max,
        step = GEOMETRY_STEP,
    )
}

@Composable
internal fun ColumnScope.AlignmentCard() {
    ExampleCard("alignmentX / alignmentY") {
        var aligned by remember { mutableStateOf(true) }
        CheckBox(text = "Align the narrow button left", checked = aligned, onCheckedChange = { aligned = it })
        // alignmentX is the property a BoxLayout lines its children up by, so a BoxPanel is what makes
        // it visible: a left-aligned child and a centered one sit at different offsets.
        BoxPanel(axis = BoxLayout.Y_AXIS) {
            Button(
                "Wide row button",
                modifier = SwingModifier.preferredSize(Dimension(WIDE_BUTTON, BUTTON_HEIGHT)),
            )
            Button(
                "Narrow",
                modifier =
                    SwingModifier.alignmentX(
                        if (aligned) Component.LEFT_ALIGNMENT else Component.CENTER_ALIGNMENT,
                    ),
            )
        }
        BoxPanel(axis = BoxLayout.X_AXIS) {
            Label("Tall ↕", modifier = SwingModifier.preferredSize(Dimension(60, 40)))
            Label("top", modifier = SwingModifier.alignmentY(0.0f))
            Label("bottom", modifier = SwingModifier.alignmentY(1.0f))
        }
    }
}

@Composable
internal fun ColumnScope.VerticalAlignmentCard() {
    ExampleCard("verticalAlignment") {
        var choice by remember { mutableIntStateOf(1) }
        FlowPanel {
            Label("Align:")
            RadioGroup(selectedIndex = choice, onSelectionChange = { choice = it }, axis = BoxLayout.X_AXIS) {
                option("Top")
                option("Center")
                option("Bottom")
            }
        }
        val alignment = intArrayOf(SwingConstants.TOP, SwingConstants.CENTER, SwingConstants.BOTTOM)[choice]
        FlowPanel {
            Label(
                "Tall content ↕",
                modifier =
                    SwingModifier
                        .preferredSize(Dimension(160, 70))
                        .opaque(true)
                        .background(Color(0xE3, 0xF2, 0xFD))
                        .verticalAlignment(alignment),
            )
        }
    }
}

@Composable
internal fun ColumnScope.MarginCard() {
    ExampleCard("margin") {
        var inset by remember { mutableIntStateOf(4) }
        FlowPanel {
            Label("Margin:")
            Spinner(
                inset,
                onValueChange = { inset = it.toInt() },
                modifier = SwingModifier.preferredSize(Dimension(64, 24)),
                min = 0,
                max = 24,
                step = 1,
            )
        }
        val padding = inset
        FlowPanel(modifier = SwingModifier.lineBorder(Color.GRAY, 1)) {
            Button(
                "Padded button",
                modifier = SwingModifier.margin(Insets(padding, padding, padding, padding)),
            )
        }
    }
}

private const val WIDE_BUTTON = 200
private const val BUTTON_HEIGHT = 30

/** The step every [GeometrySpinner] in the geometry card advances by. */
private const val GEOMETRY_STEP = 10

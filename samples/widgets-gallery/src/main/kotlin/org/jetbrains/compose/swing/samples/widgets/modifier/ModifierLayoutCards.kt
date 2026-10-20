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
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.components.style.horizontalAlignment
import org.jetbrains.compose.swing.components.style.margin
import org.jetbrains.compose.swing.components.style.verticalAlignment
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.style.alignmentX
import org.jetbrains.compose.swing.style.alignmentY
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.height
import org.jetbrains.compose.swing.style.lineBorder
import org.jetbrains.compose.swing.style.maximumSize
import org.jetbrains.compose.swing.style.minimumSize
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.visible
import org.jetbrains.compose.swing.style.width
import org.jetbrains.compose.swing.style.x
import org.jetbrains.compose.swing.style.y
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
        Panel {
            Button(
                "Wide button",
                onClick = { },
                style = { preferredSize(Dimension(WIDE_BUTTON, BUTTON_HEIGHT)) },
            )
            // The slot's footprint is reserved by the wrapping panel's preferredSize, so visible(false)
            // hides the button but keeps it attached and the row does not collapse - unlike conditional
            // composition (if (shown) Button(...)), which drops the child and lets the layout reflow.
            Panel(
                PanelLayout.Flow(hgap = 0, vgap = 0),
                style = { preferredSize(Dimension(120, BUTTON_HEIGHT)) },
            ) {
                Button("Toggle me", onClick = { }, style = { visible(shown) })
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
        Row(modifier = SwingModifier.fillMaxWidth()) {
            Button(
                "Clamped button",
                onClick = { },
                modifier =
                    SwingModifier
                        .weight(1f),
                style = {
                    minimumSize(Dimension(120, BUTTON_HEIGHT))
                    maximumSize(Dimension(240, BUTTON_HEIGHT))
                },
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
        Panel {
            GeometrySpinner("x:", frameX, { frameX = it }, min = 0, max = 200)
            GeometrySpinner("y:", frameY, { frameY = it }, min = 0, max = 140)
        }
        Panel {
            GeometrySpinner("width:", frameWidth, { frameWidth = it }, min = 40, max = 240)
            GeometrySpinner("height:", frameHeight, { frameHeight = it }, min = 30, max = 100)
        }
        GeometryPreview(
            frameX = frameX,
            frameY = frameY,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }
}

/** The layered preview component demonstrating direct-bounds geometry modifiers outside ColumnScope. */
@Composable
private fun GeometryPreview(
    frameX: Int,
    frameY: Int,
    frameWidth: Int,
    frameHeight: Int,
) {
    LayeredPane(style = { preferredSize(Dimension(280, 180)) }) {
        Label(
            "Adjust the spinners",
            modifier =
                SwingModifier
                    .layer(JLayeredPane.DEFAULT_LAYER),
            style = {
                background(Color(0xC8, 0xE6, 0xC9))
                x(frameX)
                y(frameY)
                width(frameWidth)
                height(frameHeight)
                opaque(true)
                horizontalAlignment(SwingConstants.CENTER)
            },
        )
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
        style = { preferredSize(Dimension(64, 24)) },
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
        // alignmentX is the property a BoxLayout lines its children up by, so a linear panel is what makes
        // it visible: a left-aligned child and a centered one sit at different offsets.
        Panel(PanelLayout.Box(axis = BoxLayout.Y_AXIS)) {
            Button(
                "Wide row button",
                onClick = { },
                style = { preferredSize(Dimension(WIDE_BUTTON, BUTTON_HEIGHT)) },
            )
            Button(
                "Narrow",
                onClick = { },
                style = {
                    alignmentX(
                        if (aligned) Component.LEFT_ALIGNMENT else Component.CENTER_ALIGNMENT,
                    )
                },
            )
        }
        Panel(PanelLayout.Box(axis = BoxLayout.X_AXIS)) {
            Label("Tall ↕", style = { preferredSize(Dimension(60, 40)) })
            Label("top", style = { alignmentY(0.0f) })
            Label("bottom", style = { alignmentY(1.0f) })
        }
    }
}

@Composable
internal fun ColumnScope.VerticalAlignmentCard() {
    ExampleCard("verticalAlignment") {
        var choice by remember { mutableIntStateOf(1) }
        Panel {
            Label("Align:")
            RadioGroup(selectedIndex = choice, onSelectionChange = { choice = it }, axis = BoxLayout.X_AXIS) {
                option("Top")
                option("Center")
                option("Bottom")
            }
        }
        val alignment = intArrayOf(SwingConstants.TOP, SwingConstants.CENTER, SwingConstants.BOTTOM)[choice]
        Panel {
            Label(
                "Tall content ↕",
                style = {
                    preferredSize(Dimension(160, 70))
                    background(Color(0xE3, 0xF2, 0xFD))
                    opaque(true)
                    verticalAlignment(alignment)
                },
            )
        }
    }
}

@Composable
internal fun ColumnScope.MarginCard() {
    ExampleCard("margin") {
        var inset by remember { mutableIntStateOf(4) }
        Panel {
            Label("Margin:")
            Spinner(
                inset,
                onValueChange = { inset = it.toInt() },
                style = { preferredSize(Dimension(64, 24)) },
                min = 0,
                max = 24,
                step = 1,
            )
        }
        val padding = inset
        Panel(PanelLayout.Flow(), style = { lineBorder(Color.GRAY, 1) }) {
            Button(
                "Padded button",
                onClick = { },
                style = { margin(Insets(padding, padding, padding, padding)) },
            )
        }
    }
}

private const val WIDE_BUTTON = 200
private const val BUTTON_HEIGHT = 30

/** The step every [GeometrySpinner] in the geometry card advances by. */
private const val GEOMETRY_STEP = 10

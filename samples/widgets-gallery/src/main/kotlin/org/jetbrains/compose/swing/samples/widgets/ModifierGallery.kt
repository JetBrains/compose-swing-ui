package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.focusable
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import org.jetbrains.compose.swing.modifier.layout.alignmentY
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.layout.visible
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout

// A gallery where each SwingModifier builder visibly affects a real widget, across the modifier
// families - appearance, layout, interaction, keyboard, and raw listeners. State is hoisted so the
// appearance modifiers toggle, the interaction modifiers update a live status label, and the
// keyboard/raw-listener modifiers fire counters.
@Composable
internal fun ModifierGallery() {
    SectionColumn {
        SectionHeading("Modifier gallery")
        AppearanceCard()
        CursorAndToolTipCard()
        ClientPropertyCard()
        SizeAndVisibilityCard()
        SizeConstraintsCard()
        AlignmentCard()
        EnabledCard()
        FocusableCard()
        HoverFocusCard()
        PointerCard()
        KeyStrokeCard()
        KeyEventCard()
        SwingListenerCard()
        ChangeListenerCard()
    }
}

@Composable
private fun AppearanceCard() {
    ExampleCard("background / foreground / font / border") {
        var fancy by remember { mutableStateOf(true) }
        CheckBox(text = "Fancy styling", checked = fancy, onCheckedChange = { fancy = it })
        val styled =
            if (fancy) {
                SwingModifier
                    .opaque(true)
                    .background(Color(0xFF, 0xF3, 0xE0))
                    .foreground(Color(0xBF, 0x36, 0x0C))
                    .font(Font(Font.SERIF, Font.BOLD or Font.ITALIC, 15))
                    .border(BorderFactory.createLineBorder(Color(0xBF, 0x36, 0x0C), 2))
            } else {
                SwingModifier
            }
        Label("Styled when the box is checked", modifier = styled)
    }
}

@Composable
private fun SizeAndVisibilityCard() {
    ExampleCard("preferredSize / visible") {
        var shown by remember { mutableStateOf(true) }
        CheckBox(text = "Show the second button", checked = shown, onCheckedChange = { shown = it })
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
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
private fun EnabledCard() {
    ExampleCard("enabled") {
        var editable by remember { mutableStateOf(true) }
        var text by remember { mutableStateOf("Editable when enabled") }
        CheckBox(text = "Field enabled", checked = editable, onCheckedChange = { editable = it })
        TextField(
            value = text,
            modifier = SwingModifier.enabled(editable),
            onValueChange = { text = it },
            columns = 28,
        )
    }
}

@Composable
private fun CursorAndToolTipCard() {
    ExampleCard("cursor / toolTip") {
        var hand by remember { mutableStateOf(true) }
        CheckBox(text = "Hand cursor", checked = hand, onCheckedChange = { hand = it })
        Label(
            "Hover me: cursor + tooltip",
            modifier =
                SwingModifier
                    .opaque(true)
                    .background(Color(0xE8, 0xF5, 0xE9))
                    .preferredSize(Dimension(260, 32))
                    .alignmentX(LEFT_ALIGNED)
                    .cursor(Cursor.getPredefinedCursor(if (hand) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR))
                    .toolTip(if (hand) "Click affordance: the hand cursor" else "Plain pointer"),
        )
    }
}

@Composable
private fun ClientPropertyCard() {
    ExampleCard("clientProperty") {
        var tag by remember { mutableStateOf("alpha") }
        Label(
            "Carries clientProperty \"role\" = $tag",
            modifier = SwingModifier.clientProperty("role", tag).alignmentX(LEFT_ALIGNED),
        )
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            Button("role = alpha", onClick = { tag = "alpha" })
            Button("role = beta", onClick = { tag = "beta" })
        }
    }
}

@Composable
private fun SizeConstraintsCard() {
    ExampleCard("minimumSize / maximumSize") {
        WrappedCaption(
            "The button is clamped: a vertical BoxLayout would stretch it to the column width, but its " +
                "maximumSize caps the width while minimumSize keeps it from collapsing.",
        )
        Button(
            "Clamped button",
            modifier =
                SwingModifier
                    .minimumSize(Dimension(120, BUTTON_HEIGHT))
                    .maximumSize(Dimension(240, BUTTON_HEIGHT))
                    .alignmentX(LEFT_ALIGNED),
        )
    }
}

@Composable
private fun AlignmentCard() {
    ExampleCard("alignmentX / alignmentY") {
        var aligned by remember { mutableStateOf(true) }
        CheckBox(text = "Align both rows left", checked = aligned, onCheckedChange = { aligned = it })
        BoxPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED), axis = BoxLayout.Y_AXIS) {
            Button(
                "Wide row button",
                modifier = SwingModifier.preferredSize(Dimension(WIDE_BUTTON, BUTTON_HEIGHT)),
            )
            Button(
                "Narrow",
                modifier = SwingModifier.alignmentX(if (aligned) LEFT_ALIGNED else 0.5f),
            )
        }
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            Label("Tall ↕", modifier = SwingModifier.preferredSize(Dimension(60, 40)))
            Label("top", modifier = SwingModifier.alignmentY(0.0f))
            Label("bottom", modifier = SwingModifier.alignmentY(1.0f))
        }
    }
}

@Composable
private fun FocusableCard() {
    ExampleCard("focusable") {
        var canFocus by remember { mutableStateOf(true) }
        CheckBox(text = "Button is focusable", checked = canFocus, onCheckedChange = { canFocus = it })
        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            Button("Tab reaches me only when focusable", modifier = SwingModifier.focusable(canFocus))
        }
    }
}

private const val WIDE_BUTTON = 200
private const val BUTTON_HEIGHT = 30

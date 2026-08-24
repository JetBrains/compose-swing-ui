package org.jetbrains.compose.swing.samples.widgets.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.interaction.onFocus
import org.jetbrains.compose.swing.modifier.interaction.onHover
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import org.jetbrains.compose.swing.modifier.keyboard.onKeyEvent
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.Color
import java.awt.Dimension
import java.awt.event.KeyEvent

// The interaction half of the modifier gallery: the typed onHover/onFocus/onPointerEvent/onKeyEvent
// cards, split from the appearance/layout cards so each file stays focused on one modifier family.
// The raw java.awt/javax.swing listener cards live in the ModifierGallery*ListenerCards files.

@Composable
internal fun ColumnScope.HoverFocusCard() {
    ExampleCard("onHover / onFocus") {
        var status by remember { mutableStateOf("idle") }
        FlowPanel {
            Button(
                "Hover or focus me",
                onClick = { },
                modifier =
                    SwingModifier
                        .onHover(
                            onEnter = { status = "hovering" },
                            onExit = { status = "idle" },
                        ).onFocus(
                            onGained = { status = "focused" },
                            onLost = { status = "blurred" },
                        ),
            )
        }
        Label("Status: $status")
    }
}

@Composable
internal fun ColumnScope.PointerCard() {
    ExampleCard("onPointerEvent") {
        var lastEvent by remember { mutableStateOf("none") }
        Label(
            "Click anywhere on this label",
            modifier =
                SwingModifier
                    .opaque(true)
                    .background(Color(0xE3, 0xF2, 0xFD))
                    .preferredSize(Dimension(260, 32))
                    .onPointerEvent(
                        onPress = { e -> lastEvent = "pressed button ${e.button}" },
                        onClick = { e -> lastEvent = "clicked x${e.clickCount} button ${e.button}" },
                    ),
        )
        Label("Last pointer event: $lastEvent")
    }
}

@Composable
internal fun ColumnScope.KeyStrokeCard() {
    ExampleCard("onKeyStroke") {
        var saves by remember { mutableIntStateOf(0) }
        var text by remember { mutableStateOf("Focus me, press ctrl S") }
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = SwingModifier.onKeyStroke("ctrl S") { saves++ },
            columns = 28,
        )
        Label("Save shortcut fired $saves time(s)")
    }
}

@Composable
internal fun ColumnScope.KeyEventCard() {
    ExampleCard("onKeyEvent") {
        var consume by remember { mutableStateOf(false) }
        var lastKey by remember { mutableStateOf("none") }
        var text by remember { mutableStateOf("Type here") }
        CheckBox(text = "Consume key events", checked = consume, onCheckedChange = { consume = it })
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier =
                SwingModifier.onKeyEvent { e ->
                    if (e.id == KeyEvent.KEY_PRESSED) lastKey = KeyEvent.getKeyText(e.keyCode)
                    consume
                },
            columns = 28,
        )
        Label("Last key pressed: $lastKey")
    }
}

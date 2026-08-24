package org.jetbrains.compose.swing.samples.widgets.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.modifier.listener.keyListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.event.ActionListener
import java.awt.event.ItemListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.JComboBox
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

// Raw java.awt/javax.swing listener cards for the gallery's basic input widgets (Button, ComboBox,
// CheckBox, TextField, Slider), showing that a wrapper's typed lambda callbacks and a raw listener
// modifier can coexist on the same component.

@Composable
internal fun ColumnScope.SwingListenerCard() {
    ExampleCard("actionListener (raw ActionListener)") {
        var rawClicks by remember { mutableIntStateOf(0) }
        // remember the listener instance: Swing listeners are additive, so a fresh instance each
        // recomposition would detach the old and attach the new. The wrapper's onClick still fires too.
        val listener = remember { ActionListener { rawClicks++ } }
        Button("Click (raw listener)", onClick = { }, modifier = SwingModifier.actionListener(listener))
        Label("Raw listener fired $rawClicks time(s)")
    }
}

@Composable
internal fun ColumnScope.ChangeListenerCard() {
    ExampleCard("changeListener (raw ChangeListener)") {
        var value by remember { mutableIntStateOf(0) }
        var changes by remember { mutableIntStateOf(0) }
        val listener = remember { ChangeListener { changes++ } }
        FlowPanel {
            Label("Value: $value")
            Slider(
                value = value,
                onValueChange = { value = it },
                modifier = SwingModifier.changeListener(listener),
                min = 0,
                max = 100,
            )
        }
        Label("Raw change listener fired $changes time(s)")
    }
}

@Composable
internal fun ColumnScope.ListenerEscapeHatchCard() {
    ExampleCard("listener (escape hatch: PopupMenuListener has no typed builder)") {
        var opens by remember { mutableIntStateOf(0) }
        val popupListener =
            remember {
                object : PopupMenuListener {
                    override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
                        opens++
                    }

                    override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) = Unit

                    override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
                }
            }
        var language by remember { mutableStateOf("Kotlin") }
        FlowPanel {
            Label("Open the dropdown:")
            ComboBox(
                items = listOf("Kotlin", "Java", "Scala"),
                selectedItem = language,
                onSelectionChange = { language = it ?: language },
                modifier =
                    SwingModifier.listener(popupListener, POPUP_MENU),
            )
        }
        Label("Popup opened $opens time(s)")
    }
}

@Composable
internal fun ColumnScope.ItemListenerCard() {
    ExampleCard("itemListener (raw ItemListener)") {
        var checked by remember { mutableStateOf(false) }
        var events by remember { mutableIntStateOf(0) }
        val listener = remember { ItemListener { events++ } }
        CheckBox(
            text = "Toggle me",
            checked = checked,
            onCheckedChange = { checked = it },
            modifier = SwingModifier.itemListener(listener),
        )
        Label("Item events: $events")
    }
}

@Composable
internal fun ColumnScope.DocumentListenerCard() {
    ExampleCard("documentListener (raw DocumentListener)") {
        var text by remember { mutableStateOf("Type here") }
        var edits by remember { mutableIntStateOf(0) }
        val listener =
            remember {
                object : DocumentListener {
                    override fun insertUpdate(event: DocumentEvent) {
                        edits++
                    }

                    override fun removeUpdate(event: DocumentEvent) {
                        edits++
                    }

                    override fun changedUpdate(event: DocumentEvent) = Unit
                }
            }
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = SwingModifier.documentListener(listener),
            columns = 24,
        )
        Label("Document edits: $edits")
    }
}

@Composable
internal fun ColumnScope.PropertyChangeListenerCard() {
    ExampleCard("propertyChangeListener (bound to \"text\")") {
        var text by remember { mutableStateOf("Edit me") }
        var changes by remember { mutableIntStateOf(0) }
        val listener = remember { PropertyChangeListener { changes++ } }
        Label(text, modifier = SwingModifier.propertyChangeListener("text", listener))
        FlowPanel {
            Button("Change text", onClick = { text = if (text == "Edit me") "Changed!" else "Edit me" })
            Label("\"text\" changed $changes time(s)")
        }
    }
}

@Composable
internal fun ColumnScope.KeyListenerCard() {
    ExampleCard("keyListener (raw KeyListener)") {
        var text by remember { mutableStateOf("Type here") }
        var presses by remember { mutableIntStateOf(0) }
        val listener =
            remember {
                object : KeyAdapter() {
                    override fun keyPressed(event: KeyEvent) {
                        presses++
                    }
                }
            }
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = SwingModifier.keyListener(listener),
            columns = 24,
        )
        Label("Keys pressed: $presses")
    }
}

// The registration the escape hatch uses, held once: it is what says where the listener sits.
private val POPUP_MENU =
    ListenerRegistration<JComboBox<*>, PopupMenuListener>(
        { component, listener -> component.addPopupMenuListener(listener) },
        { component, listener -> component.removePopupMenuListener(listener) },
    )

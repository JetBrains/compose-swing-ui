package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.datatransfer.clipboard
import org.jetbrains.compose.swing.modifier.datatransfer.draggable
import org.jetbrains.compose.swing.modifier.datatransfer.dropTarget
import org.jetbrains.compose.swing.modifier.datatransfer.rememberClipboardHandle
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Color
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.TransferHandler

// The data-transfer modifiers: drag-and-drop between components and system-clipboard copy/paste.
// All payloads are plain strings carried as a StringSelection.
@Composable
internal fun DataTransferSection() {
    SectionColumn {
        SectionHeading("Data transfer")
        DragAndDropCard()
        ClipboardCard()
    }
}

@Composable
private fun DragAndDropCard() {
    ExampleCard("draggable + dropTarget (drag the label onto the panel)") {
        val payload = "Hello from the drag source"
        var dropped by remember { mutableStateOf("Nothing dropped yet") }

        FlowPanel {
            Label(
                text = payload,
                modifier =
                    SwingModifier
                        .border(BorderFactory.createDashedBorder(Color.GRAY))
                        .draggable(exportedActions = TransferHandler.COPY) { StringSelection(payload) },
            )
        }
        FlowPanel {
            FlowPanel(
                modifier =
                    SwingModifier
                        .preferredSize(Dimension(240, 60))
                        .border(BorderFactory.createLineBorder(Color.GRAY))
                        .dropTarget(
                            acceptedActions = TransferHandler.COPY,
                            canImport = { flavors -> DataFlavor.stringFlavor in flavors },
                            onDrop = { transferable ->
                                dropped = transferable.getTransferData(DataFlavor.stringFlavor) as String
                                true
                            },
                        ),
            ) {
                Label("Drop here: $dropped")
            }
        }
    }
}

@Composable
private fun ClipboardCard() {
    ExampleCard("clipboard handle (copy/paste from explicit buttons)") {
        var text by remember { mutableStateOf("Copy me to the system clipboard") }
        var status by remember { mutableStateOf("") }
        val clipboard = rememberClipboardHandle()

        FlowPanel {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier =
                    SwingModifier.clipboard(
                        transferable = { StringSelection(text) },
                        onPaste = { transferable ->
                            text = transferable.getTransferData(DataFlavor.stringFlavor) as String
                            true
                        },
                        // The buttons drive this field through the handle, so leave the standard in-field
                        // editing keystrokes to the text component rather than binding them here.
                        bindKeys = false,
                        handle = clipboard,
                    ),
            )
        }
        FlowPanel {
            Button("Copy") { clipboard.copy() }
            Button("Paste") { status = if (clipboard.paste()) "Pasted" else "Nothing to paste" }
            Label(status)
        }
    }
}

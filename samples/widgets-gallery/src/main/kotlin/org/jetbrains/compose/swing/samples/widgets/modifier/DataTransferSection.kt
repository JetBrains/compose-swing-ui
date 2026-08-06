package org.jetbrains.compose.swing.samples.widgets.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.datatransfer.clipboard
import org.jetbrains.compose.swing.modifier.datatransfer.draggable
import org.jetbrains.compose.swing.modifier.datatransfer.dropTarget
import org.jetbrains.compose.swing.modifier.datatransfer.onExportDone
import org.jetbrains.compose.swing.modifier.datatransfer.rememberClipboardHandle
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
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
        OnExportDoneCard()
    }
}

@Composable
private fun ColumnScope.DragAndDropCard() {
    ExampleCard("draggable + dropTarget (drag the label onto the panel)") {
        val payload = "Hello from the drag source"
        var dropped by remember { mutableStateOf("Nothing dropped yet") }
        // A border is compared by identity, so one built inline would be a new border on every
        // recomposition and would be written to the label each time.
        val dashed = remember { BorderFactory.createDashedBorder(Color.GRAY) }

        FlowPanel {
            Label(
                text = payload,
                modifier =
                    SwingModifier
                        .border(dashed)
                        .draggable(exportedActions = TransferHandler.COPY) { StringSelection(payload) },
            )
        }
        FlowPanel {
            FlowPanel(
                modifier =
                    SwingModifier
                        .preferredSize(Dimension(240, 60))
                        .lineBorder(Color.GRAY)
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
private fun ColumnScope.ClipboardCard() {
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
                        // The buttons drive this field through the handle, and the text component already
                        // binds the standard keystrokes to the handler this modifier installs, so there
                        // is nothing to bind here.
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

@Composable
private fun ColumnScope.OnExportDoneCard() {
    ExampleCard("onExportDone (reports a completed export, for move semantics)") {
        val payload = "Move me out"
        var lastOutcome by remember { mutableStateOf("none yet") }
        val dashed = remember { BorderFactory.createDashedBorder(Color.GRAY) }

        FlowPanel {
            Label(
                text = payload,
                modifier =
                    SwingModifier
                        .border(dashed)
                        .draggable(exportedActions = TransferHandler.MOVE) { StringSelection(payload) }
                        .onExportDone { _, _, action ->
                            lastOutcome =
                                when (action) {
                                    TransferHandler.MOVE -> "moved"
                                    TransferHandler.NONE -> "cancelled"
                                    else -> "action $action"
                                }
                        },
            )
        }
        FlowPanel {
            FlowPanel(
                modifier =
                    SwingModifier
                        .preferredSize(Dimension(240, 60))
                        .lineBorder(Color.GRAY)
                        .dropTarget(acceptedActions = TransferHandler.MOVE, onDrop = { true }),
            ) {
                Label("Drop the label here to complete the move")
            }
        }
        Label("Last export outcome: $lastOutcome")
    }
}

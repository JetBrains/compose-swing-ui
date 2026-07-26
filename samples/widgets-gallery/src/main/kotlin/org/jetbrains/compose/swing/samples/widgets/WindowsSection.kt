package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.window.Dialog
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.rememberDialogState
import org.jetbrains.compose.swing.window.rememberWindowState
import java.awt.Dimension
import javax.swing.SwingConstants

// The declarative top-level window peers: a secondary Window and a modal Dialog. Each is conditionally
// composed behind a boolean, so opening is "compose it" and closing is "stop composing it" —
// onCloseRequest simply flips the state back. The dialog is application-modal and inherits the showcase
// window as its owner via LocalWindow.
@Composable
internal fun WindowsSection() {
    SectionColumn {
        SectionHeading("Top-level windows")
        SecondaryWindowCard()
        ModalDialogCard()
    }
}

@Composable
private fun SecondaryWindowCard() {
    ExampleCard("Window (secondary top-level frame)") {
        var open by remember { mutableStateOf(false) }
        FlowPanel {
            Button(if (open) "Close window" else "Open window", onClick = { open = !open })
            Label("Window is ${if (open) "open" else "closed"}")
        }
        if (open) {
            Window(
                onCloseRequest = { open = false },
                state = rememberWindowState(size = Dimension(320, 200)),
                title = "Secondary Window",
            ) {
                BorderPanel {
                    center {
                        Label(
                            "A second top-level window, composed declaratively.",
                            modifier = SwingModifier.horizontalAlignment(SwingConstants.CENTER),
                        )
                    }
                    south {
                        FlowPanel {
                            Button("Dismiss", onClick = { open = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalDialogCard() {
    ExampleCard("Dialog (application-modal)") {
        var open by remember { mutableStateOf(false) }
        var acknowledged by remember { mutableStateOf(false) }
        FlowPanel {
            Button("Open modal dialog", onClick = { open = true })
            Label(if (acknowledged) "Last dialog: acknowledged" else "No dialog acknowledged yet")
        }
        if (open) {
            Dialog(
                onCloseRequest = { open = false },
                state = rememberDialogState(size = Dimension(320, 160)),
                title = "Confirm",
                modality = java.awt.Dialog.ModalityType.APPLICATION_MODAL,
            ) {
                BorderPanel {
                    center {
                        Label(
                            "This dialog blocks its owner while shown.",
                            modifier = SwingModifier.horizontalAlignment(SwingConstants.CENTER),
                        )
                    }
                    south {
                        FlowPanel {
                            Button("OK", onClick = {
                                acknowledged = true
                                open = false
                            })
                            Button("Cancel", onClick = { open = false })
                        }
                    }
                }
            }
        }
    }
}

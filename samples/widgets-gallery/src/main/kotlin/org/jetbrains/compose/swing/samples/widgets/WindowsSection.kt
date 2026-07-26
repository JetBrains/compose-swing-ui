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
import org.jetbrains.compose.swing.window.WindowPosition
import org.jetbrains.compose.swing.window.rememberDialogState
import org.jetbrains.compose.swing.window.rememberWindowState
import java.awt.Dimension
import java.awt.Frame
import javax.swing.SwingConstants

// The declarative top-level window peers: a secondary Window and a modal Dialog. Each is conditionally
// composed behind a boolean, so opening is "compose it" and closing is "stop composing it" -
// onCloseRequest simply flips the state back. The dialog is application-modal and inherits the showcase
// window as its owner via LocalWindow. Each peer's geometry is a hoisted state object read in both
// directions: a label shows what the user's own drag or resize wrote into it, and a button writes back.
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
        // Hoisted above the `if (open)` so the readout survives closing and reopening the window.
        val state = rememberWindowState(size = Dimension(320, 200))
        FlowPanel {
            Button(if (open) "Close window" else "Open window", onClick = { open = !open })
            Label("Window is ${if (open) "open" else "closed"}")
        }
        // Geometry is two-way: these labels show what the window writes back as the user drags or
        // resizes it, and the buttons below drive the very same properties in the other direction.
        FlowPanel {
            Label("Position: ${state.position}")
            Label("Size: ${state.width} x ${state.height}")
        }
        FlowPanel {
            Button("Centre on screen", onClick = { state.position = WindowPosition.CenteredOnScreen })
            Button("Widen by 40", onClick = { state.width += 40 })
            Button("Maximize", onClick = { state.extendedState = Frame.MAXIMIZED_BOTH })
            Button("Restore", onClick = { state.extendedState = Frame.NORMAL })
        }
        if (open) {
            Window(
                onCloseRequest = { open = false },
                state = state,
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
        // Hoisted above the `if (open)` so the dialog reopens at whatever size it was last left.
        val state = rememberDialogState(size = Dimension(360, 200))
        FlowPanel {
            Button("Open modal dialog", onClick = { open = true })
            Label(if (acknowledged) "Last dialog: acknowledged" else "No dialog acknowledged yet")
        }
        if (open) {
            Dialog(
                onCloseRequest = { open = false },
                state = state,
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
                    // The readout and the grow button live inside the dialog because a modal dialog
                    // blocks its owner, so its own content is the only place a control can reach it.
                    south {
                        FlowPanel {
                            Label("Dialog is ${state.width} x ${state.height}")
                            Button("Grow", onClick = { state.size = Dimension(state.width + 40, state.height + 20) })
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

package org.jetbrains.compose.swing.samples.widgets.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.swing.components.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Menu
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.MenuSeparator
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.window.Dialog
import org.jetbrains.compose.swing.window.DialogState
import org.jetbrains.compose.swing.window.GlassPane
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowPosition
import org.jetbrains.compose.swing.window.WindowScope
import org.jetbrains.compose.swing.window.launchApplication
import org.jetbrains.compose.swing.window.rememberDialogState
import org.jetbrains.compose.swing.window.rememberWindowState
import java.awt.Color
import java.awt.Dimension
import java.awt.Frame
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.SwingConstants

// The declarative top-level window peers: a secondary Window and a modal Dialog. Each is conditionally
// composed behind a boolean, so opening is "compose it" and closing is "stop composing it" -
// onCloseRequest flips the state back. The dialog's modality is switched live between the three AWT
// modality types. Every reactive Window/Dialog argument - resizable, alwaysOnTop, undecorated,
// iconImage, minimumSize, visible - is driven by a check box beside it, and each peer's geometry is
// demonstrated as two-way state. The secondary window also demonstrates MenuBar and GlassPane.
@Composable
internal fun WindowsSection() {
    SectionColumn {
        SectionHeading("Top-level windows")
        SecondaryWindowCard()
        ModalDialogCard()
        ApplicationEntryPointCard()
    }
}

@Composable
private fun ColumnScope.SecondaryWindowCard() {
    ExampleCard("Window (secondary top-level frame)") {
        var open by remember { mutableStateOf(false) }
        // Hoisted above the `if (open)` so the readout survives closing and reopening the window.
        val state = rememberWindowState(size = Dimension(320, 200))
        val chrome = remember { WindowChromeState() }
        var busy by remember { mutableStateOf(false) }
        val icon = remember { windowIconImage() }

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
            Button("Center on screen", onClick = { state.position = WindowPosition.CenteredOnScreen })
            Button("Widen by 40", onClick = { state.width += 40 })
            Button("Maximize", onClick = { state.extendedState = Frame.MAXIMIZED_BOTH })
            Button("Restore", onClick = { state.extendedState = Frame.NORMAL })
        }
        // The remaining Window(...) arguments are reactive too: each check box writes straight into
        // the parameter it names.
        WindowChromeControls(chrome)
        if (open) {
            Window(
                onCloseRequest = { open = false },
                state = state,
                title = "Secondary Window",
                visible = chrome.visible,
                resizable = chrome.resizable,
                alwaysOnTop = chrome.alwaysOnTop,
                iconImage = if (chrome.customIcon) icon else null,
                minimumSize = if (chrome.enforceMinimumSize) Dimension(240, 160) else null,
                undecorated = chrome.undecorated,
            ) {
                SecondaryWindowContent(
                    alwaysOnTop = chrome.alwaysOnTop,
                    onAlwaysOnTopChange = { chrome.alwaysOnTop = it },
                    busy = busy,
                    onBusyChange = { busy = it },
                    onClose = { open = false },
                )
            }
        }
    }
}

// Resizable, always-on-top, undecorated, custom icon and minimum size are grouped in one state
// holder because SecondaryWindowCard only ever reads or offers them together, as the window's
// chrome controls.
private class WindowChromeState {
    var resizable by mutableStateOf(true)
    var alwaysOnTop by mutableStateOf(false)
    var undecorated by mutableStateOf(false)
    var customIcon by mutableStateOf(false)
    var enforceMinimumSize by mutableStateOf(false)
    var visible by mutableStateOf(true)
}

@Composable
private fun WindowChromeControls(state: WindowChromeState) {
    FlowPanel {
        CheckBox("Resizable", checked = state.resizable, onCheckedChange = { state.resizable = it })
        CheckBox("Always on top", checked = state.alwaysOnTop, onCheckedChange = { state.alwaysOnTop = it })
        CheckBox("Undecorated", checked = state.undecorated, onCheckedChange = { state.undecorated = it })
    }
    FlowPanel {
        CheckBox("Custom icon", checked = state.customIcon, onCheckedChange = { state.customIcon = it })
        CheckBox(
            "Minimum size 240x160",
            checked = state.enforceMinimumSize,
            onCheckedChange = { state.enforceMinimumSize = it },
        )
        CheckBox("Visible", checked = state.visible, onCheckedChange = { state.visible = it })
    }
}

@Composable
private fun WindowScope.SecondaryWindowContent(
    alwaysOnTop: Boolean,
    onAlwaysOnTopChange: (Boolean) -> Unit,
    busy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    // MenuBar is declared on the window whose content this is, so it is the secondary window - not
    // the gallery's own frame - that shows this menu bar.
    MenuBar {
        Menu("Window") {
            CheckBoxMenuItem(
                "Always on top",
                checked = alwaysOnTop,
                onCheckedChange = onAlwaysOnTopChange,
            )
            MenuSeparator()
            MenuItem("Close window", onClick = onClose)
        }
    }
    BorderPanel {
        Label(
            "A second top-level window, composed declaratively.",
            modifier = SwingModifier.center().horizontalAlignment(SwingConstants.CENTER),
        )
        FlowPanel(SwingModifier.south()) {
            Button("Dismiss", onClick = onClose)
            Button("Show busy overlay", onClick = { onBusyChange(true) })
        }
    }
    // A glass pane covers the whole window for as long as it is composed, and the window's mouse
    // events reach it instead of the content underneath - which is what makes it a busy overlay:
    // shown here behind the same `if` that drives every other overlay in the gallery.
    if (busy) {
        GlassPane {
            BorderPanel(modifier = SwingModifier.opaque(true).background(Color(0xE8EAF6))) {
                FlowPanel(SwingModifier.center()) {
                    Label("Busy...")
                    Button("Dismiss overlay", onClick = { onBusyChange(false) })
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ModalDialogCard() {
    ExampleCard("Dialog (modality switched live)") {
        var open by remember { mutableStateOf(false) }
        var acknowledged by remember { mutableStateOf(false) }
        // Hoisted above the `if (open)` so the dialog reopens at whatever size it was last left.
        val state = rememberDialogState(size = Dimension(360, 200))
        var modality by remember { mutableStateOf(java.awt.Dialog.ModalityType.APPLICATION_MODAL) }
        val chrome = remember { DialogChromeState() }
        val icon = remember { windowIconImage() }

        FlowPanel {
            Button("Open modal dialog", onClick = { open = true })
            Label(if (acknowledged) "Last dialog: acknowledged" else "No dialog acknowledged yet")
        }
        FlowPanel {
            RadioButton(
                "Modeless",
                selected = modality == java.awt.Dialog.ModalityType.MODELESS,
                onSelectedChange = { modality = java.awt.Dialog.ModalityType.MODELESS },
            )
            RadioButton(
                "Document modal",
                selected = modality == java.awt.Dialog.ModalityType.DOCUMENT_MODAL,
                onSelectedChange = { modality = java.awt.Dialog.ModalityType.DOCUMENT_MODAL },
            )
            RadioButton(
                "Application modal",
                selected = modality == java.awt.Dialog.ModalityType.APPLICATION_MODAL,
                onSelectedChange = { modality = java.awt.Dialog.ModalityType.APPLICATION_MODAL },
            )
        }
        DialogChromeControls(chrome)
        if (open) {
            Dialog(
                onCloseRequest = { open = false },
                state = state,
                title = "Confirm",
                modality = modality,
                resizable = chrome.resizable,
                alwaysOnTop = chrome.alwaysOnTop,
                iconImage = if (chrome.customIcon) icon else null,
                minimumSize = if (chrome.enforceMinimumSize) Dimension(240, 160) else null,
                undecorated = chrome.undecorated,
            ) {
                ModalDialogContent(
                    state = state,
                    onAcknowledge = {
                        acknowledged = true
                        open = false
                    },
                    onClose = { open = false },
                )
            }
        }
    }
}

// Resizable, always-on-top, undecorated, custom icon and minimum size are grouped in one state
// holder because ModalDialogCard only ever reads or offers them together, as the dialog's chrome
// controls.
private class DialogChromeState {
    var resizable by mutableStateOf(true)
    var alwaysOnTop by mutableStateOf(false)
    var undecorated by mutableStateOf(false)
    var customIcon by mutableStateOf(false)
    var enforceMinimumSize by mutableStateOf(false)
}

@Composable
private fun DialogChromeControls(state: DialogChromeState) {
    FlowPanel {
        CheckBox("Resizable", checked = state.resizable, onCheckedChange = { state.resizable = it })
        CheckBox("Always on top", checked = state.alwaysOnTop, onCheckedChange = { state.alwaysOnTop = it })
        CheckBox("Undecorated", checked = state.undecorated, onCheckedChange = { state.undecorated = it })
        CheckBox("Custom icon", checked = state.customIcon, onCheckedChange = { state.customIcon = it })
        CheckBox(
            "Minimum size 240x160",
            checked = state.enforceMinimumSize,
            onCheckedChange = { state.enforceMinimumSize = it },
        )
    }
}

@Composable
private fun ModalDialogContent(
    state: DialogState,
    onAcknowledge: () -> Unit,
    onClose: () -> Unit,
) {
    BorderPanel {
        Label(
            "The modality selected above decides whether this dialog blocks its owner.",
            modifier = SwingModifier.center().horizontalAlignment(SwingConstants.CENTER),
        )
        // The readout and the grow button live inside the dialog because a modal dialog blocks its
        // owner, so its own content is the only place a control can reach it.
        FlowPanel(SwingModifier.south()) {
            Label("Dialog is ${state.width} x ${state.height}")
            Button("Grow", onClick = { state.size = Dimension(state.width + 40, state.height + 20) })
            Button("OK", onClick = onAcknowledge)
            Button("Cancel", onClick = onClose)
        }
    }
}

@Composable
private fun ColumnScope.ApplicationEntryPointCard() {
    ExampleCard("application { } (a self-contained Compose application)") {
        // launchApplication is the entry point documented for use from a UI-thread event listener: a
        // click launches a whole new application composition, complete with its own Window, its own
        // exitApplication() and its own coroutine scope, independent of the gallery's own window and
        // composition - and of this card, so the demo keeps running if the user browses to another
        // section, until exitApplication() is called from inside it.
        var launches by remember { mutableIntStateOf(0) }

        FlowPanel {
            Button(
                "Launch application { }",
                onClick = {
                    launches++
                    MainScope().launchApplication {
                        var clicks by remember { mutableIntStateOf(0) }
                        Window(onCloseRequest = ::exitApplication, title = "application { } demo") {
                            BorderPanel {
                                Label(
                                    "Its own composition, closed by its own exitApplication().",
                                    modifier = SwingModifier.center().horizontalAlignment(SwingConstants.CENTER),
                                )
                                FlowPanel(SwingModifier.south()) {
                                    Label("Clicks: $clicks")
                                    Button("Click", onClick = { clicks++ })
                                    Button("exitApplication()", onClick = ::exitApplication)
                                }
                            }
                        }
                    }
                },
            )
            Label("Launched $launches time(s)")
        }
        WrappedCaption(
            "Each click starts a fresh application { } composition, unrelated to the gallery's own - " +
                "the declarative entry point a self-contained Compose Swing application is built from.",
        )
    }
}

private const val WINDOW_ICON_SIZE = 24
private val WindowIconColor = Color(0x2D, 0x4B, 0x73)

private fun windowIconImage(): Image {
    val image = BufferedImage(WINDOW_ICON_SIZE, WINDOW_ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.color = WindowIconColor
        graphics.fillOval(0, 0, WINDOW_ICON_SIZE, WINDOW_ICON_SIZE)
    } finally {
        graphics.dispose()
    }
    return image
}

package org.jetbrains.compose.swing.samples.widgets.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.desktop.InternalFrameControls
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.desktop.rememberInternalFrameState
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.bounds
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JLayeredPane
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent

// The layered/MDI surfaces: LayeredPane stacks self-positioned children on depth layers (it does not
// lay them out, so each child sets its own bounds; overlapping tints make the paint order visible), and
// DesktopPane floats internal frames whose controlled close routes through onClose.
@Composable
internal fun LayeredAndMdiSection() {
    SectionColumn {
        SectionHeading("Layered & MDI")
        LayeredPaneCard()
        DesktopPaneCard()
        HoistedInternalFrameCard()
    }
}

@Composable
private fun ColumnScope.LayeredPaneCard() {
    ExampleCard("LayeredPane (depth layers)") {
        LayeredPane(modifier = SwingModifier.preferredSize(Dimension(240, 160))) {
            layer(JLayeredPane.DEFAULT_LAYER) {
                BorderPanel(
                    modifier =
                        SwingModifier
                            .bounds(10, 10, 160, 110)
                            .opaque(true)
                            .background(Color(0xBBDEFB)),
                ) {
                    north { Label("Default layer") }
                }
            }
            layer(JLayeredPane.PALETTE_LAYER) {
                BorderPanel(
                    modifier =
                        SwingModifier
                            .bounds(60, 40, 140, 90)
                            .opaque(true)
                            .background(Color(0xFFE0B2)),
                ) {
                    north { Label("Palette layer") }
                }
            }
            layer(JLayeredPane.DRAG_LAYER) {
                BorderPanel(
                    modifier =
                        SwingModifier
                            .bounds(110, 70, 110, 70)
                            .opaque(true)
                            .background(Color(0xC8E6C9)),
                ) {
                    north { Label("Drag layer (top)") }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DesktopPaneCard() {
    ExampleCard("DesktopPane (internal frames)") {
        var extraOpen by remember { mutableStateOf(false) }
        var closedCount by remember { mutableIntStateOf(0) }

        FlowPanel {
            Button(
                text = if (extraOpen) "Remove frame" else "Add frame",
                onClick = { extraOpen = !extraOpen },
            )
            Label("Controlled closes: $closedCount")
        }

        DesktopPane(modifier = SwingModifier.preferredSize(Dimension(320, 220))) {
            internalFrame(title = "Editor", bounds = Rectangle(0, 0, 180, 120)) {
                Label("Editor frame")
            }
            internalFrame(title = "Console", bounds = Rectangle(60, 50, 180, 120)) {
                Label("Console frame")
            }
            if (extraOpen) {
                internalFrame(
                    title = "Inspector",
                    bounds = Rectangle(120, 90, 180, 110),
                    controls = InternalFrameControls(closable = true),
                    onClose = {
                        closedCount++
                        extraOpen = false
                    },
                ) {
                    Label("Inspector frame")
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.HoistedInternalFrameCard() {
    ExampleCard("DesktopPane (hoisted frame state, InternalFrameListener)") {
        // A plain bounds frame is placed once and thereafter left to the user; a frame declared with
        // an InternalFrameState instead has geometry and window state that are two-way, so the buttons
        // and check boxes below move, resize, iconify and maximize it exactly as dragging its title
        // bar, pulling its border or activating its own controls would.
        val frame = rememberInternalFrameState(bounds = Rectangle(20, 20, 200, 120))
        var open by remember { mutableStateOf(true) }
        var lastEvent by remember { mutableStateOf("none") }
        // The raw-listener overload attaches this adapter as-is, so unlike onClose it never gets a
        // synthesized closing handler: internalFrameClosing must be overridden here for the close
        // control to do anything, since the frame's own close operation stays do-nothing either way.
        val listener =
            remember {
                object : InternalFrameAdapter() {
                    override fun internalFrameActivated(e: InternalFrameEvent) {
                        lastEvent = "activated"
                    }

                    override fun internalFrameDeactivated(e: InternalFrameEvent) {
                        lastEvent = "deactivated"
                    }

                    override fun internalFrameIconified(e: InternalFrameEvent) {
                        lastEvent = "iconified"
                    }

                    override fun internalFrameDeiconified(e: InternalFrameEvent) {
                        lastEvent = "deiconified"
                    }

                    override fun internalFrameClosing(e: InternalFrameEvent) {
                        lastEvent = "closing"
                        open = false
                    }
                }
            }

        FlowPanel {
            Button(
                "Move",
                onClick = {
                    frame.x += 20
                    frame.y += 10
                },
            )
            Button(
                "Grow",
                onClick = {
                    frame.width += 20
                    frame.height += 10
                },
            )
            CheckBox("Iconified", checked = frame.iconified, onCheckedChange = { frame.iconified = it })
            CheckBox("Maximized", checked = frame.maximized, onCheckedChange = { frame.maximized = it })
        }
        Label("Position: (${frame.x}, ${frame.y}) size ${frame.width} x ${frame.height} - last event: $lastEvent")

        DesktopPane(modifier = SwingModifier.preferredSize(Dimension(320, 220))) {
            if (open) {
                internalFrame(
                    title = "Hoisted",
                    state = frame,
                    internalFrameListener = listener,
                    controls =
                        InternalFrameControls(
                            closable = true,
                            resizable = true,
                            maximizable = true,
                            iconifiable = true,
                        ),
                ) {
                    Label("Drag, resize, iconify or maximize me, or drive it from the controls above.")
                }
            }
        }
    }
}

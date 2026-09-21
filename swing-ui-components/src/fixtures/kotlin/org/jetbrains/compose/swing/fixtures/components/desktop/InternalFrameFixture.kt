// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.desktop

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.desktop.InternalFrameControls
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent

/** The parameters [InternalFrameFixture] varies. */
public data class InternalFrameParameters(
    val title: String,
    val bounds: Rectangle,
    val onClose: () -> Unit,
    val name: String,
    val color: Color,
    val controls: InternalFrameControls,
    val labelText: String,
)

/**
 * A frame declared on a [DesktopPane] with plain bounds and a close callback, against a raw [JDesktopPane]
 * holding the same [JInternalFrame], which closes only by reporting to the same callback.
 */
public object InternalFrameFixture : ComposableFixture<InternalFrameParameters, JDesktopPane>(
    initial =
        InternalFrameParameters(
            title = "Before",
            bounds = Rectangle(0, 0, 200, 100),
            onClose = {},
            name = "Before",
            color = Color.BLUE,
            controls = InternalFrameControls(),
            labelText = "Before",
        ),
) {
    override val alternatives: List<InternalFrameParameters> =
        listOf(
            initial.copy(title = "After"),
            initial.copy(bounds = Rectangle(20, 20, 220, 120)),
            initial.copy(onClose = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(controls = InternalFrameControls(closable = true, resizable = true)),
            initial.copy(labelText = "After"),
        )

    @Composable
    override fun Content(parameters: InternalFrameParameters): Unit =
        Content(
            parameters.title,
            parameters.bounds,
            parameters.onClose,
            parameters.name,
            parameters.color,
            parameters.controls,
            parameters.labelText,
        )

    /** Composes a [DesktopPane] holding one frame titled [title] on [bounds], named [name] and tinted [color]. */
    @Composable
    public fun Content(
        title: String,
        bounds: Rectangle,
        onClose: () -> Unit,
        name: String,
        color: Color,
        controls: InternalFrameControls,
        labelText: String,
    ) {
        DesktopPane {
            InternalFrame(
                title = title,
                bounds = bounds,
                onClose = onClose,
                modifier = SwingModifier.name(name),
                style = { foreground(color) },
                controls = controls,
            ) {
                Label(text = labelText)
            }
        }
    }

    /**
     * Builds a raw [JDesktopPane] holding one visible [JInternalFrame] with an empty [JLabel], whose close control
     * only reports.
     */
    override fun createRawSwing(): JDesktopPane =
        JDesktopPane().apply {
            add(
                JInternalFrame().apply {
                    defaultCloseOperation = JInternalFrame.DO_NOTHING_ON_CLOSE
                    contentPane.add(JLabel())
                    isVisible = true
                },
            )
        }

    /**
     * Applies [parameters] to the frame of [component]: title, bounds, name, color, controls, label text and close
     * callback. The label takes the color too, as the style's foreground is inherited.
     */
    override fun updateRawSwing(
        component: JDesktopPane,
        parameters: InternalFrameParameters,
    ) {
        val frame = component.allFrames.single()
        frame.title = parameters.title
        frame.bounds = parameters.bounds
        frame.name = parameters.name
        frame.foreground = parameters.color
        frame.isClosable = parameters.controls.closable
        frame.isResizable = parameters.controls.resizable
        frame.isMaximizable = parameters.controls.maximizable
        frame.isIconifiable = parameters.controls.iconifiable
        val label = frame.contentPane.components.single() as JLabel
        label.text = parameters.labelText
        label.foreground = parameters.color
        val listener =
            frame.internalFrameListeners.firstNotNullOfOrNull { it as? CloseListener }
                ?: CloseListener().also { frame.addInternalFrameListener(it) }
        listener.onClose = parameters.onClose
    }

    /** The listener attached once to the raw frame: it reports every close request to the latest [onClose]. */
    private class CloseListener : InternalFrameAdapter() {
        var onClose: () -> Unit = {}

        override fun internalFrameClosing(event: InternalFrameEvent) {
            onClose()
        }
    }
}

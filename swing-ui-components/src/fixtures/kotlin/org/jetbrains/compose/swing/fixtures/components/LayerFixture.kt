// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Layer
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayer
import javax.swing.plaf.LayerUI

/** The parameters [LayerFixture] varies: name, color, paint and mouse callbacks, and label text. */
public data class LayerParameters(
    val name: String,
    val color: Color,
    val onPaint: (g: Graphics2D, width: Int, height: Int, paintView: () -> Unit) -> Unit,
    val onMouseEvent: (MouseEvent) -> Unit,
    val onMouseMotionEvent: (MouseEvent) -> Unit,
    val onMouseWheelEvent: (MouseWheelEvent) -> Unit,
    val text: String,
)

/** A callback [Layer] over a [Label] against a raw [JLayer] over a [JLabel] running the same callbacks. */
public object LayerFixture : ComposableFixture<LayerParameters, JLayer<JLabel>>(
    initial =
        LayerParameters(
            name = "Before",
            color = Color.BLUE,
            onPaint = { _, _, _, paintView -> paintView() },
            onMouseEvent = {},
            onMouseMotionEvent = {},
            onMouseWheelEvent = {},
            text = "Before",
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<LayerParameters> =
        listOf(
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(
                onPaint = { g, width, height, paintView ->
                    paintView()
                    g.color = Color.RED
                    g.drawRect(0, 0, width - 1, height - 1)
                },
            ),
            initial.copy(onMouseEvent = {}),
            initial.copy(onMouseMotionEvent = {}),
            initial.copy(onMouseWheelEvent = {}),
            initial.copy(text = "After"),
        )

    @Composable
    override fun Content(parameters: LayerParameters): Unit =
        Content(
            parameters.name,
            parameters.color,
            parameters.onPaint,
            parameters.onMouseEvent,
            parameters.onMouseMotionEvent,
            parameters.onMouseWheelEvent,
            parameters.text,
        )

    /** Composes a [Layer] named [name], in [color], with the callbacks, over a [Label] showing [text]. */
    @Composable
    public fun Content(
        name: String,
        color: Color,
        onPaint: (g: Graphics2D, width: Int, height: Int, paintView: () -> Unit) -> Unit,
        onMouseEvent: (MouseEvent) -> Unit,
        onMouseMotionEvent: (MouseEvent) -> Unit,
        onMouseWheelEvent: (MouseWheelEvent) -> Unit,
        text: String,
    ) {
        Layer(
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
            onPaint = onPaint,
            onMouseEvent = onMouseEvent,
            onMouseMotionEvent = onMouseMotionEvent,
            onMouseWheelEvent = onMouseWheelEvent,
        ) {
            Label(text)
        }
    }

    /**
     * Builds a [JLayer] over an empty [JLabel] with its one [CallbackLayerUi] installed, before any of the
     * fixture's values are applied.
     */
    override fun createRawSwing(): JLayer<JLabel> = JLayer(JLabel()).apply { ui = CallbackLayerUi() }

    /**
     * Applies the name, color, text and the event mask the callbacks declare of [parameters] to [component], and
     * hands the callbacks to its [CallbackLayerUi], repainting when the paint callback changed. The view takes the
     * color too, as the style's foreground is inherited.
     */
    override fun updateRawSwing(
        component: JLayer<JLabel>,
        parameters: LayerParameters,
    ) {
        component.name = parameters.name
        component.foreground = parameters.color
        component.view?.apply {
            text = parameters.text
            foreground = parameters.color
        }
        component.layerEventMask =
            AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK
        val ui = component.ui as? CallbackLayerUi ?: return
        val paintChanged = ui.parameters.onPaint !== parameters.onPaint
        ui.parameters = parameters
        if (paintChanged) component.repaint()
    }
}

/**
 * The delegate [LayerFixture] installs once on its raw layer: it runs the callbacks of the [LayerParameters] last
 * given to it.
 */
private class CallbackLayerUi : LayerUI<JComponent>() {
    var parameters: LayerParameters =
        LayerParameters(
            name = "",
            color = Color.BLACK,
            onPaint = { _, _, _, paintView -> paintView() },
            onMouseEvent = {},
            onMouseMotionEvent = {},
            onMouseWheelEvent = {},
            text = "",
        )

    override fun paint(
        graphics: Graphics,
        component: JComponent,
    ) {
        val copy = graphics.create()
        try {
            if (copy is Graphics2D) {
                parameters.onPaint(copy, component.width, component.height) { super.paint(copy, component) }
            }
        } finally {
            copy.dispose()
        }
    }

    override fun processMouseEvent(
        event: MouseEvent,
        layer: JLayer<out JComponent>,
    ) {
        parameters.onMouseEvent(event)
    }

    override fun processMouseMotionEvent(
        event: MouseEvent,
        layer: JLayer<out JComponent>,
    ) {
        parameters.onMouseMotionEvent(event)
    }

    override fun processMouseWheelEvent(
        event: MouseWheelEvent,
        layer: JLayer<out JComponent>,
    ) {
        parameters.onMouseWheelEvent(event)
    }
}

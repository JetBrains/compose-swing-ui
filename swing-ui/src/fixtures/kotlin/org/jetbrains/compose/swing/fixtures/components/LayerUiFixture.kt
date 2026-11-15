// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Layer
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.AWTEvent
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayer
import javax.swing.plaf.LayerUI

/** The parameters [LayerUiFixture] varies: which delegate the layer runs, as a key for a delegate built per mount. */
public data class LayerUiParameters(
    val uiKey: Int,
)

/** A [Layer] driven by a `LayerUI` of its own against a raw [JLayer] with the same delegate and event mask. */
public object LayerUiFixture : ComposableFixture<LayerUiParameters, JLayer<JLabel>>(
    initial = LayerUiParameters(uiKey = 0),
) {
    /** The single-change copy of [initial] for its delegate. */
    override val alternatives: List<LayerUiParameters> = listOf(initial.copy(uiKey = 1))

    @Composable
    override fun Content(parameters: LayerUiParameters): Unit = Content(parameters.uiKey)

    /**
     * Composes a [Layer] driven by a delegate remembered for [uiKey], observing mouse motion events, over a
     * [Label] reading "Before".
     */
    @Composable
    public fun Content(uiKey: Int) {
        Layer(
            ui = remember(uiKey) { LayerUI<JComponent>() },
            modifier = SwingModifier.name("Before"),
            style = { foreground(Color.BLUE) },
            eventMask = AWTEvent.MOUSE_MOTION_EVENT_MASK,
        ) {
            Label("Before")
        }
    }

    /** Builds a [JLayer] over a [JLabel] showing the fixed name, color and text; the label inherits the color. */
    override fun createRawSwing(): JLayer<JLabel> =
        JLayer(JLabel("Before").apply { foreground = Color.BLUE }).apply {
            name = "Before"
            foreground = Color.BLUE
        }

    /**
     * Installs a fresh delegate on [component] when [parameters] name a key other than the one its delegate was
     * built for, and then applies the fixed event mask.
     */
    override fun updateRawSwing(
        component: JLayer<JLabel>,
        parameters: LayerUiParameters,
    ) {
        if ((component.ui as? KeyedLayerUi)?.key != parameters.uiKey) component.ui = KeyedLayerUi(parameters.uiKey)
        // Installing a delegate resets the event mask, so the mask is written after it.
        component.layerEventMask = AWTEvent.MOUSE_MOTION_EVENT_MASK
    }
}

/** The delegate [LayerUiFixture] builds on its raw layer for one key of [LayerUiParameters]. */
private class KeyedLayerUi(
    val key: Int,
) : LayerUI<JComponent>()

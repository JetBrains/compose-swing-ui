// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.event.ChangeListener

/** The parameters [SliderChangeListenerFixture] varies: only the change listener, which [SliderFixture] lacks. */
public data class SliderChangeListenerParameters(
    val changeListener: ChangeListener,
)

/**
 * A [Slider] over a declared value that notifies a [ChangeListener], against a raw [JSlider] with the same range,
 * ticks, labels, appearance and listener.
 */
public object SliderChangeListenerFixture : ComposableFixture<SliderChangeListenerParameters, JSlider>(
    initial =
        SliderChangeListenerParameters(
            changeListener = {},
        ),
) {
    /** The single-change copy of [initial] for the change listener, the parameter this fixture varies. */
    override val alternatives: List<SliderChangeListenerParameters> =
        listOf(
            initial.copy(changeListener = {}),
        )

    @Composable
    override fun Content(parameters: SliderChangeListenerParameters): Unit = Content(parameters.changeListener)

    /**
     * Composes a [Slider] with a change listener, over the fixed value, range, appearance, ticks and labels of
     * [initial].
     */
    @Composable
    public fun Content(changeListener: ChangeListener) {
        Slider(
            value = 40,
            changeListener = changeListener,
            modifier = SwingModifier.name("Before"),
            style = { foreground(Color.BLUE) },
            min = 0,
            max = 100,
            orientation = SwingConstants.HORIZONTAL,
            inverted = false,
            majorTickSpacing = 10,
            minorTickSpacing = 2,
            paintTicks = false,
            paintLabels = false,
            labels = null,
            snapToTicks = false,
        )
    }

    /**
     * Builds a [JSlider] with the fixed range, appearance and ticks that [Content] composes, before the listener is
     * applied.
     */
    override fun createRawSwing(): JSlider =
        JSlider().apply {
            minimum = 0
            maximum = 100
            value = 40
            name = "Before"
            foreground = Color.BLUE
            orientation = SwingConstants.HORIZONTAL
            inverted = false
            // Built before the spacing is set, so that Swing does not build a second table for it.
            labelTable = createStandardLabels(10)
            majorTickSpacing = 10
            minorTickSpacing = 2
            paintTicks = false
            paintLabels = false
            snapToTicks = false
        }

    /** Applies the listener of [parameters] to [component], replacing the one it had before. */
    override fun updateRawSwing(
        component: JSlider,
        parameters: SliderChangeListenerParameters,
    ) {
        if (parameters.changeListener !in component.changeListeners) {
            component.removeChangeListener(initial.changeListener)
            alternatives.forEach { component.removeChangeListener(it.changeListener) }
            component.addChangeListener(parameters.changeListener)
        }
    }
}

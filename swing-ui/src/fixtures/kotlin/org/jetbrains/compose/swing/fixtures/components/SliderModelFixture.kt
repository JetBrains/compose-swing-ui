// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import javax.swing.DefaultBoundedRangeModel
import javax.swing.JSlider
import javax.swing.SwingConstants

/** The parameter [SliderModelFixture] varies: the value of the model the slider reads its range and value from. */
public data class SliderModelParameters(
    val value: Int,
)

/**
 * A [Slider] over a model remembered for a value, against a raw [JSlider] with a fresh model of that value,
 * appearance, ticks, labels and a listener with no-op callbacks.
 */
public object SliderModelFixture : ComposableFixture<SliderModelParameters, JSlider>(
    initial = SliderModelParameters(value = 40),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<SliderModelParameters> =
        listOf(
            initial.copy(value = 60),
        )

    @Composable
    override fun Content(parameters: SliderModelParameters): Unit = Content(parameters.value)

    /** Composes a [Slider] over a model remembered for [value]. */
    @Composable
    public fun Content(value: Int) {
        Slider(
            model = remember(value) { DefaultBoundedRangeModel(value, 0, 0, 100) },
            modifier = SwingModifier.name("Before"),
            style = { foreground(Color.BLUE) },
            onValueChange = {},
            onValueSettled = {},
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

    /** Builds a [JSlider] with the fixed appearance, ticks and listener that [Content] composes with. */
    override fun createRawSwing(): JSlider =
        JSlider().also { slider ->
            slider.name = "Before"
            slider.foreground = Color.BLUE
            slider.orientation = SwingConstants.HORIZONTAL
            slider.inverted = false
            // Built before the spacing is set, so that Swing does not build a second table for it.
            slider.labelTable = slider.createStandardLabels(10)
            slider.majorTickSpacing = 10
            slider.minorTickSpacing = 2
            slider.paintTicks = false
            slider.paintLabels = false
            slider.snapToTicks = false
            slider.addChangeListener(SliderCallbackListener(slider, {}, {}))
        }

    /** Gives [component] a fresh model of the value of [parameters]. */
    override fun updateRawSwing(
        component: JSlider,
        parameters: SliderModelParameters,
    ) {
        component.model = DefaultBoundedRangeModel(parameters.value, 0, 0, 100)
    }
}

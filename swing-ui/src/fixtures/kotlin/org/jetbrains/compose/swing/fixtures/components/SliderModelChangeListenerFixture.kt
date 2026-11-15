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
import javax.swing.event.ChangeListener

/** The parameter [SliderModelChangeListenerFixture] varies: the change listener its slider notifies. */
public data class SliderModelChangeListenerParameters(
    val changeListener: ChangeListener,
)

/**
 * A [Slider] that notifies a [ChangeListener], against a raw [JSlider] with the same range, ticks, labels, appearance
 * and listener.
 */
public object SliderModelChangeListenerFixture : ComposableFixture<SliderModelChangeListenerParameters, JSlider>(
    initial =
        SliderModelChangeListenerParameters(
            changeListener = {},
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<SliderModelChangeListenerParameters> =
        listOf(
            initial.copy(changeListener = {}),
        )

    @Composable
    override fun Content(parameters: SliderModelChangeListenerParameters): Unit =
        Content(
            parameters.changeListener,
        )

    /** Composes a [Slider] whose change events go to [changeListener]. */
    @Composable
    public fun Content(changeListener: ChangeListener) {
        val model = remember { DefaultBoundedRangeModel(40, 0, 0, 100) }
        Slider(
            model = model,
            changeListener = changeListener,
            modifier = SwingModifier.name("Before"),
            style = { foreground(Color.BLUE) },
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

    /** Builds a [JSlider] with the fixed range, appearance, ticks, labels and snapping that [Content] passes. */
    override fun createRawSwing(): JSlider =
        JSlider().apply {
            model = DefaultBoundedRangeModel(40, 0, 0, 100)
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

    /** Applies the change listener of [parameters] to [component]. */
    override fun updateRawSwing(
        component: JSlider,
        parameters: SliderModelChangeListenerParameters,
    ) {
        if (parameters.changeListener !in component.changeListeners) {
            component.removeChangeListener(initial.changeListener)
            alternatives.forEach { component.removeChangeListener(it.changeListener) }
            component.addChangeListener(parameters.changeListener)
        }
    }
}

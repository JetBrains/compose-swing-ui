// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.Orientation
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.beans.PropertyChangeListener
import java.util.Hashtable
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/** The parameters [SliderFixture] varies: every parameter of the composable it drives. */
public data class SliderParameters(
    val value: Int,
    val onValueChange: (Int) -> Unit,
    val name: String,
    val color: Color,
    val onValueSettled: (Int) -> Unit,
    val min: Int,
    val max: Int,
    @param:Orientation val orientation: Int,
    val inverted: Boolean,
    val majorTickSpacing: Int,
    val minorTickSpacing: Int,
    val paintTicks: Boolean,
    val paintLabels: Boolean,
    val labels: Map<Int, String>?,
    val snapToTicks: Boolean,
)

/**
 * Reports a slider's value to two callbacks, except while [writing].
 */
internal class SliderCallbackListener(
    private val slider: JSlider,
    var onValueChange: (Int) -> Unit,
    var onValueSettled: (Int) -> Unit,
) : ChangeListener {
    var writing: Boolean = false

    override fun stateChanged(event: ChangeEvent) {
        if (writing) return
        onValueChange(slider.value)
        if (!slider.valueIsAdjusting) onValueSettled(slider.value)
    }
}

/**
 * A [Slider] over a declared value that reports through `onValueChange` and `onValueSettled`, against a raw [JSlider]
 * with the same range, ticks, labels, appearance and listener.
 */
public object SliderFixture : ComposableFixture<SliderParameters, JSlider>(
    initial =
        SliderParameters(
            value = 40,
            onValueChange = {},
            name = "Before",
            color = Color.BLUE,
            onValueSettled = {},
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
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<SliderParameters> =
        listOf(
            initial.copy(value = 60),
            initial.copy(onValueChange = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(onValueSettled = {}),
            initial.copy(min = 10),
            initial.copy(max = 90),
            initial.copy(orientation = SwingConstants.VERTICAL),
            initial.copy(inverted = true),
            initial.copy(majorTickSpacing = 20),
            initial.copy(minorTickSpacing = 5),
            initial.copy(paintTicks = true),
            initial.copy(paintLabels = true),
            initial.copy(labels = mapOf(20 to "Low", 80 to "High")),
            initial.copy(snapToTicks = true),
        )

    @Composable
    override fun Content(parameters: SliderParameters): Unit =
        Content(
            parameters.value,
            parameters.onValueChange,
            parameters.name,
            parameters.color,
            parameters.onValueSettled,
            parameters.min,
            parameters.max,
            parameters.orientation,
            parameters.inverted,
            parameters.majorTickSpacing,
            parameters.minorTickSpacing,
            parameters.paintTicks,
            parameters.paintLabels,
            parameters.labels,
            parameters.snapToTicks,
        )

    /**
     * Composes a [Slider] with a declared value, its callbacks, a modifier name, a foreground color, a range, an
     * orientation, ticks, labels and tick snapping.
     */
    @Composable
    public fun Content(
        value: Int,
        onValueChange: (Int) -> Unit,
        name: String,
        color: Color,
        onValueSettled: (Int) -> Unit,
        min: Int,
        max: Int,
        @Orientation orientation: Int,
        inverted: Boolean,
        majorTickSpacing: Int,
        minorTickSpacing: Int,
        paintTicks: Boolean,
        paintLabels: Boolean,
        labels: Map<Int, String>?,
        snapToTicks: Boolean,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
            onValueSettled = onValueSettled,
            min = min,
            max = max,
            orientation = orientation,
            inverted = inverted,
            majorTickSpacing = majorTickSpacing,
            minorTickSpacing = minorTickSpacing,
            paintTicks = paintTicks,
            paintLabels = paintLabels,
            labels = labels,
            snapToTicks = snapToTicks,
        )
    }

    /** Builds a bare [JSlider] before any of the fixture's values are applied. */
    override fun createRawSwing(): JSlider = JSlider()

    /**
     * Applies the range, appearance, ticks, labels and listener of [parameters] to [component]. A label table built
     * from the same declaration stays, and a replaced generated one is unregistered from the slider. The listener
     * stays attached and does not report the range and value written here.
     */
    override fun updateRawSwing(
        component: JSlider,
        parameters: SliderParameters,
    ) {
        val labelSpecKey = "fixtureLabelSpec"
        val listener =
            component.changeListeners.firstNotNullOfOrNull { it as? SliderCallbackListener }
                ?: SliderCallbackListener(component, {}, {}).also { component.addChangeListener(it) }
        listener.onValueChange = parameters.onValueChange
        listener.onValueSettled = parameters.onValueSettled
        listener.writing = true
        try {
            component.minimum = parameters.min
            component.maximum = parameters.max
            component.value = parameters.value
        } finally {
            listener.writing = false
        }
        component.name = parameters.name
        component.foreground = parameters.color
        component.orientation = parameters.orientation
        component.inverted = parameters.inverted
        component.minorTickSpacing = parameters.minorTickSpacing
        component.paintTicks = parameters.paintTicks
        component.paintLabels = parameters.paintLabels
        val labels = parameters.labels
        val labelSpec = LabelSpec(labels, if (labels == null) parameters.majorTickSpacing else 0)
        if (component.getClientProperty(labelSpecKey) != labelSpec) {
            // A generated table listens to the slider's range and unregisters only when the next one is generated.
            val outgoing = component.labelTable
            if (outgoing is PropertyChangeListener) component.removePropertyChangeListener(outgoing)
            // Built here, before the spacing is set, so that Swing does not build a second table for it.
            component.labelTable =
                when {
                    labels != null -> Hashtable(labels.mapValues { JLabel(it.value) })
                    parameters.majorTickSpacing > 0 -> component.createStandardLabels(parameters.majorTickSpacing)
                    else -> null
                }
            component.putClientProperty(labelSpecKey, labelSpec)
        }
        component.majorTickSpacing = parameters.majorTickSpacing
        component.snapToTicks = parameters.snapToTicks
    }
}

/**
 * What the label table of the raw slider of [SliderFixture] is built from: the declared labels, or else the spacing
 * of generated ones, so that a table built from the same spec stays.
 */
private data class LabelSpec(
    val labels: Map<Int, String>?,
    val standardSpacing: Int,
)

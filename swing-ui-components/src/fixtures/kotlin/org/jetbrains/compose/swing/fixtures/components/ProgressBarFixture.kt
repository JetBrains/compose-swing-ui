// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.Orientation
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import javax.swing.JProgressBar
import javax.swing.SwingConstants

/**
 * The parameters [ProgressBarFixture] varies: the value, the range, the modifier name, the foreground color,
 * the indeterminate state, the orientation and the text.
 */
public data class ProgressBarParameters(
    val value: Int,
    val name: String,
    val color: Color,
    val min: Int,
    val max: Int,
    val indeterminate: Boolean,
    @param:Orientation val orientation: Int,
    val stringPainted: Boolean,
    val string: String?,
)

/**
 * A [ProgressBar] taking an integer value against a raw [JProgressBar] with the same range, value,
 * appearance and text.
 */
public object ProgressBarFixture : ComposableFixture<ProgressBarParameters, JProgressBar>(
    initial =
        ProgressBarParameters(
            value = 40,
            name = "Before",
            color = Color.BLUE,
            min = 0,
            max = 100,
            indeterminate = false,
            orientation = SwingConstants.HORIZONTAL,
            stringPainted = false,
            string = "Before",
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ProgressBarParameters> =
        listOf(
            initial.copy(value = 60),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(min = 10),
            initial.copy(max = 90),
            initial.copy(indeterminate = true),
            initial.copy(orientation = SwingConstants.VERTICAL),
            initial.copy(stringPainted = true),
            initial.copy(string = "After"),
        )

    @Composable
    override fun Content(parameters: ProgressBarParameters): Unit =
        Content(
            parameters.value,
            parameters.name,
            parameters.color,
            parameters.min,
            parameters.max,
            parameters.indeterminate,
            parameters.orientation,
            parameters.stringPainted,
            parameters.string,
        )

    /**
     * Composes a [ProgressBar] showing [value] in the range [min] to [max], named [name] and drawn in [color],
     * with the given indeterminate state, [orientation], text painting and [string].
     */
    @Composable
    public fun Content(
        value: Int,
        name: String,
        color: Color,
        min: Int,
        max: Int,
        indeterminate: Boolean,
        @Orientation orientation: Int,
        stringPainted: Boolean,
        string: String?,
    ) {
        ProgressBar(
            value = value,
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
            min = min,
            max = max,
            indeterminate = indeterminate,
            orientation = orientation,
            stringPainted = stringPainted,
            string = string,
        )
    }

    /** Builds a bare [JProgressBar] before any of the fixture's values are applied. */
    override fun createRawSwing(): JProgressBar = JProgressBar()

    /**
     * Applies the range, value, name, foreground color, indeterminate state, orientation and text of
     * [parameters] to [component].
     */
    override fun updateRawSwing(
        component: JProgressBar,
        parameters: ProgressBarParameters,
    ) {
        component.minimum = parameters.min
        component.maximum = parameters.max
        component.value = parameters.value
        component.name = parameters.name
        component.foreground = parameters.color
        component.isIndeterminate = parameters.indeterminate
        component.orientation = parameters.orientation
        component.isStringPainted = parameters.stringPainted
        component.string = parameters.string
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * The parameters [SpinnerFixture] varies: the number value and its callback, the modifier name, the
 * foreground color, the bounds and step, the number format, and the text of a composed editor.
 */
public data class SpinnerParameters(
    val value: Int,
    val onValueChange: (Number) -> Unit,
    val name: String,
    val color: Color,
    val min: Int?,
    val max: Int?,
    val step: Int,
    val format: String?,
    val editorText: String?,
)

/**
 * A number [Spinner] against a raw [JSpinner] with the same value, bounds, step, format, editor, name and
 * foreground color. The callback is mirrored by a change listener that forwards the spinner's value.
 */
public object SpinnerFixture : ComposableFixture<SpinnerParameters, JSpinner>(
    initial =
        SpinnerParameters(
            value = 40,
            onValueChange = {},
            name = "Before",
            color = Color.BLUE,
            min = 0,
            max = 100,
            step = 1,
            format = "0",
            editorText = null,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<SpinnerParameters> =
        listOf(
            initial.copy(value = 60),
            initial.copy(onValueChange = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(min = 10),
            initial.copy(max = 90),
            initial.copy(step = 5),
            initial.copy(format = "000"),
            initial.copy(format = null, editorText = "After"),
        )

    @Composable
    override fun Content(parameters: SpinnerParameters): Unit =
        Content(
            parameters.value,
            parameters.onValueChange,
            parameters.name,
            parameters.color,
            parameters.min,
            parameters.max,
            parameters.step,
            parameters.format,
            parameters.editorText,
        )

    /**
     * Composes a number [Spinner] showing [value], named [name] and drawn in [color], stepping by [step]
     * within [min] to [max], formatted with [format], or edited through a [Label] showing [editorText]
     * when that is not `null`.
     */
    @Composable
    public fun Content(
        value: Int,
        onValueChange: (Number) -> Unit,
        name: String,
        color: Color,
        min: Int?,
        max: Int?,
        step: Int,
        format: String?,
        editorText: String?,
    ) {
        val editor: (@Composable () -> Unit)? =
            editorText?.let { text ->
                val composed: @Composable () -> Unit = { Label(text = text) }
                composed
            }
        Spinner(
            value = value,
            onValueChange = onValueChange,
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
            min = min,
            max = max,
            step = step,
            format = format,
            editor = editor,
        )
    }

    /** Builds a bare [JSpinner] over a number model before the fixture's values are applied. */
    override fun createRawSwing(): JSpinner = JSpinner(SpinnerNumberModel())

    /**
     * Applies the bounds, step, value, editor, name, foreground color and callback of [parameters] to
     * [component]. The change listener stays attached and does not report the values applied here, and an editor
     * built for the same format and editor text stays.
     */
    override fun updateRawSwing(
        component: JSpinner,
        parameters: SpinnerParameters,
    ) {
        val editorSpecKey = "fixtureEditorSpec"
        val listener =
            component.changeListeners.firstNotNullOfOrNull { it as? SpinnerNumberChangeListener }
                ?: SpinnerNumberChangeListener(component).also { component.addChangeListener(it) }
        listener.onValueChange = parameters.onValueChange
        listener.writing = true
        try {
            val model = component.model as SpinnerNumberModel
            model.value = parameters.value
            model.minimum = parameters.min
            model.maximum = parameters.max
            model.stepSize = parameters.step
            val editorSpec = EditorSpec(parameters.format, parameters.editorText)
            if (component.getClientProperty(editorSpecKey) != editorSpec) {
                component.editor = editorSpec.createEditor(component)
                component.putClientProperty(editorSpecKey, editorSpec)
            }
            component.name = parameters.name
            component.foreground = parameters.color
        } finally {
            listener.writing = false
        }
    }
}

/** What [SpinnerFixture] builds the editor of its raw spinner from: the editor text when set, else the format. */
private data class EditorSpec(
    val format: String?,
    val editorText: String?,
) {
    /** The editor of [spinner] this spec describes. */
    fun createEditor(spinner: JSpinner): JComponent =
        when {
            editorText != null -> {
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(JLabel(editorText))
                }
            }

            format != null -> {
                JSpinner.NumberEditor(spinner, format)
            }

            else -> {
                JSpinner.NumberEditor(spinner)
            }
        }
}

/**
 * The change listener [SpinnerFixture] installs once on its raw spinner. It runs the latest [onValueChange] with the
 * spinner's value whenever that value is a number, except while [writing].
 */
private class SpinnerNumberChangeListener(
    private val spinner: JSpinner,
) : ChangeListener {
    var onValueChange: (Number) -> Unit = {}
    var writing: Boolean = false

    override fun stateChanged(event: ChangeEvent) {
        val current = spinner.value
        if (!writing && current is Number) onValueChange(current)
    }
}

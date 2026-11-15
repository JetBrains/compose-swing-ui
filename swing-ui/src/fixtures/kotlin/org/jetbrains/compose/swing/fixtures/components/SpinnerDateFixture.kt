// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import java.util.Calendar
import java.util.Date
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * The parameters [SpinnerDateFixture] varies: the date value and its callback.
 */
public data class SpinnerDateParameters(
    val value: Date,
    val onValueChange: (Date) -> Unit,
)

/**
 * A date [Spinner] against a raw [JSpinner] over a [SpinnerDateModel] with the same value. The callback is
 * mirrored by a change listener that forwards the spinner's value.
 */
public object SpinnerDateFixture : ComposableFixture<SpinnerDateParameters, JSpinner>(
    initial =
        SpinnerDateParameters(
            value = Date(86400000L),
            onValueChange = {},
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<SpinnerDateParameters> =
        listOf(
            initial.copy(value = Date(172800000L)),
            initial.copy(onValueChange = {}),
        )

    @Composable
    override fun Content(parameters: SpinnerDateParameters): Unit = Content(parameters.value, parameters.onValueChange)

    /** Composes a date [Spinner] showing [value], reporting changes to [onValueChange]. */
    @Composable
    public fun Content(
        value: Date,
        onValueChange: (Date) -> Unit,
    ) {
        Spinner(
            value = value,
            onValueChange = onValueChange,
            start = null,
            end = null,
            calendarField = Calendar.DAY_OF_MONTH,
        )
    }

    /** Builds a bare [JSpinner] over a date model before the fixture's values are applied. */
    override fun createRawSwing(): JSpinner = JSpinner(SpinnerDateModel(Date(), null, null, Calendar.DAY_OF_MONTH))

    /**
     * Applies the value and callback of [parameters] to [component]. The change listener stays attached and does
     * not report the value applied here.
     */
    override fun updateRawSwing(
        component: JSpinner,
        parameters: SpinnerDateParameters,
    ) {
        val listener =
            component.changeListeners.firstNotNullOfOrNull { it as? SpinnerDateChangeListener }
                ?: SpinnerDateChangeListener(component).also { component.addChangeListener(it) }
        listener.onValueChange = parameters.onValueChange
        listener.writing = true
        try {
            (component.model as SpinnerDateModel).value = parameters.value
        } finally {
            listener.writing = false
        }
    }
}

/**
 * The change listener [SpinnerDateFixture] installs once on its raw spinner. It forwards the spinner's current
 * value to the latest [onValueChange] whenever that value is a [Date], except while [writing].
 */
private class SpinnerDateChangeListener(
    private val spinner: JSpinner,
) : ChangeListener {
    var onValueChange: (Date) -> Unit = {}
    var writing: Boolean = false

    override fun stateChanged(event: ChangeEvent) {
        val current = spinner.value
        if (!writing && current is Date) onValueChange(current)
    }
}

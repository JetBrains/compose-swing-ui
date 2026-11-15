// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JSpinner
import javax.swing.SpinnerListModel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * The parameters [SpinnerListFixture] varies: the items the spinner steps through, the selected item and
 * its callback.
 */
public data class SpinnerListParameters(
    val items: List<String>,
    val value: String,
    val onValueChange: (String) -> Unit,
)

/**
 * A list [Spinner] against a raw [JSpinner] over a [SpinnerListModel] with the same items and selection.
 * The callback is mirrored by a change listener that forwards the selected item.
 */
public object SpinnerListFixture : ComposableFixture<SpinnerListParameters, JSpinner>(
    initial =
        SpinnerListParameters(
            items = listOf("Before", "After"),
            value = "Before",
            onValueChange = {},
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<SpinnerListParameters> =
        listOf(
            initial.copy(items = listOf("Before", "After", "Extra")),
            initial.copy(value = "After"),
            initial.copy(onValueChange = {}),
        )

    @Composable
    override fun Content(parameters: SpinnerListParameters): Unit =
        Content(parameters.items, parameters.value, parameters.onValueChange)

    /** Composes a list [Spinner] of [items] with [value] selected, reporting the selected item to [onValueChange]. */
    @Composable
    public fun Content(
        items: List<String>,
        value: String,
        onValueChange: (String) -> Unit,
    ) {
        Spinner(
            items = items,
            value = value,
            onValueChange = onValueChange,
        )
    }

    /** Builds a bare [JSpinner] over a list model before the fixture's values are applied. */
    override fun createRawSwing(): JSpinner = JSpinner(SpinnerListModel())

    /**
     * Applies the items, selection and callback of [parameters] to [component]. The change listener stays
     * attached and does not report the selection applied here. The items are set before the selection, since a
     * selection must be one of the items.
     */
    override fun updateRawSwing(
        component: JSpinner,
        parameters: SpinnerListParameters,
    ) {
        val listener =
            component.changeListeners.firstNotNullOfOrNull { it as? SpinnerListChangeListener }
                ?: SpinnerListChangeListener(component).also { component.addChangeListener(it) }
        listener.onValueChange = parameters.onValueChange
        listener.writing = true
        try {
            val model = component.model as SpinnerListModel
            model.list = parameters.items
            model.value = parameters.value
        } finally {
            listener.writing = false
        }
    }
}

/**
 * The change listener [SpinnerListFixture] installs once on its raw spinner. It forwards the selected item of
 * the spinner to the latest callback when that item is a [String], except while [writing].
 */
private class SpinnerListChangeListener(
    private val spinner: JSpinner,
) : ChangeListener {
    var onValueChange: (String) -> Unit = {}
    var writing: Boolean = false

    override fun stateChanged(event: ChangeEvent) {
        val current = spinner.value
        if (!writing && current is String) onValueChange(current)
    }
}

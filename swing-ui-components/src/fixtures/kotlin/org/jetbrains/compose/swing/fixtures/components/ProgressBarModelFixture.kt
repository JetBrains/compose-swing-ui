// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.DefaultBoundedRangeModel
import javax.swing.JProgressBar

/** The parameters [ProgressBarModelFixture] varies: the value of the range model the bar renders. */
public data class ProgressBarModelParameters(
    val value: Int,
)

/**
 * A [ProgressBar] rendering a model remembered for a value against a raw [JProgressBar]
 * given a fresh model of that value.
 */
public object ProgressBarModelFixture : ComposableFixture<ProgressBarModelParameters, JProgressBar>(
    initial = ProgressBarModelParameters(value = 0),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<ProgressBarModelParameters> =
        listOf(
            initial.copy(value = 1),
        )

    @Composable
    override fun Content(parameters: ProgressBarModelParameters): Unit = Content(parameters.value)

    /** Composes a [ProgressBar] rendering a model remembered for [value]. */
    @Composable
    public fun Content(value: Int) {
        ProgressBar(model = remember(value) { DefaultBoundedRangeModel().apply { this.value = value } })
    }

    /** Builds a bare [JProgressBar] before the fixture's model is applied. */
    override fun createRawSwing(): JProgressBar = JProgressBar()

    /** Installs a fresh model of the value of [parameters] on [component]. */
    override fun updateRawSwing(
        component: JProgressBar,
        parameters: ProgressBarModelParameters,
    ) {
        component.model = DefaultBoundedRangeModel().apply { value = parameters.value }
    }
}

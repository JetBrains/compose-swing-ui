// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel

/** The parameters [LabelFixture] varies. */
public data class LabelParameters(
    val text: String,
)

/** A [Label] against a raw [JLabel] showing the same text. */
public object LabelFixture : ComposableFixture<LabelParameters, JLabel>(
    initial = LabelParameters(text = "Before"),
) {
    override val alternatives: List<LabelParameters> =
        listOf(
            initial.copy(text = "After"),
            initial.copy(text = ""),
        )

    @Composable
    override fun Content(parameters: LabelParameters): Unit = Content(parameters.text)

    /** Composes a [Label] showing [text]. */
    @Composable
    public fun Content(text: String) {
        Label(text = text)
    }

    override fun createRawSwing(): JLabel = JLabel()

    override fun updateRawSwing(
        component: JLabel,
        parameters: LabelParameters,
    ) {
        component.text = parameters.text
    }
}

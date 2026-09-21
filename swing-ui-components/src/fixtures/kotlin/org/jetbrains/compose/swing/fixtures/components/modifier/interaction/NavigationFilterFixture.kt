// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.modifier.interaction.navigationFilter
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.NavigationFilter
import javax.swing.text.Position

/** The parameters [NavigationFilterFixture] varies: the navigation filter declared on the text field. */
public data class NavigationFilterParameters(
    val filter: NavigationFilter?,
)

/** A [TextField] declaring [navigationFilter] against a raw [JTextField] carrying the same filter. */
public object NavigationFilterFixture : ComposableFixture<NavigationFilterParameters, JTextField>(
    initial = NavigationFilterParameters(filter = NavigationFilter()),
) {
    private const val TEXT = "Text"

    /** The single-change copies of [initial], one for each value the benchmark switches to. */
    override val alternatives: List<NavigationFilterParameters> =
        listOf(
            initial.copy(
                filter =
                    object : NavigationFilter() {
                        override fun setDot(
                            bypass: FilterBypass,
                            dot: Int,
                            bias: Position.Bias,
                        ) {
                            super.setDot(bypass, dot.coerceAtMost(1), bias)
                        }
                    },
            ),
        )

    @Composable
    override fun Content(parameters: NavigationFilterParameters): Unit = Content(parameters.filter)

    /** Composes a [TextField] showing the shared text with [filter] declared on it. */
    @Composable
    public fun Content(filter: NavigationFilter?) {
        TextField(
            value = TEXT,
            onValueChange = {},
            modifier = SwingModifier.navigationFilter(filter),
        )
    }

    /**
     * Builds a [JTextField] showing the shared text, with the document edit listener [TextField] installs for its
     * `onValueChange`, before any of the fixture's values are applied.
     */
    override fun createRawSwing(): JTextField =
        JTextField(TEXT).apply {
            document.addDocumentListener(
                object : DocumentListener {
                    override fun insertUpdate(event: DocumentEvent) = Unit

                    override fun removeUpdate(event: DocumentEvent) = Unit

                    override fun changedUpdate(event: DocumentEvent) = Unit
                },
            )
        }

    /** Declares the navigation filter of [parameters] on [component] with the Swing setter. */
    override fun updateRawSwing(
        component: JTextField,
        parameters: NavigationFilterParameters,
    ) {
        component.navigationFilter = parameters.filter
    }
}

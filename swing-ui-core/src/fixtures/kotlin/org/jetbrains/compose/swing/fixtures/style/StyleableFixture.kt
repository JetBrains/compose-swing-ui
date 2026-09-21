// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.style

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.styleable
import java.awt.Color
import javax.swing.JLabel

/** The text of the host label, shared by the composable and the raw Swing label. */
private const val HOST_TEXT = "Container style"

/** The parameters [StyleableFixture] varies: the foreground color that the container style sets. */
public data class StyleableParameters(
    val color: Color,
)

/**
 * A [Label] styled through the container `styleable(style)` overload against a raw [JLabel] with the same foreground.
 */
public object StyleableFixture : ComposableFixture<StyleableParameters, JLabel>(
    initial = StyleableParameters(color = Color.BLACK),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<StyleableParameters> =
        listOf(
            initial.copy(color = Color.WHITE),
        )

    @Composable
    override fun Content(parameters: StyleableParameters): Unit = Content(parameters.color)

    /** Composes a [Label] whose container style sets its foreground to [color]. */
    @Composable
    public fun Content(color: Color) {
        Label(
            text = HOST_TEXT,
            modifier = SwingModifier.styleable { foreground(color) },
        )
    }

    /** Builds a bare [JLabel] showing the host text, before any of the fixture's values are applied. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /** Applies the foreground color of [parameters] to [component]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: StyleableParameters,
    ) {
        component.foreground = parameters.color
    }
}

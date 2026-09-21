// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.desktop

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JLayeredPane

/** The parameters [LayeredPaneFixture] varies: the modifier name, the foreground color and the content text. */
public data class LayeredPaneParameters(
    val name: String,
    val color: Color,
    val text: String,
)

/**
 * A [LayeredPane] holding one [Label] against a raw [JLayeredPane] with the same name, foreground color and label text.
 */
public object LayeredPaneFixture : ComposableFixture<LayeredPaneParameters, JLayeredPane>(
    initial = LayeredPaneParameters(name = "Before", color = Color.BLUE, text = "Before"),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<LayeredPaneParameters> =
        listOf(
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(text = "After"),
        )

    @Composable
    override fun Content(parameters: LayeredPaneParameters): Unit =
        Content(parameters.name, parameters.color, parameters.text)

    /** Composes a [LayeredPane] named [name], drawn in [color], holding a [Label] showing [text]. */
    @Composable
    public fun Content(
        name: String,
        color: Color,
        text: String,
    ) {
        LayeredPane(
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
        ) {
            Label(text = text)
        }
    }

    /** Builds a bare [JLayeredPane] holding a [JLabel] before any of the fixture's values are applied. */
    override fun createRawSwing(): JLayeredPane =
        JLayeredPane().apply {
            add(JLabel())
        }

    /**
     * Applies the name and label text of [parameters], and the foreground color the style passes down to the label, to
     * [component].
     */
    override fun updateRawSwing(
        component: JLayeredPane,
        parameters: LayeredPaneParameters,
    ) {
        component.name = parameters.name
        component.foreground = parameters.color
        for (child in component.components) {
            if (child is JLabel) {
                child.foreground = parameters.color
                child.text = parameters.text
            }
        }
    }
}

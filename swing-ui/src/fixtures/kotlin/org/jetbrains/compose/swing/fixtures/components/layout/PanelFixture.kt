// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.PanelScope
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.LayoutManager
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters [PanelFixture] varies: the layout, the modifier name, the foreground color and the child's text. */
public data class PanelParameters(
    val layout: PanelLayout<PanelScope>,
    val name: String,
    val color: Color,
    val content: String,
)

/** A [Panel] against a raw [JPanel] holding one label, with the same layout, name, foreground color and text. */
public object PanelFixture : ComposableFixture<PanelParameters, JPanel>(
    initial =
        PanelParameters(
            layout = PanelLayout.Flow(),
            name = "Before",
            color = Color.BLUE,
            content = "Before",
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<PanelParameters> =
        listOf(
            initial.copy(layout = PanelLayout.Grid(rows = 1)),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(content = "After"),
        )

    @Composable
    override fun Content(parameters: PanelParameters): Unit =
        Content(parameters.layout, parameters.name, parameters.color, parameters.content)

    /** Composes a [Panel] under [layout], named [name], drawn in [color], holding one [Label] showing [content]. */
    @Composable
    public fun Content(
        layout: PanelLayout<PanelScope>,
        name: String,
        color: Color,
        content: String,
    ) {
        Panel(
            layout = layout,
            modifier = SwingModifier.name(name),
            style = { foreground(color) },
        ) {
            Label(content)
        }
    }

    /** Builds a bare [JPanel] holding one [JLabel] before any of the fixture's values are applied. */
    override fun createRawSwing(): JPanel = JPanel().apply { add(JLabel()) }

    /**
     * Applies the layout, name, foreground color and child text of [parameters] to [component]. A layout declaration
     * it has installed already is not installed again.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: PanelParameters,
    ) {
        val layoutKey = "fixtureLayout"
        if (component.getClientProperty(layoutKey) != parameters.layout) {
            installLayout(component, parameters.layout)
            component.putClientProperty(layoutKey, parameters.layout)
        }
        component.name = parameters.name
        component.foreground = parameters.color
        for (child in component.components) {
            if (child is JLabel) {
                child.text = parameters.content
                child.foreground = parameters.color
            }
        }
    }

    /**
     * Gives [panel] the layout manager for [layout]: the manager it has is updated in place while it is of the class
     * [layout] names, and a manager of another class is replaced.
     */
    private fun installLayout(
        panel: JPanel,
        layout: PanelLayout<PanelScope>,
    ) {
        val manager: LayoutManager? = panel.layout
        when (layout) {
            is PanelLayout.Flow -> {
                if (manager is FlowLayout) {
                    manager.alignment = layout.alignment
                    manager.hgap = layout.hgap
                    manager.vgap = layout.vgap
                } else {
                    panel.layout = FlowLayout(layout.alignment, layout.hgap, layout.vgap)
                }
                panel.revalidate()
            }

            is PanelLayout.Grid -> {
                if (manager is GridLayout) {
                    // A grid never holds zero rows and zero columns, so the nonzero count is written first.
                    if (layout.rows != 0) manager.rows = layout.rows
                    manager.columns = layout.cols
                    if (layout.rows == 0) manager.rows = 0
                    manager.hgap = layout.hgap
                    manager.vgap = layout.vgap
                } else {
                    panel.layout = GridLayout(layout.rows, layout.cols, layout.hgap, layout.vgap)
                }
                panel.revalidate()
            }

            else -> {
                error("PanelFixture covers only the Flow and Grid layouts")
            }
        }
    }
}

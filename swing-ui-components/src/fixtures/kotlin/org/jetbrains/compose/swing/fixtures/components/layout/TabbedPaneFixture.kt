// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.TabLayoutPolicy
import org.jetbrains.compose.swing.annotations.TabPlacement
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JTabbedPane
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * The parameters [TabbedPaneFixture] varies: the selected index, the selection callback, the enabled state,
 * the foreground color, the tab placement, the tab layout policy and the text of the first page.
 */
public data class TabbedPaneParameters(
    val selectedIndex: Int,
    val onSelectedIndexChange: (Int) -> Unit,
    val enabled: Boolean,
    val color: Color,
    @param:TabPlacement val tabPlacement: Int,
    @param:TabLayoutPolicy val tabLayoutPolicy: Int,
    val firstPageText: String,
)

/**
 * A [TabbedPane] taking an `onSelectedIndexChange` callback against a raw [JTabbedPane] with two tabs, in the
 * same selection, enabled state, color, placement, layout policy and first page text, reporting its selection to
 * the same callback.
 */
public object TabbedPaneFixture : ComposableFixture<TabbedPaneParameters, JTabbedPane>(
    initial =
        TabbedPaneParameters(
            selectedIndex = 0,
            onSelectedIndexChange = {},
            enabled = true,
            color = Color.BLUE,
            tabPlacement = JTabbedPane.TOP,
            tabLayoutPolicy = JTabbedPane.WRAP_TAB_LAYOUT,
            firstPageText = "Before",
        ),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<TabbedPaneParameters> =
        listOf(
            initial.copy(selectedIndex = 1),
            initial.copy(onSelectedIndexChange = {}),
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
            initial.copy(tabPlacement = JTabbedPane.BOTTOM),
            initial.copy(tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT),
            initial.copy(firstPageText = "After"),
        )

    @Composable
    override fun Content(parameters: TabbedPaneParameters): Unit =
        Content(
            parameters.selectedIndex,
            parameters.onSelectedIndexChange,
            parameters.enabled,
            parameters.color,
            parameters.tabPlacement,
            parameters.tabLayoutPolicy,
            parameters.firstPageText,
        )

    /**
     * Composes a [TabbedPane] with [selectedIndex] selected, reporting selections to [onSelectedIndexChange],
     * enabled as [enabled], drawn in [color], with the tab strip at [tabPlacement] and [tabLayoutPolicy], and
     * with a first tab showing [firstPageText] beside a second tab.
     */
    @Composable
    public fun Content(
        selectedIndex: Int,
        onSelectedIndexChange: (Int) -> Unit,
        enabled: Boolean,
        color: Color,
        @TabPlacement tabPlacement: Int,
        @TabLayoutPolicy tabLayoutPolicy: Int,
        firstPageText: String,
    ) {
        TabbedPane(
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            style =
                SwingStyle {
                    enabled(enabled)
                    foreground(color)
                },
            tabPlacement = tabPlacement,
            tabLayoutPolicy = tabLayoutPolicy,
        ) {
            Label(firstPageText, modifier = SwingModifier.tab("First"))
            Label("Second", modifier = SwingModifier.tab("Second"))
        }
    }

    /** Builds a bare [JTabbedPane] with a first tab and a second tab before any of the fixture's values are applied. */
    override fun createRawSwing(): JTabbedPane =
        JTabbedPane().apply {
            addTab("First", JLabel())
            addTab("Second", JLabel("Second"))
        }

    /**
     * Applies the selected index, enabled state, color, placement, layout policy, first page text and selection
     * callback of [parameters] to [component].
     */
    override fun updateRawSwing(
        component: JTabbedPane,
        parameters: TabbedPaneParameters,
    ) {
        val listener =
            component.changeListeners.firstNotNullOfOrNull { it as? SelectionListener }
                ?: SelectionListener(component).also { component.addChangeListener(it) }
        listener.onSelectedIndexChange = parameters.onSelectedIndexChange
        listener.writing = true
        try {
            applyShared(component, parameters)
        } finally {
            listener.writing = false
        }
    }

    /** Applies everything of [parameters] to [component] except the selection callback. */
    internal fun applyShared(
        component: JTabbedPane,
        parameters: TabbedPaneParameters,
    ) {
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
        component.tabPlacement = parameters.tabPlacement
        component.tabLayoutPolicy = parameters.tabLayoutPolicy
        (component.getComponentAt(0) as JLabel).text = parameters.firstPageText
        List(component.tabCount) { component.getComponentAt(it) }.forEach { it.foreground = parameters.color }
        component.selectedIndex = parameters.selectedIndex
    }

    /**
     * The change listener attached once to the raw pane: it reports the selected index to the latest
     * [onSelectedIndexChange] whenever the selection changes, except while [writing].
     */
    private class SelectionListener(
        private val component: JTabbedPane,
    ) : ChangeListener {
        var onSelectedIndexChange: (Int) -> Unit = {}
        var writing: Boolean = false

        override fun stateChanged(event: ChangeEvent) {
            if (!writing) onSelectedIndexChange(component.selectedIndex)
        }
    }
}

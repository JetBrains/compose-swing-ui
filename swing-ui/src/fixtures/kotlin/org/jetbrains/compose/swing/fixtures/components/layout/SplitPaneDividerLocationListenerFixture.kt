// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.beans.PropertyChangeListener
import javax.swing.JLabel
import javax.swing.JSplitPane

/**
 * The parameters [SplitPaneDividerLocationListenerFixture] varies: the divider location listener.
 */
public data class SplitPaneDividerLocationListenerParameters(
    val dividerLocationListener: PropertyChangeListener,
)

/**
 * A [SplitPane] taking a [PropertyChangeListener] instead of an `onDividerLocationChange` callback, against a
 * raw [JSplitPane] in the same state as [SplitPaneFixture.initial].
 */
public object SplitPaneDividerLocationListenerFixture :
    ComposableFixture<SplitPaneDividerLocationListenerParameters, JSplitPane>(
        initial =
            SplitPaneDividerLocationListenerParameters(
                dividerLocationListener = {},
            ),
    ) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<SplitPaneDividerLocationListenerParameters> =
        listOf(
            initial.copy(dividerLocationListener = {}),
        )

    @Composable
    override fun Content(parameters: SplitPaneDividerLocationListenerParameters): Unit =
        Content(parameters.dividerLocationListener)

    /**
     * Composes a [SplitPane] notifying [dividerLocationListener], in the fixed state of [initial] apart from
     * that listener.
     */
    @Composable
    public fun Content(dividerLocationListener: PropertyChangeListener) {
        SplitPane(
            dividerLocationListener = dividerLocationListener,
            style =
                SwingStyle {
                    enabled(true)
                    foreground(Color.BLUE)
                },
            orientation = JSplitPane.HORIZONTAL_SPLIT,
            dividerLocation = 40,
            resizeWeight = 0.25,
            oneTouchExpandable = false,
            dividerSize = 8,
            continuousLayout = false,
        ) {
            Label("Before", modifier = SwingModifier.first())
            Label("Second", modifier = SwingModifier.second())
        }
    }

    /** Builds a [JSplitPane] in the fixed state [Content] composes, before any divider listener is attached. */
    override fun createRawSwing(): JSplitPane =
        JSplitPane(JSplitPane.HORIZONTAL_SPLIT, null, null).apply {
            isEnabled = true
            foreground = Color.BLUE
            resizeWeight = 0.25
            isOneTouchExpandable = false
            dividerSize = 8
            isContinuousLayout = false
            leftComponent = JLabel("Before").apply { foreground = Color.BLUE }
            rightComponent = JLabel("Second").apply { foreground = Color.BLUE }
            dividerLocation = 40
        }

    /** Attaches the divider location listener of [parameters] to [component], replacing the one attached before. */
    override fun updateRawSwing(
        component: JSplitPane,
        parameters: SplitPaneDividerLocationListenerParameters,
    ) {
        val property = JSplitPane.DIVIDER_LOCATION_PROPERTY
        if (parameters.dividerLocationListener !in component.getPropertyChangeListeners(property)) {
            component.removePropertyChangeListener(property, initial.dividerLocationListener)
            alternatives.forEach { component.removePropertyChangeListener(property, it.dividerLocationListener) }
            component.addPropertyChangeListener(property, parameters.dividerLocationListener)
        }
    }
}
